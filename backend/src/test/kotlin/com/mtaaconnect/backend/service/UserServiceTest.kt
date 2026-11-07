package com.mtaaconnect.backend.service

import com.mtaaconnect.backend.model.User
import com.mtaaconnect.backend.model.UserRole
import com.mtaaconnect.backend.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.springframework.dao.DuplicateKeyException
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

class UserServiceTest {
    private val userRepository = Mockito.mock(UserRepository::class.java)
    private val passwordEncoder = BCryptPasswordEncoder()
    private val userService = UserService(userRepository, passwordEncoder)

    @Test
    fun `register normalizes email hashes password and returns no hash`() {
        allowNewAccount()
        Mockito.`when`(userRepository.save(Mockito.any(User::class.java)))
            .thenAnswer { invocation ->
                invocation.getArgument<User>(0).copy(id = "user-id")
            }

        val response = userService.register(
            fullName = "  Test User ",
            email = "  Test@Example.COM ",
            password = "safe-password",
            phoneNumber = "  1234567890 ",
        )

        val savedUser = ArgumentCaptor.forClass(User::class.java)
        Mockito.verify(userRepository).save(savedUser.capture())
        assertEquals("Test User", savedUser.value.fullName)
        assertEquals("test@example.com", savedUser.value.email)
        assertEquals("1234567890", savedUser.value.phoneNumber)
        assertEquals(UserRole.CUSTOMER, savedUser.value.role)
        assertNotEquals("safe-password", savedUser.value.passwordHash)
        assertTrue(savedUser.value.passwordHash.startsWith("\$2"))
        assertTrue(passwordEncoder.matches("safe-password", savedUser.value.passwordHash))

        assertEquals("user-id", response.id)
        assertEquals("test@example.com", response.email)
        assertFalse(response.toString().contains(savedUser.value.passwordHash))
        assertFalse(response.javaClass.declaredFields.any { it.name.contains("password", ignoreCase = true) })
    }

    @Test
    fun `register rejects invalid email`() {
        assertThrows(IllegalArgumentException::class.java) {
            userService.register("Test User", "not-an-email", "password", "1234567890")
        }

        @Test
        fun `register assigns provider role when requested`() {
            allowNewAccount()
            Mockito.`when`(userRepository.save(Mockito.any(User::class.java)))
                .thenAnswer { invocation -> invocation.getArgument<User>(0).copy(id = "provider-id") }

            val response = userService.register(
                fullName = "Test Provider",
                email = "provider@example.com",
                password = "safe-password",
                phoneNumber = "1234567890",
                role = UserRole.PROVIDER,
            )

            assertEquals(UserRole.PROVIDER, response.role)
        }

        @Test
        fun `public registration cannot assign admin role`() {
            assertThrows(IllegalArgumentException::class.java) {
                userService.register(
                    fullName = "Admin",
                    email = "admin@example.com",
                    password = "safe-password",
                    phoneNumber = "1234567890",
                    role = UserRole.ADMIN,
                )
            }
            Mockito.verifyNoInteractions(userRepository)
        }
        Mockito.verifyNoInteractions(userRepository)
    }

    @Test
    fun `register rejects blank required fields`() {
        val invalidRequests = listOf(
            listOf("", "test@example.com", "password", "1234567890"),
            listOf("Test User", " ", "password", "1234567890"),
            listOf("Test User", "test@example.com", " ", "1234567890"),
            listOf("Test User", "test@example.com", "password", " "),
        )

        invalidRequests.forEach { request ->
            assertThrows(IllegalArgumentException::class.java) {
                userService.register(request[0], request[1], request[2], request[3])
            }
        }
        Mockito.verifyNoInteractions(userRepository)
    }

    @Test
    fun `register rejects duplicate email`() {
        Mockito.`when`(userRepository.existsByEmail("test@example.com")).thenReturn(true)

        assertThrows(EmailAlreadyRegisteredException::class.java) {
            userService.register("Test User", "test@example.com", "password", "1234567890")
        }

        Mockito.verify(userRepository, Mockito.never()).save(Mockito.any(User::class.java))
    }

    @Test
    fun `register rejects duplicate phone number`() {
        Mockito.`when`(userRepository.existsByEmail("test@example.com")).thenReturn(false)
        Mockito.`when`(userRepository.existsByPhoneNumber("1234567890")).thenReturn(true)

        assertThrows(PhoneNumberAlreadyRegisteredException::class.java) {
            userService.register("Test User", "test@example.com", "password", "1234567890")
        }

        Mockito.verify(userRepository, Mockito.never()).save(Mockito.any(User::class.java))
    }

    @Test
    fun `register maps concurrent duplicate key to account conflict`() {
        allowNewAccount()
        Mockito.`when`(userRepository.save(Mockito.any(User::class.java)))
            .thenThrow(DuplicateKeyException("duplicate"))

        assertThrows(AccountAlreadyRegisteredException::class.java) {
            userService.register("Test User", "test@example.com", "password", "1234567890")
        }
    }

    private fun allowNewAccount() {
        Mockito.`when`(userRepository.existsByEmail("test@example.com")).thenReturn(false)
        Mockito.`when`(userRepository.existsByPhoneNumber("1234567890")).thenReturn(false)
    }
}
