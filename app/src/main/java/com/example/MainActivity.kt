package com.example

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AtikJalwaController.initialize(this)

        setContent {
            MyApplicationTheme {
                MainDashboardScreen(
                    onOpenSettings = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    },
                    onTriggerOverlayService = { start ->
                        val serviceIntent = Intent(this, FloatingOverlayService::class.java)
                        if (start) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                startForegroundService(serviceIntent)
                            } else {
                                startService(serviceIntent)
                            }
                        } else {
                            stopService(serviceIntent)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun MainDashboardScreen(
    onOpenSettings: () -> Unit,
    onTriggerOverlayService: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var hasOverlayPermission by remember { mutableStateOf(false) }
    var hasAccessibilityPermission by remember { mutableStateOf(false) }
    var isOverlayServiceRunning by remember { mutableStateOf(false) }

    // Recheck permissions with client-side polling on lifecycle triggers/active layouts
    LaunchedEffect(Unit) {
        while(true) {
            hasOverlayPermission = Settings.canDrawOverlays(context)
            hasAccessibilityPermission = isAccessibilityEnabled(context, AtikJalwaAccessibilityService::class.java)
            delay(1000)
        }
    }

    // Neon Accents Vibe
    val darkBg = Color(0xFF101012)
    val cardBg = Color(0xFF1A1A1E)
    val neonGreen = Color(0xFF39FF14)
    val neonRed = Color(0xFFFF073A)

    Scaffold(
        containerColor = darkBg,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            AtikJalwaBrandLogo(modifier = Modifier.size(110.dp))

            Spacer(modifier = Modifier.height(14.dp))

            // Brand Title / Headline Logo
            Text(
                text = "ATIK JALWA",
                fontSize = 28.sp,
                color = neonGreen,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Ergonomic Driver Automation",
                fontSize = 11.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            // Permissions Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Required Permissions Authorization",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // 1. Overlay Permission Status Line
                    PermissionRow(
                        title = "Draw Over Other Apps (Overlay)",
                        desc = "Needed to render the floating control panel above delivery maps",
                        isAuthorized = hasOverlayPermission,
                        onRequest = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 2. Accessibility Service Permission Status Line
                    PermissionRow(
                        title = "Atik Jalwa Accessibility Service",
                        desc = "Required to analyze incoming offer cards and dispatch gestures",
                        isAuthorized = hasAccessibilityPermission,
                        onRequest = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                            Toast.makeText(
                                context,
                                "Navigate to 'Atik Jalwa' in installed services and switch it ON",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Service Master Panel
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Service Orchestration Desk",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .align(Alignment.Start)
                            .padding(bottom = 14.dp)
                    )

                    if (!hasOverlayPermission || !hasAccessibilityPermission) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0x11FF073A), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error",
                                tint = neonRed,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Please enable both permissions above to activate service toggle controls.",
                                color = Color.LightGray,
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        // All systems green! Trigger toggle action
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    isOverlayServiceRunning = true
                                    onTriggerOverlayService(true)
                                    Toast.makeText(context, "Hovering Overlay Panel Active!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = neonGreen, contentColor = Color.Black),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("SHOW OVERLAY", fontSize = 11.sp, fontWeight = FontWeight.Black)
                            }

                            Button(
                                onClick = {
                                    isOverlayServiceRunning = false
                                    onTriggerOverlayService(false)
                                    Toast.makeText(context, "Overlay Panel Deactivated", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray, contentColor = Color.White),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("HIDE OVERLAY", fontSize = 11.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Navigation Button to filters
            Button(
                onClick = onOpenSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .border(1.dp, neonGreen, RoundedCornerShape(10.dp)),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = neonGreen),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("CONFIGURE FILTER CRITERIA", fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Information details footer explaining coordinates logic
            Text(
                text = "SYSTEM ENGINE CONVENTIONS\n" +
                        "• Dynamic reactions vary between 195ms and 440ms.\n" +
                        "• Bounding coordinates randomized to the inner 70% inner zone.\n" +
                        "• Glide transitions are solved using 20 Bezier steps.",
                fontSize = 10.sp,
                color = Color.Gray,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Start,
                lineHeight = 15.sp,
                modifier = Modifier.align(Alignment.Start)
            )
        }
    }
}

@Composable
fun PermissionRow(
    title: String,
    desc: String,
    isAuthorized: Boolean,
    onRequest: () -> Unit
) {
    val neonGreen = Color(0xFF39FF14)
    val neonRed = Color(0xFFFF073A)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1.3f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = desc,
                color = Color.Gray,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        if (isAuthorized) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0x3339FF14)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Authorized",
                    tint = neonGreen,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else {
            Button(
                onClick = onRequest,
                modifier = Modifier.height(30.dp),
                colors = ButtonDefaults.buttonColors(containerColor = neonRed, contentColor = Color.White),
                shape = RoundedCornerShape(6.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
            ) {
                Text("RESTORE", fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

/**
 * Utility checks if accessibility service is fully registered.
 */
fun isAccessibilityEnabled(context: Context, service: Class<out AccessibilityService>): Boolean {
    val settingValue = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    val expectedId = android.content.ComponentName(context, service).flattenToString()
    return settingValue.split(":").any { it.equals(expectedId, ignoreCase = true) }
}

@Composable
fun AtikJalwaBrandLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val cx = width / 2f
        val cy = height / 2f
        val radius = width.coerceAtMost(height) / 2f

        // Outer neon glow circle ring (Cyber-shield style) - Cyan/Neon Teal tint
        drawCircle(
            color = Color(0xFF00E5FF),
            radius = radius * 0.72f,
            style = Stroke(width = 6f)
        )

        // Subtle concentric neon halo
        drawCircle(
            color = Color(0x3300E5FF),
            radius = radius * 0.88f,
            style = Stroke(width = 3f)
        )

        // 4 High-tech Crosshairs/TICKS lines referencing targeting guidance
        val tickLen = radius * 0.12f
        val startGap = radius * 0.78f
        val endGap = startGap + tickLen
        
        // Verticals/Horizontals
        drawLine(color = Color(0xFF00E5FF), start = androidx.compose.ui.geometry.Offset(cx, cy - startGap), end = androidx.compose.ui.geometry.Offset(cx, cy - endGap), strokeWidth = 5f)
        drawLine(color = Color(0xFF00E5FF), start = androidx.compose.ui.geometry.Offset(cx, cy + startGap), end = androidx.compose.ui.geometry.Offset(cx, cy + endGap), strokeWidth = 5f)
        drawLine(color = Color(0xFF00E5FF), start = androidx.compose.ui.geometry.Offset(cx - startGap, cy), end = androidx.compose.ui.geometry.Offset(cx - endGap, cy), strokeWidth = 5f)
        drawLine(color = Color(0xFF00E5FF), start = androidx.compose.ui.geometry.Offset(cx + startGap, cy), end = androidx.compose.ui.geometry.Offset(cx + endGap, cy), strokeWidth = 5f)

        // Inner Targeting Scope Circle
        drawCircle(
            color = Color(0xFF00E5FF),
            radius = radius * 0.42f,
            style = Stroke(width = 4f)
        )
        drawCircle(
            color = Color(0x1F00E5FF),
            radius = radius * 0.42f
        )

        // Yellow-Orange Dual Lightning Action Strike (Accept Strike)
        val boltPath = Path().apply {
            moveTo(cx + radius * 0.40f, cy - radius * 0.45f) // top-right peak
            lineTo(cx - radius * 0.18f, cy + radius * 0.10f) // middle inflection point
            lineTo(cx + radius * 0.06f, cy + radius * 0.10f) // barb step
            lineTo(cx - radius * 0.40f, cy + radius * 0.55f) // bottom-left tail
            lineTo(cx - radius * 0.20f, cy - radius * 0.05f) // back step
            lineTo(cx - radius * 0.44f, cy - radius * 0.05f) // back wing
            close()
        }

        drawPath(
            path = boltPath,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFFFD600), Color(0xFFFF6D00)),
                start = androidx.compose.ui.geometry.Offset(cx - radius * 0.40f, cy + radius * 0.55f),
                end = androidx.compose.ui.geometry.Offset(cx + radius * 0.40f, cy - radius * 0.45f)
            )
        )

        // Central White Target Confirmation Checkmark Path
        val checkPath = Path().apply {
            moveTo(cx - radius * 0.12f, cy)
            lineTo(cx - radius * 0.02f, cy + radius * 0.10f)
            lineTo(cx + radius * 0.14f, cy - radius * 0.12f)
        }
        drawPath(
            path = checkPath,
            color = Color.White,
            style = Stroke(width = 7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

