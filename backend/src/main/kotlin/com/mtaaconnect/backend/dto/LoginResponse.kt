package com.mtaaconnect.backend.dto

data class LoginResponse(
    val token: String,
    val tokenType: String = "Bearer",
    val user: UserResponse,
)
