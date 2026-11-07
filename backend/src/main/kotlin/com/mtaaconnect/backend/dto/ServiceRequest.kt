package com.mtaaconnect.backend.dto

import com.mtaaconnect.backend.model.ServiceAvailability
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal

data class ServiceRequest(
    @field:NotBlank
    @field:Size(max = 120)
    val name: String,
    @field:NotBlank
    @field:Size(max = 2000)
    val description: String,
    @field:NotBlank
    @field:Size(max = 100)
    val category: String,
    @field:NotNull
    @field:DecimalMin(value = "0.01", message = "Price must be greater than zero")
    @field:Digits(integer = 10, fraction = 2, message = "Price must have at most 10 integer and 2 decimal digits")
    val price: BigDecimal,
    @field:NotBlank
    @field:Size(max = 200)
    val location: String,
    val availabilityStatus: ServiceAvailability = ServiceAvailability.AVAILABLE,
)
