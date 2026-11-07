package com.mtaaconnect.backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@Document(collection = "bookings")
@CompoundIndexes(
    CompoundIndex(
        name = "customer_bookings_index",
        def = "{'customerUserId': 1, 'requestedDate': -1}",
    ),
    CompoundIndex(
        name = "provider_bookings_index",
        def = "{'providerUserId': 1, 'requestedDate': -1, 'status': 1}",
    ),
    CompoundIndex(
        name = "provider_schedule_index",
        def = "{'providerUserId': 1, 'startsAt': 1, 'endsAt': 1, 'status': 1}",
    ),
)
data class Booking(
    @Id
    val id: String? = null,
    @field:Indexed
    val customerUserId: String,
    @field:Indexed
    val providerUserId: String,
    @field:Indexed
    val serviceId: String,
    val requestedDate: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val timeZone: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val status: BookingStatus = BookingStatus.PENDING,
    val customerNotes: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = createdAt,
)
