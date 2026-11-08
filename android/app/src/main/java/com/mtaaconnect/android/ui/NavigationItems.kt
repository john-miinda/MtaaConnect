package com.mtaaconnect.android.ui

fun navigationItemsFor(role: String?): List<String> = when (role) {
    "CUSTOMER" -> listOf("Explore", "My bookings", "Profile")
    "PROVIDER" -> listOf("Dashboard", "Incoming bookings", "Profile")
    else -> listOf("Explore")
}
