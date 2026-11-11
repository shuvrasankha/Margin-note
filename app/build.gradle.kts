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
        // The Play Store ID. The code keeps its own namespace (com.shuva.jotter), which users never see.
        applicationId = "com.margin.app"
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
        debug {
            // Installs next to the release app, so trying one never means uninstalling the other (and its notes).
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    // One APK per processor type, so each carries only its own native libraries (ML Kit, PDF).
    // Phones from the last decade want app-arm64-v8a-release.apk; x86_64 is for the emulator.
    splits {
        abi {
            // Off when building the Play bundle (.aab): Play splits it per phone itself, and AGP can't do both.
            isEnable = gradle.startParameter.taskNames.none { it.contains("bundle", ignoreCase = true) }
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    androidResources {
        // The app is in English; libraries' translations of their own strings aren't needed.
        localeFilters += listOf("en")
        // PdfBox's CJK character maps (~3 MB): only for Chinese, Japanese and Korean PDFs without their own
        // text mapping. Identity-H/V (used by almost every PDF with embedded fonts) and H/V are kept.
        ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*.scc:.*:<dir>_*:!CVS:!thumbs.db:!picasa.ini:!*~" +
            ":!Adobe-*:!Uni*:!GB*:!KSC*:!HKscs*:!83pv*:!90ms*:!90pv*:!Add-*:!Ext-*:!B5pc-*:!CNS-*:!ETen*:!EUC-*"
    }

    packaging {
        resources {
            // BouncyCastle (pulled in by PdfBox) carries 8 MB of post-quantum crypto tables PDFs never use.
            excludes += "org/bouncycastle/pqc/**"
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
    // OCR: on-device text recognition (Latin script). The model comes from Google Play services (downloaded at
    // install, see the manifest), which keeps it out of the APK; recognition still runs offline on the phone.
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.digital.ink)
    implementation(libs.mlkit.document.scanner)
    implementation(libs.pdfbox.android)

    testImplementation(libs.junit)
}
