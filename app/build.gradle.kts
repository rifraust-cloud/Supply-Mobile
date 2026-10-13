plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ptx7.cyclecount"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ptx7.cyclecount"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    // Keep Java and Kotlin on the SAME JVM target (17). AGP 8.7 fails the build if they differ.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    // Google's prebuilt one-tap barcode scanner UI (same proven approach as PIMS Mobile).
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    // FileProvider + ContextCompat for photo capture and intents.
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.3")
}