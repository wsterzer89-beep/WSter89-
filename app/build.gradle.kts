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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        targetSdk = 35
        versionCode = 8
        versionName = "1.6.0-test"
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
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
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
        "deu.traineddata" to (1525436 to "97ed7b2b60f2771c07040660ef0f6daf596dc7bf"),
        "eng.traineddata" to (4113088 to "bbef4675053b5b468cdb477053e28b1c698ba08e"),
        "LICENSE" to (11358 to "d645695673349e3947e8e5ae42332d0ac3164cd7")
    )
    val sha256Soll = mapOf(
        "ara.traineddata" to "e3206d3dc87fd50c24a0fb9f01838615911d25168f4e64415244b67d2bb3e729",
        "rus.traineddata" to "e16e5e036cce1d9ec2b00063cf8b54472625b9e14d893a169e2b0dedeb4df225",
        "ukr.traineddata" to "d59e53e2bded32f4445f124b4b00240fcac7e8044c003ab822ccb94f0b3db59b",
        "deu.traineddata" to "19d219bbb6672c869d20a9636c6816a81eb9a71796cb93ebe0cb1530e2cdb22d",
        "eng.traineddata" to "7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2",
        "LICENSE" to "cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30"
    )
    inputs.property("sha256", sha256Soll.toString())
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
            check(sha256 == sha256Soll.getValue(name)) { "Invalid OCR asset SHA-256: $name" }
            logger.lifecycle("Verified OCR asset $name SHA-256: $sha256")
            ordner.resolve(name).writeBytes(bytes)
        }
    }
}
android.sourceSets.getByName("main").assets.srcDir(ocrModelle)
tasks.named("preBuild").configure { dependsOn(ocrModelleBereitstellen) }
