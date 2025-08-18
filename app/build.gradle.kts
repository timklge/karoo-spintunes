import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
    kotlin("plugin.serialization") version "2.0.20"
}

android {
    namespace = "de.timklge.karoospintunes"
    compileSdk = 35
    //noinspection GradleDependency

    defaultConfig {
        applicationId = "de.timklge.karoospintunes"
        minSdk = 26
        //noinspection ExpiredTargetSdkVersion
        targetSdk = 31
        versionCode = 100 + (System.getenv("BUILD_NUMBER")?.toInt() ?: 1)
        versionName = System.getenv("RELEASE_VERSION") ?: "1.0"

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
            isMinifyEnabled = true
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

tasks.register("generateManifest") {
    description = "Generates manifest.json with current version information"
    group = "build"

    doLast {
        val manifestFile = file("$projectDir/manifest.json")
        val manifest = mapOf(
            "label" to "Spintunes",
            "packageName" to "de.timklge.karoospintunes",
            "iconUrl" to "https://github.com/timklge/karoo-spintunes/releases/latest/download/karoo-spintunes.png",
            "latestApkUrl" to "https://github.com/timklge/karoo-spintunes/releases/latest/download/app-release.apk",
            "latestVersion" to android.defaultConfig.versionName,
            "latestVersionCode" to android.defaultConfig.versionCode,
            "developer" to "timklge",
            "description" to "Provides media controls for Spotify. Can be used as a remote control for Spotify running on your phone / computer or offline if you have sideloaded the Spotify app itself on the Karoo. Currently not compatible with Karoo 2.",
            "releaseNotes" to "* Fix player state is sometimes not refreshed when starting playback from the library page\n* Also show connection errors on library page\n* Fix error on track change\n* Rearrange seek buttons\n* Show error messages on player data field",
            "screenshotUrls" to listOf(
                "https://github.com/timklge/karoo-spintunes/releases/latest/download/player.png",
                "https://github.com/timklge/karoo-spintunes/releases/latest/download/playlists.png",
                "https://github.com/timklge/karoo-spintunes/releases/latest/download/podcast.png",
                "https://github.com/timklge/karoo-spintunes/releases/latest/download/login.png"
            ),
        )

        val gson = groovy.json.JsonBuilder(manifest).toPrettyString()
        manifestFile.writeText(gson)
        println("Generated manifest.json with version ${android.defaultConfig.versionName} (${android.defaultConfig.versionCode})")
    }
}

tasks.named("assemble") {
    dependsOn("generateManifest")
}

dependencies {
    implementation(files("libs/spotify-app-remote-release-0.8.0.aar"))

    implementation(libs.ktor.client.okhttp)
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

    // Jackson databind provides the StdSerializer and StdDeserializer classes.
    implementation(libs.jackson.databind)

}