package dev.pulse.app.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pulse.app.algorithms.JournalInsight
import dev.pulse.app.algorithms.JournalInsights
import dev.pulse.app.algorithms.JournalSample
import dev.pulse.app.data.db.HealthDao
import dev.pulse.app.data.db.JournalEntryEntity
import dev.pulse.app.data.metrics.MetricsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** Привычки, которые можно отметить. Имена хранятся в базе — не переименовывать. */
enum class JournalTag { CAFFEINE, ALCOHOL, LATE_MEAL, STRESS }

data class JournalCycleUi(
    val cycleStart: Long,
    val label: String,
    /** true — человек уже заполнял журнал за этот цикл. */
    val logged: Boolean,
    val tags: Set<JournalTag>,
)

data class JournalUiState(
    val loading: Boolean = true,
    /** Последние циклы, от новых к старым. */
    val cycles: List<JournalCycleUi> = emptyList(),
    val insights: List<JournalInsight> = emptyList(),
)

@HiltViewModel
class JournalViewModel @Inject constructor(
    metrics: MetricsRepository,
    private val dao: HealthDao,
) : ViewModel() {

    val state: StateFlow<JournalUiState> = combine(metrics.state, dao.observeJournal()) { m, entries ->
        val cycles = m.output?.cycles.orEmpty()
        val byCycle = entries.groupBy({ it.cycleStart }, { it.tag })

        // Привычки цикла сравниваем с восстановлением СЛЕДУЮЩЕГО цикла: оно считается после сна,
        // который наступил уже после этих привычек.
        val samples = cycles.zipWithNext().mapNotNull { (current, next) ->
            val tags = byCycle[current.cycle.startMs] ?: return@mapNotNull null
            if (JournalEntryEntity.LOGGED !in tags) return@mapNotNull null
            val recovery = next.recovery?.percent ?: return@mapNotNull null
            JournalSample(tags.toSet(), recovery)
        }
        JournalUiState(
            loading = m.loading,
            cycles = cycles.takeLast(RECENT).reversed().map { c ->
                val tags = byCycle[c.cycle.startMs].orEmpty()
                JournalCycleUi(
                    cycleStart = c.cycle.startMs,
                    label = formatter.format(Instant.ofEpochMilli(c.cycle.startMs).atZone(ZoneId.systemDefault())),
                    logged = JournalEntryEntity.LOGGED in tags,
                    tags = JournalTag.entries.filter { it.name in tags }.toSet(),
                )
            },
            insights = JournalInsights.compute(samples, JournalTag.entries.map { it.name }),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JournalUiState())

    /** Включить или выключить привычку за цикл. Любое касание помечает журнал цикла заполненным. */
    fun toggle(cycleStart: Long, tag: JournalTag, selected: Boolean) {
        viewModelScope.launch {
            if (selected) {
                dao.insertJournal(
                    listOf(JournalEntryEntity(cycleStart, JournalEntryEntity.LOGGED), JournalEntryEntity(cycleStart, tag.name)),
                )
            } else {
                dao.insertJournal(listOf(JournalEntryEntity(cycleStart, JournalEntryEntity.LOGGED)))
                dao.deleteJournal(cycleStart, tag.name)
            }
        }
    }

    /** «Ничего из этого»: журнал заполнен, привычек не было. */
    fun markNothing(cycleStart: Long) {
        viewModelScope.launch {
            dao.insertJournal(listOf(JournalEntryEntity(cycleStart, JournalEntryEntity.LOGGED)))
            dao.clearJournalTags(cycleStart, JournalEntryEntity.LOGGED)
        }
    }

    private companion object {
        const val RECENT = 10
        val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.forLanguageTag("ru"))
    }
}
