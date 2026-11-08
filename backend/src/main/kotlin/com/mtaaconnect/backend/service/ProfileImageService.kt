package com.mtaaconnect.backend.service

import com.mtaaconnect.backend.dto.ProfileImageResponse
import com.mtaaconnect.backend.model.ProfileImage
import com.mtaaconnect.backend.model.ProfileImageCategory
import com.mtaaconnect.backend.model.ServiceAvailability
import com.mtaaconnect.backend.model.UserRole
import com.mtaaconnect.backend.repository.ProfileImageRepository
import com.mtaaconnect.backend.repository.ProviderProfileRepository
import com.mtaaconnect.backend.repository.ServiceRepository
import com.mtaaconnect.backend.repository.UserRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.UUID
import javax.imageio.ImageIO

data class StoredProfileImage(
    val content: ByteArray,
    val contentType: String,
    val isPublic: Boolean,
)

@Service
class ProfileImageService(
    private val profileImageRepository: ProfileImageRepository,
    private val providerProfileRepository: ProviderProfileRepository,
    private val serviceRepository: ServiceRepository,
    private val userRepository: UserRepository,
    @Value("\${mtaaconnect.upload-directory:./uploads}") uploadDirectory: String,
) {
    private val uploadDirectory: Path = Path.of(uploadDirectory).toAbsolutePath().normalize()

    fun upload(
        email: String,
        category: ProfileImageCategory,
        serviceId: String?,
        file: MultipartFile,
    ): ProfileImageResponse {
        val user = userRepository.findByEmail(email)
            ?: throw MarketplaceNotFoundException("User not found")
        val userId = user.id ?: throw IllegalStateException("Persisted user is missing an ID")
        validateCategory(user.role, category)
        validateFile(file)

        val providerProfileId = when (category) {
            ProfileImageCategory.PROVIDER_PROFILE, ProfileImageCategory.PORTFOLIO -> {
                val profile = providerProfileRepository.findByUserId(userId)
                    ?: throw MarketplaceConflictException("Create a provider profile before uploading business images")
                requireNotNull(profile.id) { "Persisted provider profile is missing an ID" }
            }
            ProfileImageCategory.SERVICE -> {
                val id = serviceId ?: throw IllegalArgumentException("A service ID is required for service images")
                val service = serviceRepository.findById(id)
                    .orElseThrow { MarketplaceNotFoundException("Service not found") }
                val profile = providerProfileRepository.findByUserId(userId)
                    ?: throw AccessDeniedException("Provider does not own this service")
                if (profile.id != service.providerId) throw AccessDeniedException("Provider does not own this service")
                profile.id
            }
            ProfileImageCategory.CUSTOMER_PROFILE -> {
                if (serviceId != null) throw IllegalArgumentException("A customer profile image cannot reference a service")
                null
            }
        }
        if (category != ProfileImageCategory.SERVICE && serviceId != null) {
            throw IllegalArgumentException("A service ID is only valid for service images")
        }

        val (contentType, extension) = identifyImage(file.bytes)
        validateImageDimensions(file.bytes)
        val storageKey = "${UUID.randomUUID()}.$extension"
        val target = uploadDirectory.resolve(storageKey).normalize()
        if (!target.startsWith(uploadDirectory)) throw IllegalStateException("Invalid image storage path")

        var fileCreated = false
        try {
            Files.createDirectories(uploadDirectory)
            Files.write(target, file.bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
            fileCreated = true
            val saved = profileImageRepository.save(
                ProfileImage(
                    ownerUserId = userId,
                    category = category,
                    providerProfileId = providerProfileId,
                    serviceId = serviceId,
                    storageKey = storageKey,
                    contentType = contentType,
                    sizeBytes = file.size,
                ),
            )
            return saved.toResponse()
        } catch (exception: Exception) {
            if (fileCreated) {
                try {
                    Files.deleteIfExists(target)
                } catch (cleanupException: IOException) {
                    exception.addSuppressed(cleanupException)
                }
            }
            throw exception
        }
    }

    fun getOwnedImages(email: String): List<ProfileImageResponse> {
        val userId = requireUserId(email)
        return profileImageRepository.findByOwnerUserIdOrderByCreatedAtDesc(userId).map { it.toResponse() }
    }

    fun getProviderImages(providerProfileId: String): List<ProfileImageResponse> {
        if (!providerProfileRepository.existsById(providerProfileId)) {
            throw MarketplaceNotFoundException("Provider profile not found")
        }
        return profileImageRepository
            .findByProviderProfileIdAndServiceIdIsNullOrderByCreatedAtDesc(providerProfileId)
            .map { it.toResponse() }
    }

    fun getServiceImages(serviceId: String): List<ProfileImageResponse> {
        val service = serviceRepository.findById(serviceId)
            .filter { it.availabilityStatus == ServiceAvailability.AVAILABLE }
            .orElseThrow { MarketplaceNotFoundException("Service not found") }
        return profileImageRepository.findByServiceIdOrderByCreatedAtDesc(serviceId)
            .filter { it.category == ProfileImageCategory.SERVICE && it.providerProfileId == service.providerId }
            .map { it.toResponse() }
    }

    fun getImage(id: String, email: String?): StoredProfileImage {
        val image = profileImageRepository.findById(id).orElseThrow {
            MarketplaceNotFoundException("Image not found")
        }
        when (image.category) {
            ProfileImageCategory.CUSTOMER_PROFILE -> {
                if (email == null || requireUserId(email) != image.ownerUserId) {
                    throw AccessDeniedException("Access denied")
                }
            }
            ProfileImageCategory.SERVICE -> {
                val serviceId = image.serviceId ?: throw MarketplaceNotFoundException("Image not found")
                val service = serviceRepository.findById(serviceId)
                    .filter { it.availabilityStatus == ServiceAvailability.AVAILABLE }
                    .orElseThrow { MarketplaceNotFoundException("Image not found") }
                if (service.providerId != image.providerProfileId) {
                    throw MarketplaceNotFoundException("Image not found")
                }
            }
            ProfileImageCategory.PROVIDER_PROFILE, ProfileImageCategory.PORTFOLIO -> Unit
        }
        val path = uploadDirectory.resolve(image.storageKey).normalize()
        if (!path.startsWith(uploadDirectory) || !Files.isRegularFile(path)) {
            throw MarketplaceNotFoundException("Image not found")
        }
        return try {
            StoredProfileImage(
                content = Files.readAllBytes(path),
                contentType = image.contentType,
                isPublic = image.category != ProfileImageCategory.CUSTOMER_PROFILE,
            )
        } catch (exception: IOException) {
            throw IllegalStateException("Stored image could not be read", exception)
        }
    }

    fun delete(email: String, id: String) {
        val userId = requireUserId(email)
        val image = profileImageRepository.findByIdAndOwnerUserId(id, userId)
            ?: throw MarketplaceNotFoundException("Image not found")
        profileImageRepository.delete(image)
        val path = uploadDirectory.resolve(image.storageKey).normalize()
        if (path.startsWith(uploadDirectory)) Files.deleteIfExists(path)
    }

    private fun requireUserId(email: String): String {
        val user = userRepository.findByEmail(email) ?: throw MarketplaceNotFoundException("User not found")
        return user.id ?: throw IllegalStateException("Persisted user is missing an ID")
    }

    private fun validateCategory(role: UserRole, category: ProfileImageCategory) {
        val allowed = when (role) {
            UserRole.CUSTOMER -> setOf(ProfileImageCategory.CUSTOMER_PROFILE)
            UserRole.PROVIDER -> setOf(
                ProfileImageCategory.PROVIDER_PROFILE,
                ProfileImageCategory.PORTFOLIO,
                ProfileImageCategory.SERVICE,
            )
            UserRole.ADMIN -> emptySet()
        }
        if (category !in allowed) throw AccessDeniedException("Account cannot upload this image type")
    }

    private fun validateFile(file: MultipartFile) {
        if (file.isEmpty) throw IllegalArgumentException("Choose an image to upload")
        if (file.size > MAX_FILE_SIZE_BYTES) throw IllegalArgumentException("Images must be 5 MB or smaller")
    }

    private fun identifyImage(bytes: ByteArray): Pair<String, String> {
        val isPng = bytes.size >= 8 &&
            bytes.sliceArray(0..7).contentEquals(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
        val isJpeg = bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
        return when {
            isPng -> "image/png" to "png"
            isJpeg -> "image/jpeg" to "jpg"
            else -> throw IllegalArgumentException("Only valid JPEG and PNG images are supported")
        }
    }

    private fun validateImageDimensions(bytes: ByteArray) {
        try {
            ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { input ->
                val readers = ImageIO.getImageReaders(input)
                if (!readers.hasNext()) throw IllegalArgumentException("The selected file is not a valid image")
                val reader = readers.next()
                try {
                    reader.input = input
                    val width = reader.getWidth(0)
                    val height = reader.getHeight(0)
                    if (
                        width <= 0 || height <= 0 ||
                        width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION ||
                        width.toLong() * height > MAX_IMAGE_PIXELS
                    ) {
                        throw IllegalArgumentException("Images must not exceed 6000 pixels per side or 25 megapixels")
                    }
                } finally {
                    reader.dispose()
                }
            }
        } catch (exception: IOException) {
            throw IllegalArgumentException("The selected file is not a valid image", exception)
        }
    }

    private fun ProfileImage.toResponse() = ProfileImageResponse(
        id = requireNotNull(id) { "Persisted profile image is missing an ID" },
        category = category,
        url = "/api/media/$id",
        serviceId = serviceId,
        contentType = contentType,
        sizeBytes = sizeBytes,
        createdAt = createdAt,
    )

    private companion object {
        const val MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024
        const val MAX_IMAGE_DIMENSION = 6000
        const val MAX_IMAGE_PIXELS = 25_000_000L
    }
}
