import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// ── Version resolution ────────────────────────────────────────────────────
val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties().apply {
    if (versionPropsFile.exists()) load(versionPropsFile.inputStream())
}

val ciVersionCode = System.getenv("VERSION_CODE")?.toIntOrNull()
val ciVersionName = System.getenv("VERSION_NAME")

val resolvedVersionCode: Int
val resolvedVersionName: String

if (ciVersionCode != null) {
    // CI build — use env vars, do not touch version.properties
    resolvedVersionCode = ciVersionCode
    resolvedVersionName = ciVersionName ?: versionProps.getProperty("VERSION_NAME", "1.7.3")
} else {
    // Local build — read from file, increment, write back
    resolvedVersionCode = (versionProps.getProperty("VERSION_CODE", "3").toIntOrNull() ?: 3)
    resolvedVersionName = versionProps.getProperty("VERSION_NAME", "1.7.3")
    // Write incremented value back for next build
    versionProps.setProperty("VERSION_CODE", (resolvedVersionCode + 1).toString())
    versionPropsFile.outputStream().use { versionProps.store(it, "Auto-managed — do not edit VERSION_CODE manually") }
}

android {
    namespace = "com.potato.player"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.potato.player"
        minSdk = 24
        targetSdk = 35
        versionCode = resolvedVersionCode
        versionName = resolvedVersionName
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Wire the local MPV AAR from the root libs/ folder
    repositories {
        flatDir { dirs(rootProject.file("libs")) }
    }
}

dependencies {
    // MPV Android AAR — loaded from libs/ in the project root
    implementation(fileTree(mapOf("dir" to rootProject.file("libs"), "include" to listOf("*.aar"))))

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.video)

    debugImplementation(libs.androidx.ui.tooling)
}
