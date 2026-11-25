import java.net.URI
import java.security.MessageDigest

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
        versionCode = 4
        versionName = "1.2.0-test"
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
    // Lokale zusätzliche OCR; Sprachmodelle und Integration folgen separat.
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")

    // Fingerabdruck und Geräte-PIN
    implementation("androidx.biometric:biometric:1.1.0")
}

 
// Pin official model bytes using Git blob identity and exact byte length.
val ocrModelle = layout.buildDirectory.dir("generated/ocrAssets")
val ocrModelleBereitstellen = tasks.register("ocrModelleBereitstellen") {
    val modelle = mapOf(
        "ara.traineddata" to (1432056 to "c8d129c67821c1592cac46836686b057ecc203dc"),
        "rus.traineddata" to (3861738 to "b146cb2263acbc6383f8e92ea0ce759537687bb8"),
        "ukr.traineddata" to (3825102 to "d02cf90b2911a3da66dac6ae380bcefb18dd7663"),
        "LICENSE" to (11358 to "d645695673349e3947e8e5ae42332d0ac3164cd7")
    )
    inputs.property("modelCommit", "65727574dfcd264acbb0c3e07860e4e9e9b22185")
    inputs.property("modelHashes", modelle.toString())
    outputs.dir(ocrModelle)
    doLast {
        val ordner = ocrModelle.get().dir("tessdata").asFile
        ordner.mkdirs()
        for ((name, daten) in modelle) {
            val (groesse, hash) = daten
            val url = URI("https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/65727574dfcd264acbb0c3e07860e4e9e9b22185/$name").toURL()
            val bytes = url.openConnection().apply {
                connectTimeout = 30000
                readTimeout = 60000
            }.getInputStream().use { it.readNBytes(groesse + 1) }
            check(bytes.size == groesse) { "Invalid OCR model size: $name" }
            val digest = MessageDigest.getInstance("SHA-1")
            digest.update("blob ${bytes.size}\u0000".toByteArray(Charsets.UTF_8))
            val actual = digest.digest(bytes).joinToString("") { "%02x".format(it) }
            check(actual == hash) { "Invalid OCR model Git blob: $name" }
            val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            logger.lifecycle("Verified OCR asset $name SHA-256: $sha256")
            ordner.resolve(name).writeBytes(bytes)
        }
    }
}
android.sourceSets.getByName("main").assets.srcDir(ocrModelle)
tasks.named("preBuild").configure { dependsOn(ocrModelleBereitstellen) }
