package com.mtaaconnect.backend.dto

import com.mtaaconnect.backend.model.UserRole
import java.time.Instant

data class UserResponse(
    val id: String?,
    val fullName: String,
    val email: String,
    val phoneNumber: String,
    val createdAt: Instant,
    val role: UserRole = UserRole.CUSTOMER,
)
