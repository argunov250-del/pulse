import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Номер сборки приходит из GitHub Actions (-PbuildNumber=N). Локально — 1.
val buildNumber = (findProperty("buildNumber") as String?)?.toIntOrNull() ?: 1

android {
    namespace = "dev.pulse.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.pulse.app"
        minSdk = 28
        targetSdk = 37
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
    implementation(libs.haze)
    implementation(libs.haze.blur)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Health Connect — источник данных о здоровье.
    implementation(libs.health.connect)

    // Hilt — «склеивает» части приложения между собой (внедрение зависимостей).
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.viewmodel.compose)

    // Room — база данных на телефоне (кэш данных и рассчитанные метрики).
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
}
