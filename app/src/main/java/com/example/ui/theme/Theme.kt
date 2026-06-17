package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import com.example.DrClickerController

// Elegant high-contrast premium Black and White Monochromatic color scheme
private val MagicalLightColorScheme =
  lightColorScheme(
    primary = Color(0xFFFFFFFF),       // Pure White Accent
    secondary = Color(0xFFE0E0E0),     // Soft White / Pale Silver
    tertiary = Color(0xFF9E9E9E),      // Neutral Charcoal Gray
    background = Color(0xFF000000),    // Pure Deep Black
    surface = Color(0xFF121212),       // Card Surface (Carbon Dark Gray)
    onPrimary = Color(0xFF000000),     // Solid Black Text on White Primary Buttons
    onSecondary = Color(0xFF000000),   // Solid Black Text
    onBackground = Color(0xFFFFFFFF),  // Crisp White Text
    onSurface = Color(0xFFFFFFFF),     // Crisp White Cards Text
    surfaceVariant = Color(0xFF222222),// Dark Charcoal borders
    onSurfaceVariant = Color(0xFFFFFFFF)
  )

private val MagicalDarkColorScheme = MagicalLightColorScheme

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false, // Force Light Mode globally
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  MaterialTheme(colorScheme = MagicalLightColorScheme, content = content)
}
