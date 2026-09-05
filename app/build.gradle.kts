plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.hackathon"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.hackathon"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Read Maps API key from local.properties (never commit secrets)
        val mapsApiKey = providers.gradleProperty("MAPS_API_KEY")
            .orElse(
                providers.environmentVariable("MAPS_API_KEY")
            )
            .orElse("YOUR_API_KEY_HERE")
            .get()

        // Also allow local.properties MAPS_API_KEY=
        val localProps = rootProject.file("local.properties")
        val keyFromLocal = if (localProps.exists()) {
            localProps.readLines()
                .firstOrNull { it.startsWith("MAPS_API_KEY=") }
                ?.substringAfter("=")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        } else null

        manifestPlaceholders["MAPS_API_KEY"] = keyFromLocal ?: mapsApiKey
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation("com.google.firebase:firebase-firestore:26.6.0")

    implementation("com.google.android.gms:play-services-maps:19.2.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation(libs.play.services.maps)

    // Camera + on-device labeling for Scan Assist
    val cameraxVersion = "1.4.2"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("com.google.mlkit:image-labeling:17.0.9")
    implementation("com.google.guava:guava:33.4.0-android")

    implementation(libs.play.services.location)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    implementation(platform("com.google.firebase:firebase-bom:34.18.0"))
}
