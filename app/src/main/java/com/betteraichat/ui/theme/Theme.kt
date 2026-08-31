package com.betteraichat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B)
)
private val GreenLight = lightColorScheme(
    primary = Color(0xFF2E7D32),
    primaryContainer = Color(0xFFB7F0B0)
)
private val GreenDark = darkColorScheme(
    primary = Color(0xFF81C995),
    onPrimary = Color(0xFF06300F),
    primaryContainer = Color(0xFF1E5A2E)
)
private val TealLight = lightColorScheme(
    primary = Color(0xFF00796B),
    primaryContainer = Color(0xFFB2DFDB)
)
private val TealDark = darkColorScheme(
    primary = Color(0xFF4DB6AC),
    onPrimary = Color(0xFF003732),
    primaryContainer = Color(0xFF00695C)
)
private val BlueLight = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3E),
    secondary = Color(0xFF565F71),
    secondaryContainer = Color(0xFFDAE2F9),
    onSecondaryContainer = Color(0xFF131C2B)
)
private val BlueDark = darkColorScheme(
    primary = Color(0xFFA6C8FF),
    onPrimary = Color(0xFF00305E),
    primaryContainer = Color(0xFF004A8C),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFBEC6DC),
    secondaryContainer = Color(0xFF3E4759),
    onSecondaryContainer = Color(0xFFDAE2F9)
)
private val OrangeLight = lightColorScheme(
    primary = Color(0xFFBF360C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9C0),
    onPrimaryContainer = Color(0xFF5A1E00),
    secondary = Color(0xFF9C4A3A),
    secondaryContainer = Color(0xFFFFDBD0),
    onSecondaryContainer = Color(0xFF3F0B01),
    tertiary = Color(0xFFB24A00)
)
private val OrangeDark = darkColorScheme(
    primary = Color(0xFFFFB74D),
    onPrimary = Color(0xFF4A2400),
    primaryContainer = Color(0xFF7A4000),
    onPrimaryContainer = Color(0xFFFFDDB2),
    secondary = Color(0xFFFFB4A0),
    secondaryContainer = Color(0xFF762D21),
    onSecondaryContainer = Color(0xFFFFDBD0)
)
private val RedLight = lightColorScheme(
    primary = Color(0xFFC62828),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF410002),
    secondary = Color(0xFF9A3A2E),
    secondaryContainer = Color(0xFFFFDAD3),
    onSecondaryContainer = Color(0xFF3E0700)
)
private val RedDark = darkColorScheme(
    primary = Color(0xFFFFB4AB),
    onPrimary = Color(0xFF690005),
    primaryContainer = Color(0xFF93000A),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFFFFB5A5),
    secondaryContainer = Color(0xFF7C2D1E),
    onSecondaryContainer = Color(0xFFFFDAD3)
)
private val PinkLight = lightColorScheme(
    primary = Color(0xFFD81B60),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E2),
    onPrimaryContainer = Color(0xFF3F001D),
    secondary = Color(0xFF8A4A5A),
    secondaryContainer = Color(0xFFFFD9E0),
    onSecondaryContainer = Color(0xFF390718)
)
private val PinkDark = darkColorScheme(
    primary = Color(0xFFFFB0C8),
    onPrimary = Color(0xFF5E1130),
    primaryContainer = Color(0xFF8E2351),
    onPrimaryContainer = Color(0xFFFFD9E2),
    secondary = Color(0xFFE5BDC6),
    secondaryContainer = Color(0xFF663441),
    onSecondaryContainer = Color(0xFFFFD9E0)
)
private val IndigoLight = lightColorScheme(
    primary = Color(0xFF3949AB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF00105C),
    secondary = Color(0xFF5A5D72),
    secondaryContainer = Color(0xFFDEE1F9),
    onSecondaryContainer = Color(0xFF171B2C)
)
private val IndigoDark = darkColorScheme(
    primary = Color(0xFFB9C4FF),
    onPrimary = Color(0xFF002187),
    primaryContainer = Color(0xFF2536A0),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFFC2C5DC),
    secondaryContainer = Color(0xFF424659),
    onSecondaryContainer = Color(0xFFDEE1F9)
)

@Composable
fun BetterAIChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as BetterAIChatApp
    val themeTick by app.container.themeVersion.collectAsState()
    val themeMode = app.container.settings.getThemeMode()
    val accent = app.container.settings.getAccentColor()
    val effectiveDark = when (themeMode) {
        ThemeMode.SYSTEM -> darkTheme
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = remember(accent, effectiveDark, themeTick) {
    when (accent) {
        AccentColor.ORANGE -> if (effectiveDark) OrangeDark else OrangeLight
        AccentColor.RED -> if (effectiveDark) RedDark else RedLight
        AccentColor.PINK -> if (effectiveDark) PinkDark else PinkLight
        AccentColor.INDIGO -> if (effectiveDark) IndigoDark else IndigoLight
        AccentColor.PURPLE -> if (effectiveDark) PurpleDark else PurpleLight
        AccentColor.GREEN -> if (effectiveDark) GreenDark else GreenLight
        AccentColor.TEAL -> if (effectiveDark) TealDark else TealLight
        AccentColor.BLUE -> if (effectiveDark) BlueDark else BlueLight
    }
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
