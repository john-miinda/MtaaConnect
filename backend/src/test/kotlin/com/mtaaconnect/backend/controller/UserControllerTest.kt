package com.mtaaconnect.backend.controller

import com.mtaaconnect.backend.dto.UserResponse
import com.mtaaconnect.backend.exception.GlobalHandlerException
import com.mtaaconnect.backend.service.EmailAlreadyRegisteredException
import com.mtaaconnect.backend.service.UserService
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean
import java.time.Instant

class UserControllerTest {
    private val userService = Mockito.mock(UserService::class.java)
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        val validator = LocalValidatorFactoryBean()
        validator.afterPropertiesSet()
        mockMvc = MockMvcBuilders.standaloneSetup(UserController(userService))
            .setControllerAdvice(GlobalHandlerException())
            .setValidator(validator)
            .build()
    }

    @Test
    fun `register returns created response without password hash`() {
        val response = UserResponse(
            id = "user-id",
            fullName = "Test User",
            email = "test@example.com",
            phoneNumber = "1234567890",
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        )
        Mockito.`when`(
            userService.register("Test User", "test@example.com", "password", "1234567890"),
        ).thenReturn(response)

        val result = mockMvc.perform(
            post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.email").value("test@example.com"))
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andReturn()

        assertTrue(!result.response.contentAsString.contains("passwordHash"))
    }

    @Test
    fun `register returns bad request for invalid email`() {
        val body = validRequest.replace("test@example.com", "not-an-email")

        mockMvc.perform(
            post("/api/users/register").contentType(MediaType.APPLICATION_JSON).content(body),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("A valid email address is required"))
    }

    @Test
    fun `register returns bad request for blank required field`() {
        val body = validRequest.replace("\"fullName\":\"Test User\"", "\"fullName\":\" \"")

        mockMvc.perform(
            post("/api/users/register").contentType(MediaType.APPLICATION_JSON).content(body),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Full name must not be blank"))
    }

    @Test
    fun `register returns conflict for duplicate account`() {
        Mockito.`when`(
            userService.register("Test User", "test@example.com", "password", "1234567890"),
        ).thenThrow(EmailAlreadyRegisteredException())

        mockMvc.perform(
            post("/api/users/register").contentType(MediaType.APPLICATION_JSON).content(validRequest),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("An account with this email already exists"))
    }

    private companion object {
        const val validRequest =
            """{"fullName":"Test User","email":"test@example.com","password":"password","phoneNumber":"1234567890"}"""
    }
}
