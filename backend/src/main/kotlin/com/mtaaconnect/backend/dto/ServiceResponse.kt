package com.mtaaconnect.backend.dto

import com.mtaaconnect.backend.model.ServiceAvailability
import java.math.BigDecimal
import java.time.Instant

data class ServiceResponse(
    val id: String?,
    val providerId: String,
    val name: String,
    val description: String,
    val category: String,
    val price: BigDecimal,
    val location: String,
    val availabilityStatus: ServiceAvailability,
    val createdAt: Instant,
    val updatedAt: Instant,
)
