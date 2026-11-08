package com.mtaaconnect.backend.service

import com.mtaaconnect.backend.model.ProfileImage
import com.mtaaconnect.backend.model.ProfileImageCategory
import com.mtaaconnect.backend.model.ProviderProfile
import com.mtaaconnect.backend.model.Service
import com.mtaaconnect.backend.model.ServiceAvailability
import com.mtaaconnect.backend.model.User
import com.mtaaconnect.backend.model.UserRole
import com.mtaaconnect.backend.repository.ProfileImageRepository
import com.mtaaconnect.backend.repository.ProviderProfileRepository
import com.mtaaconnect.backend.repository.ServiceRepository
import com.mtaaconnect.backend.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.access.AccessDeniedException
import java.nio.file.Files
import java.nio.file.Path
import java.math.BigDecimal
import java.util.Base64
import java.util.Optional

class ProfileImageServiceTest {
    @TempDir
    lateinit var tempDirectory: Path

    @Test
    fun `provider image is signature validated stored on disk and recorded in Mongo metadata`() {
        val fixture = createService()
        val service = fixture.service
        val imageBytes = onePixelPng()
        val provider = User(
            id = "provider-1",
            fullName = "Provider",
            email = "provider@example.com",
            passwordHash = "hash",
            phoneNumber = "123",
            role = UserRole.PROVIDER,
        )
        Mockito.`when`(fixture.users.findByEmail(provider.email)).thenReturn(provider)
        Mockito.`when`(fixture.profiles.findByUserId("provider-1"))
            .thenReturn(ProviderProfile(id = "profile-1", userId = "provider-1", businessName = "Studio", description = "", location = "", contactInformation = ""))
        Mockito.`when`(fixture.images.save(Mockito.any(ProfileImage::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<ProfileImage>(0).copy(id = "image-1") }

        val response = service.upload(
            provider.email,
            ProfileImageCategory.PORTFOLIO,
            null,
            MockMultipartFile("file", "portfolio.png", "image/png", imageBytes),
        )

        assertEquals("image-1", response.id)
        assertEquals("/api/media/image-1", response.url)
        assertEquals("image/png", response.contentType)
        val metadataCaptor = ArgumentCaptor.forClass(ProfileImage::class.java)
        Mockito.verify(fixture.images).save(metadataCaptor.capture())
        val metadata = metadataCaptor.value
        assertEquals("profile-1", metadata.providerProfileId)
        assertEquals(ProfileImageCategory.PORTFOLIO, metadata.category)
        assertTrue(Files.exists(tempDirectory.resolve(metadata.storageKey)))
    }

    @Test
    fun `customer cannot upload a provider portfolio image`() {
        val fixture = createService()
        val customer = User(
            id = "customer-1",
            fullName = "Customer",
            email = "customer@example.com",
            passwordHash = "hash",
            phoneNumber = "123",
            role = UserRole.CUSTOMER,
        )
        Mockito.`when`(fixture.users.findByEmail(customer.email)).thenReturn(customer)

        assertThrows(AccessDeniedException::class.java) {
            fixture.service.upload(
                customer.email,
                ProfileImageCategory.PORTFOLIO,
                null,
                MockMultipartFile("file", "portfolio.png", "image/png", onePixelPng()),
            )
        }
    }

    @Test
    fun `provider cannot upload an image to another providers service`() {
        val fixture = createService()
        val provider = User(
            id = "provider-1",
            fullName = "Provider",
            email = "provider@example.com",
            passwordHash = "hash",
            phoneNumber = "123",
            role = UserRole.PROVIDER,
        )
        Mockito.`when`(fixture.users.findByEmail(provider.email)).thenReturn(provider)
        Mockito.`when`(fixture.profiles.findByUserId("provider-1"))
            .thenReturn(ProviderProfile(id = "profile-1", userId = "provider-1", businessName = "Studio", description = "", location = "", contactInformation = ""))
        Mockito.`when`(fixture.services.findById("service-owned-by-other"))
            .thenReturn(
                Optional.of(
                    Service(
                        id = "service-owned-by-other",
                        providerId = "profile-other",
                        name = "Other service",
                        description = "",
                        category = "Other",
                        price = BigDecimal.ONE,
                        location = "Nairobi",
                        availabilityStatus = ServiceAvailability.AVAILABLE,
                    ),
                ),
            )

        assertThrows(AccessDeniedException::class.java) {
            fixture.service.upload(
                provider.email,
                ProfileImageCategory.SERVICE,
                "service-owned-by-other",
                MockMultipartFile("file", "service.png", "image/png", onePixelPng()),
            )
        }
        assertEquals(0, Files.list(tempDirectory).use { it.count() })
    }

    @Test
    fun `image content must match supported image signatures`() {
        val fixture = createService()
        val provider = User(
            id = "provider-1",
            fullName = "Provider",
            email = "provider@example.com",
            passwordHash = "hash",
            phoneNumber = "123",
            role = UserRole.PROVIDER,
        )
        Mockito.`when`(fixture.users.findByEmail(provider.email)).thenReturn(provider)
        Mockito.`when`(fixture.profiles.findByUserId("provider-1"))
            .thenReturn(ProviderProfile(id = "profile-1", userId = "provider-1", businessName = "Studio", description = "", location = "", contactInformation = ""))

        assertThrows(IllegalArgumentException::class.java) {
            fixture.service.upload(
                provider.email,
                ProfileImageCategory.PROVIDER_PROFILE,
                null,
                MockMultipartFile("file", "fake.png", "image/png", "not an image".toByteArray()),
            )
        }
        assertEquals(0, Files.list(tempDirectory).use { it.count() })
    }

    @Test
    fun `customer profile images cannot be read by another account`() {
        val fixture = createService()
        val customer = User(
            id = "other-customer",
            fullName = "Customer",
            email = "other@example.com",
            passwordHash = "hash",
            phoneNumber = "123",
            role = UserRole.CUSTOMER,
        )
        Mockito.`when`(fixture.images.findById("private-image"))
            .thenReturn(
                Optional.of(
                    ProfileImage(
                        id = "private-image",
                        ownerUserId = "image-owner",
                        category = ProfileImageCategory.CUSTOMER_PROFILE,
                        storageKey = "private.png",
                        contentType = "image/png",
                        sizeBytes = 1,
                    ),
                ),
            )
        Mockito.`when`(fixture.users.findByEmail(customer.email)).thenReturn(customer)

        assertThrows(AccessDeniedException::class.java) {
            fixture.service.getImage("private-image", customer.email)
        }
        assertThrows(AccessDeniedException::class.java) {
            fixture.service.getImage("private-image", null)
        }
    }

    private data class Fixture(
        val service: ProfileImageService,
        val images: ProfileImageRepository,
        val users: UserRepository,
        val profiles: ProviderProfileRepository,
        val services: ServiceRepository,
    )

    private fun createService(): Fixture {
        val imageRepository = Mockito.mock(ProfileImageRepository::class.java)
        val providerRepository = Mockito.mock(ProviderProfileRepository::class.java)
        val serviceRepository = Mockito.mock(ServiceRepository::class.java)
        val userRepository = Mockito.mock(UserRepository::class.java)
        return Fixture(
            ProfileImageService(
                imageRepository,
                providerRepository,
                serviceRepository,
                userRepository,
                tempDirectory.toString(),
            ),
            imageRepository,
            userRepository,
            providerRepository,
            serviceRepository,
        )
    }

    private fun onePixelPng(): ByteArray =
        Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p8sAAAAASUVORK5CYII=")
}
