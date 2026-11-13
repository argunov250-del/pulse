package dev.pulse.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.pulse.app.ui.glass.GlassBackground
import dev.pulse.app.ui.theme.PulseTheme
import dev.pulse.app.ui.today.FakeTodayData
import dev.pulse.app.ui.today.TodayScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Рисуем под системными панелями; значки в них светлые, потому что тема тёмная.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            PulseTheme {
                GlassBackground {
                    // Фаза 1: экран показывает демо-данные.
                    TodayScreen(state = FakeTodayData.state)
                }
            }
        }
    }
}
