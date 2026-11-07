package com.mtaaconnect.backend.repository

import com.mtaaconnect.backend.model.Booking
import org.springframework.data.mongodb.repository.MongoRepository

interface BookingRepository : MongoRepository<Booking, String>, BookingRepositoryCustom {
    fun findByCustomerUserIdOrderByRequestedDateDescStartTimeDesc(customerUserId: String): List<Booking>

    fun findByProviderUserIdOrderByRequestedDateDescStartTimeDesc(providerUserId: String): List<Booking>

}
