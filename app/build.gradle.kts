plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.androidx.room)
}

// Set JOTTER_WEB_CLIENT_ID in ~/.gradle/gradle.properties. See README.md. Never commit a real ID.
val webClientId: String = providers.gradleProperty("JOTTER_WEB_CLIENT_ID")
    .getOrElse("REPLACE_WITH_WEB_CLIENT_ID.apps.googleusercontent.com")

// Release signing comes from keystore.properties (git-ignored; see README.md). Without it the release APK is unsigned.
val keystoreProps: Map<String, String> = rootProject.file("keystore.properties").takeIf { it.exists() }
    ?.readLines()?.mapNotNull { line -> line.split("=", limit = 2).takeIf { it.size == 2 && !line.trimStart().startsWith("#") } }
    ?.associate { (k, v) -> k.trim() to v.trim() }.orEmpty()

android {
    namespace = "com.shuva.jotter"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.shuva.jotter"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "WEB_CLIENT_ID", "\"$webClientId\"")
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getValue("storeFile"))
                storePassword = keystoreProps.getValue("storePassword")
                keyAlias = keystoreProps.getValue("keyAlias")
                keyPassword = keystoreProps.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    implementation(libs.play.services.auth)

    implementation(libs.androidx.biometric)
    // OCR: on-device text recognition (Latin script), bundled so it works offline from the first use.
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.digital.ink)
    implementation(libs.mlkit.document.scanner)
    implementation(libs.pdfbox.android)

    testImplementation(libs.junit)
}
