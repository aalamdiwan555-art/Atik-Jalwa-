package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
    val logs by DrClickerController.scanLogs.collectAsState()

    // Local mutable states for input editing with initial values pre-populated
    var minPriceInput by remember { mutableStateOf(currentMinPrice.toString()) }
    var maxPriceInput by remember { 
        mutableStateOf(if (currentMaxPrice == Int.MAX_VALUE) "" else currentMaxPrice.toString()) 
    }
    var maxPickupInput by remember { mutableStateOf(currentMaxPickup.toString()) }
    var maxDropInput by remember { mutableStateOf(currentMaxDrop.toString()) }

    // Neon Styling
    val darkBg = Color(0xFF121214)
    val cardColor = Color(0xFF1E1E22)
    val neonGreen = Color(0xFF39FF14)
    val neonRed = Color(0xFFFF073A)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "FILTER PROTOCOLS", 
                        fontSize = 18.sp, 
                        fontWeight = FontWeight.Black,
                        color = neonGreen,
                        letterSpacing = 1.5.sp
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack, 
                            contentDescription = "Go Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = darkBg,
                    titleContentColor = Color.White
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
                        color = Color.White,
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
                            label = { Text("Payout Min (₹)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.LightGray,
                                focusedBorderColor = neonGreen,
                                unfocusedBorderColor = Color.DarkGray,
                                focusedLabelColor = neonGreen
                            )
                        )

                        OutlinedTextField(
                            value = maxPriceInput,
                            onValueChange = { maxPriceInput = it },
                            label = { Text("Payout Max (₹)") },
                            placeholder = { Text("∞") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.LightGray,
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
                            label = { Text("Max Pickup (KM)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.LightGray,
                                focusedBorderColor = neonGreen,
                                unfocusedBorderColor = Color.DarkGray,
                                focusedLabelColor = neonGreen
                            )
                        )

                        OutlinedTextField(
                            value = maxDropInput,
                            onValueChange = { maxDropInput = it },
                            label = { Text("Max Destination (KM)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.LightGray,
                                focusedBorderColor = neonGreen,
                                unfocusedBorderColor = Color.DarkGray,
                                focusedLabelColor = neonGreen
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // SAVE FILTER RULE TRIGGER
                    Button(
                        onClick = {
                            val parsedMin = minPriceInput.toIntOrNull() ?: 100
                            val parsedMax = if (maxPriceInput.isEmpty()) Int.MAX_VALUE else (maxPriceInput.toIntOrNull() ?: Int.MAX_VALUE)
                            val parsedPickup = maxPickupInput.toFloatOrNull() ?: 5.0f
                            val parsedDrop = maxDropInput.toFloatOrNull() ?: 15.0f

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
                        colors = ButtonDefaults.buttonColors(containerColor = neonGreen, contentColor = Color.Black),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("LOCK FILTER RULES", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Realtime Monitoring Layout Console
            Text(
                text = "Live Layout Activity Logs",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D0F)),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (logs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No scanning logs registered yet.\nEnable 'START SCANNING' inside overlay.",
                            color = Color.Gray,
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
                                    color = Color.LightGray,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = entry.text,
                                    color = if (entry.isMatch) neonGreen else Color.Gray,
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
