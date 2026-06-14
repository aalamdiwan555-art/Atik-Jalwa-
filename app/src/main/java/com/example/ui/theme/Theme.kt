package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import com.example.DrClickerController

// Cosmic Space / Celestial Midnight Theme
private val MagicalDarkColorScheme =
  darkColorScheme(
    primary = Color(0xFFB55CFF),       // Luminous Astral Violet
    secondary = Color(0xFFFF499E),     // Nebula Neon Pink
    tertiary = Color(0xFF00F5D4),      // Magical Aurora Mint/Cyan
    background = Color(0xFF070014),    // Infinite Deep Space Black
    surface = Color(0xFF120B28),       // Cozy Star-Dust Obsidian Purple
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFFF3E8FF),  // Soft Celestial Lavender Text
    onSurface = Color(0xFFF3E8FF),     // Soft Celestial Lavender Text
    surfaceVariant = Color(0xFF221545),// Starry sky deep velvet variant
    onSurfaceVariant = Color(0xFF00F5D4) // Luminous mint highlights
  )

private val MagicalLightColorScheme =
  lightColorScheme(
    primary = Color(0xFF8B00FF),       // Radiant Royal Violet
    secondary = Color(0xFFE8117F),     // Sweet Orchid Pink
    tertiary = Color(0xFF00A896),      // Aurorabeam Teal
    background = Color(0xFFF9F7FC),    // Cosmic Pearl Pink-White
    surface = Color(0xFFFFFFFF),       // Clear Aurora White
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1B0330),  // Deep Violet Velvet Text
    onSurface = Color(0xFF1B0330),     // Deep Violet Velvet Text
    surfaceVariant = Color(0xFFEDE7F6),// Pearl violet container
    onSurfaceVariant = Color(0xFF8B00FF)
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
