package com.mtaaconnect.backend.service

import com.mtaaconnect.backend.dto.UserResponse
import com.mtaaconnect.backend.model.User
import com.mtaaconnect.backend.model.UserRole
import com.mtaaconnect.backend.repository.UserRepository
import org.springframework.dao.DuplicateKeyException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
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
        role: UserRole = UserRole.CUSTOMER,
    ): UserResponse {
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
        if (role == UserRole.ADMIN) {
            throw IllegalArgumentException("Public registration cannot assign the ADMIN role")
        }

        val normalizedEmail = email.trim().lowercase(Locale.ROOT)
        val normalizedPhoneNumber = phoneNumber.trim()
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw EmailAlreadyRegisteredException()
        }
        if (userRepository.existsByPhoneNumber(normalizedPhoneNumber)) {
            throw PhoneNumberAlreadyRegisteredException()
        }

        val passwordHash = passwordEncoder.encode(password)
            ?: throw IllegalStateException("Password encoder returned no hash")
        val user = User(
            fullName = fullName.trim(),
            email = normalizedEmail,
            passwordHash = passwordHash,
            phoneNumber = normalizedPhoneNumber,
            role = role,
        )
        val savedUser = try {
            userRepository.save(user)
        } catch (exception: DuplicateKeyException) {
            throw AccountAlreadyRegisteredException()
        }

        return UserResponse(
            id = savedUser.id,
            fullName = savedUser.fullName,
            email = savedUser.email,
            phoneNumber = savedUser.phoneNumber,
            createdAt = savedUser.createdAt,
            role = savedUser.role,
        )
    }

    private companion object {
        val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}

class EmailAlreadyRegisteredException :
    RuntimeException("An account with this email already exists")

class PhoneNumberAlreadyRegisteredException :
    RuntimeException("An account with this phone number already exists")

class AccountAlreadyRegisteredException :
    RuntimeException("An account with this email or phone number already exists")
