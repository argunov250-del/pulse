package dev.pulse.app.data.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Выбор темы оформления. По умолчанию тёмная. Хранится в настройках приложения на телефоне. */
@Singleton
class ThemeSettings @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("pulse_settings", Context.MODE_PRIVATE)
    private val _dark = MutableStateFlow(prefs.getBoolean(KEY_DARK, true))
    val dark: StateFlow<Boolean> = _dark.asStateFlow()

    fun toggle() {
        val value = !_dark.value
        prefs.edit().putBoolean(KEY_DARK, value).apply()
        _dark.value = value
    }

    private companion object {
        const val KEY_DARK = "darkTheme"
    }
}
