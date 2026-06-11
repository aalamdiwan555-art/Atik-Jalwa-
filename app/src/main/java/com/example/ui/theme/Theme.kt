package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = Color(0xFF00FF87),       // Premium Cyber Emerald
    secondary = Color(0xFF00E5FF),     // Cyber Cyan
    tertiary = Color(0xFFFE3B62),      // Rubine Rose
    background = Color(0xFF07080A),    // Obsidian Noir
    surface = Color(0xFF111319),       // Sleek Dark Card
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White
  )

private val LightColorScheme =
  lightColorScheme(
    primary = Color(0xFF00FF87),       // Premium Cyber Emerald
    secondary = Color(0xFF00E5FF),     // Cyber Cyan
    tertiary = Color(0xFFFE3B62),      // Rubine Rose
    background = Color(0xFF07080A),    // Obsidian Noir
    surface = Color(0xFF111319),       // Sleek Dark Card
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Enforce obsidian branding by disabling wallpaper override
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
