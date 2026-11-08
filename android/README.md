# MtaaConnect Android

The Android client is a Kotlin, Jetpack Compose and Material 3 application. It
uses Retrofit/OkHttp for the backend API, StateFlow-backed screen state, and
Android Keystore AES-GCM encryption for persisted login sessions.

## Backend URL

Debug builds default to `http://10.0.2.2:8080/` (the Android emulator's host
loopback alias). Set `MTAA_API_BASE_URL` as a Gradle property or environment
variable to use another backend:

```powershell
.\gradlew.bat -PMTAA_API_BASE_URL=http://192.168.1.20:8080/ assembleDebug
```

For a physical device, use the computer's LAN IP and ensure the backend is
reachable through the firewall; `10.0.2.2` only works from the Android
emulator. The Retrofit client reads this configured URL from `BuildConfig`.

Cleartext networking is only enabled for debug builds configured with an HTTP
URL. Release builds require an absolute HTTPS URL and a release keystore; they
enable code/resource shrinking and keep debug network traffic disabled.
Configure release values through Gradle properties or environment variables:

- `MTAA_API_BASE_URL`
- `MTAA_RELEASE_STORE_FILE`
- `MTAA_RELEASE_STORE_PASSWORD`
- `MTAA_RELEASE_KEY_ALIAS`
- `MTAA_RELEASE_KEY_PASSWORD`

Never commit the keystore or its passwords. The app does not contain backend
credentials, JWT signing secrets, or database connection strings.

Start the backend separately from the `backend` directory with its normal
environment configured. Requests from a physical device need a reachable host
address rather than the emulator's `10.0.2.2` alias.

Providers can manage all of their listings, including unavailable ones, from
any device after signing in. The app retrieves the provider profile and
listings from authenticated backend endpoints instead of relying on a
device-local profile ID.

## Build and test

From the Android directory, build and test with its Gradle wrapper:

```powershell
.\gradlew.bat clean build
.\gradlew.bat testDebugUnitTest
```

Install on a connected device/emulator:

```powershell
.\gradlew.bat installDebug
```

Create a signed production APK or bundle after supplying release configuration:

```powershell
.\gradlew.bat assembleRelease
.\gradlew.bat bundleRelease
```

Registration and login use the existing `/api/users/register` and
`/api/auth/login` contracts. Service discovery, provider profiles, listings,
and booking actions call the backend routes documented in `backend/README.md`.
Booking request dates and times use the backend's `requestedDate`, `startTime`,
`endTime`, and IANA `timeZone` fields.

Providers can optionally add latitude and longitude to their profile to share
a precise business pin. Customers can open the provider's address or pin in
Google Maps from the public provider profile. The link opens Google Maps (or a
browser) and does not require a Google Maps SDK or API key. Shared coordinates
are public, so providers should use a business location they are comfortable
sharing.
