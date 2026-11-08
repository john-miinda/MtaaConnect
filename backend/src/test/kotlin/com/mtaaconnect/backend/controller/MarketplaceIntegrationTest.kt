package com.mtaaconnect.backend.controller

import com.mtaaconnect.backend.model.ProviderProfile
import com.mtaaconnect.backend.model.Service
import com.mtaaconnect.backend.model.ServiceAvailability
import com.mtaaconnect.backend.model.User
import com.mtaaconnect.backend.model.UserRole
import com.mtaaconnect.backend.repository.ProviderProfileRepository
import com.mtaaconnect.backend.repository.ServiceRepository
import com.mtaaconnect.backend.repository.UserRepository
import com.mtaaconnect.backend.security.JwtService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import java.math.BigDecimal
import java.util.Optional

private const val MARKETPLACE_TEST_JWT_SECRET =
    "bWFya2V0cGxhY2UtdGVzdC1zZWNyZXQtd2l0aC1tb3JlLXRoYW4tMzItYnl0ZXM="

@SpringBootTest(
    properties = [
        "spring.mongodb.uri=mongodb://localhost:27017/mtaaconnect_marketplace_test",
        "spring.data.mongodb.auto-index-creation=false",
        "jwt.secret=$MARKETPLACE_TEST_JWT_SECRET",
        "jwt.expiration=86400000",
    ],
)
@AutoConfigureMockMvc
class MarketplaceIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jwtService: JwtService

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @MockitoBean
    private lateinit var userRepository: UserRepository

    @MockitoBean
    private lateinit var providerProfileRepository: ProviderProfileRepository

    @MockitoBean
    private lateinit var serviceRepository: ServiceRepository

    @BeforeEach
    fun resetRepositories() {
        Mockito.reset(userRepository, providerProfileRepository, serviceRepository)
    }

    @Test
    fun `public registration supports customers and providers but rejects admins`() {
        Mockito.`when`(userRepository.save(Mockito.any(User::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<User>(0).copy(id = "new-user") }

        mockMvc.perform(
            post("/api/users/register").contentType(MediaType.APPLICATION_JSON).content(registrationBody()),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.role").value("CUSTOMER"))

        mockMvc.perform(
            post("/api/users/register").contentType(MediaType.APPLICATION_JSON)
                .content(registrationBody(role = "PROVIDER")),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.role").value("PROVIDER"))

        mockMvc.perform(
            post("/api/users/register").contentType(MediaType.APPLICATION_JSON)
                .content(registrationBody(role = "ADMIN")),
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `provider role from login JWT authorizes provider operations`() {
        val provider = providerUser()
        val password = "correct-password"
        val providerWithPassword = provider.copy(
            passwordHash = passwordEncoder.encode(password) ?: error("Password encoder returned no hash"),
        )
        Mockito.`when`(userRepository.findByEmail(provider.email)).thenReturn(providerWithPassword)

        val login = mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"${provider.email}","password":"$password"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.user.role").value("PROVIDER"))
            .andReturn()
        val token = Regex(""""token":"([^"]+)"""")
            .find(login.response.contentAsString)!!
            .groupValues[1]

        Mockito.`when`(providerProfileRepository.findByUserId(provider.id!!)).thenReturn(null)
        Mockito.`when`(providerProfileRepository.save(Mockito.any(ProviderProfile::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<ProviderProfile>(0).copy(id = "profile-1") }
        mockMvc.perform(
            post("/api/providers/profile")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(profileBody()),
        ).andExpect(status().isCreated)
    }

    @Test
    fun `booking endpoints require authentication and the correct role`() {
        val customerToken = jwtService.generateToken("customer@example.com", UserRole.CUSTOMER)
        val providerToken = jwtService.generateToken("provider@example.com", UserRole.PROVIDER)
        val body = """{"serviceId":"service-1","requestedDate":"2030-01-02","startTime":"12:00","endTime":"13:00","timeZone":"Africa/Nairobi"}"""

        mockMvc.perform(
            post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(body),
        ).andExpect(status().isUnauthorized)

        mockMvc.perform(
            post("/api/bookings")
                .header("Authorization", "Bearer $providerToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        ).andExpect(status().isForbidden)

        mockMvc.perform(
            get("/api/bookings/provider").header("Authorization", "Bearer $customerToken"),
        ).andExpect(status().isForbidden)

        mockMvc.perform(
            patch("/api/bookings/booking-1/accept")
                .header("Authorization", "Bearer $customerToken"),
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `provider can create a profile and duplicate profile is rejected`() {
        val provider = providerUser()
        Mockito.`when`(userRepository.findByEmail(provider.email)).thenReturn(provider)
        Mockito.`when`(providerProfileRepository.findByUserId(provider.id!!)).thenReturn(null)
        Mockito.`when`(providerProfileRepository.save(Mockito.any(ProviderProfile::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<ProviderProfile>(0).copy(id = "profile-1") }

        mockMvc.perform(
            post("/api/providers/profile")
                .header("Authorization", bearer(provider.email, provider.role))
                .contentType(MediaType.APPLICATION_JSON)
                .content(profileBody(latitude = "-1.2921", longitude = "36.8219")),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value("profile-1"))
            .andExpect(jsonPath("$.userId").value(provider.id))
            .andExpect(jsonPath("$.latitude").value(-1.2921))
            .andExpect(jsonPath("$.longitude").value(36.8219))

        Mockito.`when`(providerProfileRepository.findByUserId(provider.id))
            .thenReturn(providerProfile(provider.id, "profile-1"))
        mockMvc.perform(
            post("/api/providers/profile")
                .header("Authorization", bearer(provider.email, provider.role))
                .contentType(MediaType.APPLICATION_JSON)
                .content(profileBody()),
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `customers cannot create provider profiles`() {
        val customer = customerUser()
        mockMvc.perform(
            post("/api/providers/profile")
                .header("Authorization", bearer(customer.email, customer.role))
                .contentType(MediaType.APPLICATION_JSON)
                .content(profileBody()),
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `provider profile is publicly readable and invalid profile input is rejected`() {
        val profile = providerProfile("provider-1", "profile-1")
        Mockito.`when`(providerProfileRepository.findById("profile-1")).thenReturn(Optional.of(profile))

        mockMvc.perform(get("/api/providers/profile-1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.businessName").value(profile.businessName))
            .andExpect(jsonPath("$.user.passwordHash").doesNotExist())

        val provider = providerUser()
        mockMvc.perform(
            post("/api/providers/profile")
                .header("Authorization", bearer(provider.email, provider.role))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"businessName":" ","description":"Description","location":"Nairobi","contactInformation":"phone"}"""),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `provider map coordinates must be complete and within geographic bounds`() {
        val provider = providerUser()
        val authorization = bearer(provider.email, provider.role)

        mockMvc.perform(
            post("/api/providers/profile")
                .header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content(profileBody(latitude = "-1.2921")),
        ).andExpect(status().isBadRequest)

        mockMvc.perform(
            post("/api/providers/profile")
                .header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content(profileBody(latitude = "91.0", longitude = "36.8219")),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `provider can update their profile and cannot update a profile owned by another user`() {
        val owner = providerUser()
        val ownedProfile = providerProfile(owner.id!!, "profile-1")
        Mockito.`when`(userRepository.findByEmail(owner.email)).thenReturn(owner)
        Mockito.`when`(providerProfileRepository.findByUserId(owner.id)).thenReturn(ownedProfile)
        Mockito.`when`(providerProfileRepository.save(Mockito.any(ProviderProfile::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<ProviderProfile>(0) }

        mockMvc.perform(
            put("/api/providers/profile")
                .header("Authorization", bearer(owner.email, owner.role))
                .contentType(MediaType.APPLICATION_JSON)
                .content(profileBody("Updated business", latitude = "-1.2864", longitude = "36.8172")),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.businessName").value("Updated business"))
            .andExpect(jsonPath("$.latitude").value(-1.2864))
            .andExpect(jsonPath("$.longitude").value(36.8172))

        val anotherProvider = providerUser("other@example.com", "other-user")
        Mockito.`when`(userRepository.findByEmail(anotherProvider.email)).thenReturn(anotherProvider)
        Mockito.`when`(providerProfileRepository.findByUserId(anotherProvider.id!!)).thenReturn(null)
        mockMvc.perform(
            put("/api/providers/profile")
                .header("Authorization", bearer(anotherProvider.email, anotherProvider.role))
                .contentType(MediaType.APPLICATION_JSON)
                .content(profileBody("Not the owner")),
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `providers need a profile before publishing and can publish services after creating one`() {
        val provider = providerUser()
        Mockito.`when`(userRepository.findByEmail(provider.email)).thenReturn(provider)
        mockMvc.perform(
            post("/api/services")
                .header("Authorization", bearer(provider.email, provider.role))
                .contentType(MediaType.APPLICATION_JSON)
                .content(serviceBody()),
        ).andExpect(status().isConflict)

        Mockito.`when`(providerProfileRepository.findByUserId(provider.id!!))
            .thenReturn(providerProfile(provider.id, "profile-1"))
        Mockito.`when`(serviceRepository.save(Mockito.any(Service::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<Service>(0).copy(id = "service-1") }
        mockMvc.perform(
            post("/api/services")
                .header("Authorization", bearer(provider.email, provider.role))
                .contentType(MediaType.APPLICATION_JSON)
                .content(serviceBody()),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value("service-1"))
            .andExpect(jsonPath("$.providerId").value("profile-1"))
    }

    @Test
    fun `customers cannot publish services and invalid price is rejected`() {
        val customer = customerUser()
        mockMvc.perform(
            post("/api/services")
                .header("Authorization", bearer(customer.email, customer.role))
                .contentType(MediaType.APPLICATION_JSON)
                .content(serviceBody()),
        ).andExpect(status().isForbidden)

        val provider = providerUser()
        mockMvc.perform(
            post("/api/services")
                .header("Authorization", bearer(provider.email, provider.role))
                .contentType(MediaType.APPLICATION_JSON)
                .content(serviceBody(price = "0")),
        ).andExpect(status().isBadRequest)

        mockMvc.perform(
            post("/api/services")
                .header("Authorization", bearer(provider.email, provider.role))
                .contentType(MediaType.APPLICATION_JSON)
                .content(serviceBody(name = " ")),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `service discovery and details are public and only available services are exposed`() {
        val listing = service("service-1", "profile-1")
        Mockito.`when`(serviceRepository.findByAvailabilityStatus(ServiceAvailability.AVAILABLE))
            .thenReturn(listOf(listing))
        Mockito.`when`(serviceRepository.findById("service-1")).thenReturn(Optional.of(listing))

        mockMvc.perform(get("/api/services"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value(listing.name))
        mockMvc.perform(get("/api/services/service-1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value("service-1"))
    }

    @Test
    fun `provider can update and delete their own service`() {
        val provider = providerUser()
        val listing = service("service-1", "profile-1")
        Mockito.`when`(userRepository.findByEmail(provider.email)).thenReturn(provider)
        Mockito.`when`(serviceRepository.findById("service-1")).thenReturn(Optional.of(listing))
        Mockito.`when`(providerProfileRepository.findByUserId(provider.id!!))
            .thenReturn(providerProfile(provider.id, "profile-1"))
        Mockito.`when`(serviceRepository.save(Mockito.any(Service::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<Service>(0) }

        mockMvc.perform(
            put("/api/services/service-1")
                .header("Authorization", bearer(provider.email, provider.role))
                .contentType(MediaType.APPLICATION_JSON)
                .content(serviceBody(name = "Updated service")),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Updated service"))

        mockMvc.perform(
            delete("/api/services/service-1")
                .header("Authorization", bearer(provider.email, provider.role)),
        ).andExpect(status().isNoContent)
        Mockito.verify(serviceRepository).delete(listing)
    }

    @Test
    fun `provider cannot update or delete another providers service`() {
        val provider = providerUser("other@example.com", "other-user")
        val listing = service("service-1", "profile-owner")
        Mockito.`when`(userRepository.findByEmail(provider.email)).thenReturn(provider)
        Mockito.`when`(providerProfileRepository.findByUserId(provider.id!!))
            .thenReturn(providerProfile(provider.id, "profile-other"))
        Mockito.`when`(serviceRepository.findById("service-1")).thenReturn(Optional.of(listing))

        mockMvc.perform(
            put("/api/services/service-1")
                .header("Authorization", bearer(provider.email, provider.role))
                .contentType(MediaType.APPLICATION_JSON)
                .content(serviceBody()),
        ).andExpect(status().isForbidden)

        mockMvc.perform(
            delete("/api/services/service-1")
                .header("Authorization", bearer(provider.email, provider.role)),
        ).andExpect(status().isForbidden)
        Mockito.verify(serviceRepository, Mockito.never()).delete(Mockito.any(Service::class.java))
    }

    @Test
    fun `category and location filters are applied to public service discovery`() {
        Mockito.`when`(
            serviceRepository.findByAvailabilityStatusAndCategoryIgnoreCaseAndLocationContainingIgnoreCase(
                ServiceAvailability.AVAILABLE,
                "Plumbing",
                "Nairobi",
            ),
        ).thenReturn(listOf(service("service-1", "profile-1")))

        mockMvc.perform(get("/api/services?category=Plumbing&location=Nairobi"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].category").value("Plumbing"))
    }

    private fun bearer(email: String, role: UserRole) =
        "Bearer ${jwtService.generateToken(email, role)}"

    private fun providerUser(email: String = "provider@example.com", id: String = "provider-user") = User(
        id = id,
        fullName = "Provider",
        email = email,
        passwordHash = passwordEncoder.encode("password") ?: error("Password encoder returned no hash"),
        phoneNumber = "1234567890",
        role = UserRole.PROVIDER,
    )

    private fun customerUser() = User(
        id = "customer-user",
        fullName = "Customer",
        email = "customer@example.com",
        passwordHash = passwordEncoder.encode("password") ?: error("Password encoder returned no hash"),
        phoneNumber = "1234567891",
        role = UserRole.CUSTOMER,
    )

    private fun providerProfile(userId: String, id: String) = ProviderProfile(
        id = id,
        userId = userId,
        businessName = "Local Plumbing",
        description = "Home plumbing repairs",
        location = "Nairobi",
        contactInformation = "provider@example.com",
    )

    private fun service(id: String, providerId: String) = Service(
        id = id,
        providerId = providerId,
        name = "Tap repair",
        description = "Repair leaking taps",
        category = "Plumbing",
        price = BigDecimal("25.00"),
        location = "Nairobi",
        availabilityStatus = ServiceAvailability.AVAILABLE,
    )

    private fun registrationBody(role: String? = null): String {
        val roleProperty = role?.let { ""","role":"$it"""" } ?: ""
        return """{"fullName":"Test User","email":"test@example.com","password":"password","phoneNumber":"1234567890"$roleProperty}"""
    }

    private fun profileBody(
        businessName: String = "Local Plumbing",
        latitude: String? = null,
        longitude: String? = null,
    ): String {
        val latitudeProperty = latitude?.let { ""","latitude":$it""" } ?: ""
        val longitudeProperty = longitude?.let { ""","longitude":$it""" } ?: ""
        return """{"businessName":"$businessName","description":"Home plumbing repairs","location":"Nairobi","contactInformation":"provider@example.com"$latitudeProperty$longitudeProperty}"""
    }

    private fun serviceBody(name: String = "Tap repair", price: String = "25.00") =
        """{"name":"$name","description":"Repair leaking taps","category":"Plumbing","price":$price,"location":"Nairobi","availabilityStatus":"AVAILABLE"}"""
}
