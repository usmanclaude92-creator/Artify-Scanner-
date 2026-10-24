package com.example.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Color

/** Appearance preference: 0 = follow system, 1 = light, 2 = dark. */
object ThemeController {
    val mode = mutableIntStateOf(0)

    fun init(context: Context) {
        mode.intValue = context.getSharedPreferences("artify_ui", Context.MODE_PRIVATE).getInt("theme_mode", 0)
    }

    fun set(context: Context, value: Int) {
        mode.intValue = value
        context.getSharedPreferences("artify_ui", Context.MODE_PRIVATE).edit().putInt("theme_mode", value).apply()
    }
}

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF3B82FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF12336E),
    onPrimaryContainer = Color(0xFFCFE0FF),
    secondary = BrandGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF0F3D2A),
    onSecondaryContainer = Color(0xFFB7F0CF),
    tertiary = BrandOrange,
    background = DarkBackground,
    onBackground = Color(0xFFF1F5FF),
    surface = DarkSurface,
    onSurface = Color(0xFFF1F5FF),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkOutline,
    outlineVariant = DarkOutline,
    error = Color(0xFFFF6B6B)
)

private val LightColorScheme = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = LightSurfaceVariant,
    onPrimaryContainer = Color(0xFF0B3A9A),
    secondary = BrandGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDF6E7),
    onSecondaryContainer = Color(0xFF0B5A2C),
    tertiary = Color(0xFFD97706),
    background = LightBackground,
    onBackground = Color(0xFF0E1B3D),
    surface = LightSurface,
    onSurface = Color(0xFF0E1B3D),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightOutline,
    outlineVariant = LightOutline,
    error = Color(0xFFD92D20)
)

@Composable
fun ArtifyScannerTheme(
    darkTheme: Boolean = when (ThemeController.mode.intValue) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    },
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
