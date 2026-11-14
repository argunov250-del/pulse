package dev.pulse.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
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
import dev.pulse.app.ui.glass.EdgeBlur
import dev.pulse.app.ui.glass.GlassBackground
import dev.pulse.app.ui.glass.LocalHazeState
import dev.pulse.app.ui.navigation.BottomBarHeight
import dev.pulse.app.ui.navigation.BottomBarMargin
import dev.pulse.app.ui.navigation.BottomBarSpace
import dev.pulse.app.ui.navigation.GlassBottomBar
import dev.pulse.app.ui.navigation.PulseTab
import dev.pulse.app.ui.profile.ProfileScreen
import dev.pulse.app.ui.settings.SettingsScreen
import dev.pulse.app.ui.strain.StrainScreen
import dev.pulse.app.ui.trends.TrendsScreen
import dev.pulse.app.ui.sleep.SleepScreen
import dev.pulse.app.ui.sources.DataSourcesScreen
import dev.pulse.app.ui.sources.DataSourcesViewModel
import dev.pulse.app.ui.journal.JournalScreen
import dev.pulse.app.ui.theme.PulseTheme
import dev.pulse.app.ui.today.TodayViewModel
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
    // Уведомление о восстановлении: включается только после разрешения Android.
    val notifyEnabled by sourcesViewModel.notificationsEnabled.collectAsStateWithLifecycle()
    var notifyDenied by rememberSaveable { mutableStateOf(false) }
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) sourcesViewModel.setNotifications(true) else notifyDenied = true
    }

    // Показатели для экрана «Сегодня»; null — ещё считаются.
    val todayViewModel: TodayViewModel = hiltViewModel()
    val todayState by todayViewModel.state.collectAsStateWithLifecycle()
    val today = todayState
    LifecycleResumeEffect(Unit) {
        sourcesViewModel.refresh()
        todayViewModel.refresh()
        onPauseOrDispose { }
    }

    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    GlassBackground {
        // Содержимое прокручивается и под строкой состояния, и под нижним меню,
        // а у краёв экрана плавно размывается (см. EdgeBlur ниже).
        val hazeState = LocalHazeState.current
        Box(modifier = Modifier.fillMaxSize()) {
            // Содержимое экрана — тоже «источник» для размытия: нижнее меню размывает
            // карточки, которые прокручиваются под ним, и текст сквозь него не просвечивает.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (hazeState != null) Modifier.hazeSource(hazeState, zIndex = 1f) else Modifier),
            ) {
                val overlayPadding = PaddingValues(top = statusBarTop, bottom = navBarBottom + 12.dp)
                if (overlay == Overlay.SETTINGS) {
                    SettingsScreen(
                        onBack = back,
                        onOpenSources = { open(Overlay.DATA_SOURCES) },
                        onOpenProfile = { open(Overlay.PROFILE) },
                        notifyText = stringResource(
                            when {
                                notifyDenied -> R.string.settings_notify_denied
                                notifyEnabled -> R.string.settings_notify_on
                                else -> R.string.settings_notify_off
                            },
                        ),
                        onToggleNotify = {
                            notifyDenied = false
                            when {
                                notifyEnabled -> sourcesViewModel.setNotifications(false)
                                // До Android 13 разрешение на уведомления не спрашивают.
                                Build.VERSION.SDK_INT < 33 -> sourcesViewModel.setNotifications(true)
                                else -> notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        darkTheme = PulseTheme.colors.isDark,
                        onToggleTheme = sourcesViewModel::toggleTheme,
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
                } else if (overlay == Overlay.PROFILE) {
                    ProfileScreen(onBack = back, contentPadding = overlayPadding)
                } else if (overlay == Overlay.DATA_SOURCES) {
                    DataSourcesScreen(
                        state = sources,
                        viewModel = sourcesViewModel,
                        onBack = back,
                        contentPadding = overlayPadding,
                    )
                } else {
                    // Внизу оставляем место под меню, чтобы последнюю карточку можно было докрутить.
                    val padding = PaddingValues(top = statusBarTop, bottom = navBarBottom + BottomBarSpace)
                    when (tab) {
                        PulseTab.TODAY -> if (today != null) TodayScreen(
                            state = today,
                            onOpenSettings = { open(Overlay.SETTINGS) },
                            showConnectCard = sources.needsConnection,
                            onConnect = { open(Overlay.DATA_SOURCES) },
                            contentPadding = padding,
                            refreshing = sources.sync.running,
                            onRefresh = {
                                sourcesViewModel.syncNow()
                                todayViewModel.refresh()
                            },
                            onOpenSleep = { tab = PulseTab.SLEEP },
                            onOpenStrain = { tab = PulseTab.STRAIN },
                            onOpenTrends = { tab = PulseTab.TRENDS },
                        )
                        PulseTab.SLEEP -> SleepScreen(contentPadding = padding)
                        PulseTab.STRAIN -> StrainScreen(contentPadding = padding)
                        PulseTab.TRENDS -> TrendsScreen(contentPadding = padding)
                        PulseTab.JOURNAL -> JournalScreen(contentPadding = padding)
                    }
                }
            }
            // Верх: размытие под часами и значками, сходит на нет чуть ниже строки состояния.
            EdgeBlur(
                height = statusBarTop + 28.dp,
                strongAtTop = true,
                modifier = Modifier.align(Alignment.TopCenter),
            )
            // Низ: размытие начинается от середины меню и усиливается к краю экрана.
            EdgeBlur(
                height = if (settingsOpen) navBarBottom + 16.dp else navBarBottom + BottomBarMargin + BottomBarHeight / 2,
                strongAtTop = false,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            if (!settingsOpen) {
                GlassBottomBar(
                    selected = tab,
                    onSelect = { tab = it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 16.dp, end = 16.dp, bottom = navBarBottom + BottomBarMargin),
                )
            }
        }
    }
}

/** Экраны, которые открываются поверх вкладок. */
private enum class Overlay { SETTINGS, DATA_SOURCES, PROFILE }
