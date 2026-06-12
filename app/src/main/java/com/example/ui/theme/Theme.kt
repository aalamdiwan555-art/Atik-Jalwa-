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

private val LightColorScheme =
  lightColorScheme(
    primary = Color(0xFF0066FF),       // Vibrant Tech Blue
    secondary = Color(0xFF0E7090),     // Clean Ocean Teal
    tertiary = Color(0xFFD91E44),      // Clean Rubine Red
    background = Color(0xFFF4F7FB),    // Crisp Light Indigo-Slate
    surface = Color(0xFFFFFFFF),       // Clear Pure White
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF101827),  // Near Black
    onSurface = Color(0xFF101827)      // Near Black
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false, // Disable system dark theme to respect user's explicit light preference
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
