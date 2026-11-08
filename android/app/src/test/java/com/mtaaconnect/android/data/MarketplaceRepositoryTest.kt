package com.mtaaconnect.android.data

import com.mtaaconnect.android.ui.navigationItemsFor
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import java.time.Instant
import java.util.Base64

class MarketplaceRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var storage: MemorySessionStorage
    private var unauthorizedCount = 0
    private lateinit var repository: MarketplaceRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        storage = MemorySessionStorage()
        unauthorizedCount = 0
        val client = ApiClient(
            baseUrl = server.url("/").toString(),
            tokenProvider = { storage.read()?.token },
            onUnauthorized = { unauthorizedCount++ },
        )
        repository = MarketplaceRepository(client.api, storage)
    }
    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `login accepts actual bearer response and persists authenticated role`() = runBlocking {
        val token = token("customer@example.com", "CUSTOMER")
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"token":"$token","tokenType":"Bearer","user":{"id":"customer-1","fullName":"Customer","email":"customer@example.com","phoneNumber":"123","createdAt":"2026-01-01T00:00:00Z","role":"CUSTOMER"}}""",
            ),
        )

        val session = repository.login(" customer@example.com ", "password")

        assertEquals("CUSTOMER", session.role)
        assertEquals("customer@example.com", session.email)
        assertEquals(session, storage.read())
        assertEquals("""{"email":"customer@example.com","password":"password"}""", server.takeRequest().body.readUtf8())
        assertEquals(0, unauthorizedCount)
    }

    @Test
    fun `expired token response is not persisted as a session`() = runBlocking {
        val token = token("customer@example.com", "CUSTOMER", Instant.now().epochSecond - 10)
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"token":"$token","tokenType":"Bearer","user":{"id":"customer-1","fullName":"Customer","email":"customer@example.com","phoneNumber":"123","createdAt":"2026-01-01T00:00:00Z","role":"CUSTOMER"}}""",
            ),
        )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.login("customer@example.com", "password") }
        }
        assertNull(storage.read())
    }

    @Test
    fun `registration sends only backend registration fields and chosen role`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{"id":"provider-1","fullName":"Provider","email":"provider@example.com","phoneNumber":"123","createdAt":"2026-01-01T00:00:00Z","role":"PROVIDER"}""",
            ),
        )

        val user = repository.register("Provider", "provider@example.com", "password", "123", "PROVIDER")
        val request = server.takeRequest()
        assertEquals("/api/users/register", request.path)
        assertEquals("PROVIDER", user.role)
        assertTrue(request.body.readUtf8().contains(""""role":"PROVIDER""""))
    }

    @Test
    fun `authenticated service browsing attaches bearer token`() = runBlocking {
        storage.save(session("provider@example.com", "PROVIDER"))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """[{"id":"s1","providerId":"p1","name":"Repair","description":"Fixes","category":"Home","price":25.0,"location":"Nairobi","availabilityStatus":"AVAILABLE","createdAt":"2026-01-01T00:00:00Z","updatedAt":"2026-01-01T00:00:00Z"}]""",
            ),
        )

        val services = repository.services("Home", "Nairobi")
        val request = server.takeRequest()
        assertEquals("Repair", services.single().name)
        assertEquals("Bearer ${storage.read()?.token}", request.getHeader("Authorization"))
        assertEquals("/api/services?category=Home&location=Nairobi", request.path)
    }

    @Test
    fun `provider reloads profile and all managed services from authenticated endpoints`() = runBlocking {
        storage.save(session("provider@example.com", "PROVIDER"))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"id":"profile-1","userId":"provider-1","businessName":"Repairs","description":"Home repairs","location":"Nairobi","contactInformation":"123","latitude":null,"longitude":null,"createdAt":"2026-01-01T00:00:00Z","updatedAt":"2026-01-01T00:00:00Z"}""",
            ),
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """[{"id":"s1","providerId":"profile-1","name":"Available repair","description":"Fixes","category":"Home","price":25.0,"location":"Nairobi","availabilityStatus":"AVAILABLE","createdAt":"2026-01-01T00:00:00Z","updatedAt":"2026-01-01T00:00:00Z"},{"id":"s2","providerId":"profile-1","name":"Paused repair","description":"Paused","category":"Home","price":30.0,"location":"Nairobi","availabilityStatus":"UNAVAILABLE","createdAt":"2026-01-01T00:00:00Z","updatedAt":"2026-01-01T00:00:00Z"}]""",
            ),
        )

        val profile = repository.myProviderProfile()
        val services = repository.myProviderServices()
        val profileRequest = server.takeRequest()
        val servicesRequest = server.takeRequest()

        assertEquals("profile-1", profile?.id)
        assertEquals(listOf("AVAILABLE", "UNAVAILABLE"), services.map { it.availabilityStatus })
        assertEquals("/api/providers/profile", profileRequest.path)
        assertTrue(profileRequest.getHeader("Authorization")?.startsWith("Bearer ") == true)
        assertEquals("/api/providers/profile/services", servicesRequest.path)
        assertTrue(servicesRequest.getHeader("Authorization")?.startsWith("Bearer ") == true)
    }

    @Test
    fun `provider profile lookup treats a missing profile as a new provider`() = runBlocking {
        storage.save(session("provider@example.com", "PROVIDER"))
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"Provider profile not found"}"""))

        assertNull(repository.myProviderProfile())
        assertEquals("/api/providers/profile", server.takeRequest().path)
    }

    @Test
    fun `provider service creation sends publishable listing to configured API`() = runBlocking {
        storage.save(session("provider@example.com", "PROVIDER"))
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{"id":"s1","providerId":"profile-1","name":"Repair","description":"Fixes","category":"Home","price":25.0,"location":"Nairobi","availabilityStatus":"AVAILABLE","createdAt":"2026-01-01T00:00:00Z","updatedAt":"2026-01-01T00:00:00Z"}""",
            ),
        )

        val service = repository.createService(
            ServiceRequest("Repair", "Fixes", "Home", "25.00", "Nairobi", "AVAILABLE"),
        )
        val request = server.takeRequest()

        assertEquals("s1", service.id)
        assertEquals("/api/services", request.path)
        assertTrue(request.getHeader("Authorization")?.startsWith("Bearer ") == true)
        assertTrue(request.body.readUtf8().contains(""""availabilityStatus":"AVAILABLE""""))
    }

    @Test
    fun `profile image upload uses authenticated multipart API`() = runBlocking {
        storage.save(session("provider@example.com", "PROVIDER"))
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{"id":"image-1","category":"PORTFOLIO","url":"/api/media/image-1","serviceId":null,"contentType":"image/png","sizeBytes":4,"createdAt":"2026-01-01T00:00:00Z"}""",
            ),
        )

        val image = repository.uploadProfileImage(byteArrayOf(1, 2, 3, 4), "PORTFOLIO")
        val request = server.takeRequest()
        val body = request.body.readUtf8()

        assertEquals("image-1", image.id)
        assertEquals("/api/profile/images", request.path)
        assertTrue(request.getHeader("Authorization")?.startsWith("Bearer ") == true)
        assertTrue(request.getHeader("Content-Type")?.startsWith("multipart/form-data;") == true)
        assertTrue(body.contains("""name="category""""))
        assertTrue(body.contains("PORTFOLIO"))
        assertTrue(body.contains("""name="file"; filename="upload""""))
    }

    @Test
    fun `unauthorized response triggers session expiration callback and readable error`() = runBlocking {
        storage.save(session("customer@example.com", "CUSTOMER"))
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"Unauthorized"}"""))

        val exception = assertThrows(HttpException::class.java) {
            runBlocking { repository.myBookings() }
        }
        assertEquals(1, unauthorizedCount)
        assertEquals("Your session expired or your credentials are incorrect. Please sign in again.", exception.toUserMessage())
    }

    @Test
    fun `backend conflict error message is retained`() {
        val response = retrofit2.Response.error<List<ServiceDto>>(
            409,
            """{"error":"The provider is already booked for this time"}"""
                .toResponseBody("application/json".toMediaType()),
        )
        assertEquals(
            "The provider is already booked for this time",
            HttpException(response).toUserMessage(),
        )
    }

    @Test
    fun `role navigation does not grant management navigation to admin`() {
        assertEquals(listOf("Explore", "My bookings", "Profile"), navigationItemsFor("CUSTOMER"))
        assertEquals(listOf("Dashboard", "Incoming bookings", "Profile"), navigationItemsFor("PROVIDER"))
        assertEquals(listOf("Explore"), navigationItemsFor("ADMIN"))
        assertEquals(listOf("Explore"), navigationItemsFor(null))
    }

    private fun session(email: String, role: String) =
        Session(token(email, role), email, role, role, Instant.now().epochSecond + 3600)

    private fun token(
        email: String,
        role: String,
        expiresAt: Long = Instant.now().epochSecond + 3600,
    ): String {
        fun encode(value: String) =
            Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())
        return "${encode("""{"alg":"none"}""")}.${encode("""{"sub":"$email","role":"$role","exp":$expiresAt}""")}.signature"
    }

    private class MemorySessionStorage : SessionStorage {
        private var value: Session? = null
        override fun save(session: Session) {
            value = session
        }
        override fun read(): Session? = value
        override fun clear() {
            value = null
        }
    }
}
