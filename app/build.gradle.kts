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

    // Постоянный ключ подписи. Сам ключ в репозитории НЕ хранится: в облачной сборке он
    // приходит из GitHub Secrets через переменные окружения. Если их нет — собирается
    // обычная debug-версия с временным ключом.
    val keystorePath: String? = System.getenv("PULSE_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }
    signingConfigs {
        if (keystorePath != null) {
            create("pulse") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("PULSE_KEYSTORE_PASSWORD")
                keyAlias = "pulse"
                keyPassword = System.getenv("PULSE_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("pulse")
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

// Room сохраняет описание структуры базы в app/schemas — по нему проверяются миграции.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
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
    // Vico — графики.
    implementation(libs.vico.compose)
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

    // WorkManager — фоновая синхронизация, даже когда приложение закрыто.
    implementation(libs.androidx.work.runtime)
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)

    testImplementation(libs.junit)
}
