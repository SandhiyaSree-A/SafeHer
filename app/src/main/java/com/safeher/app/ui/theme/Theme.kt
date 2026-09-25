package com.safeher.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = DuskyPinkPrimary,
    onPrimary = Color.White,
    primaryContainer = PalePinkPrimaryContainer,
    onPrimaryContainer = DarkPinkBrownText,
    secondary = DuskyPinkSecondary,
    onSecondary = Color.White,
    secondaryContainer = PalePinkSecondaryContainer,
    onSecondaryContainer = DarkPinkBrownText,
    tertiary = DuskyPinkTertiary,
    onTertiary = Color.White,
    tertiaryContainer = PalePinkTertiaryContainer,
    onTertiaryContainer = DarkPinkBrownText,
    background = PalePinkBackground,
    onBackground = DarkPinkBrownText,
    surface = PalePinkSurface,
    onSurface = DarkPinkBrownText,
    surfaceVariant = PalePinkSurfaceVariant,
    onSurfaceVariant = DarkPinkBrownTextVariant,
    outline = DarkPinkBrownOutline
)

private val DarkColorScheme = darkColorScheme(
    primary = SoftPinkPrimaryDark,
    onPrimary = DarkPinkBrownText,
    primaryContainer = DarkPinkPrimaryContainer,
    onPrimaryContainer = LightPalePinkText,
    secondary = SoftPinkSecondaryDark,
    onSecondary = DarkPinkBrownText,
    secondaryContainer = DarkPinkSecondaryContainer,
    onSecondaryContainer = LightPalePinkText,
    tertiary = SoftPinkPrimaryDark,
    onTertiary = DarkPinkBrownText,
    tertiaryContainer = DarkPinkTertiaryContainer,
    onTertiaryContainer = LightPalePinkText,
    background = DarkPinkBackground,
    onBackground = LightPalePinkText,
    surface = DarkPinkSurface,
    onSurface = LightPalePinkText,
    surfaceVariant = DarkPinkSurfaceVariant,
    onSurfaceVariant = LightPalePinkTextVariant,
    outline = SoftPinkOutlineDark
)

@Composable
fun SafeHerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
