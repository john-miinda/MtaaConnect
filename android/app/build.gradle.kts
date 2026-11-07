import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun configuredValue(name: String): String? =
    project.findProperty(name)?.toString() ?: System.getenv(name)

fun String.toBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""

val configuredApiBaseUrl = configuredValue("MTAA_API_BASE_URL")
val debugApiBaseUrl = configuredApiBaseUrl ?: "http://10.0.2.2:8080/"
val releaseApiBaseUrl = configuredApiBaseUrl.orEmpty()

val releaseStoreFile = configuredValue("MTAA_RELEASE_STORE_FILE")
val releaseStorePassword = configuredValue("MTAA_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = configuredValue("MTAA_RELEASE_KEY_ALIAS")
val releaseKeyPassword = configuredValue("MTAA_RELEASE_KEY_PASSWORD")
val releaseSigningConfigured = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

val validateReleaseConfig = tasks.register("validateReleaseConfig") {
    doLast {
        val releaseUri = runCatching { URI(releaseApiBaseUrl) }.getOrNull()
        check(releaseUri?.scheme.equals("https", ignoreCase = true) && !releaseUri?.host.isNullOrBlank()) {
            "Release builds require MTAA_API_BASE_URL to be an absolute HTTPS URL."
        }
        check(releaseSigningConfigured) {
            "Release builds require MTAA_RELEASE_STORE_FILE, MTAA_RELEASE_STORE_PASSWORD, MTAA_RELEASE_KEY_ALIAS, and MTAA_RELEASE_KEY_PASSWORD."
        }
        check(file(releaseStoreFile!!).isFile) {
            "MTAA_RELEASE_STORE_FILE must point to an existing keystore file."
        }
    }
}

android {
    namespace = "com.mtaaconnect.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.mtaaconnect.android"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "API_BASE_URL", debugApiBaseUrl.toBuildConfigString())
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            if (releaseSigningConfigured) {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            manifestPlaceholders["usesCleartextTraffic"] = debugApiBaseUrl.startsWith("http://").toString()
        }
        getByName("release") {
            buildConfigField("String", "API_BASE_URL", releaseApiBaseUrl.toBuildConfigString())
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

tasks.configureEach {
    if (
        (name.startsWith("assembleRelease") ||
            name.startsWith("bundleRelease") ||
            name.startsWith("packageRelease")) &&
        name != validateReleaseConfig.name
    ) {
        dependsOn(validateReleaseConfig)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.10.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.navigation:navigation-compose:2.9.5")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.squareup.okhttp3:okhttp:5.1.0")
    implementation("com.google.code.gson:gson:2.13.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.1.0")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("org.mockito:mockito-core:5.20.0")
}
