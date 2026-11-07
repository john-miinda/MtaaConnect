package com.mtaaconnect.backend.controller

import com.mtaaconnect.backend.dto.BookingResponse
import com.mtaaconnect.backend.dto.CreateBookingRequest
import com.mtaaconnect.backend.service.BookingService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/bookings")
class BookingController(
    private val bookingService: BookingService,
) {
    @PostMapping
    fun create(
        authentication: Authentication,
        @Valid @RequestBody request: CreateBookingRequest,
    ): ResponseEntity<BookingResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(bookingService.create(authentication.name, request))

    @GetMapping("/my-bookings")
    fun customerBookings(authentication: Authentication): List<BookingResponse> =
        bookingService.customerBookings(authentication.name)

    @GetMapping("/provider")
    fun providerBookings(authentication: Authentication): List<BookingResponse> =
        bookingService.providerBookings(authentication.name)

    @GetMapping("/{id}")
    fun getById(authentication: Authentication, @PathVariable id: String): BookingResponse =
        bookingService.getById(authentication.name, id)

    @PatchMapping("/{id}/cancel")
    fun cancel(authentication: Authentication, @PathVariable id: String): BookingResponse =
        bookingService.cancel(authentication.name, id)

    @PatchMapping("/{id}/accept")
    fun accept(authentication: Authentication, @PathVariable id: String): BookingResponse =
        bookingService.accept(authentication.name, id)

    @PatchMapping("/{id}/reject")
    fun reject(authentication: Authentication, @PathVariable id: String): BookingResponse =
        bookingService.reject(authentication.name, id)

    @PatchMapping("/{id}/complete")
    fun complete(authentication: Authentication, @PathVariable id: String): BookingResponse =
        bookingService.complete(authentication.name, id)
}
