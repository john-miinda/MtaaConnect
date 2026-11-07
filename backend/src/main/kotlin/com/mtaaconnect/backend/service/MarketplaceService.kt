package com.mtaaconnect.backend.service

import com.mtaaconnect.backend.dto.ServiceRequest
import com.mtaaconnect.backend.dto.ServiceResponse
import com.mtaaconnect.backend.model.Service
import com.mtaaconnect.backend.model.ServiceAvailability
import com.mtaaconnect.backend.model.User
import com.mtaaconnect.backend.model.UserRole
import com.mtaaconnect.backend.repository.ProviderProfileRepository
import com.mtaaconnect.backend.repository.ServiceRepository
import com.mtaaconnect.backend.repository.UserRepository
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service as SpringService
import java.time.Instant

@SpringService
class MarketplaceService(
    private val serviceRepository: ServiceRepository,
    private val providerProfileRepository: ProviderProfileRepository,
    private val userRepository: UserRepository,
) {
    fun create(email: String, request: ServiceRequest): ServiceResponse {
        val user = requireProvider(email)
        val profile = providerProfileRepository.findByUserId(requireUserId(user))
            ?: throw MarketplaceConflictException("Create a provider profile before publishing services")
        val now = Instant.now()
        val service = Service(
            providerId = requireNotNull(profile.id) { "Persisted provider profile is missing an ID" },
            name = request.name.trim(),
            description = request.description.trim(),
            category = request.category.trim(),
            price = request.price,
            location = request.location.trim(),
            availabilityStatus = request.availabilityStatus,
            createdAt = now,
            updatedAt = now,
        )
        return serviceRepository.save(service).toResponse()
    }

    fun browse(category: String?, location: String?): List<ServiceResponse> {
        val normalizedCategory = category?.trim()?.takeIf(String::isNotEmpty)
        val normalizedLocation = location?.trim()?.takeIf(String::isNotEmpty)
        val available = ServiceAvailability.AVAILABLE
        val services = when {
            normalizedCategory != null && normalizedLocation != null ->
                serviceRepository.findByAvailabilityStatusAndCategoryIgnoreCaseAndLocationContainingIgnoreCase(
                    available,
                    normalizedCategory,
                    normalizedLocation,
                )
            normalizedCategory != null ->
                serviceRepository.findByAvailabilityStatusAndCategoryIgnoreCase(available, normalizedCategory)
            normalizedLocation != null ->
                serviceRepository.findByAvailabilityStatusAndLocationContainingIgnoreCase(available, normalizedLocation)
            else -> serviceRepository.findByAvailabilityStatus(available)
        }
        return services.map { it.toResponse() }
    }

    fun getById(id: String): ServiceResponse =
        serviceRepository.findById(id)
            .filter { it.availabilityStatus == ServiceAvailability.AVAILABLE }
            .orElseThrow { MarketplaceNotFoundException("Service not found") }
            .toResponse()

    fun getAvailableByProvider(providerId: String): List<ServiceResponse> {
        if (!providerProfileRepository.existsById(providerId)) {
            throw MarketplaceNotFoundException("Provider profile not found")
        }
        return serviceRepository
            .findByProviderIdAndAvailabilityStatus(providerId, ServiceAvailability.AVAILABLE)
            .map { it.toResponse() }
    }

    fun update(email: String, id: String, request: ServiceRequest): ServiceResponse {
        val user = requireProvider(email)
        val service = serviceRepository.findById(id)
            .orElseThrow { MarketplaceNotFoundException("Service not found") }
        requireOwnership(user, service)
        val updated = service.copy(
            name = request.name.trim(),
            description = request.description.trim(),
            category = request.category.trim(),
            price = request.price,
            location = request.location.trim(),
            availabilityStatus = request.availabilityStatus,
            updatedAt = Instant.now(),
        )
        return serviceRepository.save(updated).toResponse()
    }

    fun delete(email: String, id: String) {
        val user = requireProvider(email)
        val service = serviceRepository.findById(id)
            .orElseThrow { MarketplaceNotFoundException("Service not found") }
        requireOwnership(user, service)
        serviceRepository.delete(service)
    }

    private fun requireProvider(email: String): User {
        val user = userRepository.findByEmail(email)
            ?: throw MarketplaceNotFoundException("User not found")
        if (user.role != UserRole.PROVIDER) {
            throw AccessDeniedException("Only providers can manage services")
        }
        return user
    }

    private fun requireOwnership(user: User, service: Service) {
        val profile = providerProfileRepository.findByUserId(requireUserId(user))
            ?: throw AccessDeniedException("Provider does not own this service")
        if (profile.id != service.providerId) {
            throw AccessDeniedException("Provider does not own this service")
        }
    }

    private fun requireUserId(user: User): String =
        user.id ?: throw IllegalStateException("Persisted user is missing an ID")

    private fun Service.toResponse() = ServiceResponse(
        id = id,
        providerId = providerId,
        name = name,
        description = description,
        category = category,
        price = price,
        location = location,
        availabilityStatus = availabilityStatus,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
