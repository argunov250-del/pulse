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
import dev.pulse.app.ui.glass.GlassBackground
import dev.pulse.app.ui.navigation.BottomBarSpace
import dev.pulse.app.ui.navigation.GlassBottomBar
import dev.pulse.app.ui.navigation.PulseTab
import dev.pulse.app.ui.settings.SettingsScreen
import dev.pulse.app.ui.stub.StubScreen
import dev.pulse.app.ui.today.FakeTodayData
import dev.pulse.app.ui.today.TodayScreen

/** Каркас приложения: фон, текущий экран и нижнее меню. */
@Composable
fun PulseApp() {
    var tab by rememberSaveable { mutableStateOf(PulseTab.TODAY) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }

    // Системная кнопка «Назад»: из настроек — на вкладку, с любой вкладки — на «Сегодня».
    BackHandler(enabled = settingsOpen || tab != PulseTab.TODAY) {
        if (settingsOpen) settingsOpen = false else tab = PulseTab.TODAY
    }

    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    GlassBackground {
        // Содержимое не заезжает под строку состояния (часы, батарея).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            if (settingsOpen) {
                SettingsScreen(
                    onBack = { settingsOpen = false },
                    contentPadding = PaddingValues(bottom = navBarBottom + 12.dp),
                )
            } else {
                // Внизу оставляем место под меню, чтобы последнюю карточку можно было докрутить.
                val padding = PaddingValues(bottom = navBarBottom + BottomBarSpace)
                when (tab) {
                    PulseTab.TODAY -> TodayScreen(
                        state = FakeTodayData.state, // Фаза 1: демо-данные.
                        onOpenSettings = { settingsOpen = true },
                        contentPadding = padding,
                    )
                    PulseTab.SLEEP -> Stub(R.string.tab_sleep, R.string.stub_sleep, padding)
                    PulseTab.STRAIN -> Stub(R.string.tab_strain, R.string.stub_strain, padding)
                    PulseTab.TRENDS -> Stub(R.string.tab_trends, R.string.stub_trends, padding)
                    PulseTab.JOURNAL -> Stub(R.string.tab_journal, R.string.stub_journal, padding)
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

@Composable
private fun Stub(titleRes: Int, descriptionRes: Int, padding: PaddingValues) {
    StubScreen(
        title = stringResource(titleRes),
        cardTitle = stringResource(R.string.stub_title),
        description = stringResource(descriptionRes),
        contentPadding = padding,
    )
}
