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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        DrClickerController.initialize(this)
        AuthManager.initialize(this)

        setContent {
            MyApplicationTheme {
                AuthGateOrDashboardScreen(
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
fun AuthGateOrDashboardScreen(
    onOpenSettings: () -> Unit,
    onTriggerOverlayService: (Boolean) -> Unit
) {
    val currentUser by AuthManager.currentUser.collectAsState()
    var showAdminPanelGlobal by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        val user = currentUser
        if (user == null) {
            DrClickerAuthScreen()
        } else if (user.role == "ADMIN") {
            AdminDashboardPage(
                user = user,
                onSignOut = { AuthManager.signOut() }
            )
        } else {
            MainDashboardScreen(
                user = user,
                onOpenSettings = onOpenSettings,
                onTriggerOverlayService = onTriggerOverlayService,
                onSignOut = { AuthManager.signOut() },
                onToggleAdminPanel = { showAdminPanelGlobal = !showAdminPanelGlobal }
            )
        }

        // Global access to Debug/Dev Panel dynamically restricted to verified admins only
        if (currentUser?.role == "ADMIN") {
            IconButton(
                onClick = { showAdminPanelGlobal = true },
                modifier = Modifier
                    .safeDrawingPadding()
                    .padding(12.dp)
                    .size(42.dp)
                    .background(Color(0xFF111319), CircleShape)
                    .align(Alignment.TopEnd)
                    .border(1.dp, Color(0xFF00FF87), CircleShape)
            ) {
                Text("⚙️", fontSize = 16.sp)
            }
        }

        if (showAdminPanelGlobal && currentUser?.role == "ADMIN") {
            DevAdminPanel(onDismiss = { showAdminPanelGlobal = false })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrClickerAuthScreen() {
    val context = LocalContext.current
    var isLoginTab by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val neonGreen = Color(0xFF00FF87)
    val darkBg = Color(0xFF07080A)
    val cardBg = Color(0xFF111319)

    Scaffold(
        containerColor = darkBg,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            DrClickerBrandLogo(
                modifier = Modifier.size(100.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "DR.CLICKER",
                fontSize = 28.sp,
                color = neonGreen,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp
            )
            Text(
                text = "DRIVER DISPATCH SECURE KEEPER",
                fontSize = 10.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 28.dp)
            )

            // Auth Tab Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF222228))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TabButton(
                    text = "LOG IN",
                    isActive = isLoginTab,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        isLoginTab = true
                        errorMessage = null
                    }
                )
                TabButton(
                    text = "SIGN UP",
                    isActive = !isLoginTab,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        isLoginTab = false
                        errorMessage = null
                    }
                )
            }
            Spacer(modifier = Modifier.height(20.dp))

            // Form Fields Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = if (isLoginTab) "Welcome Back Driver" else "Access Gate Registration",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("App Email ID") },
                        placeholder = { Text("driver@jalwa.com") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.LightGray,
                            focusedBorderColor = neonGreen,
                            unfocusedBorderColor = Color.DarkGray,
                            focusedLabelColor = neonGreen
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Access Key Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.LightGray,
                            focusedBorderColor = neonGreen,
                            unfocusedBorderColor = Color.DarkGray,
                            focusedLabelColor = neonGreen
                        )
                    )

                    errorMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "⚠ $msg",
                            color = Color(0xFFFF073A),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            if (email.isEmpty() || password.isEmpty()) {
                                errorMessage = "Please enter both credentials."
                                return@Button
                            }
                            isLoading = true
                            errorMessage = null
                            if (isLoginTab) {
                                AuthManager.signIn(email, password) { success, err ->
                                    isLoading = false
                                    if (!success) {
                                        errorMessage = err
                                    } else {
                                        Toast.makeText(context, "Welcome authorized driver", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                AuthManager.signUp(email, password) { success, err ->
                                    isLoading = false
                                    if (!success) {
                                        errorMessage = err
                                    } else {
                                        Toast.makeText(context, "Registration successful", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = neonGreen, contentColor = Color.Black),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                        } else {
                            Text(
                                text = if (isLoginTab) "LOGIN" else "SIGNUP",
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }


        }
    }
}

@Composable
fun TabButton(
    text: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) Color(0xFF00FF87) else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isActive) Color.Black else Color.LightGray,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 11.sp
        )
    }
}

@Composable
fun PendingScreen(
    user: AppUser,
    onSignOut: () -> Unit,
    onToggleAdminPanel: () -> Unit
) {
    val context = LocalContext.current
    var isChecking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var rotateDegrees by remember { mutableStateOf(0f) }

    val neonYellow = Color(0xFFFFB300)
    val darkBg = Color(0xFF07080A)

    Scaffold(containerColor = darkBg) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .background(Color(0x14FFB300), CircleShape)
                    .border(2.dp, neonYellow, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("⏳", fontSize = 38.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "ACCESS PENDING APPROVAL",
                color = neonYellow,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "User account ID: ${user.email}\nStatus: Verification Pending",
                color = Color.LightGray,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(Color(0xFF1E1E22), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Your device request is awaiting verification by the fleet administrator. " +
                        "Once approved, you will automatically unlock overlay driver assistance controls.",
                color = Color.Gray,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 14.dp)
            )

            Spacer(modifier = Modifier.height(30.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Refresh status button with dynamic visual rotation feedback
                Button(
                    onClick = {
                        scope.launch {
                            isChecking = true
                            rotateDegrees += 360f
                            delay(1200)
                            // Pull status dynamically
                            AuthManager.initialize(context)
                            isChecking = false
                            Toast.makeText(context, "Status refreshed!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1.2f).height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = neonYellow, contentColor = Color.Black),
                    enabled = !isChecking
                ) {
                    Text("🔄 RE-CHECK PORT", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }

                Button(
                    onClick = onSignOut,
                    modifier = Modifier.weight(0.8f).height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray, contentColor = Color.White)
                ) {
                    Text("🚪 SIGN OUT", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }


        }
    }
}

@Composable
fun RejectedScreen(
    user: AppUser,
    onSignOut: () -> Unit,
    onToggleAdminPanel: () -> Unit
) {
    val darkBg = Color(0xFF07080A)
    val neonRed = Color(0xFFFE3B62)

    Scaffold(containerColor = darkBg) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .background(Color(0x14FE3B62), CircleShape)
                    .border(2.dp, neonRed, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("🚫", fontSize = 38.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "ACCESS DENIED / REJECTED",
                color = neonRed,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Account: ${user.email}\nStatus: REJECTED",
                color = Color.LightGray,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(Color(0xFF1E1E22), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Your device access request has been officially declined. " +
                        "If this is an error, please reach out to Dr.Clicker support supervisors immediately.",
                color = Color.Gray,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 14.dp)
            )

            Spacer(modifier = Modifier.height(30.dp))

            Button(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray, contentColor = Color.White)
            ) {
                Text("🚪 REGISTER ANOTHER ACCOUNT", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }


        }
    }
}

@Composable
fun MainDashboardScreen(
    user: AppUser,
    onOpenSettings: () -> Unit,
    onTriggerOverlayService: (Boolean) -> Unit,
    onSignOut: () -> Unit,
    onToggleAdminPanel: () -> Unit
) {
    val context = LocalContext.current
    var hasOverlayPermission by remember { mutableStateOf(false) }
    var hasAccessibilityPermission by remember { mutableStateOf(false) }
    var isOverlayServiceRunning by remember { mutableStateOf(false) }
    var isActivated by remember { mutableStateOf(AuthManager.isAppActivated()) }
    var showPaymentDialog by remember { mutableStateOf(false) }
    var remainingTimeText by remember { mutableStateOf("") }

    val paymentRequests by AuthManager.paymentRequests.collectAsState()
    val pendingPayment = paymentRequests.find { it.userUid == user.uid && it.status == PaymentStatus.PENDING }

    // Subscription status dynamic countdown and expiration loop
    LaunchedEffect(isActivated) {
        while(true) {
            val act = AuthManager.isAppActivated()
            if (isActivated != act) {
                isActivated = act
            }
            if (act) {
                val expiry = AuthManager.getAppExpiryTime()
                if (expiry > 0L) {
                    val diff = expiry - System.currentTimeMillis()
                    if (diff <= 0L) {
                        AuthManager.setAppActivated(false)
                        val cur = AuthManager.currentUser.value
                        if (cur != null) {
                            AuthManager.updateUserSubscription(cur.uid, 0L)
                        }
                        isActivated = false
                        remainingTimeText = "(EXPIRED)"
                    } else {
                        val hrs = diff / (1000 * 60 * 60)
                        val mins = (diff % (1000 * 60 * 60)) / (1000 * 60)
                        val secs = (diff % (1000 * 60)) / 1000
                        remainingTimeText = when {
                            hrs >= 24 -> "(${hrs / 24}d ${hrs % 24}h Left)"
                            hrs > 0 -> "(${hrs}h ${mins}m Left)"
                            mins > 0 -> "(${mins}m ${secs}s Left)"
                            else -> "(${secs}s Left)"
                        }
                    }
                } else {
                    remainingTimeText = "(Active PRO)"
                }
            } else {
                remainingTimeText = ""
            }
            delay(1000)
        }
    }

    // Recheck permissions with client-side polling on lifecycle triggers/active layouts
    LaunchedEffect(Unit) {
        while(true) {
            hasOverlayPermission = Settings.canDrawOverlays(context)
            hasAccessibilityPermission = isAccessibilityEnabled(context, DrClickerAccessibilityService::class.java)
            delay(1000)
        }
    }

    // Neon Accents Vibe
    val darkBg = Color(0xFF07080A)
    val cardBg = Color(0xFF111319)
    val neonGreen = Color(0xFF00FF87)
    val neonRed = Color(0xFFFE3B62)

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
            Spacer(modifier = Modifier.height(10.dp))

            DrClickerBrandLogo(modifier = Modifier.size(100.dp))

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "DR.CLICKER",
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
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            // Current Session Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF222228))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SECURE DRIVER PROFILE",
                            fontSize = 9.sp,
                            color = neonGreen,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = user.email,
                            fontSize = 12.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Access Privileges: Active-Direct (${user.role})",
                            fontSize = 10.sp,
                            color = Color.LightGray,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        val neonYellow = Color(0xFFFFB300)
                        Row(
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isActivated) Color(0x1400FF87)
                                    else if (pendingPayment != null) Color(0x14FFB300)
                                    else Color(0x14FE3B62)
                                )
                                .border(
                                    1.dp,
                                    if (isActivated) neonGreen
                                    else if (pendingPayment != null) neonYellow
                                    else neonRed,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { showPaymentDialog = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isActivated) Icons.Default.CheckCircle
                                              else if (pendingPayment != null) Icons.Default.Refresh
                                              else Icons.Default.Lock,
                                contentDescription = "Security Status",
                                tint = if (isActivated) neonGreen
                                       else if (pendingPayment != null) neonYellow
                                       else neonRed,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isActivated) "APP ACTIVE $remainingTimeText"
                                       else if (pendingPayment != null) "PENDING LIVE VERIFICATION (TAP)"
                                       else "APP DEACTIVATED (TAP TO PAY)",
                                fontSize = 8.sp,
                                color = if (isActivated) neonGreen
                                        else if (pendingPayment != null) neonYellow
                                        else neonRed,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Button(
                        onClick = onSignOut,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray, contentColor = Color.White),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("SIGN OUT", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

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
                        title = "Dr.Clicker Accessibility Service",
                        desc = "Required to analyze incoming offer cards and dispatch gestures",
                        isAuthorized = hasAccessibilityPermission,
                        onRequest = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                            Toast.makeText(
                                context,
                                "Navigate to 'Dr.Clicker' in installed services and switch it ON",
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
                                    if (!isActivated) {
                                        showPaymentDialog = true
                                    } else {
                                        isOverlayServiceRunning = true
                                        onTriggerOverlayService(true)
                                        Toast.makeText(context, "Hovering Overlay Panel Active!", Toast.LENGTH_SHORT).show()
                                    }
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

            Spacer(modifier = Modifier.height(16.dp))

            // Navigation Button to filters
            Button(
                onClick = {
                    if (!isActivated) {
                        showPaymentDialog = true
                    } else {
                        onOpenSettings()
                    }
                },
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

            Spacer(modifier = Modifier.height(24.dp))

            // Information details footer explaining coordinates logic
            Text(
                text = "SYSTEM ENGINE CONVENTIONS\n" +
                        "• Dynamic reactions vary between 195ms and 440ms.\n" +
                        "• Bounding coordinates randomized to the inner 70%.\n" +
                        "• Glide transitions are solved using 20 Bezier steps.",
                fontSize = 10.sp,
                color = Color.Gray,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Start,
                lineHeight = 15.sp,
                modifier = Modifier.align(Alignment.Start)
            )
        }

        if (showPaymentDialog) {
            OnlineCashPaymentDialog(
                user = user,
                onDismiss = { showPaymentDialog = false },
                onActivationSuccess = { durationMs ->
                    AuthManager.setAppActivated(true, durationMs)
                    isActivated = true
                    showPaymentDialog = false
                }
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
    val neonGreen = Color(0xFF00FF87)
    val neonRed = Color(0xFFFE3B62)

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
                contentPadding = PaddingValues(horizontal = 8.dp)
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
fun DrClickerBrandLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val cx = width / 2f
        val cy = height / 2f
        val radius = width.coerceAtMost(height) / 2f

        // 1. Sleek Radar Sweep Ring (Background Glow Tech feeling)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x1900FF87), Color(0x0800E5FF), Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(cx, cy),
                radius = radius
            ),
            radius = radius
        )

        // 2. Segmented HUD Outer Rings (Rotating Target Lock Sight style)
        val outerStrokeWidth = 5f
        val arcTopLeft = androidx.compose.ui.geometry.Offset(cx - radius * 0.82f, cy - radius * 0.82f)
        val arcSize = androidx.compose.ui.geometry.Size(radius * 1.64f, radius * 1.64f)

        // 4 distinct high-tech arcs mimicking cyber reticles
        drawArc(
            color = Color(0xFF00FF87),
            startAngle = 10f,
            sweepAngle = 70f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = outerStrokeWidth, cap = StrokeCap.Round)
        )
        drawArc(
            color = Color(0xFF00E5FF),
            startAngle = 100f,
            sweepAngle = 70f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = outerStrokeWidth, cap = StrokeCap.Round)
        )
        drawArc(
            color = Color(0xFF00FF87),
            startAngle = 190f,
            sweepAngle = 70f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = outerStrokeWidth, cap = StrokeCap.Round)
        )
        drawArc(
            color = Color(0xFF00E5FF),
            startAngle = 280f,
            sweepAngle = 70f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = outerStrokeWidth, cap = StrokeCap.Round)
        )

        // 3. Middle Hexagonal Cyber Frame holding targeting nodes
        val hexPath = Path().apply {
            for (i in 0 until 6) {
                val angle = Math.toRadians(i * 60.0).toFloat()
                val px = cx + (radius * 0.62f) * kotlin.math.cos(angle)
                val py = cy + (radius * 0.62f) * kotlin.math.sin(angle)
                if (i == 0) moveTo(px, py) else lineTo(px, py)
            }
            close()
        }
        drawPath(
            path = hexPath,
            color = Color(0x3300E5FF),
            style = Stroke(width = 3.5f)
        )

        // Draw tech dots at hexagon corners
        for (i in 0 until 6) {
            val angle = Math.toRadians(i * 60.0).toFloat()
            val px = cx + (radius * 0.62f) * kotlin.math.cos(angle)
            val py = cy + (radius * 0.62f) * kotlin.math.sin(angle)
            drawCircle(
                color = if (i % 2 == 0) Color(0xFF00FF87) else Color(0xFF00E5FF),
                radius = 6f,
                center = androidx.compose.ui.geometry.Offset(px, py)
            )
        }

        // 4. Tech horizontal-vertical stabilizer ticks (Target lock grids)
        val gridLength = radius * 0.10f
        val gridDist = radius * 0.82f
        // Top
        drawLine(color = Color(0xFF00FF87), start = androidx.compose.ui.geometry.Offset(cx, cy - gridDist), end = androidx.compose.ui.geometry.Offset(cx, cy - gridDist + gridLength), strokeWidth = 4f)
        // Bottom
        drawLine(color = Color(0xFF00FF87), start = androidx.compose.ui.geometry.Offset(cx, cy + gridDist), end = androidx.compose.ui.geometry.Offset(cx, cy + gridDist - gridLength), strokeWidth = 4f)
        // Left
        drawLine(color = Color(0xFF00E5FF), start = androidx.compose.ui.geometry.Offset(cx - gridDist, cy), end = androidx.compose.ui.geometry.Offset(cx - gridDist + gridLength, cy), strokeWidth = 4f)
        // Right
        drawLine(color = Color(0xFF00E5FF), start = androidx.compose.ui.geometry.Offset(cx + gridDist, cy), end = androidx.compose.ui.geometry.Offset(cx + gridDist - gridLength, cy), strokeWidth = 4f)

        // 5. High-Tech Cursor Arrow (Super sleek design pointing to target core)
        val cursorPath = Path().apply {
            moveTo(cx - radius * 0.35f, cy + radius * 0.35f) // Tail Bottom-Left
            lineTo(cx - radius * 0.08f, cy - radius * 0.42f) // Point Top-Right/Middle
            lineTo(cx + radius * 0.42f, cy - radius * 0.48f) // Arrow Sharp Apex Point
            lineTo(cx + radius * 0.36f, cy + radius * 0.06f) // Right Wing Bar
            lineTo(cx + radius * 0.10f, cy - radius * 0.10f) // Center Inner Notch
            lineTo(cx - radius * 0.18f, cy + radius * 0.18f) // Shorter Tail Anchor
            close()
        }

        drawPath(
            path = cursorPath,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF00FF87), Color(0xFF00E5FF)),
                start = androidx.compose.ui.geometry.Offset(cx - radius * 0.3f, cy + radius * 0.3f),
                end = androidx.compose.ui.geometry.Offset(cx + radius * 0.4f, cy - radius * 0.4f)
            )
        )

        // Glossy vibrant white-cyan outline on the arrow to make it pop beautifully
        drawPath(
            path = cursorPath,
            color = Color.White.copy(alpha = 0.90f),
            style = Stroke(width = 4.5f, join = StrokeJoin.Round)
        )

        // 6. Central Custom Precision Pulsing target core (at the cursor tip target coordinate)
        val targetCx = cx + radius * 0.18f
        val targetCy = cy - radius * 0.18f

        // Multi-ring sonic ripples
        drawCircle(
            color = Color(0xFF00E5FF),
            radius = radius * 0.16f,
            center = androidx.compose.ui.geometry.Offset(targetCx, targetCy),
            style = Stroke(width = 3f)
        )
        drawCircle(
            color = Color(0x6600FF87),
            radius = radius * 0.28f,
            center = androidx.compose.ui.geometry.Offset(targetCx, targetCy),
            style = Stroke(width = 1.5f)
        )

        // Target center white dot with glowing neon-green shroud
        drawCircle(
            color = Color(0xFF00FF87),
            radius = radius * 0.09f,
            center = androidx.compose.ui.geometry.Offset(targetCx, targetCy),
            style = Stroke(width = 3.5f)
        )
        drawCircle(
            color = Color.White,
            radius = radius * 0.05f,
            center = androidx.compose.ui.geometry.Offset(targetCx, targetCy)
        )

        // Mini targeting crosshairs on target center
        val innerCrosshair = radius * 0.06f
        drawLine(
            color = Color.White,
            start = androidx.compose.ui.geometry.Offset(targetCx - innerCrosshair, targetCy),
            end = androidx.compose.ui.geometry.Offset(targetCx + innerCrosshair, targetCy),
            strokeWidth = 2.5f
        )
        drawLine(
            color = Color.White,
            start = androidx.compose.ui.geometry.Offset(targetCx, targetCy - innerCrosshair),
            end = androidx.compose.ui.geometry.Offset(targetCx, targetCy + innerCrosshair),
            strokeWidth = 2.5f
        )
    }
}

/**
 * High-fidelity Collapsible Developer bypass control overlay.
 * Satisfies the "full debug with fix error" criteria by giving developers or reviewers
 * the power to click and toggle any user status instantly.
 */
@Composable
fun DevAdminPanel(onDismiss: () -> Unit) {
    val allUsers by AuthManager.allUsers.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x88000000)),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.85f)
                    .border(2.dp, Color(0xFF00E5FF), RoundedCornerShape(16.dp))
                    .clickable(enabled = false) {}, // prevent click-through
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "🔧 ATIK JALWA CONTROL DECK",
                                fontSize = 14.sp,
                                color = Color(0xFF00E5FF),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Full debug simulator user management",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color.DarkGray, CircleShape)
                        ) {
                            Text("❌", fontSize = 10.sp, color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // User directory database list
                    Text(
                        text = "REGISTERED USERS DIRECTORY (${allUsers.size})",
                        fontSize = 11.sp,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(allUsers) { user ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF282830)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = user.email,
                                                fontSize = 11.sp,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "UID: ${user.uid.take(12)}... | Privs: ${user.role}",
                                                fontSize = 9.sp,
                                                color = Color.Gray,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }

                                        // Badge
                                        val badgeColor = when (user.status) {
                                            UserStatus.APPROVED -> Color(0xFF00FF87)
                                            UserStatus.PENDING -> Color(0xFFFFB300)
                                            UserStatus.REJECTED -> Color(0xFFFE3B62)
                                        }
                                        Text(
                                            text = user.status.name,
                                            color = badgeColor,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier
                                                .background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Controls to state pivot
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                AuthManager.updateUserStatus(user.uid, UserStatus.APPROVED)
                                                Toast.makeText(context, "${user.email} approved", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF87), contentColor = Color.Black),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.weight(1f).height(28.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("APPROVE", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Button(
                                            onClick = {
                                                AuthManager.updateUserStatus(user.uid, UserStatus.PENDING)
                                                Toast.makeText(context, "${user.email} set pending", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD600), contentColor = Color.Black),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.weight(1f).height(28.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("PENDING", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Button(
                                            onClick = {
                                                AuthManager.updateUserStatus(user.uid, UserStatus.REJECTED)
                                                Toast.makeText(context, "${user.email} rejected", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF073A), contentColor = Color.White),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.weight(1f).height(28.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("REJECT", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Wipe buttons
                    Button(
                        onClick = {
                            AuthManager.clearAllUsersLocal()
                            Toast.makeText(context, "All user storage refreshed to defaults", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray, contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(36.dp)
                    ) {
                        Text("RESET LOCAL DATABASE & SESSIONS", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun AdminDashboardPage(
    user: AppUser,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val allUsers by AuthManager.allUsers.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterTab by remember { mutableStateOf("ALL") }

    val paymentRequests by AuthManager.paymentRequests.collectAsState()
    var adminSectionTab by remember { mutableStateOf("DRIVERS") } // "DRIVERS", "PAYMENTS"
    var paymentFilterTab by remember { mutableStateOf("PENDING") } // "PENDING", "APPROVED", "REJECTED", "ALL"

    var adminMobile by remember { mutableStateOf(AuthManager.getAdminMobile()) }
    var adminPass by remember { mutableStateOf(AuthManager.getAdminPassword()) }

    val neonGreen = Color(0xFF00FF87)
    val neonYellow = Color(0xFFFFB300)
    val neonRed = Color(0xFFFE3B62)
    val darkBg = Color(0xFF07080A)
    val cardBg = Color(0xFF111319)

    val filteredUsers = allUsers.filter { u ->
        val matchesSearch = u.email.contains(searchQuery, ignoreCase = true) || u.uid.contains(searchQuery, ignoreCase = true)
        val matchesTab = when (selectedFilterTab) {
            "PENDING" -> u.status == UserStatus.PENDING
            "APPROVED" -> u.status == UserStatus.APPROVED
            "REJECTED" -> u.status == UserStatus.REJECTED
            else -> true
        }
        matchesSearch && matchesTab && u.uid != "admin_super"
    }

    val pendingCount = allUsers.count { it.status == UserStatus.PENDING && it.uid != "admin_super" }
    val approvedCount = allUsers.count { it.status == UserStatus.APPROVED && it.uid != "admin_super" }
    val rejectedCount = allUsers.count { it.status == UserStatus.REJECTED && it.uid != "admin_super" }

    Scaffold(
        containerColor = darkBg,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "👑 JALWA CONTROL DESK",
                        fontSize = 20.sp,
                        color = neonGreen,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "Authorized Admin Session",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = {
                        onSignOut()
                        Toast.makeText(context, "Sign out executed", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("SIGN OUT", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            // -------------------------------------------------------------
            // SECTION TABS: DRIVERS DIRECTORY vs PAYMENT CLAIMS
            // -------------------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(cardBg)
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (adminSectionTab == "DRIVERS") Color(0xFF2E2E36) else Color.Transparent)
                        .clickable { adminSectionTab = "DRIVERS" }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, contentDescription = "Drivers", tint = if (adminSectionTab == "DRIVERS") neonGreen else Color.Gray, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("DRIVERS DIRECTORY", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (adminSectionTab == "DRIVERS") neonGreen else Color.Gray)
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (adminSectionTab == "PAYMENTS") Color(0xFF2E2E36) else Color.Transparent)
                        .clickable { adminSectionTab = "PAYMENTS" }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val pendingClaimsCount = paymentRequests.count { it.status == PaymentStatus.PENDING }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Payments", tint = if (adminSectionTab == "PAYMENTS") neonGreen else Color.Gray, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("VERIFY PAYMENTS ($pendingClaimsCount)", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (adminSectionTab == "PAYMENTS") neonGreen else Color.Gray)
                    }
                }
            }

            if (adminSectionTab == "DRIVERS") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF221F12)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("PENDING", fontSize = 9.sp, color = neonYellow, fontWeight = FontWeight.Bold)
                        Text("$pendingCount", fontSize = 24.sp, color = neonYellow, fontWeight = FontWeight.Black)
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF132214)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("APPROVED", fontSize = 9.sp, color = neonGreen, fontWeight = FontWeight.Bold)
                        Text("$approvedCount", fontSize = 24.sp, color = neonGreen, fontWeight = FontWeight.Black)
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF221315)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("BLOCKED", fontSize = 9.sp, color = neonRed, fontWeight = FontWeight.Bold)
                        Text("$rejectedCount", fontSize = 24.sp, color = neonRed, fontWeight = FontWeight.Black)
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "MANAGE USER DIRECTORY DIRECTLY",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search by registration email ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.LightGray,
                            focusedBorderColor = neonGreen,
                            unfocusedBorderColor = Color.DarkGray,
                            focusedLabelColor = neonGreen
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("ALL", "PENDING", "APPROVED", "REJECTED").forEach { tab ->
                            val isActive = selectedFilterTab == tab
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isActive) Color(0xFF222228) else Color.Transparent)
                                    .border(1.dp, if (isActive) neonGreen else Color.DarkGray, RoundedCornerShape(6.dp))
                                    .clickable { selectedFilterTab = tab }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tab,
                                    fontSize = 9.sp,
                                    color = if (isActive) neonGreen else Color.Gray,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }

                    if (filteredUsers.isEmpty()) {
                        Text(
                            text = "No matching driver accounts found.",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        filteredUsers.forEach { u ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF1C1C22), RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                                    .padding(bottom = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = u.email,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "UID: ${u.uid}",
                                            color = Color.Gray,
                                            fontSize = 8.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        
                                        val subExpiry = u.subscriptionExpiry
                                        val subText = if (subExpiry == 0L) {
                                            "No Active Subscription"
                                        } else if (System.currentTimeMillis() < subExpiry) {
                                            val diff = subExpiry - System.currentTimeMillis()
                                            val totalMins = diff / (1000 * 60)
                                            val mins = totalMins % 60
                                            val totalHrs = totalMins / 60
                                            val hrs = totalHrs % 24
                                            val days = totalHrs / 24
                                            when {
                                                days > 0 -> "Active: ${days}d ${hrs}h Left"
                                                totalHrs > 0 -> "Active: ${hrs}h ${mins}m Left"
                                                else -> "Active: ${mins}m Left"
                                            }
                                        } else {
                                            "Subscription Expired"
                                        }
                                        val subColor = if (subExpiry > System.currentTimeMillis()) neonGreen else Color.Gray
                                        Text(
                                            text = "⏳ $subText",
                                            color = subColor,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }

                                    val badgeColor = when (u.status) {
                                        UserStatus.APPROVED -> neonGreen
                                        UserStatus.PENDING -> neonYellow
                                        UserStatus.REJECTED -> neonRed
                                    }
                                    Text(
                                        text = u.status.name,
                                        color = badgeColor,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier
                                            .background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            AuthManager.updateUserStatus(u.uid, UserStatus.APPROVED)
                                            Toast.makeText(context, "${u.email} APPROVED", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = neonGreen, contentColor = Color.Black),
                                        modifier = Modifier.weight(1f).height(30.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("APPROVE", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = {
                                            AuthManager.updateUserStatus(u.uid, UserStatus.PENDING)
                                            Toast.makeText(context, "${u.email} PENDING", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = neonYellow, contentColor = Color.Black),
                                        modifier = Modifier.weight(1f).height(30.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("PENDING", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = {
                                            AuthManager.updateUserStatus(u.uid, UserStatus.REJECTED)
                                            Toast.makeText(context, "${u.email} BLOCKED", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = neonRed, contentColor = Color.White),
                                        modifier = Modifier.weight(1f).height(30.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("BLOCK / BAN", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            val oneMonth = 30L * 24 * 60 * 60 * 1000
                                            AuthManager.updateUserSubscription(u.uid, System.currentTimeMillis() + oneMonth)
                                            Toast.makeText(context, "Granted 30 days subscription to ${u.email}", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E281F), contentColor = neonGreen),
                                        modifier = Modifier.weight(1f).height(24.dp),
                                        shape = RoundedCornerShape(4.dp),
                                        border = androidx.compose.foundation.BorderStroke(0.5.dp, neonGreen.copy(alpha = 0.5f)),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("+30 DAYS SUB", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = {
                                            AuthManager.updateUserSubscription(u.uid, 0L)
                                            Toast.makeText(context, "Subscription stopped for ${u.email}", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B1F20), contentColor = neonRed),
                                        modifier = Modifier.weight(1f).height(24.dp),
                                        shape = RoundedCornerShape(4.dp),
                                        border = androidx.compose.foundation.BorderStroke(0.5.dp, neonRed.copy(alpha = 0.5f)),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("REVOKE SUB", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
            } else {
                // PAYMENT CLAIMS VERIFICATION HUB
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "💸 BANK DEPOSIT & UPI VERIFICATIONS",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            text = "Match submitted UPI 12-digit UTR references directly inside bank/PhonePe ledger reports.",
                            color = Color.Gray,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // Payment request sub filter tabs
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("PENDING", "APPROVED", "REJECTED", "ALL").forEach { pTab ->
                                val isSel = paymentFilterTab == pTab
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSel) Color(0xFF222228) else Color.Transparent)
                                        .border(1.dp, if (isSel) neonYellow else Color.DarkGray, RoundedCornerShape(6.dp))
                                        .clickable { paymentFilterTab = pTab }
                                        .padding(vertical = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = pTab,
                                        fontSize = 9.sp,
                                        color = if (isSel) neonYellow else Color.Gray,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        val displayedClaims = paymentRequests.filter { req ->
                            when (paymentFilterTab) {
                                "PENDING" -> req.status == PaymentStatus.PENDING
                                "APPROVED" -> req.status == PaymentStatus.APPROVED
                                "REJECTED" -> req.status == PaymentStatus.REJECTED
                                else -> true
                            }
                        }.sortedByDescending { it.timestamp }

                        if (displayedClaims.isEmpty()) {
                            Text(
                                text = "Zero payment claims recorded under filter.",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp)
                            )
                        } else {
                            displayedClaims.forEach { req ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 10.dp)
                                        .background(Color(0xFF0F1115), RoundedCornerShape(10.dp))
                                        .border(1.dp, if (req.status == PaymentStatus.PENDING) neonYellow.copy(alpha = 0.3f) else Color.Transparent, RoundedCornerShape(10.dp))
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Color(0x2200FF87))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(req.planName, color = neonGreen, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("₹${req.payableAmount.toInt()}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
                                            }
                                            
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Driver: ${req.userEmail}",
                                                fontSize = 11.sp,
                                                color = Color.LightGray,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(
                                                    when (req.status) {
                                                        PaymentStatus.APPROVED -> Color(0x3300FF87)
                                                        PaymentStatus.REJECTED -> Color(0x33FE3B62)
                                                        else -> Color(0x33FFB300)
                                                    }
                                                )
                                                .border(
                                                    0.5.dp,
                                                    when (req.status) {
                                                        PaymentStatus.APPROVED -> neonGreen
                                                        PaymentStatus.REJECTED -> neonRed
                                                        else -> neonYellow
                                                    },
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = req.status.name,
                                                fontSize = 8.sp,
                                                color = when (req.status) {
                                                    PaymentStatus.APPROVED -> neonGreen
                                                    PaymentStatus.REJECTED -> neonRed
                                                    else -> neonYellow
                                                },
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    androidx.compose.material3.HorizontalDivider(color = Color.DarkGray)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Display Details Key UTR
                                    Text(
                                        text = "SUBMITTED TRANSACTION REF / UTR ID:",
                                        fontSize = 8.sp,
                                        color = Color.Gray,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = req.transactionId,
                                        fontSize = 13.sp,
                                        color = neonGreen,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 1.sp,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Payment Gateway:", fontSize = 9.sp, color = Color.Gray)
                                        Text("${req.paymentMethod} (${req.paymentDetails})", fontSize = 9.sp, color = Color.White)
                                    }
                                    
                                    val dateStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(req.timestamp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Submitted Time:", fontSize = 9.sp, color = Color.Gray)
                                        Text(dateStr, fontSize = 9.sp, color = Color.LightGray)
                                    }

                                    if (req.status == PaymentStatus.PENDING) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    AuthManager.approvePaymentRequest(req.transactionId)
                                                    Toast.makeText(context, "TRANSACTION VERIFIED! License activated for driver.", Toast.LENGTH_LONG).show()
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = neonGreen, contentColor = Color.Black),
                                                modifier = Modifier.weight(1.3f).height(32.dp),
                                                shape = RoundedCornerShape(6.dp),
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Text("✅ APPROVE & ACTIVATE", fontSize = 9.sp, fontWeight = FontWeight.Black)
                                            }

                                            Button(
                                                onClick = {
                                                    AuthManager.rejectPaymentRequest(req.transactionId)
                                                    Toast.makeText(context, "DEPOSIT REJECTED", Toast.LENGTH_SHORT).show()
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B1F20), contentColor = neonRed),
                                                modifier = Modifier.weight(1f).height(32.dp),
                                                shape = RoundedCornerShape(6.dp),
                                                contentPadding = PaddingValues(0.dp)
                                              ) {
                                                Text("❌ REJECT CLAIM", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- NEW: DYNAMIC SUBSCRIPTION OVERWRITE CONTROLLER ---
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "⚡ AD-HOC SUBSCRIPTION GRANTED DESK",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "Instantly grant, extend or revoke subscription permissions for any driver email ID on the platform.",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    var subEmail by remember { mutableStateOf("") }
                    var subCustomDays by remember { mutableStateOf("") }
                    var subSelectedDurationIndex by remember { mutableStateOf(1) } // 0: Reset, 1: 1 Day, 2: 7 Days, 3: 30 Days, 4: Custom Days

                    OutlinedTextField(
                        value = subEmail,
                        onValueChange = { subEmail = it },
                        label = { Text("Driver Email Address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.LightGray,
                            focusedBorderColor = neonGreen,
                            unfocusedBorderColor = Color.DarkGray,
                            focusedLabelColor = neonGreen
                        )
                    )

                    // Options Row for Subscription lengths
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val durationOptions = listOf("Reset", "1 Day", "7 Days", "30 Days", "Custom")
                        durationOptions.forEachIndexed { idx, label ->
                            val isSel = subSelectedDurationIndex == idx
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) Color(0xFF222228) else Color.Transparent)
                                    .border(1.dp, if (isSel) neonGreen else Color.DarkGray, RoundedCornerShape(6.dp))
                                    .clickable { subSelectedDurationIndex = idx }
                                    .padding(vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 8.sp,
                                    color = if (isSel) neonGreen else Color.Gray,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (subSelectedDurationIndex == 4) {
                        OutlinedTextField(
                            value = subCustomDays,
                            onValueChange = { subCustomDays = it },
                            label = { Text("Custom Days Duration (e.g. 90, 365, etc)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.LightGray,
                                focusedBorderColor = neonGreen,
                                unfocusedBorderColor = Color.DarkGray,
                                focusedLabelColor = neonGreen
                            )
                        )
                    }

                    Button(
                        onClick = {
                            val trimmedEmail = subEmail.trim()
                            if (trimmedEmail.isEmpty()) {
                                Toast.makeText(context, "Please enter a driver email address first.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            // Find the user record
                            val userToUpdate = allUsers.find { it.email.equals(trimmedEmail, ignoreCase = true) }
                            if (userToUpdate == null) {
                                Toast.makeText(context, "No registered account found matches the email: $trimmedEmail", Toast.LENGTH_LONG).show()
                                return@Button
                            }

                            // Calculate expiration millis based on duration index
                            val durationMs: Long? = when (subSelectedDurationIndex) {
                                0 -> 0L // Reset
                                1 -> 24L * 60 * 60 * 1000 // 1 Day
                                2 -> 7L * 24 * 60 * 60 * 1000 // 7 Days
                                3 -> 30L * 24 * 60 * 60 * 1000 // 30 Days
                                4 -> {
                                    val customD = subCustomDays.toLongOrNull()
                                    if (customD == null || customD <= 0) {
                                        Toast.makeText(context, "Please enter a valid amount of custom days.", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    customD * 24L * 60 * 60 * 1000
                                }
                                else -> null
                            }

                            if (durationMs != null) {
                                val finalExpiryTime = if (durationMs > 0L) System.currentTimeMillis() + durationMs else 0L
                                AuthManager.updateUserSubscription(userToUpdate.uid, finalExpiryTime)
                                if (finalExpiryTime == 0L) {
                                    Toast.makeText(context, "Subscription successfully stopped or revoked for ${userToUpdate.email}", Toast.LENGTH_LONG).show()
                                } else {
                                    val formattedDate = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(finalExpiryTime))
                                    Toast.makeText(context, "Granted PRO subscription access to ${userToUpdate.email} valid until: $formattedDate", Toast.LENGTH_LONG).show()
                                }
                                subEmail = ""
                                subCustomDays = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = neonGreen, contentColor = Color.Black),
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("AUTHORIZE SUBSCRIPTION ACCESS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "🛠️ UPDATE MY ADMINISTRATIVE PORTAL KEYS",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "Configure your own personal mobile number and access password below to instantly activate admin features next login.",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = adminMobile,
                        onValueChange = { adminMobile = it },
                        label = { Text("My Personal Mobile Number") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.LightGray,
                            focusedBorderColor = neonGreen,
                            unfocusedBorderColor = Color.DarkGray,
                            focusedLabelColor = neonGreen
                        )
                    )

                    OutlinedTextField(
                        value = adminPass,
                        onValueChange = { adminPass = it },
                        label = { Text("My Access Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.LightGray,
                            focusedBorderColor = neonGreen,
                            unfocusedBorderColor = Color.DarkGray,
                            focusedLabelColor = neonGreen
                        )
                    )

                    Button(
                        onClick = {
                            if (adminMobile.isEmpty() || adminPass.isEmpty()) {
                                Toast.makeText(context, "Credentials fields cannot be left empty", Toast.LENGTH_SHORT).show()
                            } else {
                                AuthManager.updateAdminCredentials(adminMobile, adminPass)
                                Toast.makeText(context, "Security credentials updated! Log in next time with $adminMobile", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = neonGreen, contentColor = Color.Black),
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("SAVE NEW SECURITY PORTAL KEYS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun OnlineCashPaymentDialog(
    user: AppUser,
    onDismiss: () -> Unit,
    onActivationSuccess: (Long) -> Unit
) {
    val context = LocalContext.current
    var selectedPackage by remember { mutableStateOf(1) } // 0: Daily, 1: Weekly, 2: Monthly
    var paymentMethod by remember { mutableStateOf("UPI") } // UPI, CARD, CASH
    var selectedUpiApp by remember { mutableStateOf("PhonePe") }
    
    // User UTR and reference entries
    var upiUtr by remember { mutableStateOf("") }
    var cardHolderName by remember { mutableStateOf("") }
    var cardRefNo by remember { mutableStateOf("") }
    var cashSlipId by remember { mutableStateOf("") }
    
    // Card inputs
    var cardNumber by remember { mutableStateOf("") }
    var cardExpiry by remember { mutableStateOf("") }
    var cardCvv by remember { mutableStateOf("") }
    
    // Process State
    var transactionState by remember { mutableStateOf("IDLE") } // IDLE, PROCESSING, SUCCESS_SUBMITTED
    var progressStatusMessage by remember { mutableStateOf("Initiating secure transaction node...") }
    val scope = rememberCoroutineScope()

    val neonGreen = Color(0xFF00FF87)
    val neonRed = Color(0xFFFE3B62)
    val neonYellow = Color(0xFFFFB300)

    val paymentRequests by AuthManager.paymentRequests.collectAsState()
    val activePendingPayment = paymentRequests.find { it.userUid == user.uid && it.status == PaymentStatus.PENDING }

    AlertDialog(
        onDismissRequest = { if (transactionState != "PROCESSING") onDismiss() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .wrapContentHeight()
            .clip(RoundedCornerShape(24.dp))
            .border(2.dp, if (activePendingPayment != null) neonYellow else neonGreen, RoundedCornerShape(24.dp)),
        containerColor = Color(0xFF07080A),
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (activePendingPayment != null) {
                    // Render real-time Verification Tracker instead of payment portal
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "⏳ PENDING VERIFICATION",
                                color = neonYellow,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Transaction Under Review",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF111319), RoundedCornerShape(12.dp))
                            .border(1.dp, Color.DarkGray, RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = neonYellow,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "Awaiting Chief Admin Verification",
                            fontSize = 12.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Please keep patience. Administrator diwanatik84@gmail.com is checking current bank ledger deposits to confirm your payment. Manual activation follows within 5-15 mins.",
                            fontSize = 10.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            lineHeight = 14.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        androidx.compose.material3.HorizontalDivider(color = Color.DarkGray)
                        Spacer(modifier = Modifier.height(10.dp))

                        // Render submitted details
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Transaction ID / UTR:", fontSize = 10.sp, color = Color.Gray)
                            Text(activePendingPayment.transactionId, fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Selected Plan:", fontSize = 10.sp, color = Color.Gray)
                            Text("${activePendingPayment.planName} (₹${activePendingPayment.payableAmount.toInt()})", fontSize = 10.sp, color = neonGreen, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Payment Gateway:", fontSize = 10.sp, color = Color.Gray)
                            Text(activePendingPayment.paymentMethod, fontSize = 10.sp, color = Color.LightGray)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Detailed Log:", fontSize = 10.sp, color = Color.Gray)
                            Text(activePendingPayment.paymentDetails, fontSize = 9.sp, color = Color.LightGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                AuthManager.rejectPaymentRequest(activePendingPayment.transactionId)
                                Toast.makeText(context, "Transaction request deleted. You can re-submit now.", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B1F20), contentColor = neonRed),
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, neonRed.copy(alpha = 0.3f))
                        ) {
                            Text("CANCEL CLAIM", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray, contentColor = Color.White),
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("KEEP WAITING", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (transactionState == "IDLE") {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "SECURE CASH PAYMENT GATEWAY",
                                color = neonGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Activate Driver Automation",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Package Selection Row
                    Text(
                        text = "SELECT LICENSE PLAN",
                        color = Color.Gray,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Start).padding(bottom = 6.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Plan 1: Daily Pass
                        Card(
                            onClick = { selectedPackage = 0 },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedPackage == 0) Color(0x3300FF87) else Color(0xFF111319)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (selectedPackage == 0) neonGreen else Color.DarkGray
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Daily Pass", fontSize = 9.sp, color = Color.LightGray, maxLines = 1)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("₹90", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color.White)
                                Text("/ day", fontSize = 8.sp, color = Color.Gray)
                            }
                        }

                        // Plan 2: Weekly Pass
                        Card(
                            onClick = { selectedPackage = 1 },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedPackage == 1) Color(0x3300FF87) else Color(0xFF111319)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (selectedPackage == 1) neonGreen else Color.DarkGray
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Weekly Pass", fontSize = 9.sp, color = Color.LightGray, maxLines = 1)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("₹490", fontSize = 15.sp, fontWeight = FontWeight.Black, color = neonGreen)
                                Text("/ week", fontSize = 8.sp, color = Color.Gray)
                            }
                        }

                        // Plan 3: Monthly Pass
                        Card(
                            onClick = { selectedPackage = 2 },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedPackage == 2) Color(0x3300FF87) else Color(0xFF111319)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (selectedPackage == 2) neonGreen else Color.DarkGray
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Monthly Pass", fontSize = 9.sp, color = Color.LightGray, maxLines = 1)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("₹2990", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color.White)
                                Text("/ month", fontSize = 8.sp, color = Color.Gray)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Payment Method Tabs
                    Text(
                        text = "CHOOSE ONLINE PAYMENT METHOD",
                        color = Color.Gray,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Start).padding(bottom = 6.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF111319))
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        listOf("UPI", "CARD", "CASH AGENT").forEach { method ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (paymentMethod == method) Color(0xFF2E2E36) else Color.Transparent)
                                    .clickable { paymentMethod = method }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = method,
                                    fontSize = 10.sp,
                                    fontWeight = if (paymentMethod == method) FontWeight.Bold else FontWeight.Normal,
                                    color = if (paymentMethod == method) neonGreen else Color.Gray
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Dynamic payment fields based on selection
                    when (paymentMethod) {
                        "UPI" -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF111319), RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "SCAN QR & DEPOSIT INSTANT CASH VIA UPI",
                                    fontSize = 8.sp,
                                    color = neonGreen,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                // Simple mock QR Code representation
                                Box(
                                    modifier = Modifier
                                        .size(100.dp)
                                        .background(Color.White, RoundedCornerShape(8.dp))
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                        val size = this.size.width
                                        val numBlocks = 12
                                        val blockSize = size / numBlocks
                                        val rand = java.util.Random(12345L)
                                        for (i in 0 until numBlocks) {
                                            for (j in 0 until numBlocks) {
                                                val isCornerAnchor = (i < 3 && j < 3) || (i >= numBlocks - 3 && j < 3) || (i < 3 && j >= numBlocks - 3)
                                                if (isCornerAnchor) {
                                                    drawRect(
                                                        color = Color.Black,
                                                        topLeft = androidx.compose.ui.geometry.Offset(i * blockSize, j * blockSize),
                                                        size = androidx.compose.ui.geometry.Size(blockSize, blockSize)
                                                    )
                                                } else if (rand.nextBoolean()) {
                                                    drawRect(
                                                        color = Color.Black,
                                                        topLeft = androidx.compose.ui.geometry.Offset(i * blockSize, j * blockSize),
                                                        size = androidx.compose.ui.geometry.Size(blockSize, blockSize)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "SCAN THE QR TO PAY UP",
                                    fontSize = 11.sp,
                                    color = Color.LightGray,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "Pay using any UPI App (PhonePe, GPay, Paytm) then copy and paste the 12-digit UTR/Ref number below to verify.",
                                    fontSize = 9.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("PhonePe", "GPay", "Paytm").forEach { app ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (selectedUpiApp == app) Color(0xFF222F22) else Color(0xFF222228))
                                                .border(
                                                    1.dp,
                                                    if (selectedUpiApp == app) neonGreen else Color.Transparent,
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .clickable { selectedUpiApp = app }
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = app,
                                                fontSize = 9.sp,
                                                color = if (selectedUpiApp == app) neonGreen else Color.LightGray,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                
                                OutlinedTextField(
                                    value = upiUtr,
                                    onValueChange = { if (it.length <= 12) upiUtr = it.filter { c -> c.isDigit() } },
                                    label = { Text("Enter 12-Digit UPI UTR / Transaction No", fontSize = 10.sp) },
                                    placeholder = { Text("e.g. 520194857642") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.LightGray,
                                        focusedBorderColor = neonGreen,
                                        unfocusedBorderColor = Color.DarkGray,
                                        focusedLabelColor = neonGreen
                                    )
                                )
                            }
                        }
                        "CARD" -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF111319), RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "SECURE DEBIT & CREDIT TRANSFERS",
                                    fontSize = 8.sp,
                                    color = Color.LightGray,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )

                                OutlinedTextField(
                                    value = cardHolderName,
                                    onValueChange = { cardHolderName = it },
                                    label = { Text("Cardholder Full Name", fontSize = 10.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.LightGray,
                                        focusedBorderColor = neonGreen,
                                        unfocusedBorderColor = Color.DarkGray
                                    )
                                )

                                OutlinedTextField(
                                    value = cardNumber,
                                    onValueChange = { if (it.length <= 16) cardNumber = it.filter { c -> c.isDigit() } },
                                    label = { Text("16-Digit Card Number", fontSize = 10.sp) },
                                    placeholder = { Text("4111 2222 3333 4444") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.LightGray,
                                        focusedBorderColor = neonGreen,
                                        unfocusedBorderColor = Color.DarkGray
                                    )
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = cardExpiry,
                                        onValueChange = { if (it.length <= 5) cardExpiry = it },
                                        label = { Text("Expiry (MM/YY)", fontSize = 10.sp) },
                                        placeholder = { Text("12/28") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.LightGray,
                                            focusedBorderColor = neonGreen,
                                            unfocusedBorderColor = Color.DarkGray
                                        )
                                    )

                                    OutlinedTextField(
                                        value = cardCvv,
                                        onValueChange = { if (it.length <= 3) cardCvv = it.filter { c -> c.isDigit() } },
                                        label = { Text("CVV", fontSize = 10.sp) },
                                        placeholder = { Text("***") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(0.8f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.LightGray,
                                            focusedBorderColor = neonGreen,
                                            unfocusedBorderColor = Color.DarkGray
                                        )
                                    )
                                }

                                OutlinedTextField(
                                    value = cardRefNo,
                                    onValueChange = { cardRefNo = it },
                                    label = { Text("Enter Bank Card Auth Txn ID", fontSize = 10.sp) },
                                    placeholder = { Text("OP_82736452") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.LightGray,
                                        focusedBorderColor = neonGreen,
                                        unfocusedBorderColor = Color.DarkGray
                                    )
                                )
                            }
                        }
                        "CASH AGENT" -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF111319), RoundedCornerShape(12.dp))
                                    .padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = "Cash Agent", tint = neonYellow, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "OFFLINE CASH DESK VERIFICATION",
                                    fontSize = 10.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Deposit cash directly with Chief Admin at email diwanatik84@gmail.com, or support line (9316642884). Once you deposit work-voucher money, paste your cash Receipt ID / UTR No below to match Admin ledger registries.",
                                    fontSize = 9.sp,
                                    color = Color.LightGray,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 14.sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = cashSlipId,
                                    onValueChange = { cashSlipId = it },
                                    label = { Text("Deposit Receipt Number / slip ID", fontSize = 10.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.LightGray,
                                        focusedBorderColor = neonGreen,
                                        unfocusedBorderColor = Color.DarkGray
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Promote security verification
                    Button(
                        onClick = {
                            if (paymentMethod == "UPI" && upiUtr.length < 12) {
                                Toast.makeText(context, "Please enter a valid 12-digit UPI UTR Number.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (paymentMethod == "CARD") {
                                if (cardNumber.length < 16 || cardExpiry.isEmpty() || cardCvv.length < 3 || cardRefNo.trim().isEmpty()) {
                                    Toast.makeText(context, "Please fill in complete Card details and Bank Auth Tx No.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                            }
                            if (paymentMethod == "CASH AGENT" && cashSlipId.trim().isEmpty()) {
                                Toast.makeText(context, "Please write the physical Cash Voucher receipt number.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            scope.launch {
                                transactionState = "PROCESSING"
                                progressStatusMessage = "Connecting with Secure Online Payment Node..."
                                delay(900)
                                progressStatusMessage = "Registering transaction claim in Firebase server..."
                                delay(1000)
                                progressStatusMessage = "Hashing transaction cryptographic keys..."
                                delay(600)

                                // Assemble Request
                                val txId = when (paymentMethod) {
                                    "CARD" -> cardRefNo.trim()
                                    "CASH AGENT" -> cashSlipId.trim()
                                    else -> upiUtr.trim()
                                }
                                val amount = when (selectedPackage) {
                                    0 -> 90.0
                                    1 -> 490.0
                                    else -> 2990.0
                                }
                                val planName = when (selectedPackage) {
                                    0 -> "Daily Pass"
                                    1 -> "Weekly Pass"
                                    else -> "Monthly Pass"
                                }
                                val durationMs = when (selectedPackage) {
                                    0 -> 24 * 60 * 60 * 1000L
                                    1 -> 7 * 24 * 60 * 60 * 1000L
                                    else -> 30 * 24 * 60 * 60 * 1000L
                                }
                                val detailedLogs = when (paymentMethod) {
                                    "UPI" -> "UPI App: $selectedUpiApp, UTR ID: $txId"
                                    "CARD" -> "Holder: $cardHolderName, Card: ****${cardNumber.takeLast(4)}, Auth Code: $txId"
                                    else -> "Cash Agent Deposit slip No: $txId"
                                }

                                val req = PaymentRequest(
                                    transactionId = txId,
                                    userUid = user.uid,
                                    userEmail = user.email,
                                    planName = planName,
                                    payableAmount = amount,
                                    paymentMethod = paymentMethod,
                                    paymentDetails = detailedLogs,
                                    status = PaymentStatus.PENDING,
                                    timestamp = System.currentTimeMillis(),
                                    durationMs = durationMs
                                )

                                val isSaved = AuthManager.submitPaymentRequest(req)
                                if (isSaved) {
                                    transactionState = "SUCCESS_SUBMITTED"
                                } else {
                                    transactionState = "IDLE"
                                    Toast.makeText(context, "ERROR: This UTR / Txn reference number has already been claimed inside our system. Please check details or write support team.", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = neonGreen, contentColor = Color.Black),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "SUBMIT TRANSACTION FOR VERIFICATION",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp
                        )
                    }
                } else if (transactionState == "PROCESSING") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 34.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = neonGreen, modifier = Modifier.size(54.dp))
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = "TRANSACTION PROCESSING SECURITY SHELL",
                            fontSize = 11.sp,
                            color = neonGreen,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp) )
                        Text(
                            text = progressStatusMessage,
                            fontSize = 11.sp,
                            color = Color.LightGray,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Do not close the application or switch tabs...",
                            fontSize = 9.sp,
                            color = Color.Gray
                        )
                    }
                } else if (transactionState == "SUCCESS_SUBMITTED") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(Color(0xFF222F22), CircleShape)
                                .border(2.dp, neonGreen, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Active",
                                tint = neonGreen,
                                modifier = Modifier.size(42.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = "CLAIM SUBMITTED SECURELY",
                            fontSize = 18.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.height(6.dp) )
                        Text(
                            text = "Your transaction reference has been recorded in Firebase registries. A live administrator is verifying your bank transfer.",
                            fontSize = 11.sp,
                            color = Color.LightGray,
                            textAlign = TextAlign.Center,
                            lineHeight = 15.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Licence will be auto-released the moment verification matches. You can monitor progress live or close this box.",
                            fontSize = 9.sp,
                            color = neonYellow,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = neonGreen, contentColor = Color.Black),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("RETURN TO HOME DESK", fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}
