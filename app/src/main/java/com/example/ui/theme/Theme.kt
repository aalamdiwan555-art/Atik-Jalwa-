package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import com.example.DrClickerController

// Elegant high-contrast premium Slate Midnight & Cyberpunk Emerald color scheme
private val MagicalLightColorScheme =
  lightColorScheme(
    primary = Color(0xFF00FF87),       // Radiant High-Energy Neon Green / Mint
    secondary = Color(0xFF00E5FF),     // Vibrant Neon Cyan / Sky Blue
    tertiary = Color(0xFFD946EF),      // Bright Tech Magenta / Purple Accent
    background = Color(0xFF090D16),    // Super deep premium dark cosmic space navy blue
    surface = Color(0xFF131C2E),       // Rich deep carbon surface card
    onPrimary = Color(0xFF000000),     // Solid Black Text on Primary Neon Green
    onSecondary = Color(0xFF000000),   // Solid Black Text on Secondary Cyan
    onBackground = Color(0xFFF8FAFC),  // Crisp Slate White Text
    onSurface = Color(0xFFF1F5F9),     // Crisp Slate White Card Text
    surfaceVariant = Color(0xFF1E294B),// Deep navy border outline
    onSurfaceVariant = Color(0xFF94A3B8) // Highly visible light slate gray for secondary labels/text
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
