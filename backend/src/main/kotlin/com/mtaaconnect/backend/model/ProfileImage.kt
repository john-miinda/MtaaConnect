package com.mtaaconnect.backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

enum class ProfileImageCategory {
    CUSTOMER_PROFILE,
    PROVIDER_PROFILE,
    PORTFOLIO,
    SERVICE,
}

@Document(collection = "profile_images")
data class ProfileImage(
    @Id
    val id: String? = null,
    @field:Indexed
    val ownerUserId: String,
    val category: ProfileImageCategory,
    val providerProfileId: String? = null,
    val serviceId: String? = null,
    val storageKey: String,
    val contentType: String,
    val sizeBytes: Long,
    val createdAt: Instant = Instant.now(),
)
