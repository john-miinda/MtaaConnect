package com.mtaaconnect.backend.repository

import com.mtaaconnect.backend.model.BookingSlotReservation
import org.springframework.data.mongodb.repository.MongoRepository
import java.time.Instant

interface BookingSlotReservationRepository : MongoRepository<BookingSlotReservation, String> {
    fun existsByProviderUserIdAndSlotStartIn(providerUserId: String, slotStarts: Collection<Instant>): Boolean

    fun findByProviderUserIdAndSlotStartIn(
        providerUserId: String,
        slotStarts: Collection<Instant>,
    ): List<BookingSlotReservation>

    fun deleteByAttemptId(attemptId: String)

    fun deleteByBookingId(bookingId: String)
}
