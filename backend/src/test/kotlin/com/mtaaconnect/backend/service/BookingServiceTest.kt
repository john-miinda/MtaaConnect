package com.mtaaconnect.backend.service

import com.mtaaconnect.backend.dto.CreateBookingRequest
import com.mtaaconnect.backend.model.Booking
import com.mtaaconnect.backend.model.BookingSlotReservation
import com.mtaaconnect.backend.model.BookingStatus
import com.mtaaconnect.backend.model.ProviderProfile
import com.mtaaconnect.backend.model.Service
import com.mtaaconnect.backend.model.ServiceAvailability
import com.mtaaconnect.backend.model.User
import com.mtaaconnect.backend.model.UserRole
import com.mtaaconnect.backend.repository.BookingRepository
import com.mtaaconnect.backend.repository.BookingSlotReservationRepository
import com.mtaaconnect.backend.repository.ProviderProfileRepository
import com.mtaaconnect.backend.repository.ServiceRepository
import com.mtaaconnect.backend.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.springframework.dao.DuplicateKeyException
import org.springframework.security.access.AccessDeniedException
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.Instant
import java.util.Optional

class BookingServiceTest {
    private val bookingRepository = Mockito.mock(BookingRepository::class.java)
    private val reservationRepository = Mockito.mock(BookingSlotReservationRepository::class.java)
    private val serviceRepository = Mockito.mock(ServiceRepository::class.java)
    private val providerProfileRepository = Mockito.mock(ProviderProfileRepository::class.java)
    private val userRepository = Mockito.mock(UserRepository::class.java)
    private val bookingService = BookingService(
        bookingRepository,
        reservationRepository,
        serviceRepository,
        providerProfileRepository,
        userRepository,
    )

    @BeforeEach
    fun setUp() {
        Mockito.reset(
            bookingRepository,
            reservationRepository,
            serviceRepository,
            providerProfileRepository,
            userRepository,
        )
    }

    @Test
    fun `customer creates a pending booking using authenticated customer identity`() {
        stubCustomer()
        stubAvailableService()
        Mockito.`when`(reservationRepository.existsByProviderUserIdAndSlotStartIn("provider-1", slots()))
            .thenReturn(false)
        Mockito.`when`(bookingRepository.save(Mockito.any(Booking::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<Booking>(0) }

        val response = bookingService.create("customer@example.com", validRequest())

        assertEquals(BookingStatus.PENDING, response.status)
        assertEquals("customer-1", response.customerUserId)
        assertEquals("provider-1", response.providerUserId)
        assertEquals("service-1", response.serviceId)
        assertEquals("Africa/Nairobi", response.timeZone)
        val saved = ArgumentCaptor.forClass(Booking::class.java)
        Mockito.verify(bookingRepository).save(saved.capture())
        assertEquals("customer-1", saved.value.customerUserId)
        assertEquals(4, reservationRepository.saveCount())
    }

    @Test
    fun `booking an unavailable service is rejected`() {
        stubCustomer()
        Mockito.`when`(serviceRepository.findById("service-1"))
            .thenReturn(Optional.of(service().copy(availabilityStatus = ServiceAvailability.UNAVAILABLE)))

        assertThrows(MarketplaceConflictException::class.java) {
            bookingService.create("customer@example.com", validRequest())
        }
        Mockito.verifyNoInteractions(reservationRepository)
    }

    @Test
    fun `booking date in the past is rejected`() {
        stubCustomer()
        stubAvailableService()

        assertThrows(IllegalArgumentException::class.java) {
            bookingService.create(
                "customer@example.com",
                validRequest(date = LocalDate.now().minusDays(1)),
            )
        }
        Mockito.verifyNoInteractions(reservationRepository)
    }

    @Test
    fun `end time must be later than start time`() {
        stubCustomer()
        stubAvailableService()

        assertThrows(IllegalArgumentException::class.java) {
            bookingService.create(
                "customer@example.com",
                validRequest(start = LocalTime.NOON, end = LocalTime.NOON),
            )
        }
        Mockito.verifyNoInteractions(reservationRepository)
    }

    @Test
    fun `booking times must use valid unambiguous timezone and fifteen minute slots`() {
        stubCustomer()
        stubAvailableService()

        assertThrows(IllegalArgumentException::class.java) {
            bookingService.create(
                "customer@example.com",
                validRequest(zone = "not-a-time-zone"),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            bookingService.create(
                "customer@example.com",
                validRequest(start = LocalTime.of(12, 5), end = LocalTime.of(12, 35)),
            )
        }
    }

    @Test
    fun `overlapping provider reservation is rejected`() {
        stubCustomer()
        stubAvailableService()
        Mockito.`when`(reservationRepository.existsByProviderUserIdAndSlotStartIn("provider-1", slots()))
            .thenReturn(true)

        assertThrows(MarketplaceConflictException::class.java) {
            bookingService.create("customer@example.com", validRequest())
        }
        Mockito.verify(reservationRepository, Mockito.never())
            .save(Mockito.any(BookingSlotReservation::class.java))
    }

    @Test
    fun `concurrent reservation collision returns conflict and cleans partial slot claims`() {
        stubCustomer()
        stubAvailableService()
        Mockito.`when`(reservationRepository.existsByProviderUserIdAndSlotStartIn("provider-1", slots()))
            .thenReturn(false)
        Mockito.`when`(reservationRepository.save(Mockito.any(BookingSlotReservation::class.java)))
            .thenAnswer { invocation ->
                val reservation = invocation.getArgument<BookingSlotReservation>(0)
                if (reservation.slotStart == slots()[1]) {
                    throw DuplicateKeyException("Unique provider slot conflict")
                }
                reservation
            }

        assertThrows(MarketplaceConflictException::class.java) {
            bookingService.create("customer@example.com", validRequest())
        }
        Mockito.verify(reservationRepository).deleteByBookingId(Mockito.anyString())
        Mockito.verify(bookingRepository, Mockito.never()).save(Mockito.any(Booking::class.java))
    }

    @Test
    fun `customer can view only their own bookings`() {
        stubCustomer()
        Mockito.`when`(bookingRepository.findByCustomerUserIdOrderByRequestedDateDescStartTimeDesc("customer-1"))
            .thenReturn(listOf(booking()))
        Mockito.`when`(userRepository.findByEmail("other@example.com"))
            .thenReturn(user("other-customer", "other@example.com", UserRole.CUSTOMER))
        Mockito.`when`(bookingRepository.findById("booking-1")).thenReturn(Optional.of(booking()))
        assertEquals(1, bookingService.customerBookings("customer@example.com").size)

        assertThrows(AccessDeniedException::class.java) {
            bookingService.getById("other@example.com", "booking-1")
        }
    }

    @Test
    fun `provider can view only their incoming bookings`() {
        stubProvider()
        stubCustomer()
        Mockito.`when`(bookingRepository.findByProviderUserIdOrderByRequestedDateDescStartTimeDesc("provider-1"))
            .thenReturn(listOf(booking()))
        assertEquals(1, bookingService.providerBookings("provider@example.com").size)

        assertThrows(AccessDeniedException::class.java) {
            bookingService.providerBookings("customer@example.com")
        }
    }

    @Test
    fun `provider can accept and complete a pending booking`() {
        stubProvider()
        Mockito.`when`(bookingRepository.findById("booking-1")).thenReturn(Optional.of(booking()))
        Mockito.`when`(
            bookingRepository.transitionStatus(
                "booking-1",
                BookingStatus.PENDING,
                BookingStatus.ACCEPTED,
            ),
        ).thenAnswer { booking().copy(status = BookingStatus.ACCEPTED) }
        Mockito.`when`(
            bookingRepository.transitionStatus(
                "booking-1",
                BookingStatus.ACCEPTED,
                BookingStatus.COMPLETED,
            ),
        ).thenAnswer { booking().copy(status = BookingStatus.COMPLETED) }

        assertEquals(BookingStatus.ACCEPTED, bookingService.accept("provider@example.com", "booking-1").status)
        Mockito.`when`(bookingRepository.findById("booking-1"))
            .thenReturn(Optional.of(booking().copy(status = BookingStatus.ACCEPTED)))
        assertEquals(BookingStatus.COMPLETED, bookingService.complete("provider@example.com", "booking-1").status)
    }

    @Test
    fun `provider can reject a pending booking and release its reservation`() {
        stubProvider()
        Mockito.`when`(bookingRepository.findById("booking-1")).thenReturn(Optional.of(booking()))
        Mockito.`when`(
            bookingRepository.transitionStatus(
                "booking-1",
                BookingStatus.PENDING,
                BookingStatus.REJECTED,
            ),
        ).thenAnswer { booking().copy(status = BookingStatus.REJECTED) }

        assertEquals(BookingStatus.REJECTED, bookingService.reject("provider@example.com", "booking-1").status)
        Mockito.verify(reservationRepository).deleteByBookingId("booking-1")
    }

    @Test
    fun `provider cannot manage another provider booking`() {
        stubProvider()
        Mockito.`when`(bookingRepository.findById("booking-1"))
            .thenReturn(Optional.of(booking().copy(providerUserId = "another-provider")))

        assertThrows(AccessDeniedException::class.java) {
            bookingService.accept("provider@example.com", "booking-1")
        }
        Mockito.verify(bookingRepository, Mockito.never())
            .transitionStatus(
                "booking-1",
                BookingStatus.PENDING,
                BookingStatus.ACCEPTED,
            )
    }

    @Test
    fun `customer can cancel pending or accepted bookings but not terminal bookings`() {
        stubCustomer()
        Mockito.`when`(bookingRepository.findById("booking-1")).thenReturn(Optional.of(booking()))
        Mockito.`when`(
            bookingRepository.transitionStatus(
                "booking-1",
                BookingStatus.PENDING,
                BookingStatus.CANCELLED,
            ),
        ).thenAnswer { booking().copy(status = BookingStatus.CANCELLED) }
        assertEquals(BookingStatus.CANCELLED, bookingService.cancel("customer@example.com", "booking-1").status)
        Mockito.verify(reservationRepository).deleteByBookingId("booking-1")

        Mockito.`when`(bookingRepository.findById("booking-1")).thenReturn(
            Optional.of(booking().copy(status = BookingStatus.ACCEPTED)),
            Optional.of(booking().copy(status = BookingStatus.COMPLETED)),
        )
        Mockito.`when`(
            bookingRepository.transitionStatus(
                "booking-1",
                BookingStatus.ACCEPTED,
                BookingStatus.CANCELLED,
            ),
        ).thenAnswer { booking().copy(status = BookingStatus.CANCELLED) }
        assertEquals(BookingStatus.CANCELLED, bookingService.cancel("customer@example.com", "booking-1").status)

        assertThrows(MarketplaceConflictException::class.java) {
            bookingService.cancel("customer@example.com", "booking-1")
        }
        Mockito.verify(reservationRepository, Mockito.times(2)).deleteByBookingId("booking-1")
    }

    @Test
    fun `compare and set transition reports a concurrent status change`() {
        stubProvider()
        Mockito.`when`(bookingRepository.findById("booking-1")).thenReturn(Optional.of(booking()))
        Mockito.`when`(
            bookingRepository.transitionStatus(
                "booking-1",
                BookingStatus.PENDING,
                BookingStatus.ACCEPTED,
            ),
        ).thenReturn(null)

        assertThrows(MarketplaceConflictException::class.java) {
            bookingService.accept("provider@example.com", "booking-1")
        }
    }

    private fun stubCustomer() {
        Mockito.`when`(userRepository.findByEmail("customer@example.com"))
            .thenReturn(user("customer-1", "customer@example.com", UserRole.CUSTOMER))
    }

    private fun stubProvider() {
        Mockito.`when`(userRepository.findByEmail("provider@example.com"))
            .thenReturn(user("provider-1", "provider@example.com", UserRole.PROVIDER))
    }

    private fun stubAvailableService() {
        Mockito.`when`(serviceRepository.findById("service-1")).thenReturn(Optional.of(service()))
        Mockito.`when`(providerProfileRepository.findById("profile-1"))
            .thenReturn(Optional.of(profile()))
        Mockito.`when`(userRepository.findById("provider-1"))
            .thenReturn(Optional.of(user("provider-1", "provider@example.com", UserRole.PROVIDER)))
    }

    private fun user(id: String, email: String, role: UserRole) = User(
        id = id,
        fullName = role.name,
        email = email,
        passwordHash = "not-used",
        phoneNumber = "1234567890",
        role = role,
    )

    private fun service() = Service(
        id = "service-1",
        providerId = "profile-1",
        name = "Plumbing",
        description = "Repairs",
        category = "Plumbing",
        price = BigDecimal("25.00"),
        location = "Nairobi",
        availabilityStatus = ServiceAvailability.AVAILABLE,
    )

    private fun profile() = ProviderProfile(
        id = "profile-1",
        userId = "provider-1",
        businessName = "Provider",
        description = "Repairs",
        location = "Nairobi",
        contactInformation = "provider@example.com",
    )

    private fun booking(status: BookingStatus = BookingStatus.PENDING) = Booking(
        id = "booking-1",
        customerUserId = "customer-1",
        providerUserId = "provider-1",
        serviceId = "service-1",
        requestedDate = LocalDate.now().plusDays(2),
        startTime = LocalTime.NOON,
        endTime = LocalTime.of(13, 0),
        timeZone = "Africa/Nairobi",
        startsAt = Instant.now().plusSeconds(172800),
        endsAt = Instant.now().plusSeconds(176400),
        status = status,
    )

    private fun validRequest(
        date: LocalDate = LocalDate.now().plusDays(2),
        start: LocalTime = LocalTime.NOON,
        end: LocalTime = LocalTime.of(13, 0),
        zone: String = "Africa/Nairobi",
    ) = CreateBookingRequest(
        serviceId = "service-1",
        requestedDate = date,
        startTime = start,
        endTime = end,
        timeZone = zone,
        customerNotes = "  Please call first  ",
    )

    private fun slots(): List<java.time.Instant> {
        val start = LocalDate.now().plusDays(2).atTime(LocalTime.NOON)
            .atZone(java.time.ZoneId.of("Africa/Nairobi")).toInstant()
        return (0..3).map { start.plusSeconds(it * 15 * 60L) }
    }

    private fun BookingSlotReservationRepository.saveCount(): Int {
        val captor = ArgumentCaptor.forClass(BookingSlotReservation::class.java)
        Mockito.verify(this, Mockito.times(4)).save(captor.capture())
        return captor.allValues.size
    }
}
