package com.example.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = MippediaDarkPrimary,
    onPrimary = MippediaDarkOnPrimary,
    primaryContainer = MippediaBlueDark,
    onPrimaryContainer = MippediaBlueLight,
    secondary = MippediaTealAccent,
    background = MippediaDarkBackground,
    surface = MippediaDarkSurface,
    surfaceVariant = MippediaDarkSurfaceVariant,
    onBackground = MippediaDarkOnSurface,
    onSurface = MippediaDarkOnSurface,
    outline = MippediaDarkOutline
)

private val LightColorScheme = lightColorScheme(
    primary = MippediaBluePrimary,
    onPrimary = Color.White,
    primaryContainer = MippediaBlueLight,
    onPrimaryContainer = MippediaBlueDark,
    secondary = MippediaTealAccent,
    background = MippediaSurface,
    surface = Color.White,
    surfaceVariant = MippediaSurfaceVariant,
    onBackground = MippediaOnSurface,
    onSurface = MippediaOnSurface,
    outline = MippediaOutline
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep Mippedia's signature clean encyclopedia styling
    content: @Composable () -> Unit,
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
