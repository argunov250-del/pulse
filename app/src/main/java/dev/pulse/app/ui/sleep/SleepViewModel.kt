package dev.pulse.app.ui.sleep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pulse.app.algorithms.cycle.Cycle
import dev.pulse.app.algorithms.cycle.CycleSegmenter
import dev.pulse.app.algorithms.cycle.SleepInput
import dev.pulse.app.algorithms.sleep.SleepStage
import dev.pulse.app.data.db.HealthDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Длительности стадий в основном сне, мс. */
data class StageBreakdown(
    val deepMs: Long,
    val remMs: Long,
    val lightMs: Long,
    /** Сон без разбивки на стадии. */
    val asleepMs: Long,
    val awakeMs: Long,
) {
    val hasDetailedStages: Boolean get() = deepMs + remMs + lightMs > 0
    val totalMs: Long get() = deepMs + remMs + lightMs + asleepMs + awakeMs
}

data class LastSleepUi(
    val startMs: Long,
    val endMs: Long,
    val asleepMs: Long,
    val source: String,
    val stages: StageBreakdown,
)

data class SleepUiState(
    val loading: Boolean = true,
    val lastSleep: LastSleepUi? = null,
    /** Идущий сейчас цикл; null — его нет. */
    val currentCycle: Cycle? = null,
    /** Последние циклы, от новых к старым. */
    val recentCycles: List<Cycle> = emptyList(),
    val totalCycles: Int = 0,
    val nowMs: Long = 0,
) {
    val isEmpty: Boolean get() = !loading && lastSleep == null
}

/**
 * Готовит экран «Сон»: берёт все сессии сна из базы, делит их на циклы и собирает сводку.
 * База сама сообщает об изменениях, поэтому после синхронизации или импорта экран обновляется сам.
 */
@HiltViewModel
class SleepViewModel @Inject constructor(
    private val dao: HealthDao,
) : ViewModel() {

    val state: StateFlow<SleepUiState> = dao.observeSleepSessions()
        .map { sessions ->
            val now = System.currentTimeMillis()
            val sources = sessions.associate { it.id to it.source }
            val cycles = CycleSegmenter.segment(
                sessions.map { SleepInput(it.id, it.startTime, it.endTime, it.asleepMs) },
                nowMs = now,
            )
            val last = cycles.lastOrNull()
            SleepUiState(
                loading = false,
                lastSleep = last?.mainSleep?.let { main ->
                    LastSleepUi(
                        startMs = main.startMs,
                        endMs = main.endMs,
                        asleepMs = main.asleepMs,
                        source = sources[main.id].orEmpty(),
                        stages = breakdown(main.id),
                    )
                },
                currentCycle = last?.takeIf { it.endMs == null },
                recentCycles = cycles.takeLast(RECENT_CYCLES).reversed(),
                totalCycles = cycles.size,
                nowMs = now,
            )
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SleepUiState())

    private suspend fun breakdown(sessionId: Long): StageBreakdown {
        var deep = 0L
        var rem = 0L
        var light = 0L
        var asleep = 0L
        var awake = 0L
        for (stage in dao.sleepStages(sessionId)) {
            val duration = stage.endTime - stage.startTime
            when (SleepStage.entries.getOrNull(stage.stage)) {
                SleepStage.DEEP -> deep += duration
                SleepStage.REM -> rem += duration
                SleepStage.LIGHT -> light += duration
                SleepStage.ASLEEP -> asleep += duration
                SleepStage.AWAKE -> awake += duration
                SleepStage.IN_BED, null -> Unit
            }
        }
        return StageBreakdown(deep, rem, light, asleep, awake)
    }

    private companion object {
        const val RECENT_CYCLES = 14
    }
}
