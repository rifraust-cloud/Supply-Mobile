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
