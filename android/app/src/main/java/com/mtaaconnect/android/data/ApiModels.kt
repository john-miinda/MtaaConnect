package com.mtaaconnect.android.data

data class UserDto(
    val id: String?,
    val fullName: String,
    val email: String,
    val phoneNumber: String,
    val createdAt: String,
    val role: String = "CUSTOMER",
)

data class LoginRequest(val email: String, val password: String)
data class RegisterRequest(
    val fullName: String,
    val email: String,
    val password: String,
    val phoneNumber: String,
    val role: String,
)

data class LoginResponse(val token: String, val tokenType: String, val user: UserDto)
data class ErrorResponse(val error: String? = null)

data class ProviderProfileDto(
    val id: String?,
    val userId: String,
    val businessName: String,
    val description: String,
    val location: String,
    val contactInformation: String,
    val latitude: Double?,
    val longitude: Double?,
    val createdAt: String,
    val updatedAt: String,
)

data class ProviderProfileRequest(
    val businessName: String,
    val description: String,
    val location: String,
    val contactInformation: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

data class ProfileImageDto(
    val id: String,
    val category: String,
    val url: String,
    val serviceId: String?,
    val contentType: String,
    val sizeBytes: Long,
    val createdAt: String,
)

data class ServiceDto(
    val id: String?,
    val providerId: String,
    val name: String,
    val description: String,
    val category: String,
    val price: String,
    val location: String,
    val availabilityStatus: String,
    val createdAt: String,
    val updatedAt: String,
)

data class ServiceRequest(
    val name: String,
    val description: String,
    val category: String,
    val price: String,
    val location: String,
    val availabilityStatus: String = "AVAILABLE",
)

data class CreateBookingRequest(
    val serviceId: String,
    val requestedDate: String,
    val startTime: String,
    val endTime: String,
    val timeZone: String,
    val customerNotes: String? = null,
)

data class BookingDto(
    val id: String?,
    val customerUserId: String,
    val providerUserId: String,
    val serviceId: String,
    val requestedDate: String,
    val startTime: String,
    val endTime: String,
    val timeZone: String,
    val startsAt: String,
    val endsAt: String,
    val status: String,
    val customerNotes: String?,
    val createdAt: String,
    val updatedAt: String,
)

data class Session(
    val token: String,
    val email: String,
    val fullName: String,
    val role: String,
    val expiresAtEpochSeconds: Long,
)
