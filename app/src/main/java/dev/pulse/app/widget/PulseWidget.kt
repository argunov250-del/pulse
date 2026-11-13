package dev.pulse.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.pulse.app.MainActivity
import dev.pulse.app.R
import dev.pulse.app.algorithms.RecoveryZone
import dev.pulse.app.data.metrics.MetricsPublisher
import dev.pulse.app.data.metrics.MetricsSnapshot
import java.util.Locale

/** Виджет на главный экран: восстановление, нагрузка и сон последнего цикла. */
class PulseWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = MetricsPublisher.read(context)
        provideContent { Content(context, snapshot) }
    }
}

class PulseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PulseWidget()
}

private val Background = Color(0xE6141824)
private val TextPrimary = Color(0xFFF4F5FA)
private val TextSecondary = Color(0xA6F4F5FA)
private val Green = Color(0xFF3DDC97)
private val Yellow = Color(0xFFFFC857)
private val Red = Color(0xFFFF5D5D)
private val Blue = Color(0xFF4DA3FF)
private val Violet = Color(0xFFA78BFA)

@Composable
private fun Content(context: Context, snapshot: MetricsSnapshot?) {
    val modifier = GlanceModifier
        .fillMaxSize()
        .background(Background)
        .cornerRadius(24.dp)
        .padding(12.dp)
        .clickable(actionStartActivity<MainActivity>())
    if (snapshot == null) {
        Column(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = context.getString(R.string.widget_empty),
                style = TextStyle(color = ColorProvider(TextSecondary), fontSize = 13.sp),
            )
        }
        return
    }
    val recoveryColor = when (snapshot.zone) {
        RecoveryZone.GREEN -> Green
        RecoveryZone.YELLOW -> Yellow
        RecoveryZone.RED -> Red
        null -> TextSecondary
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Metric(
            label = context.getString(R.string.widget_recovery),
            value = snapshot.recoveryPercent?.let { "$it%" } ?: "—",
            color = recoveryColor,
            modifier = GlanceModifier.defaultWeight(),
        )
        Metric(
            label = context.getString(R.string.widget_strain),
            value = snapshot.strainTenths?.let { String.format(Locale.forLanguageTag("ru"), "%.1f", it / 10.0) } ?: "—",
            color = Blue,
            modifier = GlanceModifier.defaultWeight(),
        )
        Metric(
            label = context.getString(R.string.widget_sleep),
            value = "${snapshot.sleepPercent}%",
            color = Violet,
            modifier = GlanceModifier.defaultWeight(),
        )
    }
}

@Composable
private fun Metric(label: String, value: String, color: Color, modifier: GlanceModifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = TextStyle(color = ColorProvider(color), fontSize = 22.sp, fontWeight = FontWeight.Bold),
        )
        Text(text = label, style = TextStyle(color = ColorProvider(TextPrimary), fontSize = 12.sp))
    }
}
