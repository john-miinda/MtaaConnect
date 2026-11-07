package com.mtaaconnect.backend.security

import com.mtaaconnect.backend.model.User
import com.mtaaconnect.backend.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.core.Authentication
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import kotlin.test.assertFalse
import java.time.Instant
import java.util.Date
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys

private const val TEST_JWT_SECRET = "dGVzdC1qd3Qtc2VjcmV0LXdpdGgtbW9yZS10aGFuLTMyeC1ieXRlcy0xMjM0NQ=="

@SpringBootTest(
    properties = [
        "spring.mongodb.uri=mongodb://localhost:27017/mtaaconnect_test",
        "spring.data.mongodb.auto-index-creation=false",
        "jwt.secret=$TEST_JWT_SECRET",
        "jwt.expiration=86400000",
    ],
)
@AutoConfigureMockMvc
@Import(ProtectedEndpointConfiguration::class)
class AuthenticationIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @MockitoBean
    private lateinit var userRepository: UserRepository

    private lateinit var registeredUser: User

    @BeforeEach
    fun setUp() {
        Mockito.reset(userRepository)
        registeredUser = User(
            id = "user-id",
            fullName = "Test User",
            email = "test@example.com",
            passwordHash = passwordEncoder.encode("correct-password")
                ?: throw IllegalStateException("Password encoder returned no hash"),
            phoneNumber = "1234567890",
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        )
        Mockito.`when`(userRepository.findByEmail("test@example.com")).thenReturn(registeredUser)
    }

    @Test
    fun `valid credentials return bearer token and safe user information`() {
        val response = mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"Test@Example.COM","password":"correct-password"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").isNotEmpty)
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.user.email").value("test@example.com"))
            .andExpect(jsonPath("$.user.password").doesNotExist())
            .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andReturn()

        assertFalse(response.response.contentAsString.contains("correct-password"))
        assertFalse(response.response.contentAsString.contains(registeredUser.passwordHash))
    }

    @Test
    fun `wrong password returns generic unauthorized response`() {
        mockMvc.perform(loginRequest("test@example.com", "wrong-password"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("Invalid email or password"))
    }

    @Test
    fun `unknown email returns the same generic unauthorized response`() {
        mockMvc.perform(loginRequest("unknown@example.com", "wrong-password"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("Invalid email or password"))
    }

    @Test
    fun `missing token cannot access protected endpoint`() {
        mockMvc.perform(get("/api/test/protected"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `invalid token is rejected`() {
        val now = System.currentTimeMillis()
        val tokenSignedWithDifferentKey = Jwts.builder()
            .subject("test@example.com")
            .issuedAt(Date(now - 1_000))
            .expiration(Date(now + 60_000))
            .signWith(Keys.hmacShaKeyFor(ByteArray(32) { 1 }))
            .compact()

        mockMvc.perform(
            get("/api/test/protected").header("Authorization", "Bearer $tokenSignedWithDifferentKey"),
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `token without expiration is rejected`() {
        val key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_JWT_SECRET))
        val tokenWithoutExpiration = Jwts.builder()
            .subject("test@example.com")
            .issuedAt(Date())
            .signWith(key)
            .compact()

        mockMvc.perform(
            get("/api/test/protected").header("Authorization", "Bearer $tokenWithoutExpiration"),
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `expired token is rejected`() {
        val now = System.currentTimeMillis()
        val key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_JWT_SECRET))
        val expiredToken = Jwts.builder()
            .subject("test@example.com")
            .issuedAt(Date(now - 2_000))
            .expiration(Date(now - 1_000))
            .signWith(key)
            .compact()

        mockMvc.perform(
            get("/api/test/protected").header("Authorization", "Bearer $expiredToken"),
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `valid token grants access to protected endpoint`() {
        val login = mockMvc.perform(loginRequest("test@example.com", "correct-password"))
            .andExpect(status().isOk)
            .andReturn()
        val token = Regex(""""token":"([^"]+)"""")
            .find(login.response.contentAsString)!!
            .groupValues[1]

        mockMvc.perform(
            get("/api/test/protected").header("Authorization", "Bearer $token"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value("test@example.com"))
    }

    @Test
    fun `registration remains publicly accessible`() {
        val savedUser = registeredUser.copy(email = "new@example.com")
        Mockito.`when`(userRepository.existsByEmail("new@example.com")).thenReturn(false)
        Mockito.`when`(userRepository.existsByPhoneNumber("0987654321")).thenReturn(false)
        Mockito.`when`(userRepository.save(Mockito.any(User::class.java))).thenReturn(savedUser)

        mockMvc.perform(
            post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"fullName":"New User","email":"new@example.com","password":"new-password","phoneNumber":"0987654321"}""",
                ),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.email").value("new@example.com"))
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
    }

    private fun loginRequest(email: String, password: String) =
        post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"email":"$email","password":"$password"}""")

}

@TestConfiguration
class ProtectedEndpointConfiguration {
    @Bean
    fun protectedEndpointController() = ProtectedEndpointController()
}

@RestController
class ProtectedEndpointController {
    @GetMapping("/api/test/protected")
    fun protected(authentication: Authentication): Map<String, String> =
        mapOf("email" to authentication.name)
}
