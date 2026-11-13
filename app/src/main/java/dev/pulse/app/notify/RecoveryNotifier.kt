package dev.pulse.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pulse.app.MainActivity
import dev.pulse.app.R
import dev.pulse.app.algorithms.RecoveryZone
import dev.pulse.app.algorithms.strainTarget
import dev.pulse.app.data.metrics.MetricsSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Уведомление «Восстановление N %» после нового основного сна.
 * Приходит один раз на цикл и только если цикл свежий — старые данные из архива не шумят.
 */
@Singleton
class RecoveryNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences("pulse_settings", Context.MODE_PRIVATE)
    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _enabled.value = enabled
    }

    fun onNewSnapshot(snapshot: MetricsSnapshot) {
        val percent = snapshot.recoveryPercent ?: return
        val zone = snapshot.zone ?: return
        if (!_enabled.value) return
        if (prefs.getLong(KEY_LAST_CYCLE, 0) == snapshot.cycleStartMs) return
        // Запоминаем цикл в любом случае: о старом цикле не сообщаем и позже.
        prefs.edit().putLong(KEY_LAST_CYCLE, snapshot.cycleStartMs).apply()
        if (System.currentTimeMillis() - snapshot.cycleStartMs > FRESH_MS) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.notify_channel), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.notify_channel_description) },
        )
        val zoneName = context.getString(
            when (zone) {
                RecoveryZone.GREEN -> R.string.recovery_zone_green
                RecoveryZone.YELLOW -> R.string.recovery_zone_yellow
                RecoveryZone.RED -> R.string.recovery_zone_red
            },
        ).lowercase()
        val target = strainTarget(zone)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_tab_today)
            .setContentTitle(context.getString(R.string.notify_title, percent, zoneName))
            .setContentText(
                context.getString(
                    R.string.notify_text,
                    target.start.roundToInt().toString(),
                    target.endInclusive.roundToInt().toString(),
                ),
            )
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Разрешение отозвали между проверкой и показом — просто не показываем.
        }
    }

    private companion object {
        const val CHANNEL = "recovery"
        const val NOTIFICATION_ID = 1
        const val KEY_ENABLED = "recoveryNotification"
        const val KEY_LAST_CYCLE = "lastNotifiedCycle"
        const val FRESH_MS = 24 * 3_600_000L
    }
}
