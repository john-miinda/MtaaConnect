package com.mtaaconnect.backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.math.BigDecimal
import java.time.Instant

@Document(collection = "services")
@CompoundIndexes(
    CompoundIndex(
        name = "service_discovery_index",
        def = "{'availabilityStatus': 1, 'category': 1, 'location': 1}",
    ),
)
data class Service(
    @Id
    val id: String? = null,
    @field:Indexed
    val providerId: String,
    val name: String,
    val description: String,
    @field:Indexed
    val category: String,
    val price: BigDecimal,
    @field:Indexed
    val location: String,
    val availabilityStatus: ServiceAvailability = ServiceAvailability.AVAILABLE,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = createdAt,
)
