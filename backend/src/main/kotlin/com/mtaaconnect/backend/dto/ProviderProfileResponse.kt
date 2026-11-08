package com.mtaaconnect.backend.dto

import java.time.Instant

data class ProviderProfileResponse(
    val id: String?,
    val userId: String,
    val businessName: String,
    val description: String,
    val location: String,
    val contactInformation: String,
    val latitude: Double?,
    val longitude: Double?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
