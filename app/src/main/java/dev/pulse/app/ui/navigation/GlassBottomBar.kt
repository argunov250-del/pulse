package dev.pulse.app.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pulse.app.ui.glass.GlassCard
import dev.pulse.app.ui.theme.PulseTheme

/** Высота меню вместе с отступами — столько места экраны оставляют внизу под него. */
val BottomBarSpace = 92.dp

/** Примерная высота самого меню и его отступ от низа экрана. */
val BottomBarHeight = 66.dp
val BottomBarMargin = 10.dp

/** «Парящее» стеклянное нижнее меню. */
@Composable
fun GlassBottomBar(
    selected: PulseTab,
    onSelect: (PulseTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 28.dp,
        contentPadding = PaddingValues(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            PulseTab.entries.forEach { tab ->
                BarItem(
                    tab = tab,
                    selected = tab == selected,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BarItem(
    tab: PulseTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PulseTheme.colors
    val contentColor by animateColorAsState(
        targetValue = if (selected) colors.textPrimary else colors.textSecondary,
        label = "tabColor",
    )
    val highlight by animateColorAsState(
        targetValue = if (selected) colors.glassBorder else Color.Transparent,
        label = "tabHighlight",
    )
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(highlight)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            painter = painterResource(tab.iconRes),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = stringResource(tab.labelRes),
            fontSize = 10.5.sp,
            lineHeight = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            softWrap = false,
        )
    }
}
