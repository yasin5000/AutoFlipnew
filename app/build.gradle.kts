plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.autoflip"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.autoflip"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "3.0"
    }

    // Sob build e same signing key, jate notun APK purono app er upor install hoy
    // (uninstall korte hoy na, ADB permission ar Accessibility setting thake).
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
