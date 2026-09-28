package com.mersadai.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.mersadai.app.domain.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF087E78),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1F1EA),
    onPrimaryContainer = Color(0xFF003D38),
    secondary = Color(0xFF9A5B00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDDB2),
    background = Color(0xFFF7F8F5),
    surface = Color(0xFFF7F8F5),
    surfaceVariant = Color(0xFFE5EAE6),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF73D5C8),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005047),
    onPrimaryContainer = Color(0xFF91F2E4),
    secondary = Color(0xFFFFB95C),
    onSecondary = Color(0xFF492900),
    secondaryContainer = Color(0xFF693D00),
    background = Color(0xFF101513),
    surface = Color(0xFF101513),
    surfaceVariant = Color(0xFF27312D),
    error = Color(0xFFFFB4AB),
)

@Composable
fun MersadTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val view = LocalView.current
    val window = (LocalContext.current as? Activity)?.window
    val colorScheme = if (useDarkTheme) DarkColors else LightColors
    SideEffect {
        window?.let {
            it.statusBarColor = colorScheme.background.toArgb()
            it.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(it, view).apply {
                isAppearanceLightStatusBars = !useDarkTheme
                isAppearanceLightNavigationBars = !useDarkTheme
            }
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
