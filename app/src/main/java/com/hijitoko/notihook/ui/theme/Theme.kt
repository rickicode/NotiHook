package com.hijitoko.notihook.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = MintGreenLight,
    secondary = SoftBlueLight,
    tertiary = MintGreen,
    surface = SurfaceDark,
)

private val LightColorScheme = lightColorScheme(
    primary = MintGreen,
    secondary = SoftBlue,
    tertiary = DeepTeal,
    surface = SurfaceLight,
)

@Composable
fun NotiHookTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
