package com.betteraichat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.betteraichat.BetterAIChatApp
import com.betteraichat.core.storage.AccentColor
import com.betteraichat.core.storage.ThemeMode

private val LightColors = lightColorScheme()
private val DarkColors = darkColorScheme()

private val PurpleLight = lightColorScheme(
    primary = Color(0xFF6750A4),
    primaryContainer = Color(0xFFEADDFF)
)
private val PurpleDark = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    primaryContainer = Color(0xFF4F378B)
)
private val GreenLight = lightColorScheme(
    primary = Color(0xFF2E7D32),
    primaryContainer = Color(0xFFB7F0B0)
)
private val GreenDark = darkColorScheme(
    primary = Color(0xFF81C995),
    primaryContainer = Color(0xFF1E5A2E)
)
private val TealLight = lightColorScheme(
    primary = Color(0xFF00796B),
    primaryContainer = Color(0xFFB2DFDB)
)
private val TealDark = darkColorScheme(
    primary = Color(0xFF4DB6AC),
    primaryContainer = Color(0xFF00695C)
)

@Composable
fun BetterAIChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as BetterAIChatApp
    val themeMode = app.container.settings.getThemeMode()
    val accent = app.container.settings.getAccentColor()
    val effectiveDark = when (themeMode) {
        ThemeMode.SYSTEM -> darkTheme
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = when {
        accent != AccentColor.BLUE -> when (accent) {
            AccentColor.PURPLE -> if (effectiveDark) PurpleDark else PurpleLight
            AccentColor.GREEN -> if (effectiveDark) GreenDark else GreenLight
            AccentColor.TEAL -> if (effectiveDark) TealDark else TealLight
            AccentColor.BLUE -> if (effectiveDark) DarkColors else LightColors
        }
        dynamicColor && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S -> {
            if (effectiveDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        effectiveDark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
