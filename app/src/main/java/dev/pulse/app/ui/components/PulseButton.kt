package dev.pulse.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.pulse.app.ui.theme.PulseTheme

private val ButtonShape = RoundedCornerShape(16.dp)
private val ButtonPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)

/** Главная кнопка действия (заливка акцентным цветом). */
@Composable
fun PulseButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = ButtonShape,
        contentPadding = ButtonPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = PulseTheme.colors.strain,
            contentColor = Color(0xFF06101F),
        ),
    ) {
        Text(text = text, fontWeight = FontWeight.SemiBold)
    }
}

/** Второстепенная кнопка (только обводка). */
@Composable
fun PulseOutlinedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = ButtonShape,
        contentPadding = ButtonPadding,
        border = BorderStroke(1.dp, PulseTheme.colors.glassBorder),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = PulseTheme.colors.textPrimary),
    ) {
        Text(text = text, fontWeight = FontWeight.Medium)
    }
}
