package com.mtaaconnect.backend.dto

import com.mtaaconnect.backend.model.UserRole
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class RegisterRequest(
    @field:NotBlank(message = "Full name must not be blank")
    val fullName: String,

    @field:NotBlank(message = "A valid email address is required")
    @field:Email(message = "A valid email address is required")
    val email: String,

    @field:NotBlank(message = "Password must not be blank")
    val password: String,

    @field:NotBlank(message = "Phone number must not be blank")
    val phoneNumber: String,

    val role: UserRole = UserRole.CUSTOMER,
)