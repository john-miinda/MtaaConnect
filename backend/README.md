# MtaaConnect backend

## Configure MongoDB

Spring Boot reads the MongoDB URI from the `MONGODB_URI` environment variable via
`spring.mongodb.uri` in `src/main/resources/application.properties`. Spring Boot
does not load `.env` files automatically; `run.ps1` loads the ignored local
`.env` file before starting the application.
MongoDB auto-index creation is enabled for unique email, phone, and provider
profile user IDs, plus service discovery indexes. If an existing database
already contains duplicate values for a unique field, those duplicates must be
resolved before MongoDB can create the indexes.

In PowerShell, from the `backend` directory:

```powershell
Copy-Item .env.example .env
```

Replace the placeholder in `.env` with your MongoDB Atlas connection URI. The
launcher generates and saves a cryptographically random JWT signing secret the
first time it runs if `JWT_SECRET` is blank. Keep both values private and do not
commit `.env`; `.gitignore` excludes it. The launcher accepts quoted MongoDB
URIs and does not print either value.

If `.env` already exists, confirm locally that it contains a non-empty
`MONGODB_URI` assignment. The launcher will populate a missing signing secret.
Do not share either value when asking for help.

## Build and tests

From the `backend` directory:

```powershell
.\gradlew.bat clean build
```

The registration tests use mocked repository and service dependencies and do
not require MongoDB credentials.

## Marketplace API

Public registration assigns `CUSTOMER` by default. A caller may request
`"role": "PROVIDER"`; public registration rejects `ADMIN`. Existing users with
no stored role are treated as `CUSTOMER`. Login JWTs carry the user's role.

Provider operations require a provider account. A provider may create one
profile and manage services only after that profile exists. Profile and service
updates/deletes are scoped to the authenticated provider. Public reads return
provider profiles and available services only; service search accepts optional
`category` and partial `location` query parameters.

Provider profiles accept optional `latitude` and `longitude` decimal fields.
They must be supplied together and must be within the valid geographic ranges
(-90 to 90 latitude, -180 to 180 longitude). The Android app uses these
coordinates to open a public pin in Google Maps; no Google Maps API key is
required. Treat the coordinates as public business-location information.

| Method | Endpoint | Access |
| --- | --- | --- |
| `POST` | `/api/providers/profile` | Provider |
| `GET` | `/api/providers/{id}` | Public |
| `PUT` | `/api/providers/profile` | Owning provider |
| `POST` | `/api/services` | Provider with profile |
| `GET` | `/api/services?category=...&location=...` | Public |
| `GET` | `/api/services/{id}` | Public, available services |
| `GET` | `/api/providers/{providerId}/services` | Public, available services |
| `PUT` | `/api/services/{id}` | Owning provider |
| `DELETE` | `/api/services/{id}` | Owning provider |

Marketplace endpoint tests mock MongoDB repositories and do not require a live
MongoDB instance.

## Android client

The Android emulator uses `http://10.0.2.2:8080/` for the local backend by
default. For a physical device or a deployed API, set `MTAA_API_BASE_URL` to a
reachable base URL before building:

```powershell
$env:MTAA_API_BASE_URL = "https://api.example.com/"
Set-Location ..\android
.\gradlew.bat :app:assembleDebug
```

The client uses the same REST endpoints listed below and attaches the signed-in
session token to authenticated requests. Release builds require an HTTPS API
URL and the `MTAA_RELEASE_STORE_FILE`, `MTAA_RELEASE_STORE_PASSWORD`,
`MTAA_RELEASE_KEY_ALIAS`, and `MTAA_RELEASE_KEY_PASSWORD` signing properties.

## Booking API

Booking request dates and times are expressed as `requestedDate`, `startTime`,
and `endTime` with an IANA `timeZone` such as `Africa/Nairobi`. Times in daylight
saving gaps or ambiguous repeated times are rejected. Starts and ends must be
on 15-minute boundaries, bookings must be future-dated, and a booking may not
exceed 24 hours.

Each booking initially has `PENDING` status. Providers can accept or reject a
pending booking; only accepted bookings can be completed. Customers may cancel
pending or accepted bookings. Every transition uses an atomic status
compare-and-set in MongoDB.

Booking creation claims unique provider/time-slot reservation documents in
MongoDB. The unique `(providerUserId, slotStart)` index arbitrates concurrent
requests, so overlapping pending requests cannot both be created even if they
race past an application-level availability check. Rejected and cancelled
bookings release their slots.

| Method | Endpoint | Access |
| --- | --- | --- |
| `POST` | `/api/bookings` | Customer |
| `GET` | `/api/bookings/my-bookings` | Customer |
| `GET` | `/api/bookings/{id}` | Booking customer or provider |
| `PATCH` | `/api/bookings/{id}/cancel` | Booking customer |
| `GET` | `/api/bookings/provider` | Provider |
| `PATCH` | `/api/bookings/{id}/accept` | Booking provider |
| `PATCH` | `/api/bookings/{id}/reject` | Booking provider |
| `PATCH` | `/api/bookings/{id}/complete` | Booking provider |

## Run and manually test registration

From the `backend` directory, start the application:

```powershell
.\run.ps1
```

In another PowerShell window, submit a registration request:

```powershell
$body = @{
    fullName = "Test User"
    email = "test@example.com"
    password = "a-local-test-password"
    phoneNumber = "1234567890"
} | ConvertTo-Json

Invoke-RestMethod `
    -Method Post `
    -Uri "http://localhost:8080/api/users/register" `
    -ContentType "application/json" `
    -Body $body
```

Successful registration returns HTTP 201 and a user object without a password
or password hash. Invalid input returns HTTP 400; duplicate email or phone
number returns HTTP 409. Registration has not been tested against a live Atlas
database unless you run the manual request with your own valid connection URI.
