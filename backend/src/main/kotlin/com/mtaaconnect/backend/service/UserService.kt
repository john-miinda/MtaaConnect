package com.mtaaconnect.backend.service

import com.mtaaconnect.backend.model.User
import com.mtaaconnect.backend.repository.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.Locale

@Service
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    fun register(
        fullName: String,
        email: String,
        password: String,
        phoneNumber: String,
    ): RegisteredUserResponse {
        if (fullName.isBlank()) {
            throw IllegalArgumentException("Full name must not be blank")
        }
        if (email.isBlank() || !EMAIL_PATTERN.matches(email.trim())) {
            throw IllegalArgumentException("A valid email address is required")
        }
        if (password.isBlank()) {
            throw IllegalArgumentException("Password must not be blank")
        }
        if (phoneNumber.isBlank()) {
            throw IllegalArgumentException("Phone number must not be blank")
        }

        val normalizedEmail = email.trim().lowercase(Locale.ROOT)
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw EmailAlreadyRegisteredException()
        }
        if (userRepository.existsByPhoneNumber(phoneNumber.trim())) {
            throw PhoneNumberAlreadyRegisteredException()
        }

        val passwordHash = passwordEncoder.encode(password)
            ?: throw IllegalStateException("Password encoder returned no hash")
        val user = User(
            fullName = fullName.trim(),
            email = normalizedEmail,
            passwordHash = passwordHash,
            phoneNumber = phoneNumber.trim(),
        )
        val savedUser = userRepository.save(user)

        return RegisteredUserResponse(
            id = savedUser.id,
            fullName = savedUser.fullName,
            email = savedUser.email,
            phoneNumber = savedUser.phoneNumber,
            createdAt = savedUser.createdAt,
        )
    }

    private companion object {
        val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}

data class RegisteredUserResponse(
    val id: String?,
    val fullName: String,
    val email: String,
    val phoneNumber: String,
    val createdAt: Instant,
)

class EmailAlreadyRegisteredException :
    IllegalArgumentException("An account with this email already exists")

class PhoneNumberAlreadyRegisteredException :
    IllegalArgumentException("An account with this phone number already exists")
