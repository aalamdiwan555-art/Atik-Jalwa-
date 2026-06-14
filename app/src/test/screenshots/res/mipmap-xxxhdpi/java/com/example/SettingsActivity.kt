package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                SettingsScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    
    // Core parameters tracked inside in-memory controllers
    val currentMinPrice by DrClickerController.minPrice.collectAsState()
    val currentMaxPrice by DrClickerController.maxPrice.collectAsState()
    val currentMaxPickup by DrClickerController.maxPickupDistance.collectAsState()
    val currentMaxDrop by DrClickerController.maxDropDistance.collectAsState()
    val currentSpeedMode by DrClickerController.speedMode.collectAsState()
    val logs by DrClickerController.scanLogs.collectAsState()
    val currentMinPricePerKm by DrClickerController.minPricePerKm.collectAsState()
    val currentClickInterval by DrClickerController.clickInterval.collectAsState()
    val currentRandomJitter by DrClickerController.randomJitter.collectAsState()

    // Local mutable states for input editing with initial values pre-populated
    var minPriceInput by remember { mutableStateOf(currentMinPrice.toString()) }
    var maxPriceInput by remember { 
        mutableStateOf(if (currentMaxPrice == Int.MAX_VALUE) "" else currentMaxPrice.toString()) 
    }
    var maxPickupInput by remember { mutableStateOf(currentMaxPickup.toString()) }
    var maxDropInput by remember { mutableStateOf(currentMaxDrop.toString()) }

    val (savedApiKey, savedProjectId, savedAppId) = remember { AuthManager.getDynamicFirebaseConfig() }
    var firebaseApiKey by remember { mutableStateOf(savedApiKey) }
    var firebaseProjectId by remember { mutableStateOf(savedProjectId) }
    var firebaseAppId by remember { mutableStateOf(savedAppId) }
    var isFirebaseExpanded by remember { mutableStateOf(false) }

    // High-contrast Styling (Adapts dynamically to Light/Dark themes)
    val darkBg = MaterialTheme.colorScheme.background
    val cardColor = MaterialTheme.colorScheme.surface
    val neonGreen = MaterialTheme.colorScheme.tertiary
    val neonRed = Color(0xFFFF073A)
    val textBlueMain = MaterialTheme.colorScheme.primary
    val textBlueCard = MaterialTheme.colorScheme.primary
    val textBlueMuted = MaterialTheme.colorScheme.secondary

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "FILTER PROTOCOLS", 
                        fontSize = 18.sp, 
                        fontWeight = FontWeight.Black,
                        color = textBlueMain,
                        letterSpacing = 1.5.sp
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack, 
                            contentDescription = "Go Back",
                            tint = textBlueMain
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = darkBg,
                    titleContentColor = textBlueMain
                )
            )
        },
        containerColor = darkBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // MODULE 3: PRECISE DATA FILTERING SETTINGS
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardColor),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Threshold Rules Configuration",
                        color = textBlueCard,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Row 1: Payout Minimum & Maximum
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = minPriceInput,
                            onValueChange = { minPriceInput = it },
                            label = { Text("Payout Min (₹)", color = textBlueMuted) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = textBlueCard,
                                unfocusedTextColor = textBlueMuted,
                                focusedBorderColor = neonGreen,
                                unfocusedBorderColor = Color.DarkGray,
                                focusedLabelColor = neonGreen
                            )
                        )

                        OutlinedTextField(
                            value = maxPriceInput,
                            onValueChange = { maxPriceInput = it },
                            label = { Text("Payout Max (₹)", color = textBlueMuted) },
                            placeholder = { Text("∞", color = textBlueMuted) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = textBlueCard,
                                unfocusedTextColor = textBlueMuted,
                                focusedBorderColor = neonGreen,
                                unfocusedBorderColor = Color.DarkGray,
                                focusedLabelColor = neonGreen
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Row 2: Pickup proximity & Trip Distance
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = maxPickupInput,
                            onValueChange = { maxPickupInput = it },
                            label = { Text("Max Pickup (KM)", color = textBlueMuted) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = textBlueCard,
                                unfocusedTextColor = textBlueMuted,
                                focusedBorderColor = neonGreen,
                                unfocusedBorderColor = Color.DarkGray,
                                focusedLabelColor = neonGreen
                            )
                        )

                        OutlinedTextField(
                            value = maxDropInput,
                            onValueChange = { maxDropInput = it },
                            label = { Text("Max Destination (KM)", color = textBlueMuted) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = textBlueCard,
                                unfocusedTextColor = textBlueMuted,
                                focusedBorderColor = neonGreen,
                                unfocusedBorderColor = Color.DarkGray,
                                focusedLabelColor = neonGreen
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // SPEED MODE CONFIGURATION ROW
                    Text(
                        text = "Auto-Click Speed Selection (Faster Reaction)",
                        color = textBlueCard,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            Triple("INSTANT", "⚡ INSTANT", Color(0xFF5D4037)),
                            Triple("ANTIBAN", "🛡️ ANTI-BAN", Color(0xFF8D6E63)),
                            Triple("HUMAN", "👤 HUMAN", Color(0xFF8C6239))
                        ).forEach { (modeCode, label, selectColor) ->
                            val isSelected = currentSpeedMode == modeCode
                            Button(
                                onClick = { 
                                    DrClickerController.updateSpeedMode(modeCode)
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) selectColor else Color(0xFFEDE8E5),
                                    contentColor = if (isSelected) Color.White else textBlueMuted
                                )
                            ) {
                                Text(
                                    text = label, 
                                    fontSize = 10.sp, 
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // SAVE FILTER RULE TRIGGER
                    Button(
                        onClick = {
                            val parsedMin = minPriceInput.toIntOrNull() ?: 0
                            val parsedMax = if (maxPriceInput.isEmpty()) Int.MAX_VALUE else (maxPriceInput.toIntOrNull() ?: Int.MAX_VALUE)
                            val parsedPickup = maxPickupInput.toFloatOrNull() ?: 15.0f
                            val parsedDrop = maxDropInput.toFloatOrNull() ?: 30.0f

                            if (parsedMin < 0 || parsedPickup < 0 || parsedDrop < 0) {
                                Toast.makeText(context, "Negative numbers are illegal", Toast.LENGTH_LONG).show()
                                return@Button
                            }

                            // Commit to central SharedPreferences via Controller
                            DrClickerController.updateMinPrice(parsedMin)
                            DrClickerController.updateMaxPrice(parsedMax)
                            DrClickerController.updateMaxPickupDistance(parsedPickup)
                            DrClickerController.updateMaxDropDistance(parsedDrop)

                            Toast.makeText(context, "Filter criteria locked successfully!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = neonGreen, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("LOCK FILTER RULES", fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // NEW MODULE: ADVANCED MACRO DRIVER FILTER RULES
            val isDarkThemeMode by DrClickerController.isDarkTheme.collectAsState()
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardColor),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Advanced Clicking & Fare Optimization",
                        color = textBlueCard,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Text(
                        text = "Customize specialized criteria utilized by professional drivers to isolate highest-paying tasks.",
                        color = textBlueMuted,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // 1. Min Price Per KM Filter (Slider)
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Minimum Rate Target",
                                color = textBlueCard,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (currentMinPricePerKm <= 0.5f) "No Min Rate (Off)" else "₹${String.format(java.util.Locale.US, "%.1f", currentMinPricePerKm)} / KM",
                                color = neonGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        androidx.compose.material3.Slider(
                            value = currentMinPricePerKm,
                            onValueChange = { DrClickerController.updateMinPricePerKm(it) },
                            valueRange = 0f..50f,
                            colors = androidx.compose.material3.SliderDefaults.colors(
                                thumbColor = neonGreen,
                                activeTrackColor = neonGreen,
                                inactiveTrackColor = if (isDarkThemeMode) Color(0xFF2B2220) else Color(0xFFEDE8E5)
                            )
                        )
                        Text(
                            "Filters out offers where fare-to-distance ratio is lower than target (e.g. ₹15/KM).",
                            color = textBlueMuted,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                    }

                    // 2. Click Inter-Pulse Rate (ms) (Slider)
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Click Tap-Interval Rate",
                                color = textBlueCard,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${currentClickInterval} ms",
                                color = neonGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        androidx.compose.material3.Slider(
                            value = currentClickInterval.toFloat(),
                            onValueChange = { DrClickerController.updateClickInterval(it.toInt()) },
                            valueRange = 50f..1000f,
                            colors = androidx.compose.material3.SliderDefaults.colors(
                                thumbColor = neonGreen,
                                activeTrackColor = neonGreen,
                                inactiveTrackColor = if (isDarkThemeMode) Color(0xFF2B2220) else Color(0xFFEDE8E5)
                            )
                        )
                        Text(
                            "The delay between sequential tap events inside active target apps.",
                            color = textBlueMuted,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                    }

                    // 3. Coordinate Jitter Radius (px) (Slider)
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Visual Anti-Ban Coordinate Jitter",
                                color = textBlueCard,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "±${currentRandomJitter} px",
                                color = neonGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        androidx.compose.material3.Slider(
                            value = currentRandomJitter.toFloat(),
                            onValueChange = { DrClickerController.updateRandomJitter(it.toInt()) },
                            valueRange = 0f..50f,
                            colors = androidx.compose.material3.SliderDefaults.colors(
                                thumbColor = neonGreen,
                                activeTrackColor = neonGreen,
                                inactiveTrackColor = if (isDarkThemeMode) Color(0xFF2B2220) else Color(0xFFEDE8E5)
                            )
                        )
                        Text(
                            "Adds random pixel offset bounds to trigger locations to confuse automated gesture block scripts.",
                            color = textBlueMuted,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // MODULE: VISUAL COMFORT & NIGHT SHIFT MODE
            val isDarkTheme by DrClickerController.isDarkTheme.collectAsState()
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardColor),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🌙 Night Shift Theme Mode",
                            color = textBlueMain,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Reduces glare and active eye strain for partners working late during night shifts.",
                            color = textBlueMuted,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    androidx.compose.material3.Switch(
                        checked = isDarkTheme,
                        onCheckedChange = { DrClickerController.updateDarkTheme(it) },
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF8C6239), // Bronze Accent
                            uncheckedThumbColor = textBlueMuted,
                            uncheckedTrackColor = Color(0xFFEDE8E5)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // MODULE: FIREBASE CONFIGURATION GATEWAY
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardColor),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Firebase Auth Registry",
                                color = textBlueCard,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            if (AuthManager.isFirebaseActive()) Color(0xFF4CAF50) else Color(0xFFFF5722),
                                            CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (AuthManager.isFirebaseActive()) "Status: Live Realtime Auth" else "Status: Local Simulated Mode",
                                    color = if (AuthManager.isFirebaseActive()) Color(0xFF2E7D32) else Color(0xFFD84315),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Button(
                            onClick = { isFirebaseExpanded = !isFirebaseExpanded },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isFirebaseExpanded) neonGreen.copy(alpha = 0.15f) else Color(0xFFEDE8E5),
                                contentColor = textBlueMain
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (isFirebaseExpanded) "Collapse" else "Configure",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (isFirebaseExpanded) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "To run with real cloud authentication, specify your Google Firebase credentials. Alternatively, configure them securely inside your AI Studio Secrets panel using: FIREBASE_API_KEY, FIREBASE_PROJECT_ID, FIREBASE_APP_ID.",
                            fontSize = 10.sp,
                            color = textBlueMuted,
                            lineHeight = 14.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = firebaseApiKey,
                            onValueChange = { firebaseApiKey = it },
                            label = { Text("Firebase API Key", color = textBlueMuted) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = textBlueCard,
                                unfocusedTextColor = textBlueMuted,
                                focusedBorderColor = neonGreen,
                                unfocusedBorderColor = Color.DarkGray,
                                focusedLabelColor = neonGreen
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = firebaseProjectId,
                                onValueChange = { firebaseProjectId = it },
                                label = { Text("Project ID", color = textBlueMuted) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = textBlueCard,
                                    unfocusedTextColor = textBlueMuted,
                                    focusedBorderColor = neonGreen,
                                    unfocusedBorderColor = Color.DarkGray,
                                    focusedLabelColor = neonGreen
                                )
                            )

                            OutlinedTextField(
                                value = firebaseAppId,
                                onValueChange = { firebaseAppId = it },
                                label = { Text("Application ID", color = textBlueMuted) },
                                modifier = Modifier.weight(1.2f),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = textBlueCard,
                                    unfocusedTextColor = textBlueMuted,
                                    focusedBorderColor = neonGreen,
                                    unfocusedBorderColor = Color.DarkGray,
                                    focusedLabelColor = neonGreen
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                if (firebaseApiKey.trim().isEmpty() || firebaseProjectId.trim().isEmpty() || firebaseAppId.trim().isEmpty()) {
                                    Toast.makeText(context, "All configuration parameters must be set!", Toast.LENGTH_LONG).show()
                                    return@Button
                                }
                                val ok = AuthManager.saveDynamicFirebaseConfig(
                                    firebaseApiKey,
                                    firebaseProjectId,
                                    firebaseAppId
                                )
                                if (ok) {
                                    Toast.makeText(context, "Firebase dynamically locked and initialized!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Failed to connect! Ensure credentials are correct.", Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = neonGreen, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("SAVE & INITIALIZE CLOUD AUTH", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Realtime Monitoring Layout Console
            Text(
                text = "Live Layout Activity Logs",
                color = textBlueMain,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(containerColor = cardColor),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (logs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No scanning logs registered yet.\nEnable 'START SCANNING' inside overlay.",
                            color = textBlueMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    ) {
                        items(logs) { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "[${entry.timestamp}] ",
                                    color = textBlueMuted,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = entry.text,
                                    color = if (entry.isMatch) Color(0xFF39FF14) else textBlueCard,
                                    fontSize = 10.sp,
                                    fontWeight = if (entry.isMatch) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
