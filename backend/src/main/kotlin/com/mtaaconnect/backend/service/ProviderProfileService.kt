package com.mtaaconnect.backend.service

import com.mtaaconnect.backend.dto.ProviderProfileRequest
import com.mtaaconnect.backend.dto.ProviderProfileResponse
import com.mtaaconnect.backend.model.ProviderProfile
import com.mtaaconnect.backend.model.User
import com.mtaaconnect.backend.model.UserRole
import com.mtaaconnect.backend.repository.ProviderProfileRepository
import com.mtaaconnect.backend.repository.UserRepository
import org.springframework.dao.DuplicateKeyException
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class ProviderProfileService(
    private val providerProfileRepository: ProviderProfileRepository,
    private val userRepository: UserRepository,
) {
    fun create(email: String, request: ProviderProfileRequest): ProviderProfileResponse {
        request.validateMapCoordinates()
        val user = requireProvider(email)
        if (providerProfileRepository.findByUserId(requireUserId(user)) != null) {
            throw MarketplaceConflictException("Provider profile already exists")
        }

        val now = Instant.now()
        val profile = ProviderProfile(
            userId = requireUserId(user),
            businessName = request.businessName.trim(),
            description = request.description.trim(),
            location = request.location.trim(),
            contactInformation = request.contactInformation.trim(),
            latitude = request.latitude,
            longitude = request.longitude,
            createdAt = now,
            updatedAt = now,
        )
        val saved = try {
            providerProfileRepository.save(profile)
        } catch (_: DuplicateKeyException) {
            throw MarketplaceConflictException("Provider profile already exists")
        }
        return saved.toResponse()
    }

    fun update(email: String, request: ProviderProfileRequest): ProviderProfileResponse {
        request.validateMapCoordinates()
        val user = requireProvider(email)
        val profile = providerProfileRepository.findByUserId(requireUserId(user))
            ?: throw MarketplaceNotFoundException("Provider profile not found")
        val updated = profile.copy(
            businessName = request.businessName.trim(),
            description = request.description.trim(),
            location = request.location.trim(),
            contactInformation = request.contactInformation.trim(),
            latitude = request.latitude,
            longitude = request.longitude,
            updatedAt = Instant.now(),
        )
        return providerProfileRepository.save(updated).toResponse()
    }

    fun getById(id: String): ProviderProfileResponse =
        providerProfileRepository.findById(id).orElseThrow {
            MarketplaceNotFoundException("Provider profile not found")
        }.toResponse()

    private fun requireProvider(email: String): User {
        val user = userRepository.findByEmail(email)
            ?: throw MarketplaceNotFoundException("User not found")
        if (user.role != UserRole.PROVIDER) {
            throw AccessDeniedException("Only providers can manage provider profiles")
        }
        return user
    }

    private fun requireUserId(user: User): String =
        user.id ?: throw IllegalStateException("Persisted user is missing an ID")

    private fun ProviderProfile.toResponse() = ProviderProfileResponse(
        id = id,
        userId = userId,
        businessName = businessName,
        description = description,
        location = location,
        contactInformation = contactInformation,
        latitude = latitude,
        longitude = longitude,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun ProviderProfileRequest.validateMapCoordinates() {
        require((latitude == null) == (longitude == null)) {
            "Enter both latitude and longitude to share a map location"
        }
    }
}
