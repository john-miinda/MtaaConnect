package com.mtaaconnect.backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "users")
data class User(
    @Id
    val id: String? = null,
    val fullName: String,
    val email: String,
    val passwordHash: String,
    val phoneNumber: String,
    val createdAt: Instant = Instant.now(),
)
