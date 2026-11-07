package com.mtaaconnect.backend.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class LoginRequest(
    @field:NotBlank(message = "A valid email address is required")
    @field:Email(message = "A valid email address is required")
    val email: String,

    @field:NotBlank(message = "Password must not be blank")
    val password: String,
)
