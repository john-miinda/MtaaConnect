package com.mtaaconnect.backend.repository

import com.mtaaconnect.backend.model.Service
import com.mtaaconnect.backend.model.ServiceAvailability
import org.springframework.data.mongodb.repository.MongoRepository

interface ServiceRepository : MongoRepository<Service, String> {
    fun findByProviderId(providerId: String): List<Service>

    fun findByAvailabilityStatus(availabilityStatus: ServiceAvailability): List<Service>

    fun findByAvailabilityStatusAndCategoryIgnoreCase(
        availabilityStatus: ServiceAvailability,
        category: String,
    ): List<Service>

    fun findByAvailabilityStatusAndLocationContainingIgnoreCase(
        availabilityStatus: ServiceAvailability,
        location: String,
    ): List<Service>

    fun findByAvailabilityStatusAndCategoryIgnoreCaseAndLocationContainingIgnoreCase(
        availabilityStatus: ServiceAvailability,
        category: String,
        location: String,
    ): List<Service>

    fun findByProviderIdAndAvailabilityStatus(
        providerId: String,
        availabilityStatus: ServiceAvailability,
    ): List<Service>
}
