plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "net.velalab.tvhotel"
    compileSdk = 35

    defaultConfig {
        applicationId = "net.velalab.tvhotel"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "1.0.0"
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
