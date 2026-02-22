package com.kilu.pocketagent.core.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = Color(0xFFE0D0FF),
    secondary = Secondary,
    onSecondary = Color(0xFF003822),
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = Color(0xFFA0F0D0),
    tertiary = Tertiary,
    tertiaryContainer = TertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = ErrorColor,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = Color(0xFF21005D),
    secondary = SecondaryDark,
    onSecondary = Color.White,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = Color(0xFF002114),
    tertiary = Color(0xFF2196F3),
    tertiaryContainer = TertiaryContainerLight,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    error = ErrorColorLight,
    onError = Color.White
)

@Composable
fun KiluTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = KiluTypography,
        content = content
    )
}
