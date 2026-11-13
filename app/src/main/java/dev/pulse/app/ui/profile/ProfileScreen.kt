package dev.pulse.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pulse.app.R
import dev.pulse.app.ui.components.PulseButton
import dev.pulse.app.ui.components.PulseOutlinedButton
import dev.pulse.app.ui.glass.GlassCard
import dev.pulse.app.ui.glass.GlassIconButton
import dev.pulse.app.ui.theme.PulseTheme

/** Экран «Профиль»: год рождения, пол, рост и максимальный пульс. */
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val colors = PulseTheme.colors
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(R.drawable.ic_back, stringResource(R.string.back), onBack)
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.settings_profile),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
            )
        }
        if (!state.loaded) return@Column

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.profile_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                NumberField(
                    label = stringResource(R.string.profile_birth_year),
                    value = state.birthYear,
                    onChange = viewModel::onBirthYear,
                    error = if (state.birthYearError) stringResource(R.string.profile_birth_year_error) else null,
                    hint = stringResource(R.string.profile_birth_year_hint),
                )
                Text(
                    text = stringResource(R.string.profile_sex),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textPrimary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SexButton(stringResource(R.string.profile_sex_male), !state.female, { viewModel.onFemale(false) }, Modifier.weight(1f))
                    SexButton(stringResource(R.string.profile_sex_female), state.female, { viewModel.onFemale(true) }, Modifier.weight(1f))
                }
                NumberField(
                    label = stringResource(R.string.profile_height),
                    value = state.heightCm,
                    onChange = viewModel::onHeight,
                    error = if (state.heightError) stringResource(R.string.profile_height_error) else null,
                    hint = stringResource(R.string.profile_height_hint),
                )
                NumberField(
                    label = stringResource(R.string.profile_hr_max),
                    value = state.hrMax,
                    onChange = viewModel::onHrMax,
                    error = if (state.hrMaxError) stringResource(R.string.profile_hr_max_error) else null,
                    hint = stringResource(R.string.profile_hr_max_hint, state.autoHrMax ?: 0),
                )
                PulseButton(stringResource(R.string.profile_save), viewModel::save, Modifier.fillMaxWidth())
                if (state.saved) {
                    Text(
                        text = stringResource(R.string.profile_saved),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.recoveryGreen,
                    )
                }
            }
        }
    }
}

@Composable
private fun SexButton(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    if (selected) PulseButton(text, onClick, modifier) else PulseOutlinedButton(text, onClick, modifier)
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, error: String?, hint: String) {
    val colors = PulseTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = { Text(error ?: hint) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
            focusedBorderColor = colors.strain,
            unfocusedBorderColor = colors.glassBorder,
            focusedLabelColor = colors.strain,
            unfocusedLabelColor = colors.textSecondary,
            cursorColor = colors.strain,
            focusedSupportingTextColor = colors.textSecondary,
            unfocusedSupportingTextColor = colors.textSecondary,
        ),
    )
}
