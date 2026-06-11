plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
}

import java.io.FileInputStream
import java.util.Properties

val envProperties = Properties()
val envFile = File(rootProject.projectDir.parentFile, ".env.local")
if (envFile.exists()) {
    envFile.inputStream().use { envProperties.load(it) }
}

val googleClientId = envProperties.getProperty("GOOGLE_CLIENT_ID") ?: ""
val googleClientSecret = envProperties.getProperty("GOOGLE_CLIENT_SECRET") ?: ""
val googleRefreshToken = envProperties.getProperty("GOOGLE_REFRESH_TOKEN") ?: ""

android {
    namespace = "com.nam.novelreader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nam.novelreader"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GOOGLE_CLIENT_ID", "\"$googleClientId\"")
        buildConfigField("String", "GOOGLE_CLIENT_SECRET", "\"$googleClientSecret\"")
        buildConfigField("String", "GOOGLE_REFRESH_TOKEN", "\"$googleRefreshToken\"")

        // Chỉ build cho arm64 để tối ưu tốc độ và dung lượng
        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Rhino includes some files that conflict with Android packaging
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "mozilla/javascript/**"
        }
    }
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation("androidx.browser:browser:1.8.0")

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation("androidx.compose.ui:ui-text-google-fonts")
    debugImplementation(libs.androidx.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)
    implementation("androidx.compose.material:material-icons-extended")

    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)

    // Room Database
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Network
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // HTML Parser
    implementation(libs.jsoup)

    // Image Loading
    implementation(libs.coil.compose)

    // Background Work
    implementation(libs.work.runtime.ktx)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // JS Runtime — Mozilla Rhino (same as VBook)
    implementation(libs.rhino)

    // DataStore
    implementation(libs.datastore.preferences)

    // WebView
    implementation(libs.webkit)
    implementation(libs.androidx.browser)

    // Media3 Player (ExoPlayer + HLS stream support)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)

    // Cronet Network Stack
    implementation(libs.play.services.cronet)
    implementation(libs.cronet.okhttp)
}
