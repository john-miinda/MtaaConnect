package com.mtaaconnect.android.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface MtaaApi {
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("api/users/register")
    suspend fun register(@Body request: RegisterRequest): UserDto

    @GET("api/services")
    suspend fun services(
        @Query("category") category: String? = null,
        @Query("location") location: String? = null,
    ): List<ServiceDto>

    @GET("api/services/{id}")
    suspend fun service(@Path("id") id: String): ServiceDto

    @GET("api/providers/{id}")
    suspend fun provider(@Path("id") id: String): ProviderProfileDto

    @GET("api/providers/profile")
    suspend fun myProviderProfile(): Response<ProviderProfileDto>

    @GET("api/providers/profile/services")
    suspend fun myProviderServices(): List<ServiceDto>

    @POST("api/providers/profile")
    suspend fun createProviderProfile(@Body request: ProviderProfileRequest): ProviderProfileDto

    @PUT("api/providers/profile")
    suspend fun updateProviderProfile(@Body request: ProviderProfileRequest): ProviderProfileDto

    @POST("api/services")
    suspend fun createService(@Body request: ServiceRequest): ServiceDto

    @PUT("api/services/{id}")
    suspend fun updateService(@Path("id") id: String, @Body request: ServiceRequest): ServiceDto

    @DELETE("api/services/{id}")
    suspend fun deleteService(@Path("id") id: String): Response<Unit>

    @GET("api/providers/{providerId}/services")
    suspend fun providerServices(@Path("providerId") providerId: String): List<ServiceDto>

    @POST("api/bookings")
    suspend fun createBooking(@Body request: CreateBookingRequest): BookingDto

    @GET("api/bookings/my-bookings")
    suspend fun myBookings(): List<BookingDto>

    @GET("api/bookings/provider")
    suspend fun providerBookings(): List<BookingDto>

    @GET("api/bookings/{id}")
    suspend fun booking(@Path("id") id: String): BookingDto

    @PATCH("api/bookings/{id}/cancel")
    suspend fun cancelBooking(@Path("id") id: String): BookingDto

    @PATCH("api/bookings/{id}/accept")
    suspend fun acceptBooking(@Path("id") id: String): BookingDto

    @PATCH("api/bookings/{id}/reject")
    suspend fun rejectBooking(@Path("id") id: String): BookingDto

    @PATCH("api/bookings/{id}/complete")
    suspend fun completeBooking(@Path("id") id: String): BookingDto
}
