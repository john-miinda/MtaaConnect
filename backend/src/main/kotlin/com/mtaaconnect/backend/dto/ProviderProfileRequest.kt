package com.mtaaconnect.backend.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

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
)
