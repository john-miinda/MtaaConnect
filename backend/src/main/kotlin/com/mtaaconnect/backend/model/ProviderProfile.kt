package com.mtaaconnect.backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "provider_profiles")
data class ProviderProfile(
    @Id
    val id: String? = null,
    @field:Indexed(unique = true)
    val userId: String,
    val businessName: String,
    val description: String,
    val location: String,
    val contactInformation: String,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = createdAt,
)
