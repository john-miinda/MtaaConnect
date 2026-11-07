package com.mtaaconnect.backend.dto

import com.mtaaconnect.backend.model.BookingStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

data class BookingResponse(
    val id: String?,
    val customerUserId: String,
    val providerUserId: String,
    val serviceId: String,
    val requestedDate: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val timeZone: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val status: BookingStatus,
    val customerNotes: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
