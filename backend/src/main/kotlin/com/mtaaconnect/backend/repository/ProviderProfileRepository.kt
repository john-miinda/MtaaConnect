package com.mtaaconnect.backend.repository

import com.mtaaconnect.backend.model.ProviderProfile
import org.springframework.data.mongodb.repository.MongoRepository

interface ProviderProfileRepository : MongoRepository<ProviderProfile, String> {
    fun findByUserId(userId: String): ProviderProfile?
}
