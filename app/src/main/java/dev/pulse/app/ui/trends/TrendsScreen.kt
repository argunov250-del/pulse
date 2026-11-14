package dev.pulse.app.ui.trends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.common.VicoTheme
import dev.pulse.app.R
import dev.pulse.app.ui.components.PulseButton
import dev.pulse.app.ui.components.PulseOutlinedButton
import dev.pulse.app.ui.glass.GlassCard
import dev.pulse.app.ui.theme.PulseTheme
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/** Экран «Тренды»: графики показателей по циклам за 7, 30 или 90 дней. */
@Composable
fun TrendsScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: TrendsViewModel = hiltViewModel(),
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
            text = stringResource(R.string.tab_trends),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7, 30, 90).forEach { days ->
                val text = stringResource(R.string.trends_range_days, days)
                if (days == state.rangeDays) {
                    PulseButton(text, { viewModel.setRange(days) }, Modifier.weight(1f))
                } else {
                    PulseOutlinedButton(text, { viewModel.setRange(days) }, Modifier.weight(1f))
                }
            }
        }
        if (state.loading) return@Column

        if (state.series.isEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.trends_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
            return@Column
        }
        Text(
            text = stringResource(R.string.trends_cycles_in_range, state.cycleCount),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        state.series.forEach { series ->
            // Каждый график привязан к своему показателю и периоду: при смене периода он создаётся заново.
            key(series.kind, state.rangeDays) { TrendCard(series) }
        }
    }
}

@Composable
private fun TrendCard(series: TrendSeries) {
    val colors = PulseTheme.colors
    val (titleRes, unitRes, color, decimals) = when (series.kind) {
        TrendKind.RECOVERY -> Spec(R.string.recovery_label, R.string.unit_percent, colors.recoveryGreen, 0)
        TrendKind.STRAIN -> Spec(R.string.strain_label, R.string.unit_none, colors.strain, 1)
        TrendKind.SLEEP_HOURS -> Spec(R.string.sleep_label, R.string.unit_hours, colors.sleep, 1)
        TrendKind.RESTING_HR -> Spec(R.string.rhr_label, R.string.rhr_unit, colors.recoveryRed, 0)
        TrendKind.HRV -> Spec(
            R.string.hrv_label, if (series.sdnn) R.string.hrv_unit_sdnn else R.string.hrv_unit, colors.recoveryYellow, 0,
        )
    }
    val unit = stringResource(unitRes)
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.trends_average, format(series.average, decimals), unit).trim(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                )
            }
            Text(
                text = stringResource(R.string.trends_min_max, format(series.min, decimals), format(series.max, decimals)),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            TrendChart(series, color)
        }
    }
}

private data class Spec(val titleRes: Int, val unitRes: Int, val color: Color, val decimals: Int)

@Composable
private fun TrendChart(series: TrendSeries, color: Color) {
    val colors = PulseTheme.colors
    // Данные графика строятся сразу, вместе со шкалой: так при смене периода график никогда
    // не оказывается со старыми точками и новой шкалой (из-за этого приложение вылетало).
    val model = remember(series.values) {
        CartesianChartModel(LineCartesianLayerModel.build { series(series.values) })
    }
    // Подписи по оси X — даты циклов (значение оси — номер точки).
    val labels = series.labels
    val dateFormatter = remember(labels) {
        CartesianValueFormatter { _, value, _ -> labels.getOrNull(value.toInt()) ?: " " }
    }
    // Диапазон оси Y: у процентов и нагрузки шкала полная, у остальных — вокруг самих значений,
    // чтобы линия не прижималась к верху из-за пустого места от нуля.
    val rangeProvider = remember(series.kind, series.min, series.max) {
        when (series.kind) {
            TrendKind.RECOVERY -> CartesianLayerRangeProvider.fixed(minY = 0.0, maxY = maxOf(100.0, series.max))
            TrendKind.STRAIN -> CartesianLayerRangeProvider.fixed(minY = 0.0, maxY = maxOf(21.0, ceil(series.max)))
            TrendKind.SLEEP_HOURS -> CartesianLayerRangeProvider.fixed(minY = 0.0, maxY = ceil(series.max + 1.0))
            TrendKind.RESTING_HR, TrendKind.HRV -> CartesianLayerRangeProvider.fixed(
                minY = floor(series.min / 10.0) * 10.0,
                maxY = ceil(series.max / 10.0) * 10.0 + if (series.max == series.min) 10.0 else 0.0,
            )
        }
    }
    val theme = remember(color, colors) {
        VicoTheme(
            candlestickCartesianLayerColors = VicoTheme.CandlestickCartesianLayerColors(color, color, color),
            columnCartesianLayerColors = listOf(color),
            lineCartesianLayerColors = listOf(color),
            lineColor = colors.ringTrack,
            textColor = colors.textSecondary,
        )
    }
    ProvideVicoTheme(theme) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(rangeProvider = rangeProvider),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = dateFormatter, guideline = null),
            ),
            model = model,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            // Весь период помещается на экран без прокрутки.
            scrollState = rememberVicoScrollState(scrollEnabled = false),
            zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content),
        )
    }
}

private fun format(value: Double, decimals: Int): String =
    String.format(Locale.forLanguageTag("ru"), "%.${decimals}f", value)
