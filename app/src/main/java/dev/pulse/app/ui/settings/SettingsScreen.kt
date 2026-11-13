package dev.pulse.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.pulse.app.R
import dev.pulse.app.ui.glass.GlassCard
import dev.pulse.app.ui.glass.GlassDefaults
import dev.pulse.app.ui.glass.GlassIconButton
import dev.pulse.app.ui.theme.PulseTheme

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenSources: () -> Unit,
    sourcesSummary: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val colors = PulseTheme.colors
    val context = LocalContext.current
    val versionName = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(
                iconRes = R.drawable.ic_back,
                contentDescription = stringResource(R.string.back),
                onClick = onBack,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
            )
        }
        SettingsCard(
            title = stringResource(R.string.settings_profile),
            text = stringResource(R.string.settings_profile_stub),
        )
        SettingsCard(
            title = stringResource(R.string.settings_sources),
            text = sourcesSummary,
            onClick = onOpenSources,
        )
        SettingsCard(
            title = stringResource(R.string.settings_disclaimer),
            text = stringResource(R.string.settings_disclaimer_text),
        )
        SettingsCard(
            title = stringResource(R.string.settings_privacy),
            text = stringResource(R.string.settings_privacy_text),
        )
        Text(
            text = stringResource(R.string.settings_version, versionName),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun SettingsCard(title: String, text: String, onClick: (() -> Unit)? = null) {
    val colors = PulseTheme.colors
    val shape = RoundedCornerShape(GlassDefaults.CornerRadius)
    val clickModifier = if (onClick != null) {
        Modifier.clip(shape).clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    GlassCard(modifier = Modifier.fillMaxWidth().then(clickModifier)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (onClick != null) {
                    Text(
                        text = stringResource(R.string.sources_open) + " ›",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.strain,
                    )
                }
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}
