plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val stagePublicHotelAssets = tasks.register<Copy>("stagePublicHotelAssets") {
    from("src/main/assets/hotel_content.json")
    into(layout.buildDirectory.dir("generated/publicHotelAssets"))
}

android {
    namespace = "net.velalab.tvhotel"
    compileSdk = 35

    defaultConfig {
        applicationId = "net.velalab.tvhotel"
        minSdk = 26
        targetSdk = 35
        versionCode = 13
        versionName = "1.0.5"
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }

    // Stage only the canonical public seed. The ignored legacy fiore_internal
    // directory is intentionally outside every packaged source set.
    sourceSets {
        getByName("main") {
            assets.setSrcDirs(listOf(layout.buildDirectory.dir("generated/publicHotelAssets")))
            // Owner-selected photos are opt-in for internal QA. Public CI keeps
            // its illustrative fallback and does not include these files.
            if (providers.gradleProperty("includeApprovedFioreMedia").orNull == "true") {
                assets.srcDir("../media/fiore-approved/assets")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(stagePublicHotelAssets)
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
