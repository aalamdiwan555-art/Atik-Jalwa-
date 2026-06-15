package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import com.example.DrClickerController

// Turbo Cyber Green & High-Voltage Electric Amber Theme (Highly fast & performance-oriented)
private val MagicalDarkColorScheme =
  darkColorScheme(
    primary = Color(0xFF00FF87),       // Supercharging Turbo Neon Green
    secondary = Color(0xFFFFD700),     // High-Voltage Electric Gold / Yellow
    tertiary = Color(0xFF00E5FF),      // Lightning Cyber Blue / Cyan
    background = Color(0xFF060913),    // Rich Obsidian Onyx Deep
    surface = Color(0xFF0F1626),       // Deep Space Starry Steel Card
    onPrimary = Color(0xFF05080E),     // Deep onyx contrast
    onSecondary = Color(0xFF05080E),   // Deep onyx contrast
    onBackground = Color(0xFFECEFF1),  // Soft Starry Ice White Text
    onSurface = Color(0xFFECEFF1),     // Soft Starry Ice White Text
    surfaceVariant = Color(0xFF1E293B),// Starry sky deep velvet variant
    onSurfaceVariant = Color(0xFF00FF87) // Luminous mint highlights
  )

private val MagicalLightColorScheme =
  lightColorScheme(
    primary = Color(0xFF00A254),       // Solid Premium Mint Green
    secondary = Color(0xFFC48B00),     // Polished Amber / Gold
    tertiary = Color(0xFF00838F),      // Dark Cyber Teal
    background = Color(0xFFF1F5F9),    // Crisp Light Slate Gray
    surface = Color(0xFFFFFFFF),       // Clear Aurora White
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF0F172A),  // Deep Slate Obsidian Text
    onSurface = Color(0xFF0F172A),     // Deep Slate Obsidian Text
    surfaceVariant = Color(0xFFE2E8F0),// Container steel border
    onSurfaceVariant = Color(0xFF00A254)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = DrClickerController.isDarkTheme.collectAsState().value,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  // We prefer the magical theme!
  val colorScheme = if (darkTheme) MagicalDarkColorScheme else MagicalLightColorScheme

  MaterialTheme(colorScheme = colorScheme, content = content)
}
