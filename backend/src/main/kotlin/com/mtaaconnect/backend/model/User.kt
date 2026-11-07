package com.mtaaconnect.backend.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "users")
data class User(
    @Id
    val id: String? = null,
    val fullName: String,
    @field:Indexed(unique = true)
    val email: String,
    val passwordHash: String,
    @field:Indexed(unique = true)
    val phoneNumber: String,
    val role: UserRole = UserRole.CUSTOMER,
    val createdAt: Instant = Instant.now(),
)
