package com.mtaaconnect.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
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
import java.time.LocalDate

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
                    Column {
                        Text("MtaaConnect", fontWeight = FontWeight.Bold)
                        currentSession?.let { Text("${it.fullName} · ${it.role}", style = MaterialTheme.typography.labelSmall) }
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
                ),
            )
        },
        bottomBar = {
            if (currentSession != null) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    navigationItemsFor(currentSession.role).forEach { label ->
                        val destination = when (label) {
                            "Explore" -> "home"
                            "My bookings" -> "bookings"
                            "Dashboard" -> "provider"
                            "Incoming bookings" -> "provider-bookings"
                            else -> "profile-editor"
                        }
                        val selected = when (destination) {
                            "home" -> state.route == "home"
                            "bookings" -> state.route == "bookings" || state.route == "booking-detail"
                            "provider" -> state.route == "provider" || state.route == "service-editor"
                            "provider-bookings" -> state.route == "provider-bookings"
                            else -> state.route == "profile-editor"
                        }
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (destination == "profile-editor") viewModel.editProfile()
                                else viewModel.navigate(destination)
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
        "booking-detail", "provider", "profile-editor", "service-editor",
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
                "provider-public" -> ProviderPublicScreen(state.selectedProvider) { viewModel.returnTo("detail") }
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
                    loading = state.loading,
                    onProfile = viewModel::editProfile,
                    onAddService = { viewModel.editService(null) },
                    onEdit = viewModel::editService,
                    onDelete = viewModel::deleteService,
                    onRefresh = { viewModel.navigate("provider") },
                )
                "profile-editor" -> ProfileEditorScreen(
                    profile = state.providerProfile,
                    busy = state.busy,
                    onBack = { viewModel.returnTo("provider") },
                    onSave = viewModel::saveProfile,
                )
                "service-editor" -> ServiceEditorScreen(
                    service = state.selectedService,
                    busy = state.busy,
                    onBack = { viewModel.returnTo("provider") },
                    onSave = viewModel::saveService,
                )
                else -> ExploreScreen(
                    services = state.services,
                    loading = state.loading,
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
        Button(onClick = { onLogin(email, password) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
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
                if (candidate == role) Button(onClick = { role = candidate }) { Text(candidate.lowercase().replaceFirstChar(Char::uppercase)) }
                else OutlinedButton(onClick = { role = candidate }) { Text(candidate.lowercase().replaceFirstChar(Char::uppercase)) }
            }
        }
        Button(
            onClick = { onRegister(fullName, email, password, phone, role) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { if (busy) CircularProgressIndicator() else Text("Create account") }
        TextButton(onClick = onLogin, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Already registered? Sign in") }
    }
}

@Composable
private fun ExploreScreen(
    services: List<ServiceDto>,
    loading: Boolean,
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
        ) {
            Column(
                Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "YOUR COMMUNITY, CONNECTED",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Find trusted help\nright around you.",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Discover skilled local providers and book with confidence.",
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f),
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
        else if (services.isEmpty()) EmptyState("No services found", "Try another category or location.", onRefresh)
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
    onBack: () -> Unit,
) {
    if (provider == null) {
        LoadingState()
        return
    }
    FormColumn(title = provider.businessName, subtitle = provider.location) {
        Text(provider.description)
        Text("Contact: ${provider.contactInformation}")
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
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
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
    onProfile: () -> Unit,
    onAddService: () -> Unit,
    onEdit: (ServiceDto) -> Unit,
    onDelete: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text("Provider dashboard", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Manage your profile, services, and incoming requests.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onProfile) { Text("Provider profile") }
                Button(onClick = onAddService, enabled = profileId != null) { Text("Add service") }
                TextButton(onClick = onRefresh) { Text("Refresh") }
            }
            if (profileId == null) Text("Create your provider profile before publishing services.")
        }
        if (loading && services.isEmpty()) LoadingState()
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
                Card {
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
    busy: Boolean,
    onBack: () -> Unit,
    onSave: (String, String, String, String, Boolean) -> Unit,
) {
    var business by rememberSaveable(profile?.id) { mutableStateOf(profile?.businessName.orEmpty()) }
    var description by rememberSaveable(profile?.id) { mutableStateOf(profile?.description.orEmpty()) }
    var location by rememberSaveable(profile?.id) { mutableStateOf(profile?.location.orEmpty()) }
    var contact by rememberSaveable(profile?.id) { mutableStateOf(profile?.contactInformation.orEmpty()) }
    var updateExisting by rememberSaveable(profile?.id) { mutableStateOf(profile != null) }
    FormColumn(title = "Provider profile", subtitle = "Tell customers about your business.") {
        OutlinedTextField(business, { business = it }, label = { Text("Business name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(description, { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(location, { location = it }, label = { Text("Location") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(contact, { contact = it }, label = { Text("Contact information") }, modifier = Modifier.fillMaxWidth())
        if (profile == null) {
            TextButton(onClick = { updateExisting = !updateExisting }) {
                Text(if (updateExisting) "Switch to creating a new profile" else "Already have a profile? Update it")
            }
        }
        Button(
            onClick = { onSave(business, description, location, contact, updateExisting) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { if (busy) CircularProgressIndicator() else Text(if (updateExisting) "Update profile" else "Create profile") }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
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
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    service.category,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
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
private fun FormColumn(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
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
