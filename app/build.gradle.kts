import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing comes from keystore.properties when you create one
// (see the README); otherwise it falls back to the local debug key, which
// is fine for sideloading onto your own watch but not for distribution.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.bunnypranav.watchcalc"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.bunnypranav.watchcalc"
        minSdk = 30          // Wear OS 3 and later
        targetSdk = 36       // Wear OS 6 / Android 16
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            signingConfig = if (keystoreProps.isNotEmpty()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

/*
 * assembleRelease keeps the debug-key fallback so sideloading onto your own
 * watch stays a one-liner. A bundle headed for Play must not: the Console
 * rejects anything signed with the debug key, so fail early and loudly here
 * rather than after a five-minute upload.
 */
tasks.matching { it.name == "bundleRelease" }.configureEach {
    doFirst {
        if (keystoreProps.isEmpty()) {
            throw GradleException(
                "bundleRelease needs a real upload key. Create keystore.properties " +
                    "next to settings.gradle.kts with storeFile, storePassword, " +
                    "keyAlias and keyPassword — see README.md."
            )
        }
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.preview)
    debugImplementation(libs.compose.ui.tooling)

    // ScalingLazyColumn: the curved list that a round screen wants
    implementation(libs.wear.compose.foundation)

    // the tile that lives in the watch's swipe-left carousel
    implementation(libs.wear.tiles)
    implementation(libs.protolayout)
    implementation(libs.protolayout.expression)
    implementation(libs.concurrent.futures)

    testImplementation(libs.junit)
}
