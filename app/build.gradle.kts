plugins {
    id("com.android.application")
}

val releaseKeystorePath = providers.environmentVariable("SUPSECU_KEYSTORE_PATH").orNull
val releaseKeystorePassword = providers.environmentVariable("SUPSECU_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("SUPSECU_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("SUPSECU_KEY_PASSWORD").orNull
val releaseSigningAvailable = listOf(
    releaseKeystorePath,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "be.supsecu.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "be.supsecu.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "1.2.1"

        testInstrumentationRunner = "android.app.Instrumentation"
    }

    signingConfigs {
        if (releaseSigningAvailable) {
            create("release") {
                storeFile = file(requireNotNull(releaseKeystorePath))
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
