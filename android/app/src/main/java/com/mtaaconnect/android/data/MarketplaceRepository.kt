package com.mtaaconnect.android.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class MarketplaceRepository(
    private val api: MtaaApi,
    private val sessionStore: SessionStorage,
) {
    fun restoreSession(): Session? {
        val stored = sessionStore.read() ?: return null
        return try {
            val decoded = SessionDecoder.decode(stored.token, stored.fullName)
            if (decoded.role != stored.role || decoded.email != stored.email) {
                sessionStore.clear()
                null
            } else {
                decoded
            }
        } catch (_: IllegalArgumentException) {
            sessionStore.clear()
            null
        }
    }

    suspend fun login(email: String, password: String): Session {
        val response = api.login(LoginRequest(email.trim(), password))
        if (!response.tokenType.equals("Bearer", ignoreCase = true)) {
            throw ApiFailure(message = "The server returned an unsupported token type.")
        }
        val session = SessionDecoder.decode(response.token, response.user.fullName)
        if (session.email != response.user.email || session.role != response.user.role) {
            throw ApiFailure(message = "The server returned inconsistent account details.")
        }
        sessionStore.save(session)
        return session
    }

    suspend fun register(
        fullName: String,
        email: String,
        password: String,
        phoneNumber: String,
        role: String,
    ): UserDto = api.register(
        RegisterRequest(fullName.trim(), email.trim(), password, phoneNumber.trim(), role),
    )

    fun logout() = sessionStore.clear()

    suspend fun services(category: String?, location: String?) =
        api.services(category?.trim()?.takeIf(String::isNotEmpty), location?.trim()?.takeIf(String::isNotEmpty))

    suspend fun service(id: String) = api.service(id)
    suspend fun booking(id: String) = api.booking(id)
    suspend fun provider(id: String) = api.provider(id)
    suspend fun providerServices(id: String) = api.providerServices(id)
    suspend fun createProviderProfile(request: ProviderProfileRequest) = api.createProviderProfile(request)
    suspend fun updateProviderProfile(request: ProviderProfileRequest) = api.updateProviderProfile(request)
    suspend fun createService(request: ServiceRequest) = api.createService(request)
    suspend fun updateService(id: String, request: ServiceRequest) = api.updateService(id, request)
    suspend fun deleteService(id: String) = api.deleteService(id)
    suspend fun createBooking(serviceId: String, date: String, start: String, end: String, notes: String?) =
        api.createBooking(
            CreateBookingRequest(
                serviceId = serviceId,
                requestedDate = LocalDate.parse(date).toString(),
                startTime = LocalTime.parse(start).toString(),
                endTime = LocalTime.parse(end).toString(),
                timeZone = ZoneId.systemDefault().id,
                customerNotes = notes?.trim()?.takeIf(String::isNotEmpty),
            ),
        )

    suspend fun myBookings() = api.myBookings()
    suspend fun providerBookings() = api.providerBookings()
    suspend fun cancelBooking(id: String) = api.cancelBooking(id)
    suspend fun acceptBooking(id: String) = api.acceptBooking(id)
    suspend fun rejectBooking(id: String) = api.rejectBooking(id)
    suspend fun completeBooking(id: String) = api.completeBooking(id)
}
