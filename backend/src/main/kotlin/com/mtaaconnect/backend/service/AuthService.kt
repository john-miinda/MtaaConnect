package com.mtaaconnect.backend.service

import com.mtaaconnect.backend.dto.LoginResponse
import com.mtaaconnect.backend.dto.UserResponse
import com.mtaaconnect.backend.repository.UserRepository
import com.mtaaconnect.backend.security.JwtService
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.util.Locale

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
) {
    fun login(email: String, password: String): LoginResponse {
        val normalizedEmail = email.trim().lowercase(Locale.ROOT)
        val user = userRepository.findByEmail(normalizedEmail)
            ?: throw BadCredentialsException(INVALID_CREDENTIALS)

        if (!passwordEncoder.matches(password, user.passwordHash)) {
            throw BadCredentialsException(INVALID_CREDENTIALS)
        }

        return LoginResponse(
            token = jwtService.generateToken(user.email, user.role),
            user = UserResponse(
                id = user.id,
                fullName = user.fullName,
                email = user.email,
                phoneNumber = user.phoneNumber,
                createdAt = user.createdAt,
                role = user.role,
            ),
        )
    }

    private companion object {
        const val INVALID_CREDENTIALS = "Invalid email or password"
    }
}
