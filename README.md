# MtaaConnect

A full-stack local services marketplace connecting customers with service providers.

## Overview

MtaaConnect is a Kotlin-based platform with two main application layers:

- Backend: Spring Boot API for authentication, provider profiles, services, bookings, and media uploads
- Android client: Jetpack Compose mobile app for customers and providers

The project is designed to let local service providers list their offerings and allow customers to discover, book, and manage services.

## Repository structure

- `backend/` — server-side application and API documentation
- `android/` — Android client application
- `.vscode/` — editor configuration

## Getting started

### Backend

From the `backend` directory:

```powershell
Copy-Item .env.example .env
.\run.ps1
```

The backend reads its MongoDB connection from `MONGODB_URI`, and the launcher will generate a JWT secret if one is not already configured.

For more details, see `backend/README.md`.

### Android app

From the `android` directory, build or run the app:

```powershell
.\gradlew.bat clean build
.\gradlew.bat installDebug
```

If you are running the app on an emulator, the default backend URL is `http://10.0.2.2:8080/`.

For more details, see `android/README.md`.

## Key capabilities

- User registration and login
- Provider profile and service management
- Public service discovery by category and location
- Booking creation, acceptance, rejection, cancellation, and completion
- Media uploads for user and provider profiles, portfolios, and services

## Documentation

- `backend/README.md` — backend setup, API routes, and configuration
- `android/README.md` — Android app setup and build instructions

## Tech stack

- Kotlin
- Spring Boot
- MongoDB
- Android Jetpack Compose
- Gradle

## License

This project does not currently declare a license in the repository root. Check with the repository owner before reusing or redistributing the code in production or public contexts.
