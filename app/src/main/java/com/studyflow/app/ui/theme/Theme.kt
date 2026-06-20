package com.studyflow.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    background     = Background,
    surface        = Surface,
    surfaceVariant = SurfaceVariant,
    primary        = Primary,
    onPrimary      = OnPrimary,
    onBackground   = TextPrimary,
    onSurface      = TextPrimary,
    secondary      = Primary,
    onSecondary    = OnPrimary,
)

@Composable
fun StudyFlowTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = Typography,
        content     = content,
    )
}
