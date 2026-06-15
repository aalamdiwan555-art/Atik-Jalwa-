package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import com.example.DrClickerController

// Crisp clean high-contrast Light mode with White background and Black text
private val MagicalLightColorScheme =
  lightColorScheme(
    primary = Color(0xFF00A254),       // Premium Forest Green
    secondary = Color(0xFFC48B00),     // Amber Accent
    tertiary = Color(0xFF00838F),      // Deep Cyber Teal
    background = Color(0xFFFFFFFF),    // Pure Clean White Background
    surface = Color(0xFFF1F5F9),       // Light Elegant Card Surface
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF000000),  // Sharp Black Text
    onSurface = Color(0xFF000000),     // Sharp Black Text
    surfaceVariant = Color(0xFFE2E8F0),// Slate steel borders
    onSurfaceVariant = Color(0xFF00A254)
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
