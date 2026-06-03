package com.example.appmobile.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val EmoGardenShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(22.dp)
)

private val DarkColorScheme = darkColorScheme(
    primary = EmoGardenSkyDark,
    onPrimary = Color(0xFF052A4A),
    primaryContainer = Color(0xFF1E344D),
    onPrimaryContainer = Color(0xFFEAF7FF),
    secondary = Color(0xFF9DD4FF),
    onSecondary = Color(0xFF052A4A),
    secondaryContainer = EmoGardenDarkSurfaceSoft,
    onSecondaryContainer = Color(0xFFEAF7FF),
    tertiary = Color(0xFF8FD29D),
    onTertiary = Color(0xFF12351D),
    background = EmoGardenDarkBackground,
    onBackground = Color(0xFFEAF7FF),
    surface = EmoGardenDarkSurface,
    onSurface = Color(0xFFEAF7FF),
    surfaceVariant = EmoGardenDarkSurfaceSoft,
    onSurfaceVariant = Color(0xFFC2CBD7),
    outline = EmoGardenDarkBorder,
    error = Color(0xFFFF8A9B),
    onError = Color(0xFF4A101A)
)

private val LightColorScheme = lightColorScheme(
    primary = EmoGardenBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEEFF),
    onPrimaryContainer = EmoGardenNavy,
    secondary = Color(0xFF3B82B8),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEAF2FF),
    onSecondaryContainer = EmoGardenNavy,
    tertiary = EmoGardenGreen,
    onTertiary = Color.White,
    background = EmoGardenBackground,
    onBackground = EmoGardenNavy,
    surface = Color.White,
    onSurface = EmoGardenNavy,
    surfaceVariant = EmoGardenSurfaceSoft,
    onSurfaceVariant = Color(0xFF64748B),
    outline = EmoGardenBorder,
    error = Color(0xFFDC4C64),
    onError = Color.White
)

@Composable
fun AppMobileTheme(
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
        typography = AppTypography,
        shapes = EmoGardenShapes,
        content = content
    )
}
