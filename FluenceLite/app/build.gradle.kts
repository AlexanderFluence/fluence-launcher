plugins {
    id("com.android.application")
}

// Fluence Lite: launcher liviano. Sin librerías externas => APK pequeño y arranque rápido.
android {
    namespace = "com.fluence.lite"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.fluence.lite"
        minSdk = 24
        // La radio usa Android 10 (API 29)
        targetSdk = 29
        versionCode = 1
        versionName = "1.0"
    }

    // Misma firma fija que el launcher Fluence: las actualizaciones se instalan encima
    signingConfigs {
        create("fija") {
            storeFile = file("fluence.keystore")
            storePassword = "fluence123"
            keyAlias = "fluence"
            keyPassword = "fluence123"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("fija")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("fija")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}
