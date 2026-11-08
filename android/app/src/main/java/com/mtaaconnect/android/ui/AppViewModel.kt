package com.mtaaconnect.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mtaaconnect.android.BuildConfig
import com.mtaaconnect.android.data.ApiClient
import com.mtaaconnect.android.data.BookingDto
import com.mtaaconnect.android.data.MarketplaceRepository
import com.mtaaconnect.android.data.ProviderProfileDto
import com.mtaaconnect.android.data.ProviderProfileRequest
import com.mtaaconnect.android.data.SecureSessionStore
import com.mtaaconnect.android.data.ServiceDto
import com.mtaaconnect.android.data.ServiceRequest
import com.mtaaconnect.android.data.Session
import com.mtaaconnect.android.data.SessionDecoder
import com.mtaaconnect.android.data.toUserMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppUiState(
    val session: Session? = null,
    val route: String = "home",
    val loading: Boolean = false,
    val servicesLoading: Boolean = false,
    val providerServicesLoading: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val servicesError: String? = null,
    val providerServicesError: String? = null,
    val services: List<ServiceDto> = emptyList(),
    val providerServices: List<ServiceDto> = emptyList(),
    val bookings: List<BookingDto> = emptyList(),
    val selectedService: ServiceDto? = null,
    val selectedBooking: BookingDto? = null,
    val selectedProvider: ProviderProfileDto? = null,
    val categoryFilter: String = "",
    val locationFilter: String = "",
    val profileId: String? = null,
    val providerProfile: ProviderProfileDto? = null,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SecureSessionStore(application)
    private val profilePreferences = application.getSharedPreferences("mtaaconnect_local", 0)
    private val repository: MarketplaceRepository
    private val restoredSession = restoreSession()
    private val _state = MutableStateFlow(
        AppUiState(
            session = restoredSession,
            route = if (restoredSession?.role == "PROVIDER") "provider" else "home",
            profileId = profilePreferences.getString(PROFILE_ID_KEY, null),
        ),
    )
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    init {
        val client = ApiClient(
            baseUrl = BuildConfig.API_BASE_URL,
            tokenProvider = { store.read()?.token },
            onUnauthorized = {
                viewModelScope.launch(Dispatchers.Main) {
                    store.clear()
                    _state.value = _state.value.copy(
                        session = null,
                        route = "login",
                        error = "Your session expired. Please sign in again.",
                        busy = false,
                    )
                }
            },
        )
        repository = MarketplaceRepository(client.api, store)
        when (_state.value.session?.role) {
            "PROVIDER" -> {
                refreshBookings()
                refreshProviderServices()
            }
            "CUSTOMER" -> {
                refreshServices()
                refreshBookings()
            }
            else -> refreshServices()
        }
    }

    fun navigate(route: String) {
        _state.value = _state.value.copy(route = route, error = null, notice = null)
        when (route) {
            "home" -> refreshServices()
            "bookings", "provider-bookings" -> refreshBookings()
            "provider" -> refreshProviderServices()
        }
    }

    fun openService(service: ServiceDto) {
        _state.value = _state.value.copy(selectedService = service, route = "detail", error = null)
    }

    fun editService(service: ServiceDto?) {
        _state.value = _state.value.copy(selectedService = service, route = "service-editor", error = null)
    }

    fun editProfile() {
        _state.value = _state.value.copy(route = "profile-editor", error = null)
    }

    fun returnTo(route: String) {
        _state.value = _state.value.copy(route = route, error = null, notice = null)
    }

    fun openProvider(service: ServiceDto) {
        val id = service.providerId
        launchRequest {
            val provider = repository.provider(id)
            _state.value = _state.value.copy(selectedProvider = provider, route = "provider-public")
        }
    }

    fun openBooking(booking: BookingDto) {
        val id = booking.id ?: return
        launchRequest {
            val detailed = repository.booking(id)
            _state.value = _state.value.copy(selectedBooking = detailed, route = "booking-detail")
        }
    }

    fun filterServices(category: String, location: String) {
        _state.value = _state.value.copy(categoryFilter = category, locationFilter = location)
        refreshServices(category, location)
    }

    fun refreshServices(category: String = _state.value.categoryFilter, location: String = _state.value.locationFilter) {
        launchRequest(serviceLoading = true, errorTarget = RequestErrorTarget.SERVICES) {
            val services = repository.services(category, location)
            _state.value = _state.value.copy(services = services)
        }
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || !email.contains('@') || password.isBlank()) {
            _state.value = _state.value.copy(error = "Enter a valid email address and password.")
            return
        }
        launchRequest(busy = true) {
            val session = repository.login(email, password)
            _state.value = _state.value.copy(
                session = session,
                route = if (session.role == "ADMIN") "home" else if (session.role == "PROVIDER") "provider" else "home",
                error = null,
                notice = "Welcome, ${session.fullName}.",
            )
            if (session.role == "PROVIDER") {
                refreshProviderServices()
                refreshBookings()
            } else {
                refreshServices()
                if (session.role == "CUSTOMER") refreshBookings()
            }
        }
    }

    fun register(fullName: String, email: String, password: String, phone: String, role: String) {
        when {
            fullName.isBlank() || email.isBlank() || password.isBlank() || phone.isBlank() ->
                _state.value = _state.value.copy(error = "Complete every field before continuing.")
            !email.contains('@') -> _state.value = _state.value.copy(error = "Enter a valid email address.")
            password.length < 8 -> _state.value = _state.value.copy(error = "Use a password with at least 8 characters.")
            role !in setOf("CUSTOMER", "PROVIDER") -> _state.value = _state.value.copy(error = "Choose a valid account type.")
            else -> launchRequest(busy = true) {
                repository.register(fullName, email, password, phone, role)
                _state.value = _state.value.copy(
                    route = "login",
                    error = null,
                    notice = "Account created. Sign in to continue.",
                )
            }
        }
    }

    fun logout() {
        launchRequest {
            repository.logout()
            profilePreferences.edit().remove(PROFILE_ID_KEY).apply()
            _state.value = AppUiState(route = "home")
            refreshServices()
        }
    }

    fun createBooking(serviceId: String, date: String, start: String, end: String, notes: String) {
        if (date.isBlank() || start.isBlank() || end.isBlank()) {
            _state.value = _state.value.copy(error = "Choose a date, start time, and end time.")
            return
        }
        launchRequest(busy = true) {
            repository.createBooking(serviceId, date, start, end, notes)
            _state.value = _state.value.copy(route = "bookings", notice = "Booking request sent.")
            refreshBookings()
        }
    }

    fun refreshBookings() {
        val role = _state.value.session?.role ?: return
        if (role != "CUSTOMER" && role != "PROVIDER") return
        launchRequest(loading = true) {
            val bookings = if (role == "PROVIDER") repository.providerBookings() else repository.myBookings()
            _state.value = _state.value.copy(bookings = bookings)
        }
    }

    fun bookingAction(id: String, action: String) {
        launchRequest(busy = true) {
            when (action) {
                "cancel" -> repository.cancelBooking(id)
                "accept" -> repository.acceptBooking(id)
                "reject" -> repository.rejectBooking(id)
                "complete" -> repository.completeBooking(id)
                else -> error("Unsupported booking action")
            }
            _state.value = _state.value.copy(notice = "Booking updated.")
            refreshBookings()
        }
    }

    fun saveProfile(
        businessName: String,
        description: String,
        location: String,
        contact: String,
        latitude: String,
        longitude: String,
        updateExisting: Boolean,
    ) {
        if (listOf(businessName, description, location, contact).any(String::isBlank)) {
            _state.value = _state.value.copy(error = "Complete every provider profile field.")
            return
        }
        val parsedLatitude = latitude.trim().takeIf(String::isNotEmpty)?.toDoubleOrNull()
        val parsedLongitude = longitude.trim().takeIf(String::isNotEmpty)?.toDoubleOrNull()
        val coordinateError = when {
            latitude.isBlank() != longitude.isBlank() ->
                "Enter both latitude and longitude, or leave both blank."
            !latitude.isBlank() && (parsedLatitude == null || parsedLongitude == null) ->
                "Enter valid numeric map coordinates."
            parsedLatitude != null && parsedLatitude !in -90.0..90.0 ->
                "Latitude must be between -90 and 90."
            parsedLongitude != null && parsedLongitude !in -180.0..180.0 ->
                "Longitude must be between -180 and 180."
            else -> null
        }
        if (coordinateError != null) {
            _state.value = _state.value.copy(error = coordinateError)
            return
        }
        val request = ProviderProfileRequest(
            businessName = businessName.trim(),
            description = description.trim(),
            location = location.trim(),
            contactInformation = contact.trim(),
            latitude = parsedLatitude,
            longitude = parsedLongitude,
        )
        launchRequest(busy = true) {
            val profile = if (updateExisting) {
                repository.updateProviderProfile(request)
            } else {
                repository.createProviderProfile(request)
            }
            val id = profile.id ?: error("The server did not return a provider profile ID.")
            profilePreferences.edit().putString(PROFILE_ID_KEY, id).apply()
            _state.value = _state.value.copy(
                providerProfile = profile,
                profileId = id,
                route = "provider",
                notice = "Provider profile saved.",
            )
            refreshProviderServices()
        }
    }

    fun saveService(
        id: String?,
        name: String,
        description: String,
        category: String,
        price: String,
        location: String,
        available: Boolean,
    ) {
        if (listOf(name, description, category, price, location).any(String::isBlank) ||
            price.toBigDecimalOrNull()?.let { it > java.math.BigDecimal.ZERO } != true
        ) {
            _state.value = _state.value.copy(error = "Enter all service details and a price greater than zero.")
            return
        }
        val request = ServiceRequest(
            name.trim(),
            description.trim(),
            category.trim(),
            price.trim(),
            location.trim(),
            if (available) "AVAILABLE" else "UNAVAILABLE",
        )
        launchRequest(busy = true) {
            if (id == null) repository.createService(request) else repository.updateService(id, request)
            _state.value = _state.value.copy(route = "provider", notice = "Service listing saved.")
            refreshProviderServices()
        }
    }

    fun deleteService(id: String) {
        launchRequest(busy = true) {
            repository.deleteService(id)
            _state.value = _state.value.copy(notice = "Service listing deleted.")
            refreshProviderServices()
        }
    }

    private fun refreshProviderServices() {
        if (_state.value.session?.role != "PROVIDER") return
        launchRequest(
            providerServicesLoading = true,
            errorTarget = RequestErrorTarget.PROVIDER_SERVICES,
        ) {
            val profile = repository.myProviderProfile()
            val profileId = profile?.id
            if (profile != null && profileId == null) {
                error("The server returned a provider profile without an ID.")
            }
            if (profileId == null) {
                profilePreferences.edit().remove(PROFILE_ID_KEY).apply()
            } else {
                profilePreferences.edit().putString(PROFILE_ID_KEY, profileId).apply()
            }
            val services = repository.myProviderServices()
            _state.value = _state.value.copy(
                providerProfile = profile,
                profileId = profileId,
                providerServices = services,
            )
        }
    }

    private fun launchRequest(
        loading: Boolean = false,
        serviceLoading: Boolean? = null,
        providerServicesLoading: Boolean? = null,
        busy: Boolean = false,
        errorTarget: RequestErrorTarget? = null,
        request: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            val currentState = _state.value
            _state.value = currentState.copy(
                loading = loading,
                servicesLoading = serviceLoading ?: currentState.servicesLoading,
                providerServicesLoading = providerServicesLoading ?: currentState.providerServicesLoading,
                busy = busy,
                error = null,
                notice = null,
                servicesError = if (errorTarget == RequestErrorTarget.SERVICES) null else currentState.servicesError,
                providerServicesError = if (errorTarget == RequestErrorTarget.PROVIDER_SERVICES) {
                    null
                } else {
                    currentState.providerServicesError
                },
            )
            try {
                request()
            } catch (exception: Exception) {
                val message = exception.toUserMessage()
                val latestState = _state.value
                _state.value = latestState.copy(
                    error = message,
                    servicesError = if (errorTarget == RequestErrorTarget.SERVICES) {
                        message
                    } else {
                        latestState.servicesError
                    },
                    providerServicesError = if (errorTarget == RequestErrorTarget.PROVIDER_SERVICES) {
                        message
                    } else {
                        latestState.providerServicesError
                    },
                )
            } finally {
                val latestState = _state.value
                _state.value = latestState.copy(
                    loading = false,
                    servicesLoading = if (serviceLoading == true) {
                        false
                    } else {
                        latestState.servicesLoading
                    },
                    providerServicesLoading = if (providerServicesLoading == true) {
                        false
                    } else {
                        latestState.providerServicesLoading
                    },
                    busy = false,
                )
            }
        }
    }

    private fun restoreSession(): Session? {
        val stored = store.read() ?: return null
        return try {
            SessionDecoder.decode(stored.token, stored.fullName).takeIf {
                it.email == stored.email && it.role == stored.role
            }
        } catch (_: IllegalArgumentException) {
            store.clear()
            null
        }
    }

    private companion object {
        const val PROFILE_ID_KEY = "provider_profile_id"
    }

    private enum class RequestErrorTarget {
        SERVICES,
        PROVIDER_SERVICES,
    }
}
