// Корневой файл сборки: здесь только объявляются плагины, применяются они в модулях.
plugins {
    alias(libs.plugins.android.application) apply false
    // Kotlin для Android встроен в плагин сборки; эта строка лишь задаёт его версию.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
