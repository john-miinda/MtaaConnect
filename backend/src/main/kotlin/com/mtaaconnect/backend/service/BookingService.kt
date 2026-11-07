package com.mtaaconnect.backend.service

import com.mtaaconnect.backend.dto.BookingResponse
import com.mtaaconnect.backend.dto.CreateBookingRequest
import com.mtaaconnect.backend.model.Booking
import com.mtaaconnect.backend.model.BookingSlotReservation
import com.mtaaconnect.backend.model.BookingStatus
import com.mtaaconnect.backend.model.ServiceAvailability
import com.mtaaconnect.backend.model.User
import com.mtaaconnect.backend.model.UserRole
import com.mtaaconnect.backend.repository.BookingRepository
import com.mtaaconnect.backend.repository.BookingSlotReservationRepository
import com.mtaaconnect.backend.repository.ProviderProfileRepository
import com.mtaaconnect.backend.repository.ServiceRepository
import com.mtaaconnect.backend.repository.UserRepository
import org.springframework.dao.DuplicateKeyException
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

@Service
class BookingService(
    private val bookingRepository: BookingRepository,
    private val reservationRepository: BookingSlotReservationRepository,
    private val serviceRepository: ServiceRepository,
    private val providerProfileRepository: ProviderProfileRepository,
    private val userRepository: UserRepository,
) {
    fun create(customerEmail: String, request: CreateBookingRequest): BookingResponse {
        val customer = requireRole(customerEmail, UserRole.CUSTOMER)
        val customerId = requireUserId(customer)
        val service = serviceRepository.findById(request.serviceId)
            .orElseThrow { MarketplaceNotFoundException("Service not found") }
        if (service.availabilityStatus != ServiceAvailability.AVAILABLE) {
            throw MarketplaceConflictException("Service is not available")
        }

        val profile = providerProfileRepository.findById(service.providerId)
            .orElseThrow { MarketplaceConflictException("Service provider profile is unavailable") }
        val provider = userRepository.findById(profile.userId)
            .filter { it.role == UserRole.PROVIDER }
            .orElseThrow { MarketplaceConflictException("Service provider is unavailable") }
        val providerId = requireUserId(provider)

        val zone = parseZoneId(request.timeZone)
        val startsAt = resolveDateTime(request.requestedDate, request.startTime, zone)
        val endsAt = resolveDateTime(request.requestedDate, request.endTime, zone)
        val now = Instant.now()
        if (!startsAt.isAfter(now)) {
            throw IllegalArgumentException("Booking date and time must be in the future")
        }
        if (!endsAt.isAfter(startsAt)) {
            throw IllegalArgumentException("Booking end time must be later than start time")
        }
        val duration = Duration.between(startsAt, endsAt)
        if (duration > MAX_BOOKING_DURATION) {
            throw IllegalArgumentException("A booking cannot be longer than 24 hours")
        }
        if (startsAt.epochSecond % SLOT_SECONDS != 0L || endsAt.epochSecond % SLOT_SECONDS != 0L) {
            throw IllegalArgumentException("Booking times must align to 15-minute intervals")
        }

        val bookingId = UUID.randomUUID().toString()
        val slotStarts = slotsBetween(startsAt, endsAt)
        if (reservationRepository.existsByProviderUserIdAndSlotStartIn(providerId, slotStarts)) {
            throw MarketplaceConflictException("The provider is already booked for this time")
        }

        val booking = Booking(
            id = bookingId,
            customerUserId = customerId,
            providerUserId = providerId,
            serviceId = service.id ?: throw IllegalStateException("Persisted service is missing an ID"),
            requestedDate = request.requestedDate,
            startTime = request.startTime,
            endTime = request.endTime,
            timeZone = zone.id,
            startsAt = startsAt,
            endsAt = endsAt,
            status = BookingStatus.PENDING,
            customerNotes = request.customerNotes?.trim()?.takeIf(String::isNotEmpty),
            createdAt = now,
            updatedAt = now,
        )

        try {
            slotStarts.forEach { slotStart ->
                reservationRepository.save(
                    BookingSlotReservation(
                        providerUserId = providerId,
                        slotStart = slotStart,
                        bookingId = bookingId,
                        attemptId = bookingId,
                    ),
                )
            }
            return bookingRepository.save(booking).toResponse()
        } catch (exception: DuplicateKeyException) {
            reservationRepository.deleteByBookingId(bookingId)
            throw MarketplaceConflictException("The provider is already booked for this time")
        } catch (exception: RuntimeException) {
            try {
                reservationRepository.deleteByBookingId(bookingId)
            } catch (cleanupException: RuntimeException) {
                exception.addSuppressed(cleanupException)
            }
            throw exception
        }
    }

    fun customerBookings(email: String): List<BookingResponse> {
        val customer = requireRole(email, UserRole.CUSTOMER)
        return bookingRepository
            .findByCustomerUserIdOrderByRequestedDateDescStartTimeDesc(requireUserId(customer))
            .map { it.toResponse() }
    }

    fun providerBookings(email: String): List<BookingResponse> {
        val provider = requireRole(email, UserRole.PROVIDER)
        return bookingRepository
            .findByProviderUserIdOrderByRequestedDateDescStartTimeDesc(requireUserId(provider))
            .map { it.toResponse() }
    }

    fun getById(email: String, id: String): BookingResponse {
        val user = requireUser(email)
        val booking = bookingRepository.findById(id)
            .orElseThrow { MarketplaceNotFoundException("Booking not found") }
        if (user.id != booking.customerUserId && user.id != booking.providerUserId) {
            throw AccessDeniedException("User does not own this booking")
        }
        if (user.role == UserRole.CUSTOMER && user.id != booking.customerUserId ||
            user.role == UserRole.PROVIDER && user.id != booking.providerUserId
        ) {
            throw AccessDeniedException("User does not own this booking")
        }
        if (user.role != UserRole.CUSTOMER && user.role != UserRole.PROVIDER) {
            throw AccessDeniedException("Only customers and providers can access bookings")
        }
        return booking.toResponse()
    }

    fun cancel(email: String, id: String): BookingResponse {
        val customer = requireRole(email, UserRole.CUSTOMER)
        val booking = bookingRepository.findById(id)
            .orElseThrow { MarketplaceNotFoundException("Booking not found") }
        if (booking.customerUserId != requireUserId(customer)) {
            throw AccessDeniedException("User does not own this booking")
        }
        if (booking.status != BookingStatus.PENDING && booking.status != BookingStatus.ACCEPTED) {
            throw MarketplaceConflictException("Only pending or accepted bookings can be cancelled")
        }
        val cancelled = transition(booking, BookingStatus.CANCELLED)
        reservationRepository.deleteByBookingId(id)
        return cancelled.toResponse()
    }

    fun accept(email: String, id: String): BookingResponse =
        providerTransition(email, id, BookingStatus.PENDING, BookingStatus.ACCEPTED)

    fun reject(email: String, id: String): BookingResponse {
        val rejected = providerTransition(email, id, BookingStatus.PENDING, BookingStatus.REJECTED)
        reservationRepository.deleteByBookingId(id)
        return rejected
    }

    fun complete(email: String, id: String): BookingResponse =
        providerTransition(email, id, BookingStatus.ACCEPTED, BookingStatus.COMPLETED)

    private fun providerTransition(
        email: String,
        id: String,
        expectedStatus: BookingStatus,
        newStatus: BookingStatus,
    ): BookingResponse {
        val provider = requireRole(email, UserRole.PROVIDER)
        val booking = bookingRepository.findById(id)
            .orElseThrow { MarketplaceNotFoundException("Booking not found") }
        if (booking.providerUserId != requireUserId(provider)) {
            throw AccessDeniedException("Provider does not own this booking")
        }
        if (booking.status != expectedStatus) {
            throw MarketplaceConflictException("Booking cannot transition from ${booking.status} to $newStatus")
        }
        return transition(booking, newStatus).toResponse()
    }

    private fun transition(booking: Booking, newStatus: BookingStatus): Booking =
        bookingRepository.transitionStatus(
            id = booking.id ?: throw IllegalStateException("Persisted booking is missing an ID"),
            expectedStatus = booking.status,
            newStatus = newStatus,
        ) ?: throw MarketplaceConflictException("Booking status changed; refresh and try again")

    private fun requireRole(email: String, role: UserRole): User {
        val user = requireUser(email)
        if (user.role != role) {
            throw AccessDeniedException("This operation requires the $role role")
        }
        return user
    }

    private fun requireUser(email: String): User =
        userRepository.findByEmail(email)
            ?: throw MarketplaceNotFoundException("User not found")

    private fun requireUserId(user: User): String =
        user.id ?: throw IllegalStateException("Persisted user is missing an ID")

    private fun parseZoneId(value: String): ZoneId =
        try {
            ZoneId.of(value)
        } catch (exception: DateTimeException) {
            throw IllegalArgumentException("A valid timeZone is required", exception)
        }

    private fun resolveDateTime(date: java.time.LocalDate, time: LocalTime, zone: ZoneId): Instant {
        val localDateTime = LocalDateTime.of(date, time)
        val offsets = zone.rules.getValidOffsets(localDateTime)
        if (offsets.size != 1) {
            throw IllegalArgumentException("Booking time is invalid or ambiguous in the requested time zone")
        }
        return localDateTime.toInstant(offsets.single())
    }

    private fun slotsBetween(startsAt: Instant, endsAt: Instant): List<Instant> {
        val slots = mutableListOf<Instant>()
        var slotStart = startsAt
        while (slotStart.isBefore(endsAt)) {
            slots += slotStart
            slotStart = slotStart.plusSeconds(SLOT_SECONDS)
        }
        return slots
    }

    private fun Booking.toResponse() = BookingResponse(
        id = id,
        customerUserId = customerUserId,
        providerUserId = providerUserId,
        serviceId = serviceId,
        requestedDate = requestedDate,
        startTime = startTime,
        endTime = endTime,
        timeZone = timeZone,
        startsAt = startsAt,
        endsAt = endsAt,
        status = status,
        customerNotes = customerNotes,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private companion object {
        const val SLOT_SECONDS = 15 * 60L
        val MAX_BOOKING_DURATION: Duration = Duration.ofHours(24)
    }
}
