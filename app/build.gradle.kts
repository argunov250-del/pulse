import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Номер сборки приходит из GitHub Actions (-PbuildNumber=N). Локально — 1.
val buildNumber = (findProperty("buildNumber") as String?)?.toIntOrNull() ?: 1

android {
    namespace = "dev.pulse.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.pulse.app"
        minSdk = 28
        targetSdk = 36
        versionCode = buildNumber
        versionName = "0.0.$buildNumber"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
}
