package com.mtaaconnect.backend.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.time.LocalTime

data class CreateBookingRequest(
    @field:NotBlank
    val serviceId: String,
    @field:NotNull
    val requestedDate: LocalDate,
    @field:NotNull
    val startTime: LocalTime,
    @field:NotNull
    val endTime: LocalTime,
    @field:NotBlank
    val timeZone: String,
    @field:Size(max = 1000)
    val customerNotes: String? = null,
)
