import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
    kotlin("plugin.serialization") version "2.0.20"
}

android {
    namespace = "de.timklge.karoospotify"
    compileSdk = 35
    //noinspection GradleDependency

    defaultConfig {
        applicationId = "de.timklge.karoospotify"
        minSdk = 26
        //noinspection ExpiredTargetSdkVersion
        targetSdk = 31
        versionCode = 1
        versionName = "1.0-beta1"

        val env: MutableMap<String, String> = System.getenv()
        val clientId = env["SPOTIFY_CLIENT_ID"] ?: project.findProperty("SPOTIFY_CLIENT_ID").toString()
        buildConfigField("String", "SPOTIFY_CLIENT_ID",
            "\"$clientId\""
        )
    }

    signingConfigs {
        create("release") {
            val env: MutableMap<String, String> = System.getenv()
            keyAlias = env["KEY_ALIAS"]
            keyPassword = env["KEY_PASSWORD"]

            val base64keystore: String = env["KEYSTORE_BASE64"] ?: ""
            val keystoreFile: File = File.createTempFile("keystore", ".jks")
            keystoreFile.writeBytes(Base64.getDecoder().decode(base64keystore))
            storeFile = keystoreFile
            storePassword = env["KEYSTORE_PASSWORD"]
        }
    }
    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(files("libs/spotify-app-remote-release-0.8.0.aar"))

    implementation(libs.hammerhead.karoo.ext)
    implementation(libs.androidx.core.ktx)
    implementation(libs.bundles.androidx.lifeycle)
    implementation(libs.androidx.activity.compose)
    implementation(libs.bundles.compose.ui)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.appwidget.preview)
    implementation(libs.androidx.glance.preview)
    implementation(libs.androidx.appcompat)
    implementation(libs.gson)
    implementation(libs.androidx.paging.common.android)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.navigation.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
}