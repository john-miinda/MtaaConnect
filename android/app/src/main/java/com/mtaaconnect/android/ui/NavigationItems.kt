package com.mtaaconnect.android.ui

fun navigationItemsFor(role: String?): List<String> = when (role) {
    "CUSTOMER" -> listOf("Explore", "My bookings")
    "PROVIDER" -> listOf("Dashboard", "Incoming bookings", "Profile")
    else -> listOf("Explore")
}
