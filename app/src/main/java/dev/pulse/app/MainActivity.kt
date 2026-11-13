package dev.pulse.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.pulse.app.data.settings.ThemeSettings
import javax.inject.Inject
import dev.pulse.app.ui.PulseApp
import dev.pulse.app.ui.theme.PulseTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var themeSettings: ThemeSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Рисуем под системными панелями; значки в них светлые, потому что тема тёмная.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            val dark by themeSettings.dark.collectAsStateWithLifecycle()
            PulseTheme(darkTheme = dark) {
                PulseApp()
            }
        }
    }
}
