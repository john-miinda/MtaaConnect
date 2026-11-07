package com.mtaaconnect.backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "booking_slot_reservations")
@CompoundIndex(
    name = "provider_slot_unique_index",
    def = "{'providerUserId': 1, 'slotStart': 1}",
    unique = true,
)
data class BookingSlotReservation(
    @Id
    val id: String? = null,
    val providerUserId: String,
    val slotStart: Instant,
    val bookingId: String,
    val attemptId: String,
)
