package dev.pulse.app.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import dev.pulse.app.R

/** Вкладки нижнего меню. */
enum class PulseTab(@StringRes val labelRes: Int, @DrawableRes val iconRes: Int) {
    TODAY(R.string.tab_today, R.drawable.ic_tab_today),
    SLEEP(R.string.tab_sleep, R.drawable.ic_tab_sleep),
    STRAIN(R.string.tab_strain, R.drawable.ic_tab_strain),
    TRENDS(R.string.tab_trends, R.drawable.ic_tab_trends),
    JOURNAL(R.string.tab_journal, R.drawable.ic_tab_journal),
}
