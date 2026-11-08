package com.mtaaconnect.backend.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin

data class ProviderProfileRequest(
    @field:NotBlank
    @field:Size(max = 120)
    val businessName: String,
    @field:NotBlank
    @field:Size(max = 2000)
    val description: String,
    @field:NotBlank
    @field:Size(max = 200)
    val location: String,
    @field:NotBlank
    @field:Size(max = 200)
    val contactInformation: String,
    @field:DecimalMin("-90.0")
    @field:DecimalMax("90.0")
    val latitude: Double? = null,
    @field:DecimalMin("-180.0")
    @field:DecimalMax("180.0")
    val longitude: Double? = null,
)
