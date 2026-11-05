plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Wersja: plik VERSION (np. 1.1.0); wydanie z tagu vX.Y.Z, CI podaje -PversionName/-PversionCode.
val baseVersion = rootProject.file("VERSION").readText().trim()
val appVersionName = (project.findProperty("versionName") as String?) ?: baseVersion
val appVersionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1

// Klucz wydania: w CI ze zmiennych środowiskowych, lokalnie z .secrets/ (poza repozytorium).
val localKey = rootProject.file(".secrets/release.jks")
val localPassword = rootProject.file(".secrets/signing-password.txt").takeIf { it.exists() }?.readText()?.trim()
val keyFile = System.getenv("SIGNING_KEY_PATH")?.let { file(it) } ?: localKey.takeIf { it.exists() && localPassword != null }
val hasReleaseKey = keyFile != null && keyFile.exists()

// SHA-256 certyfikatu, którym podpisywane są wydania. Aktualizator i CI sprawdzają zgodność.
val releaseCertSha256 = "d2f79cdb57eb825cfc3e7e23bf935615f3d49dfe5f74fca75c69967cde7b9781"

android {
    namespace = "pl.openpayments"
    compileSdk = 36

    defaultConfig {
        applicationId = "pl.openpayments"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "UPDATE_REPO", "\"BlackSingularities/OpenPayments\"")
        buildConfigField("String", "RELEASE_CERT_SHA256", "\"$releaseCertSha256\"")
        buildConfigField("boolean", "UPDATES_ENABLED", "true")
        resValue("string", "app_name", "OpenPayments")
    }

    signingConfigs {
        create("release") {
            if (hasReleaseKey) {
                storeFile = keyFile
                storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: localPassword
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "openpayments"
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: localPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }
    buildTypes {
        release {
            // Bez klucza (np. fork) wydanie podpisuje się kluczem debug; CI odrzuca taki APK.
            signingConfig = if (hasReleaseKey) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }
        debug {
            // Osobny pakiet: wersja testowa nie koliduje z podpisanym wydaniem na tym samym telefonie.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("boolean", "UPDATES_ENABLED", "false")
            resValue("string", "app_name", "OpenPayments Debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true; resValues = true }
    lint { checkReleaseBuilds = false; abortOnError = false }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
        )
    }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.5.0-alpha18")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("androidx.navigation:navigation-compose:2.9.3")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation("io.coil-kt:coil-compose:2.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
