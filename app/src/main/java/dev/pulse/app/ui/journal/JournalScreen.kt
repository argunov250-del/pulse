package dev.pulse.app.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pulse.app.R
import dev.pulse.app.algorithms.JournalInsight
import dev.pulse.app.ui.glass.GlassCard
import dev.pulse.app.ui.theme.PulseTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/** Экран «Журнал»: отметки привычек по циклам и их связь с восстановлением. */
@Composable
fun JournalScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: JournalViewModel = hiltViewModel(),
) {
    val colors = PulseTheme.colors
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(R.string.tab_journal),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
        Text(
            text = stringResource(R.string.journal_intro),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        if (state.loading) return@Column
        if (state.cycles.isEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.journal_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
            return@Column
        }

        InsightsCard(state.insights)
        state.cycles.forEach { cycle ->
            CycleCard(
                cycle = cycle,
                onToggle = { tag, selected -> viewModel.toggle(cycle.cycleStart, tag, selected) },
                onNothing = { viewModel.markNothing(cycle.cycleStart) },
            )
        }
    }
}

@Composable
private fun InsightsCard(insights: List<JournalInsight>) {
    val colors = PulseTheme.colors
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.journal_insights_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            if (insights.isEmpty()) {
                Text(
                    text = stringResource(R.string.journal_insights_wait),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                return@Column
            }
            insights.forEach { insight ->
                val tag = JournalTag.entries.firstOrNull { it.name == insight.tag } ?: return@forEach
                val delta = insight.delta.roundToInt()
                val (verdict, color) = when {
                    delta <= -3 -> stringResource(R.string.journal_insight_worse, abs(delta)) to colors.recoveryRed
                    delta >= 3 -> stringResource(R.string.journal_insight_better, delta) to colors.recoveryGreen
                    else -> stringResource(R.string.journal_insight_same) to colors.textSecondary
                }
                Text(
                    text = stringResource(
                        R.string.journal_insight_line,
                        stringResource(tagLabel(tag)),
                        insight.averageWith.roundToInt(),
                        insight.withCount,
                        insight.averageWithout.roundToInt(),
                        insight.withoutCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textPrimary,
                )
                Text(text = verdict, style = MaterialTheme.typography.labelLarge, color = color)
            }
            Text(
                text = stringResource(R.string.journal_insights_note),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CycleCard(
    cycle: JournalCycleUi,
    onToggle: (JournalTag, Boolean) -> Unit,
    onNothing: () -> Unit,
) {
    val colors = PulseTheme.colors
    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, contentPadding = PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.journal_cycle, cycle.label),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (!cycle.logged) {
                    Text(
                        text = stringResource(R.string.journal_not_logged),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.recoveryYellow,
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                JournalTag.entries.forEach { tag ->
                    val selected = tag in cycle.tags
                    Chip(stringResource(tagLabel(tag)), selected, colors.strain) { onToggle(tag, !selected) }
                }
                // «Ничего из этого» выделено, когда журнал заполнен и ни одной привычки не отмечено.
                Chip(
                    text = stringResource(R.string.journal_nothing),
                    selected = cycle.logged && cycle.tags.isEmpty(),
                    color = colors.recoveryGreen,
                    onClick = onNothing,
                )
            }
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    val colors = PulseTheme.colors
    val shape = RoundedCornerShape(50)
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) Color(0xFF06101F) else colors.textPrimary,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) color else Color.Transparent)
            .border(1.dp, if (selected) color else colors.glassBorder, shape)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

private fun tagLabel(tag: JournalTag): Int = when (tag) {
    JournalTag.CAFFEINE -> R.string.journal_tag_caffeine
    JournalTag.ALCOHOL -> R.string.journal_tag_alcohol
    JournalTag.LATE_MEAL -> R.string.journal_tag_late_meal
    JournalTag.STRESS -> R.string.journal_tag_stress
}
