package com.mtaaconnect.backend.repository

import com.mtaaconnect.backend.model.Booking
import com.mtaaconnect.backend.model.BookingStatus

interface BookingRepositoryCustom {
    fun transitionStatus(
        id: String,
        expectedStatus: BookingStatus,
        newStatus: BookingStatus,
    ): Booking?
}
