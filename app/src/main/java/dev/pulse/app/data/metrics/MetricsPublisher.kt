package dev.pulse.app.data.metrics

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.pulse.app.algorithms.RecoveryZone
import dev.pulse.app.algorithms.engine.EngineOutput
import dev.pulse.app.notify.RecoveryNotifier
import dev.pulse.app.widget.PulseWidget
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** Краткий итог последнего цикла — то, что показывают виджет и уведомление. */
data class MetricsSnapshot(
    val cycleStartMs: Long,
    /** null — восстановление посчитать не из чего. */
    val recoveryPercent: Int?,
    val zone: RecoveryZone?,
    /** Нагрузка ×10 (чтобы хранить целым числом); null — нет данных. */
    val strainTenths: Int?,
    val sleepPercent: Int,
)

/**
 * Сохраняет итог последнего расчёта и рассылает его «наружу»: виджету и уведомлению.
 * Хранится в настройках приложения, чтобы виджет мог прочитать итог мгновенно, без базы.
 */
@Singleton
class MetricsPublisher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notifier: RecoveryNotifier,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    suspend fun publish(output: EngineOutput) {
        val last = output.cycles.lastOrNull() ?: return
        val snapshot = MetricsSnapshot(
            cycleStartMs = last.cycle.startMs,
            recoveryPercent = last.recovery?.percent,
            zone = last.recovery?.zone,
            strainTenths = last.strain?.let { (it * 10).roundToInt() },
            sleepPercent = last.sleepPerformance.roundToInt(),
        )
        if (snapshot == read(context)) return // ничего не изменилось — никого не дёргаем
        prefs.edit()
            .putLong(KEY_CYCLE, snapshot.cycleStartMs)
            .putInt(KEY_RECOVERY, snapshot.recoveryPercent ?: NONE)
            .putInt(KEY_ZONE, snapshot.zone?.ordinal ?: NONE)
            .putInt(KEY_STRAIN, snapshot.strainTenths ?: NONE)
            .putInt(KEY_SLEEP, snapshot.sleepPercent)
            .apply()
        runCatching { PulseWidget().updateAll(context) }
        runCatching { notifier.onNewSnapshot(snapshot) }
    }

    companion object {
        private const val PREFS = "pulse_snapshot"
        private const val KEY_CYCLE = "cycleStart"
        private const val KEY_RECOVERY = "recovery"
        private const val KEY_ZONE = "zone"
        private const val KEY_STRAIN = "strain"
        private const val KEY_SLEEP = "sleep"
        private const val NONE = -1

        /** Последний сохранённый итог; null — расчёта ещё не было. */
        fun read(context: Context): MetricsSnapshot? {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!prefs.contains(KEY_CYCLE)) return null
            return MetricsSnapshot(
                cycleStartMs = prefs.getLong(KEY_CYCLE, 0),
                recoveryPercent = prefs.getInt(KEY_RECOVERY, NONE).takeIf { it != NONE },
                zone = RecoveryZone.entries.getOrNull(prefs.getInt(KEY_ZONE, NONE)),
                strainTenths = prefs.getInt(KEY_STRAIN, NONE).takeIf { it != NONE },
                sleepPercent = prefs.getInt(KEY_SLEEP, 0),
            )
        }
    }
}
