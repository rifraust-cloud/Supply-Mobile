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
    // Scanning is handled by the Point Mobile PM86 built-in hardware scanner in
    // keyboard-wedge mode (EmKit ScanSettings: Result type = Keyboard Event, Terminator = LF).
    // The app captures the wedge keystrokes in a focused field -- no camera/ML Kit needed.
    // (Camera scanner intentionally removed; can be re-added later if required.)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.3")
}
