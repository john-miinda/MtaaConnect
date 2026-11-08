package com.mtaaconnect.android.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Storefront
import com.mtaaconnect.android.data.BookingDto
import com.mtaaconnect.android.data.ServiceDto
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import com.mtaaconnect.android.BuildConfig
import com.mtaaconnect.android.data.ProfileImageDto
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MtaaApp(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsState()
    val currentSession = state.session
    val navController = rememberNavController()
    LaunchedEffect(state.route) {
        if (navController.currentDestination?.route != state.route) {
            navController.navigate(state.route) { launchSingleTop = true }
        }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("MtaaConnect", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        currentSession?.let {
                            Text(
                                "${it.fullName} · ${it.role}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                            )
                        }
                    }
                },
                actions = {
                    if (currentSession == null) {
                        TextButton(onClick = { viewModel.returnTo("login") }) { Text("Sign in") }
                    } else {
                        TextButton(onClick = viewModel::logout) { Text("Log out") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
        bottomBar = {
            if (currentSession != null) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                ) {
                    navigationItemsFor(currentSession.role).forEach { label ->
                        val destination = when (label) {
                            "Explore" -> "home"
                            "My bookings" -> "bookings"
                            "Dashboard" -> "provider"
                            "Incoming bookings" -> "provider-bookings"
                            else -> if (currentSession.role == "PROVIDER") "profile-editor" else "account-images"
                        }
                        val selected = when (destination) {
                            "home" -> state.route == "home"
                            "bookings" -> state.route == "bookings" || state.route == "booking-detail"
                            "provider" -> state.route == "provider" || state.route == "service-editor"
                            "provider-bookings" -> state.route == "provider-bookings"
                            else -> state.route == "profile-editor" || state.route == "account-images"
                        }
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                when (destination) {
                                    "profile-editor" -> viewModel.editProfile()
                                    "account-images" -> viewModel.editAccountImages()
                                    else -> viewModel.navigate(destination)
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = when (label) {
                                        "Explore" -> Icons.Outlined.Home
                                        "My bookings", "Incoming bookings" -> Icons.Outlined.CalendarMonth
                                        "Dashboard" -> Icons.Outlined.Storefront
                                        else -> Icons.Outlined.PersonOutline
                                    },
                                    contentDescription = null,
                                )
                            },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            state.error?.let { Banner(it, true) }
            state.notice?.let { Banner(it, false) }
            AppNavigation(navController, state.route) {
                AppDestination(state, viewModel)
            }
        }
    }
}

@Composable
private fun AppNavigation(
    navController: NavHostController,
    startDestination: String,
    content: @Composable () -> Unit,
) {
    val destinations = listOf(
        "home", "login", "register", "detail", "provider-public", "bookings", "provider-bookings",
        "booking-detail", "provider", "profile-editor", "account-images", "service-editor",
    )
    NavHost(navController = navController, startDestination = startDestination) {
        destinations.forEach { destination ->
            composable(destination) { content() }
        }
    }
}

@Composable
private fun AppDestination(state: AppUiState, viewModel: AppViewModel) {
    when (state.route) {
                "login" -> LoginScreen(
                    busy = state.busy,
                    onLogin = viewModel::login,
                    onRegister = { viewModel.returnTo("register") },
                )
                "register" -> RegisterScreen(
                    busy = state.busy,
                    onRegister = viewModel::register,
                    onLogin = { viewModel.returnTo("login") },
                )
                "detail" -> ServiceDetailScreen(
                    service = state.selectedService,
                    role = state.session?.role,
                    busy = state.busy,
                    onBack = { viewModel.returnTo("home") },
                    onProvider = { state.selectedService?.let(viewModel::openProvider) },
                    onBook = { serviceId, date, start, end, notes ->
                        viewModel.createBooking(serviceId, date, start, end, notes)
                    },
                    onLogin = { viewModel.returnTo("login") },
                )
                "provider-public" -> ProviderPublicScreen(
                    state.selectedProvider,
                    state.publicProviderImages,
                    state.session?.token,
                ) { viewModel.returnTo("detail") }
                "bookings" -> BookingListScreen(
                    title = "My bookings",
                    bookings = state.bookings,
                    loading = state.loading,
                    providerMode = false,
                    onRefresh = viewModel::refreshBookings,
                    onOpen = viewModel::openBooking,
                    onAction = viewModel::bookingAction,
                )
                "provider-bookings" -> BookingListScreen(
                    title = "Incoming bookings",
                    bookings = state.bookings,
                    loading = state.loading,
                    providerMode = true,
                    onRefresh = viewModel::refreshBookings,
                    onOpen = viewModel::openBooking,
                    onAction = viewModel::bookingAction,
                )
                "booking-detail" -> BookingDetailScreen(state.selectedBooking) {
                    viewModel.returnTo(
                        if (state.session?.role == "PROVIDER") "provider-bookings" else "bookings",
                    )
                }
                "provider" -> ProviderDashboard(
                    services = state.providerServices,
                    profileId = state.profileId,
                    loading = state.providerServicesLoading,
                    error = state.providerServicesError,
                    onProfile = viewModel::editProfile,
                    onAddService = { viewModel.editService(null) },
                    onEdit = viewModel::editService,
                    onDelete = viewModel::deleteService,
                    onRefresh = { viewModel.navigate("provider") },
                )
                "profile-editor" -> ProfileEditorScreen(
                    profile = state.providerProfile,
                    images = state.profileImages,
                    busy = state.busy,
                    authToken = state.session?.token,
                    onBack = { viewModel.returnTo("provider") },
                    onSave = viewModel::saveProfile,
                    onUploadImage = viewModel::uploadProfileImage,
                    onDeleteImage = viewModel::deleteProfileImage,
                )
                "account-images" -> AccountImagesScreen(
                    images = state.profileImages,
                    busy = state.busy,
                    authToken = state.session?.token,
                    onUpload = { bytes -> viewModel.uploadProfileImage(bytes, "CUSTOMER_PROFILE") },
                    onDelete = viewModel::deleteProfileImage,
                )
                "service-editor" -> ServiceEditorScreen(
                    service = state.selectedService,
                    busy = state.busy,
                    onBack = { viewModel.returnTo("provider") },
                    onSave = viewModel::saveService,
                )
                else -> ExploreScreen(
                    services = state.services,
                    loading = state.servicesLoading,
                    error = state.servicesError,
                    category = state.categoryFilter,
                    location = state.locationFilter,
                    role = state.session?.role,
                    onFilter = viewModel::filterServices,
                    onOpen = viewModel::openService,
                    onRefresh = viewModel::refreshServices,
                    onLogin = { viewModel.returnTo("login") },
                )
    }
}

@Composable
private fun LoginScreen(
    busy: Boolean,
    onLogin: (String, String) -> Unit,
    onRegister: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    FormColumn(title = "Welcome back", subtitle = "Sign in to find trusted local services.") {
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            password,
            { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onLogin(email, password) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            if (busy) CircularProgressIndicator() else Text("Sign in")
        }
        TextButton(onClick = onRegister, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("New to MtaaConnect? Create an account")
        }
    }
}

@Composable
private fun RegisterScreen(
    busy: Boolean,
    onRegister: (String, String, String, String, String) -> Unit,
    onLogin: () -> Unit,
) {
    var fullName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf("CUSTOMER") }
    FormColumn(title = "Create your account", subtitle = "Join as a customer or local provider.") {
        OutlinedTextField(fullName, { fullName = it }, label = { Text("Full name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(phone, { phone = it }, label = { Text("Phone number") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            password,
            { password = it },
            label = { Text("Password (8+ characters)") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Account type", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("CUSTOMER", "PROVIDER").forEach { candidate ->
                if (candidate == role) {
                    Button(
                        onClick = { role = candidate },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) { Text(candidate.lowercase().replaceFirstChar(Char::uppercase)) }
                } else {
                    OutlinedButton(
                        onClick = { role = candidate },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) { Text(candidate.lowercase().replaceFirstChar(Char::uppercase)) }
                }
            }
        }
        Button(
            onClick = { onRegister(fullName, email, password, phone, role) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) { if (busy) CircularProgressIndicator() else Text("Create account") }
        TextButton(onClick = onLogin, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Already registered? Sign in") }
    }
}

@Composable
private fun ExploreScreen(
    services: List<ServiceDto>,
    loading: Boolean,
    error: String?,
    category: String,
    location: String,
    role: String?,
    onFilter: (String, String) -> Unit,
    onOpen: (ServiceDto) -> Unit,
    onRefresh: () -> Unit,
    onLogin: () -> Unit,
) {
    var categoryText by rememberSaveable(category) { mutableStateOf(category) }
    var locationText by rememberSaveable(location) { mutableStateOf(location) }
    Column(Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primary,
            tonalElevation = 2.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
        ) {
            Column(
                Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "Trusted local expertise",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Find help nearby.",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Discover vetted professionals and book with confidence.",
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                )
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        categoryText, { categoryText = it }, label = { Text("Category") },
                        modifier = Modifier.weight(1f), singleLine = true,
                    )
                    OutlinedTextField(
                        locationText, { locationText = it }, label = { Text("Location") },
                        modifier = Modifier.weight(1f), singleLine = true,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { onFilter(categoryText, locationText) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) { Text("Find services") }
                    TextButton(onClick = {
                        categoryText = ""
                        locationText = ""
                        onFilter("", "")
                    }) { Text("Clear") }
                    if (role == null) TextButton(onClick = onLogin) { Text("Sign in") }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (loading && services.isEmpty()) LoadingState()
        else if (services.isEmpty() && error != null) {
            EmptyState("Couldn't load services", "Check your connection and try again.", onRefresh)
        } else if (services.isEmpty()) {
            EmptyState("No services found", "Try another category or location.", onRefresh)
        }
        else LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(services, key = { it.id ?: it.name }) { service ->
                ServiceCard(service, onClick = { onOpen(service) })
            }
        }
    }
}

@Composable
private fun ServiceDetailScreen(
    service: ServiceDto?,
    role: String?,
    busy: Boolean,
    onBack: () -> Unit,
    onProvider: () -> Unit,
    onBook: (String, String, String, String, String) -> Unit,
    onLogin: () -> Unit,
) {
    if (service == null) {
        EmptyState("Service unavailable", "Return to explore and choose another listing.", onBack)
        return
    }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().plusDays(1).toString()) }
    var start by rememberSaveable { mutableStateOf("09:00") }
    var end by rememberSaveable { mutableStateOf("10:00") }
    var notes by rememberSaveable { mutableStateOf("") }
    FormColumn(title = service.name, subtitle = service.category) {
        Text(service.description)
        Text("KES ${service.price}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Available in ${service.location}")
        OutlinedButton(onClick = onProvider, modifier = Modifier.fillMaxWidth()) { Text("View provider") }
        if (role == "CUSTOMER") {
            HorizontalDivider()
            Text("Request a booking", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(date, { date = it }, label = { Text("Date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(start, { start = it }, label = { Text("Start (HH:MM)") }, modifier = Modifier.weight(1f))
                OutlinedTextField(end, { end = it }, label = { Text("End (HH:MM)") }, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = { service.id?.let { onBook(it, date, start, end, notes) } },
                enabled = !busy && service.id != null,
                modifier = Modifier.fillMaxWidth(),
            ) { if (busy) CircularProgressIndicator() else Text("Send booking request") }
        } else if (role == null) {
            Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) { Text("Sign in to book") }
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to services") }
    }
}

@Composable
private fun ProviderPublicScreen(
    provider: com.mtaaconnect.android.data.ProviderProfileDto?,
    images: List<ProfileImageDto>,
    authToken: String?,
    onBack: () -> Unit,
) {
    if (provider == null) {
        LoadingState()
        return
    }
    FormColumn(title = provider.businessName, subtitle = provider.location) {
        images.forEach { image -> RemoteProfileImage(image, authToken) }
        Text(provider.description)
        Text("Contact: ${provider.contactInformation}")
        GoogleMapsButton(
            query = if (provider.latitude != null && provider.longitude != null) {
                "${provider.latitude},${provider.longitude}"
            } else {
                provider.location
            },
            label = "View location in Google Maps",
        )
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to service") }
    }
}

@Composable
private fun BookingListScreen(
    title: String,
    bookings: List<BookingDto>,
    loading: Boolean,
    providerMode: Boolean,
    onRefresh: () -> Unit,
    onOpen: (BookingDto) -> Unit,
    onAction: (String, String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            TextButton(onClick = onRefresh) { Text("Refresh") }
        }
        if (loading && bookings.isEmpty()) LoadingState()
        else if (bookings.isEmpty()) EmptyState("No bookings yet", "New requests will appear here.", onRefresh)
        else LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(bookings, key = { it.id ?: it.createdAt }) { booking ->
                BookingCard(booking, providerMode, onOpen, onAction)
            }
        }
    }
}

@Composable
private fun BookingCard(
    booking: BookingDto,
    providerMode: Boolean,
    onOpen: (BookingDto) -> Unit,
    onAction: (String, String) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.32f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${booking.requestedDate} · ${booking.startTime}–${booking.endTime}", fontWeight = FontWeight.Bold)
            Text(
                booking.status.lowercase().replaceFirstChar(Char::uppercase),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text("Service booking")
            TextButton(onClick = { onOpen(booking) }) { Text("Booking details") }
            val id = booking.id
            if (id != null && providerMode && booking.status == "PENDING") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onAction(id, "accept") }) { Text("Accept") }
                    OutlinedButton(onClick = { onAction(id, "reject") }) { Text("Decline") }
                }
            } else if (id != null && providerMode && booking.status == "ACCEPTED") {
                Button(onClick = { onAction(id, "complete") }) { Text("Mark completed") }
            } else if (id != null && !providerMode && booking.status in setOf("PENDING", "ACCEPTED")) {
                OutlinedButton(onClick = { onAction(id, "cancel") }) { Text("Cancel booking") }
            }
        }
    }
}

@Composable
private fun BookingDetailScreen(booking: BookingDto?, onBack: () -> Unit) {
    if (booking == null) {
        EmptyState("Booking not found", "Return to your bookings.", onBack)
        return
    }
    FormColumn(
        title = "Booking details",
        subtitle = "Status: ${booking.status.lowercase().replaceFirstChar(Char::uppercase)}",
    ) {
        Text("Date: ${booking.requestedDate}")
        Text("Time: ${booking.startTime}–${booking.endTime} (${booking.timeZone})")
        booking.customerNotes?.let { Text("Notes: $it") }
        Text("Your booking is securely linked with the service provider.")
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to bookings") }
    }
}

@Composable
private fun ProviderDashboard(
    services: List<ServiceDto>,
    profileId: String?,
    loading: Boolean,
    error: String?,
    onProfile: () -> Unit,
    onAddService: () -> Unit,
    onEdit: (ServiceDto) -> Unit,
    onDelete: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Column(
                Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "PROVIDER STUDIO",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold,
                )
                Text("Your business, beautifully presented.", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Manage your profile, services, and incoming requests.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onProfile) { Text("Provider profile") }
                Button(onClick = onAddService, enabled = profileId != null) { Text("Add service") }
                TextButton(onClick = onRefresh) { Text("Refresh") }
            }
            if (profileId == null) Text("Create your provider profile before publishing services.")
        }
        if (loading && services.isEmpty()) LoadingState()
        else if (services.isEmpty() && error != null) EmptyState(
            "Couldn't load your listings",
            "Check your connection and try again.",
            onRefresh,
        )
        else if (services.isEmpty() && profileId == null) EmptyState(
            "Set up your provider profile",
            "Create your business profile before adding service listings.",
            onProfile,
        )
        else if (services.isEmpty()) EmptyState(
            "No service listings",
            "Create your first listing to help customers discover your business.",
            onRefresh,
        )
        else LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(services, key = { it.id ?: it.name }) { service ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.32f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(service.name, fontWeight = FontWeight.Bold)
                        Text("${service.category} · ${service.location} · KES ${service.price}")
                        Text("Status: ${service.availabilityStatus}")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onEdit(service) }) { Text("Edit") }
                            TextButton(onClick = { service.id?.let(onDelete) }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileEditorScreen(
    profile: com.mtaaconnect.android.data.ProviderProfileDto?,
    images: List<ProfileImageDto>,
    busy: Boolean,
    authToken: String?,
    onBack: () -> Unit,
    onSave: (String, String, String, String, String, String, Boolean) -> Unit,
    onUploadImage: (ByteArray, String) -> Unit,
    onDeleteImage: (String) -> Unit,
) {
    var business by rememberSaveable(profile?.id) { mutableStateOf(profile?.businessName.orEmpty()) }
    var description by rememberSaveable(profile?.id) { mutableStateOf(profile?.description.orEmpty()) }
    var location by rememberSaveable(profile?.id) { mutableStateOf(profile?.location.orEmpty()) }
    var contact by rememberSaveable(profile?.id) { mutableStateOf(profile?.contactInformation.orEmpty()) }
    var latitude by rememberSaveable(profile?.id) { mutableStateOf(profile?.latitude?.toString().orEmpty()) }
    var longitude by rememberSaveable(profile?.id) { mutableStateOf(profile?.longitude?.toString().orEmpty()) }
    var updateExisting by rememberSaveable(profile?.id) { mutableStateOf(profile != null) }
    FormColumn(title = "Provider profile", subtitle = "Tell customers about your business.") {
        ProfileImageManager(
            title = "Business profile image",
            category = "PROVIDER_PROFILE",
            images = images,
            busy = busy,
            authToken = authToken,
            onUpload = { onUploadImage(it, "PROVIDER_PROFILE") },
            onDelete = onDeleteImage,
        )
        ProfileImageManager(
            title = "Portfolio",
            category = "PORTFOLIO",
            images = images,
            busy = busy,
            authToken = authToken,
            onUpload = { onUploadImage(it, "PORTFOLIO") },
            onDelete = onDeleteImage,
        )
        OutlinedTextField(business, { business = it }, label = { Text("Business name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(description, { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(location, { location = it }, label = { Text("Location") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(contact, { contact = it }, label = { Text("Contact information") }, modifier = Modifier.fillMaxWidth())
        HorizontalDivider()
        Text("Map location", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Optional. Copy your business coordinates from Google Maps to share a public pin. Find the location, then long-press the map to drop a pin.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                latitude,
                { latitude = it },
                label = { Text("Latitude") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            OutlinedTextField(
                longitude,
                { longitude = it },
                label = { Text("Longitude") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }
        val mapCoordinates = latitude.toDoubleOrNull()?.let { lat ->
            longitude.toDoubleOrNull()?.let { lon -> "$lat,$lon" }
        }
        if (location.isNotBlank()) {
            GoogleMapsButton(
                query = mapCoordinates ?: location,
                label = if (mapCoordinates == null) "Preview address in Google Maps" else "Preview pin in Google Maps",
            )
        }
        if (profile == null) {
            TextButton(onClick = { updateExisting = !updateExisting }) {
                Text(if (updateExisting) "Switch to creating a new profile" else "Already have a profile? Update it")
            }
        }
        Button(
            onClick = { onSave(business, description, location, contact, latitude, longitude, updateExisting) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { if (busy) CircularProgressIndicator() else Text(if (updateExisting) "Update profile" else "Create profile") }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

@Composable
private fun AccountImagesScreen(
    images: List<ProfileImageDto>,
    busy: Boolean,
    authToken: String?,
    onUpload: (ByteArray) -> Unit,
    onDelete: (String) -> Unit,
) {
    FormColumn(title = "Profile images", subtitle = "Add or remove photos shown on your account.") {
        ProfileImageManager(
            title = "Your photos",
            category = "CUSTOMER_PROFILE",
            images = images,
            busy = busy,
            authToken = authToken,
            onUpload = onUpload,
            onDelete = onDelete,
        )
    }
}

@Composable
private fun ProfileImageManager(
    title: String,
    category: String,
    images: List<ProfileImageDto>,
    busy: Boolean,
    authToken: String?,
    onUpload: (ByteArray) -> Unit,
    onDelete: (String) -> Unit,
) {
    val context = LocalContext.current
    var selectedBytes by remember(category) { mutableStateOf<ByteArray?>(null) }
    var selectedBitmap by remember(category) { mutableStateOf<Bitmap?>(null) }
    var selectionError by remember(category) { mutableStateOf<String?>(null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IOException("The selected image could not be opened.")
                if (bytes.size > 5 * 1024 * 1024) {
                    selectionError = "Choose an image up to 5 MB."
                    selectedBytes = null
                    selectedBitmap = null
                } else {
                    val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                    selectedBytes = bytes
                    selectedBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    selectionError = if (selectedBitmap == null) "The selected file is not a supported image." else null
                }
            } catch (exception: IOException) {
                selectionError = exception.message ?: "The selected image could not be read."
                selectedBytes = null
                selectedBitmap = null
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { imagePicker.launch("image/*") }, enabled = !busy) {
                Text("Choose image")
            }
            selectedBytes?.takeIf { selectedBitmap != null }?.let { bytes ->
                Button(onClick = { onUpload(bytes) }, enabled = !busy) {
                    if (busy) CircularProgressIndicator() else Text("Upload")
                }
            }
        }
        selectedBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Selected image preview",
                modifier = Modifier.size(112.dp),
                contentScale = ContentScale.Crop,
            )
        }
        selectionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val matchingImages = images.filter { it.category == category }
        if (matchingImages.isEmpty()) {
            Text("No images uploaded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            matchingImages.forEach { image ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RemoteProfileImage(image, authToken)
                    TextButton(onClick = { onDelete(image.id) }, enabled = !busy) { Text("Remove") }
                }
            }
        }
    }
}

private data class ProfileImageLoadState(
    val finished: Boolean = false,
    val bitmap: Bitmap? = null,
)

@Composable
private fun RemoteProfileImage(image: ProfileImageDto, authToken: String?) {
    val loadState by produceState(ProfileImageLoadState(), image.id, authToken) {
        value = withContext(Dispatchers.IO) {
            if (!image.url.startsWith("/api/media/")) {
                ProfileImageLoadState(finished = true)
            } else {
                val connection = java.net.URL(BuildConfig.API_BASE_URL.trimEnd('/') + image.url)
                    .openConnection() as java.net.HttpURLConnection
                try {
                    connection.connectTimeout = 10_000
                    connection.readTimeout = 15_000
                    connection.setRequestProperty("Accept", image.contentType)
                    authToken?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
                    val bitmap = if (connection.responseCode in 200..299) {
                        connection.inputStream.use { BitmapFactory.decodeStream(it) }
                    } else {
                        null
                    }
                    ProfileImageLoadState(finished = true, bitmap = bitmap)
                } catch (_: IOException) {
                    ProfileImageLoadState(finished = true)
                } finally {
                    connection.disconnect()
                }
            }
        }
    }
    val loadedBitmap = loadState.bitmap
    when {
        loadedBitmap != null -> Image(
            bitmap = loadedBitmap.asImageBitmap(),
            contentDescription = image.category.lowercase().replace('_', ' '),
            modifier = Modifier.size(112.dp),
            contentScale = ContentScale.Crop,
        )
        loadState.finished -> Text("Image unavailable", color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> CircularProgressIndicator(modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun ServiceEditorScreen(
    service: ServiceDto?,
    busy: Boolean,
    onBack: () -> Unit,
    onSave: (String?, String, String, String, String, String, Boolean) -> Unit,
) {
    var name by rememberSaveable(service?.id) { mutableStateOf(service?.name.orEmpty()) }
    var description by rememberSaveable(service?.id) { mutableStateOf(service?.description.orEmpty()) }
    var category by rememberSaveable(service?.id) { mutableStateOf(service?.category.orEmpty()) }
    var price by rememberSaveable(service?.id) { mutableStateOf(service?.price.orEmpty()) }
    var location by rememberSaveable(service?.id) { mutableStateOf(service?.location.orEmpty()) }
    var available by rememberSaveable(service?.id) { mutableStateOf(service?.availabilityStatus != "UNAVAILABLE") }
    FormColumn(title = if (service == null) "Create service" else "Edit service", subtitle = "Keep your listing clear and accurate.") {
        OutlinedTextField(name, { name = it }, label = { Text("Service name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(description, { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(category, { category = it }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(price, { price = it }, label = { Text("Price (KES)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(location, { location = it }, label = { Text("Location") }, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Available: ")
            TextButton(onClick = { available = !available }) { Text(if (available) "Yes" else "No · tap to change") }
        }
        Button(
            onClick = { onSave(service?.id, name, description, category, price, location, available) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { if (busy) CircularProgressIndicator() else Text("Save listing") }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

@Composable
private fun ServiceCard(service: ServiceDto, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.32f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    service.category,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(service.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                service.description,
                maxLines = 2,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(service.location, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "KES ${service.price}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun GoogleMapsButton(query: String, label: String) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            val uri = Uri.Builder()
                .scheme("https")
                .authority("www.google.com")
                .path("/maps/search/")
                .appendQueryParameter("api", "1")
                .appendQueryParameter("query", query)
                .build()
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, "No app is available to open Google Maps.", Toast.LENGTH_LONG).show()
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label)
    }
}

@Composable
private fun FormColumn(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f), style = MaterialTheme.typography.bodyLarge)
            content()
        },
    )
}

@Composable
private fun Banner(message: String, isError: Boolean) {
    Surface(
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            message,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun LoadingState() {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator()
        Text("Loading from MtaaConnect…")
    }
}

@Composable
private fun EmptyState(title: String, message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(message)
        OutlinedButton(onClick = onRetry) { Text("Try again") }
    }
}
