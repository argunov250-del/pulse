package dev.pulse.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.pulse.app.R
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.hazeSource
import dev.pulse.app.ui.glass.GlassBackground
import dev.pulse.app.ui.glass.LocalHazeState
import dev.pulse.app.ui.navigation.BottomBarSpace
import dev.pulse.app.ui.navigation.GlassBottomBar
import dev.pulse.app.ui.navigation.PulseTab
import dev.pulse.app.ui.settings.SettingsScreen
import dev.pulse.app.ui.sleep.SleepScreen
import dev.pulse.app.ui.sources.DataSourcesScreen
import dev.pulse.app.ui.sources.DataSourcesViewModel
import dev.pulse.app.ui.stub.StubScreen
import dev.pulse.app.ui.today.FakeTodayData
import dev.pulse.app.ui.today.TodayScreen

/** Каркас приложения: фон, текущий экран и нижнее меню. */
@Composable
fun PulseApp() {
    var tab by rememberSaveable { mutableStateOf(PulseTab.TODAY) }
    // Экраны, открытые поверх вкладок (настройки, источники данных), в порядке открытия.
    var overlays by rememberSaveable { mutableStateOf(listOf<Overlay>()) }
    val overlay = overlays.lastOrNull()
    val settingsOpen = overlay != null
    val open = { screen: Overlay -> overlays = overlays + screen }
    val back = { overlays = overlays.dropLast(1) }

    // Системная кнопка «Назад»: закрывает верхний экран, а с любой вкладки ведёт на «Сегодня».
    BackHandler(enabled = settingsOpen || tab != PulseTab.TODAY) {
        if (settingsOpen) back() else tab = PulseTab.TODAY
    }

    // Состояние Health Connect общее для всего приложения; перечитываем при каждом возврате в него
    // (например, после того как человек выдал разрешения в самом Health Connect).
    val sourcesViewModel: DataSourcesViewModel = hiltViewModel()
    val sources by sourcesViewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        sourcesViewModel.refresh()
        onPauseOrDispose { }
    }

    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    GlassBackground {
        // Содержимое не заезжает под строку состояния (часы, батарея).
        val hazeState = LocalHazeState.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            // Содержимое экрана — тоже «источник» для размытия: нижнее меню размывает
            // карточки, которые прокручиваются под ним, и текст сквозь него не просвечивает.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (hazeState != null) Modifier.hazeSource(hazeState, zIndex = 1f) else Modifier),
            ) {
                val overlayPadding = PaddingValues(bottom = navBarBottom + 12.dp)
                if (overlay == Overlay.SETTINGS) {
                    SettingsScreen(
                        onBack = back,
                        onOpenSources = { open(Overlay.DATA_SOURCES) },
                        sourcesSummary = if (sources.needsConnection) {
                            stringResource(R.string.sources_settings_not_connected)
                        } else {
                            stringResource(
                                R.string.sources_settings_connected,
                                sources.grantedCount,
                                sources.supportedCount,
                            )
                        },
                        contentPadding = overlayPadding,
                    )
                } else if (overlay == Overlay.DATA_SOURCES) {
                    DataSourcesScreen(
                        state = sources,
                        viewModel = sourcesViewModel,
                        onBack = back,
                        contentPadding = overlayPadding,
                    )
                } else {
                    // Внизу оставляем место под меню, чтобы последнюю карточку можно было докрутить.
                    val padding = PaddingValues(bottom = navBarBottom + BottomBarSpace)
                    when (tab) {
                        PulseTab.TODAY -> TodayScreen(
                            state = FakeTodayData.state, // Фаза 1: демо-данные.
                            onOpenSettings = { open(Overlay.SETTINGS) },
                            showConnectCard = sources.needsConnection,
                            onConnect = { open(Overlay.DATA_SOURCES) },
                            contentPadding = padding,
                        )
                        PulseTab.SLEEP -> SleepScreen(contentPadding = padding)
                        PulseTab.STRAIN -> Stub(R.string.tab_strain, R.string.stub_strain, padding)
                        PulseTab.TRENDS -> Stub(R.string.tab_trends, R.string.stub_trends, padding)
                        PulseTab.JOURNAL -> Stub(R.string.tab_journal, R.string.stub_journal, padding)
                    }
                }
            }
            if (!settingsOpen) {
                GlassBottomBar(
                    selected = tab,
                    onSelect = { tab = it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 16.dp, end = 16.dp, bottom = navBarBottom + 10.dp),
                )
            }
        }
    }
}

/** Экраны, которые открываются поверх вкладок. */
private enum class Overlay { SETTINGS, DATA_SOURCES }

@Composable
private fun Stub(titleRes: Int, descriptionRes: Int, padding: PaddingValues) {
    StubScreen(
        title = stringResource(titleRes),
        cardTitle = stringResource(R.string.stub_title),
        description = stringResource(descriptionRes),
        contentPadding = padding,
    )
}
