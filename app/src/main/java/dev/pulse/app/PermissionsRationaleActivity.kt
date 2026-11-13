package dev.pulse.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.pulse.app.ui.components.PulseButton
import dev.pulse.app.ui.glass.GlassBackground
import dev.pulse.app.ui.glass.GlassCard
import dev.pulse.app.ui.theme.PulseTheme

/**
 * Экран «как Pulse использует ваши данные». Health Connect открывает его, когда человек
 * нажимает ссылку на политику конфиденциальности в окне разрешений. Без него Android
 * не разрешит запрашивать доступ к данным о здоровье.
 */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            PulseTheme {
                GlassBackground {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .safeDrawingPadding()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.rationale_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = PulseTheme.colors.textPrimary,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = stringResource(R.string.rationale_text),
                                style = MaterialTheme.typography.bodyMedium,
                                color = PulseTheme.colors.textPrimary,
                            )
                        }
                        PulseButton(
                            text = stringResource(R.string.rationale_close),
                            onClick = { finish() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
