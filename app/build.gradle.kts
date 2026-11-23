
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

android {
    namespace = "de.dokumentenpilot.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.dokumentenpilot.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.1"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3:1.3.1")

    // Mehrseitiger Dokumentenscanner
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")

    // Texterkennung für gescannte Dokumente
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // Fingerabdruck und Geräte-PIN
    implementation("androidx.biometric:biometric:1.1.0")
}
