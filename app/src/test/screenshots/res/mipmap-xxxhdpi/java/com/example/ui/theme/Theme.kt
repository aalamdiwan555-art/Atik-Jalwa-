package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import com.example.DrClickerController

private val LightColorScheme =
  lightColorScheme(
    primary = Color(0xFF5D4037),       // Rich Espresso Cocoa Brown
    secondary = Color(0xFF8D6E63),     // Soft Milk Chocolate Brown
    tertiary = Color(0xFF8C6239),      // Elegant Warm Tan Bronze
    background = Color(0xFFFAF6F0),    // Warm Soft Ivory / Cream White
    surface = Color(0xFFFFFFFF),       // Clear Pure White
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF2D2421),  // Dark Roasted Coffee Charcoal
    onSurface = Color(0xFF2D2421)      // Dark Roasted Coffee Charcoal
  )

private val DarkColorScheme =
  darkColorScheme(
    primary = Color(0xFFD7CCC8),       // Soft Milky Hazelnut Cream
    secondary = Color(0xFFA1887F),     // Velvet Cocoa Chocolate Muted
    tertiary = Color(0xFFD7A15C),      // Soft Luminous Amber Gold Accent
    background = Color(0xFF13100E),    // Elegant Deep Eye-Safe Espresso Onyx
    surface = Color(0xFF1F1B18),       // Cozy Night Coffee Lounge Slate
    onPrimary = Color(0xFF3E2723),     // Darkest Coffee Text on Primary
    onSecondary = Color(0xFF1F1210),   // Darkest Night on Secondary
    onBackground = Color(0xFFEFEBE9),  // Delicate warm Latte Cream Text
    onSurface = Color(0xFFEFEBE9),     // Warm Latte Cream Text
    surfaceVariant = Color(0xFF2B2220),// Deeper variant background
    onSurfaceVariant = Color(0xFFD7CCC8)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = DrClickerController.isDarkTheme.collectAsState().value,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

