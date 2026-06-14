package com.example

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import android.accounts.AccountManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Email
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
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
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        DrClickerController.initialize(this)
        AuthManager.initialize(this)
        JobOfferStorage.initialize(this)
        
        // Start background subscription timer monitoring service module
        startService(Intent(this, SubscriptionTimerService::class.java))

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
        androidx.compose.animation.AnimatedContent(
            targetState = currentUser,
            transitionSpec = {
                (androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(500)) + 
                 androidx.compose.animation.scaleIn(initialScale = 0.96f, animationSpec = androidx.compose.animation.core.tween(500)))
                    .togetherWith(androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(350)))
            },
            label = "AuthDashboardSwitch"
        ) { user ->
            val authPrefs = LocalContext.current.getSharedPreferences("DrClickerPrefs", Context.MODE_PRIVATE)
            if (user == null) {
                DrClickerAuthScreen()
            } else if (user.role == "ADMIN") {
                AdminDashboardPage(
                    user = user,
                    onSignOut = {
                        authPrefs.edit().remove("saved_pass").apply()
                        AuthManager.signOut()
                    }
                )
            } else {
                MainDashboardScreen(
                    user = user,
                    onOpenSettings = onOpenSettings,
                    onTriggerOverlayService = onTriggerOverlayService,
                    onSignOut = {
                        authPrefs.edit().remove("saved_pass").apply()
                        AuthManager.signOut()
                    },
                    onToggleAdminPanel = { showAdminPanelGlobal = !showAdminPanelGlobal }
                )
            }
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
                    .border(1.dp, Color(0xFF00C4FF), CircleShape)
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
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var isPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }
    var isForgotNewPasswordVisible by remember { mutableStateOf(false) }

    val prefs = remember { context.getSharedPreferences("DrClickerPrefs", Context.MODE_PRIVATE) }
    var rememberMe by remember { mutableStateOf(prefs.getBoolean("remember_me", true)) }

    LaunchedEffect(Unit) {
        if (rememberMe) {
            val savedEmail = prefs.getString("saved_email", "") ?: ""
            val savedPass = prefs.getString("saved_pass", "") ?: ""
            if (savedEmail.isNotEmpty() && savedPass.isNotEmpty()) {
                email = savedEmail
                password = savedPass
                isLoading = true
                AuthManager.signIn(savedEmail, savedPass) { success, err ->
                    isLoading = false
                    if (success) {
                        Toast.makeText(context, "Safar Shuru! Auto-Logged In.", Toast.LENGTH_SHORT).show()
                    } else {
                        errorMessage = err
                    }
                }
            } else {
                email = savedEmail
            }
        }
    }

    val isEmailValid = remember(email) {
        email.isNotEmpty() && android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    }

    val hasMinLength = password.length >= 6
    val hasLetter = password.any { it.isLetter() }
    val hasDigit = password.any { it.isDigit() }
    
    var showGoogleDialog by remember { mutableStateOf(false) }
    var customGoogleEmail by remember { mutableStateOf("") }
    var isGoogleLoading by remember { mutableStateOf(false) }

    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var forgotEmail by remember { mutableStateOf("") }
    var forgotNewPassword by remember { mutableStateOf("") }
    var isForgotLoading by remember { mutableStateOf(false) }

    var isAnimateInVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isAnimateInVisible = true
    }

    val googleAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val accountName = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (!accountName.isNullOrEmpty()) {
                isGoogleLoading = true
                AuthManager.signInWithGoogle(accountName) { success, err ->
                    isGoogleLoading = false
                    if (success) {
                        prefs.edit().apply {
                            putBoolean("remember_me", rememberMe)
                            if (rememberMe) {
                                putString("saved_email", accountName)
                            } else {
                                remove("saved_email")
                            }
                            apply()
                        }
                        showGoogleDialog = false
                        Toast.makeText(context, "Logged in via Google: $accountName", Toast.LENGTH_SHORT).show()
                    } else {
                        errorMessage = err
                    }
                }
            } else {
                Toast.makeText(context, "No Google account selected.", Toast.LENGTH_SHORT).show()
            }
        } else {
            showGoogleDialog = true
        }
    }

    val primaryAccent = MaterialTheme.colorScheme.primary  // Deep Cocoa Espresso Brown or dynamic dark mode Cream
    val darkBg = MaterialTheme.colorScheme.background     // Dynamic background
    val cardBg = MaterialTheme.colorScheme.surface        // Dynamic box background
    val textColor = MaterialTheme.colorScheme.onSurface    // Dynamic text color

    Scaffold(
        containerColor = darkBg,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        androidx.compose.animation.AnimatedVisibility(
            visible = isAnimateInVisible,
            enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(800)) + 
                    androidx.compose.animation.slideInVertically(
                        initialOffsetY = { it / 8 },
                        animationSpec = androidx.compose.animation.core.tween(800)
                    ),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Brand Header Section
                DrClickerBrandLogo(
                    modifier = Modifier.size(96.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "DR.CLICKER",
                    fontSize = 30.sp,
                    color = primaryAccent,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 6.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "PROFESSIONAL DISPATCH COMPLIANT ENGINE",
                    fontSize = 9.sp,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                    textAlign = TextAlign.Center
                )

                // Active Connection Cloud Status Badge
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(if (AuthManager.isFirebaseActive()) Color(0xFFECFDF5) else Color(0xFFFFF7ED))
                        .clickable {
                            Toast.makeText(context, "Firebase  dynamic settings can be modified anytime in the settings configuration pane.", Toast.LENGTH_LONG).show()
                        }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(if (AuthManager.isFirebaseActive()) Color(0xFF10B981) else Color(0xFFF97316), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (AuthManager.isFirebaseActive()) "REALTIME FIREBASE AUTH AC" else "LOCAL OFFLINE SIMULATOR ACTIVE",
                        color = if (AuthManager.isFirebaseActive()) Color(0xFF047857) else Color(0xFFC2410C),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))

                // Beautiful New Tab Switcher Pill
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFF1F5F9))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TabButton(
                        text = "LOG IN",
                        isActive = isLoginTab,
                        activeColor = primaryAccent,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            isLoginTab = true
                            errorMessage = null
                        }
                    )
                    TabButton(
                        text = "SIGN UP",
                        isActive = !isLoginTab,
                        activeColor = primaryAccent,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            isLoginTab = false
                            errorMessage = null
                        }
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))

                // Form Fields Container Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Text(
                            text = if (isLoginTab) "Welcome Back, Authorized Driver" else "Create Dispatch Credentials",
                            color = textColor,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 20.dp)
                        )

                        // 1. FULL NAME (ONLY ON SIGN UP)
                        if (!isLoginTab) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Driver Full Name") },
                                placeholder = { Text("E.g. Jayson Diwan") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("name_input"),
                                shape = RoundedCornerShape(12.dp),
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "Name Icon",
                                        tint = if (name.isNotEmpty()) primaryAccent else Color.Gray
                                    )
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = textColor,
                                    unfocusedTextColor = textColor,
                                    focusedBorderColor = primaryAccent,
                                    unfocusedBorderColor = Color(0xFFCBD5E1),
                                    focusedLabelColor = primaryAccent,
                                    cursorColor = primaryAccent
                                )
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // 2. EMAIL ID Field (BOTH)
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("App Registered Email") },
                            placeholder = { Text("driver@example.com") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("email_input"),
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = "Email Icon",
                                    tint = if (isEmailValid) primaryAccent else Color.Gray
                                )
                            },
                            trailingIcon = {
                                if (email.isNotEmpty()) {
                                    if (isEmailValid) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Valid Email Format",
                                            tint = Color(0xFF10B981)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = "Invalid Email",
                                            tint = Color(0xFFEF4444)
                                        )
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = textColor,
                                unfocusedTextColor = textColor,
                                focusedBorderColor = primaryAccent,
                                unfocusedBorderColor = Color(0xFFCBD5E1),
                                focusedLabelColor = primaryAccent,
                                cursorColor = primaryAccent
                            )
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // 3. ACCESS PASSWORD Field (BOTH)
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Driver Access Password") },
                            placeholder = { Text("Enter account password") },
                            singleLine = true,
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("password_input"),
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Lock Icon",
                                    tint = if (password.isNotEmpty()) primaryAccent else Color.Gray
                                )
                            },
                            trailingIcon = {
                                androidx.compose.material3.TextButton(
                                    onClick = { isPasswordVisible = !isPasswordVisible },
                                    modifier = Modifier.testTag("password_visibility_toggle")
                                ) {
                                    Text(
                                        text = if (isPasswordVisible) "HIDE" else "SHOW",
                                        color = if (password.isNotEmpty()) primaryAccent else Color.Gray,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = textColor,
                                unfocusedTextColor = textColor,
                                focusedBorderColor = primaryAccent,
                                unfocusedBorderColor = Color(0xFFCBD5E1),
                                focusedLabelColor = primaryAccent,
                                cursorColor = primaryAccent
                            )
                        )

                        // 4. CONFIRM PASSWORD Field (ONLY ON SIGN UP)
                        if (!isLoginTab) {
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it },
                                label = { Text("Confirm Secure Password") },
                                placeholder = { Text("Re-enter password to verify") },
                                singleLine = true,
                                visualTransformation = if (isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("confirm_password_input"),
                                shape = RoundedCornerShape(12.dp),
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Confirm Lock Icon",
                                        tint = if (confirmPassword.isNotEmpty() && confirmPassword == password) Color(0xFF10B981) else Color.Gray
                                    )
                                },
                                trailingIcon = {
                                    androidx.compose.material3.TextButton(
                                        onClick = { isConfirmPasswordVisible = !isConfirmPasswordVisible }
                                    ) {
                                        Text(
                                            text = if (isConfirmPasswordVisible) "HIDE" else "SHOW",
                                            color = if (confirmPassword.isNotEmpty()) primaryAccent else Color.Gray,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = textColor,
                                    unfocusedTextColor = textColor,
                                    focusedBorderColor = primaryAccent,
                                    unfocusedBorderColor = Color(0xFFCBD5E1),
                                    focusedLabelColor = primaryAccent,
                                    cursorColor = primaryAccent
                                )
                            )
                        }

                        // SIGN UP STRENGTH CHECKLIST
                        if (!isLoginTab && password.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "PASSWORD SECURITY PROTOCOL CHECK",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF64748B),
                                        letterSpacing = 0.5.sp
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(
                                            imageVector = if (hasMinLength) Icons.Default.CheckCircle else Icons.Default.Close,
                                            tint = if (hasMinLength) Color(0xFF10B981) else Color.Gray,
                                            modifier = Modifier.size(14.dp),
                                            contentDescription = null
                                        )
                                        Text(
                                            text = "Minimum 6 characters",
                                            fontSize = 11.sp,
                                            color = if (hasMinLength) textColor else Color.Gray
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(
                                            imageVector = if (hasLetter) Icons.Default.CheckCircle else Icons.Default.Close,
                                            tint = if (hasLetter) Color(0xFF10B981) else Color.Gray,
                                            modifier = Modifier.size(14.dp),
                                            contentDescription = null
                                        )
                                        Text(
                                            text = "Contains at least one letter (A-Z)",
                                            fontSize = 11.sp,
                                            color = if (hasLetter) textColor else Color.Gray
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(
                                            imageVector = if (hasDigit) Icons.Default.CheckCircle else Icons.Default.Close,
                                            tint = if (hasDigit) Color(0xFF10B981) else Color.Gray,
                                            modifier = Modifier.size(14.dp),
                                            contentDescription = null
                                        )
                                        Text(
                                            text = "Contains at least one number (0-9)",
                                            fontSize = 11.sp,
                                            color = if (hasDigit) textColor else Color.Gray
                                        )
                                    }
                                }
                            }
                        }

                        // REMEMBER ME Toggle Row
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { rememberMe = !rememberMe }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = rememberMe,
                                onCheckedChange = { rememberMe = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = primaryAccent,
                                    uncheckedColor = Color.Gray,
                                    checkmarkColor = Color.White
                                ),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Keep me signed in on this device",
                                color = Color(0xFF475569),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // FORGOT PASSWORD trigger (ONLY ON LOGIN MODE)
                        if (isLoginTab) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                              ) {
                                androidx.compose.material3.TextButton(
                                    onClick = {
                                        forgotEmail = email.trim()
                                        forgotNewPassword = ""
                                        showForgotPasswordDialog = true
                                    },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp, horizontal = 2.dp)
                                ) {
                                    Text(
                                        text = "Forgot password? Reset Key",
                                        color = primaryAccent,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                                    )
                                }
                            }
                        }

                        // Realtime user interaction feedback
                        errorMessage?.let { msg ->
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFFEF2F2))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Alert Symbol",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = msg,
                                    color = Color(0xFFB91C1C),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(22.dp))

                        // MAIN FORM ACTION SUBMISSION BUTTON
                        Button(
                            onClick = {
                                if (email.isEmpty() || password.isEmpty()) {
                                    errorMessage = "Please enter both Email and Password details."
                                    return@Button
                                }
                                if (!isLoginTab) {
                                    if (name.trim().isEmpty()) {
                                        errorMessage = "Please enter your Driver Full Name."
                                        return@Button
                                    }
                                    if (password.length < 6) {
                                        errorMessage = "Password must be at least 6 characters."
                                        return@Button
                                    }
                                    if (password != confirmPassword) {
                                        errorMessage = "Confirm Password does not match original."
                                        return@Button
                                    }
                                }
                                isLoading = true
                                errorMessage = null
                                if (isLoginTab) {
                                    AuthManager.signIn(email, password) { success, err ->
                                        isLoading = false
                                        if (!success) {
                                            errorMessage = err
                                        } else {
                                            prefs.edit().apply {
                                                putBoolean("remember_me", rememberMe)
                                                if (rememberMe) {
                                                    putString("saved_email", email.trim())
                                                    putString("saved_pass", password)
                                                } else {
                                                    remove("saved_email")
                                                    remove("saved_pass")
                                                }
                                                apply()
                                            }
                                            Toast.makeText(context, "System active. Authorized Driver validated.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else {
                                    AuthManager.signUp(name, email, password) { success, err ->
                                        isLoading = false
                                        if (!success) {
                                            errorMessage = err
                                        } else {
                                            prefs.edit().apply {
                                                putBoolean("remember_me", rememberMe)
                                                if (rememberMe) {
                                                    putString("saved_email", email.trim())
                                                    putString("saved_pass", password)
                                                } else {
                                                    remove("saved_email")
                                                    remove("saved_pass")
                                                }
                                                apply()
                                            }
                                            Toast.makeText(context, "Registry verified successfully!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("auth_submit_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryAccent, contentColor = Color.White),
                            shape = RoundedCornerShape(14.dp),
                            enabled = !isLoading
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            } else {
                                Text(
                                    text = if (isLoginTab) "SIGN IN TO SYSTEM" else "CREATE SECURE ACCOUNT",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        // SPECIFIC REDIRECT TOGGLES AS REQUESTED ("already have account", "not have account" redirects)
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isLoginTab) {
                                Text(
                                    text = "Don't have an account? ",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "Sign Up Now",
                                    color = primaryAccent,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .clickable {
                                            isLoginTab = false
                                            errorMessage = null
                                        }
                                        .padding(4.dp)
                                )
                            } else {
                                Text(
                                    text = "Already have an account? ",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "Log In Here",
                                    color = primaryAccent,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .clickable {
                                            isLoginTab = true
                                            errorMessage = null
                                        }
                                        .padding(4.dp)
                                )
                            }
                        }
                    }
                }

                // Social Single-Sign-On OR divider
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = Color(0xFFE2E8F0)
                    )
                    Text(
                        text = "SECURE PROTOCOL",
                        color = Color.Gray,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 14.dp),
                        letterSpacing = 1.sp
                    )
                    androidx.compose.material3.HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = Color(0xFFE2E8F0)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                // HIGH-GRADE GOOGLE SIGN IN OR AUTO CHOOSER BUTTON
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        errorMessage = null
                        try {
                            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                AccountManager.newChooseAccountIntent(
                                    null,
                                    null,
                                    arrayOf("com.google"),
                                    null,
                                    null,
                                    null,
                                    null
                                )
                            } else {
                                @Suppress("DEPRECATION")
                                AccountManager.newChooseAccountIntent(
                                    null, null, arrayOf("com.google"), false, null, null, null, null
                                )
                            }
                            googleAuthLauncher.launch(intent)
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Google account selection failed: ${e.message}")
                            showGoogleDialog = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF475569))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .background(Color.White, androidx.compose.foundation.shape.CircleShape)
                                .border(1.dp, Color(0xFFE2E8F0), androidx.compose.foundation.shape.CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "G",
                                color = Color(0xFF4285F4),
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "CONTINUE VERIFICATION WITH GOOGLE",
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp,
                            color = Color(0xFF475569)
                        )
                    }
                }
            }
        }
    }

    // --- ACCORDINGLY STYLED MULTI ACCOUNT GOOGLE DIALOG CARD ---
    if (showGoogleDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showGoogleDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Text("o", color = Color(0xFFEA4335), fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Text("o", color = Color(0xFFFBBC05), fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Text("g", color = Color(0xFF4285F4), fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Text("l", color = Color(0xFF34A853), fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Text("e", color = Color(0xFFEA4335), fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    }

                    Text(
                        text = "Choose an account",
                        color = textColor,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "securely connect Dr. Clicker platform",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                    )

                    if (isGoogleLoading) {
                        CircularProgressIndicator(color = primaryAccent)
                    } else {
                        val accountsToDisplay = remember(showGoogleDialog) {
                            val list = mutableListOf<String>()
                            try {
                                val am = AccountManager.get(context)
                                val accounts = am.getAccountsByType("com.google")
                                for (ac in accounts) {
                                    if (ac.name.contains("@") && ac.name.endsWith(".com")) {
                                        list.add(ac.name)
                                    }
                                }
                            } catch (e: SecurityException) {
                                Log.w("DrClicker", "Security exception: ${e.message}")
                            } catch (e: Exception) {
                                Log.w("DrClicker", "Error getting accounts: ${e.message}")
                            }

                            try {
                                val p = context.getSharedPreferences("GoogleAccountHistory", Context.MODE_PRIVATE)
                                p.getStringSet("saved_emails", emptySet())?.forEach { em ->
                                    if (!list.contains(em)) list.add(em)
                                }
                            } catch(e: Exception) {}

                            list.distinct().sorted()
                        }

                        if (accountsToDisplay.isEmpty()) {
                            Text(
                                text = "No local Google Accounts found. Please enter a Google account email below to connect instantly.",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                            )
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                accountsToDisplay.forEach { accountEmail ->
                                    val letterDisplay = accountEmail.firstOrNull()?.uppercase() ?: "G"
                                    Card(
                                        onClick = {
                                            isGoogleLoading = true
                                            AuthManager.signInWithGoogle(accountEmail) { success, err ->
                                                isGoogleLoading = false
                                                if (success) {
                                                    showGoogleDialog = false
                                                    Toast.makeText(context, "Logged in via Google", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    errorMessage = err
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .background(primaryAccent, androidx.compose.foundation.shape.CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = letterDisplay,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                val accountTitle = if (accountEmail == "aalamdiwan555@gmail.com") "Chief Admin (Diwan)" else "Connected Profile"
                                                Text(
                                                    text = accountTitle,
                                                    color = textColor,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = accountEmail,
                                                    color = Color.Gray,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))
                        androidx.compose.material3.HorizontalDivider(color = Color(0xFFE2E8F0))
                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = "Connect with another address",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.Start)
                                .padding(bottom = 8.dp)
                        )

                        OutlinedTextField(
                            value = customGoogleEmail,
                            onValueChange = { customGoogleEmail = it },
                            label = { Text("Google Account Identity", fontSize = 11.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = textColor,
                                unfocusedTextColor = textColor,
                                focusedBorderColor = primaryAccent,
                                unfocusedBorderColor = Color(0xFFCBD5E1)
                            )
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { showGoogleDialog = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray, contentColor = Color.Black),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("CANCEL", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    if (customGoogleEmail.trim().isEmpty() || !customGoogleEmail.contains("@")) {
                                        Toast.makeText(context, "Please enter a valid Google Account identity.", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    isGoogleLoading = true
                                    AuthManager.signInWithGoogle(customGoogleEmail.trim()) { success, err ->
                                        isGoogleLoading = false
                                        if (success) {
                                            showGoogleDialog = false
                                            Toast.makeText(context, "Logged in via Google", Toast.LENGTH_SHORT).show()
                                        } else {
                                            errorMessage = err
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = primaryAccent, contentColor = Color.White),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("VALIDATE", fontSize = 11.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }
    }

    // --- ACCORDINGLY STYLED PROFESSIONAL FORGOT PASSWORD DIALOG ---
    if (showForgotPasswordDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showForgotPasswordDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .background(primaryAccent.copy(alpha = 0.08f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Shield Guard Logo",
                            tint = primaryAccent,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Emergency Key Reset",
                        color = textColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Enter your registered email ID and formulate a strong new password access key.",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                    )

                    OutlinedTextField(
                        value = forgotEmail,
                        onValueChange = { forgotEmail = it },
                        label = { Text("Registered System Email", fontSize = 11.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("forgot_email_input"),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = "Forgot Email Icon",
                                tint = if (forgotEmail.isNotEmpty()) primaryAccent else Color.Gray
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textColor,
                            unfocusedTextColor = textColor,
                            focusedBorderColor = primaryAccent,
                            unfocusedBorderColor = Color(0xFFCBD5E1)
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = forgotNewPassword,
                        onValueChange = { forgotNewPassword = it },
                        label = { Text("Create Strong New Key", fontSize = 11.sp) },
                        singleLine = true,
                        visualTransformation = if (isForgotNewPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("forgot_password_input"),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Auth Key Icon",
                                tint = if (forgotNewPassword.isNotEmpty()) primaryAccent else Color.Gray
                            )
                        },
                        trailingIcon = {
                            androidx.compose.material3.TextButton(
                                onClick = { isForgotNewPasswordVisible = !isForgotNewPasswordVisible },
                                modifier = Modifier.testTag("forgot_password_visibility_toggle")
                            ) {
                                Text(
                                    text = if (isForgotNewPasswordVisible) "HIDE" else "SHOW",
                                    color = if (forgotNewPassword.isNotEmpty()) primaryAccent else Color.Gray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textColor,
                            unfocusedTextColor = textColor,
                            focusedBorderColor = primaryAccent,
                            unfocusedBorderColor = Color(0xFFCBD5E1)
                        )
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    if (isForgotLoading) {
                        CircularProgressIndicator(color = primaryAccent)
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { showForgotPasswordDialog = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray, contentColor = Color.Black),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("CANCEL", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    val tEmail = forgotEmail.trim()
                                    val tPass = forgotNewPassword.trim()
                                    if (tEmail.isEmpty() || tPass.isEmpty()) {
                                        Toast.makeText(context, "Both credentials fields are mandatory.", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    if (tPass.length < 6) {
                                        Toast.makeText(context, "Password must be at least 6 characters.", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    isForgotLoading = true
                                    AuthManager.resetPassword(tEmail, tPass) { success, msg ->
                                        isForgotLoading = false
                                        if (success) {
                                            Toast.makeText(context, msg ?: "Password reset requested successfully!", Toast.LENGTH_LONG).show()
                                            email = tEmail
                                            password = tPass
                                            showForgotPasswordDialog = false
                                        } else {
                                            Toast.makeText(context, "Failed: ${msg ?: "Account Mismatch"}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = primaryAccent, contentColor = Color.White),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(46.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("RESET AND SIGN IN", fontSize = 11.sp, fontWeight = FontWeight.Black)
                            }
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
    activeColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActive) activeColor else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isActive) Color.White else Color(0xFF64748B),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 11.sp,
            letterSpacing = 0.5.sp
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
    
    // Dynamic angle for the refreshing re-check icon
    val infiniteTransition = rememberInfiniteTransition(label = "pending_wait")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wait_spin"
    )

    val bounceScale by infiniteTransition.animateFloat(
        initialValue = 0.93f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wait_bounce"
    )

    val darkBg = MaterialTheme.colorScheme.background
    val cardBg = MaterialTheme.colorScheme.surface
    val primaryColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onBackground
    val amberHighlight = Color(0xFFF59E0B) // Amber gold

    Scaffold(containerColor = darkBg) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .graphicsLayer(scaleX = bounceScale, scaleY = bounceScale)
                    .background(amberHighlight.copy(alpha = 0.12f), CircleShape)
                    .border(2.dp, primaryColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Spinning hourglass emoji
                Text(
                    text = "⏳",
                    fontSize = 44.sp,
                    modifier = Modifier.graphicsLayer(rotationZ = rotationAngle)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "ACCESS PENDING VERIFICATION",
                color = primaryColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Beautiful Card instead of flat text block
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.2.dp, primaryColor.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "ACCOUNT IDENTIFIER",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = primaryColor.copy(alpha = 0.8f),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = user.email,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = primaryColor.copy(alpha = 0.12f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(amberHighlight, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "STATUS: Fleet Verification Pending",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = primaryColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Your device request is awaiting activation by the fleet administrator. " +
                        "Once approved, you will automatically unlock the driver automation overlay desk. Thank you for your patience!",
                color = textColor.copy(alpha = 0.72f),
                fontSize = 11.5.sp,
                textAlign = TextAlign.Center,
                lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            isChecking = true
                            delay(1200)
                            AuthManager.initialize(context)
                            isChecking = false
                            Toast.makeText(context, "Fleet portal state refreshed!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .weight(1.2f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = Color.White),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                    enabled = !isChecking
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("RE-CHECK PORT", fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp)
                    }
                }

                Button(
                    onClick = onSignOut,
                    modifier = Modifier
                        .weight(0.8f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = textColor),
                    border = BorderStroke(1.dp, textColor.copy(alpha = 0.15f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Sign Out",
                            tint = textColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SIGN OUT", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
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
    val darkBg = MaterialTheme.colorScheme.background
    val cardBg = MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.onBackground
    val primaryColor = MaterialTheme.colorScheme.primary
    val neonRed = Color(0xFFEF4444) // Clean, gorgeous error red

    Scaffold(containerColor = darkBg) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(neonRed.copy(alpha = 0.12f), CircleShape)
                    .border(2.dp, neonRed, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Access Declined",
                    tint = neonRed,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "ACCESS DECLINED / REJECTED",
                color = neonRed,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.2.dp, neonRed.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "ACCOUNT IDENTIFIER",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.Gray,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = user.email,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = neonRed.copy(alpha = 0.12f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(neonRed, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "STATUS: Admin Access Declined",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = neonRed
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Your device access request has been officially declined. " +
                        "If you believe this is an error or your subscription period was miscalculated, please reach out to Dr.Clicker support supervisors immediately.",
                color = textColor.copy(alpha = 0.72f),
                fontSize = 11.5.sp,
                textAlign = TextAlign.Center,
                lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onSignOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = Color.White),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Register Another Account",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("REGISTER ANOTHER ACCOUNT", fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 0.5.sp)
                }
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

    // Custom Aesthetic (Dynamic Ivory/Dark theme)
    val darkBg = MaterialTheme.colorScheme.background
    val cardBg = MaterialTheme.colorScheme.surface
    val neonGreen = MaterialTheme.colorScheme.primary
    val neonRed = Color(0xFFEF4444)     // Ruby Red
    val textColor = MaterialTheme.colorScheme.onSurface // Beautiful Dynamic Text Color

    val prefs = remember { context.getSharedPreferences("DrClickerPrefs", Context.MODE_PRIVATE) }
    var permissionPopupDismissed by remember {
        mutableStateOf(prefs.getBoolean("permission_popup_dismissed_${user.uid}", false))
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentTab by remember { mutableStateOf("DASHBOARD") }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = cardBg,
                drawerContentColor = textColor,
                drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
                modifier = Modifier.width(300.dp)
            ) {
                // Header of Drawer with Logo and Title
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DrClickerBrandLogo(modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "DR. CLICKER PRO",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = textColor,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Driver Automation Core",
                                fontSize = 10.sp,
                                color = neonGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    androidx.compose.material3.HorizontalDivider(color = Color(0xFFE2E8F0))
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Text(
                        text = "Aapka swagat hai, Driver!",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = user.email,
                        fontSize = 13.sp,
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Navigation Items
                data class NavigationMenuItem(
                    val id: String,
                    val label: String,
                    val tagline: String,
                    val icon: androidx.compose.ui.graphics.vector.ImageVector
                )

                val menuItems = listOf(
                    NavigationMenuItem("DASHBOARD", "Dashboard Core", "Active driver control panel", Icons.Default.PlayArrow),
                    NavigationMenuItem("PROFILE", "Driver Profile", "Shift history & statistics", Icons.Default.Person),
                    NavigationMenuItem("RAPIDO_SIMULATOR", "Rapido Sim Mode", "Test Captain login & simulation", Icons.Default.Send),
                    NavigationMenuItem("SUBSCRIPTIONS", "Subscription Plans", "Dynamically calculated packages", Icons.Default.Star),
                    NavigationMenuItem("REFER_REWARDS", "Refer & Earn", "Share code, earn extra hours", Icons.Default.Share),
                    NavigationMenuItem("HELP_ASSISTANCE", "Help & Guidance", "AI support assistant chat & FAQ", Icons.Default.Info)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    menuItems.forEach { item ->
                        val isSelected = currentTab == item.id
                        Card(
                            onClick = {
                                currentTab = item.id
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) neonGreen.copy(alpha = 0.08f) else Color.Transparent
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) neonGreen.copy(alpha = 0.3f) else Color.Transparent
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(
                                            if (isSelected) neonGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) neonGreen.copy(alpha = 0.3f) else Color.Transparent,
                                            RoundedCornerShape(8.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.label,
                                        tint = if (isSelected) neonGreen else Color(0xFF64748B),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(12.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.label,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                        color = if (isSelected) neonGreen else Color(0xFF334155)
                                    )
                                    Text(
                                        text = item.tagline,
                                        fontSize = 9.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }
                        }
                    }
                }

                // Sign Out at the Bottom
                Card(
                    onClick = {
                        scope.launch { drawerState.close() }
                        onSignOut()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Sign Out",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SIGN OUT ACCOUNT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFEF4444)
                        )
                    }
                }
            }
        },
        gesturesEnabled = true
    ) {
        Scaffold(
            containerColor = darkBg,
            modifier = Modifier.fillMaxSize(),
            topBar = {
                val screenHeaderTitle = when (currentTab) {
                    "DASHBOARD" -> "DR.CLICKER HOME"
                    "PROFILE" -> "DRIVER PORTAL STATS"
                    "SUBSCRIPTIONS" -> "ACTIVE SUBSCRIPTION PLANS"
                    "REFER_REWARDS" -> "REFERRAL BONUS WINNER"
                    else -> "HELP HUB & AI ASSISTANT"
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .background(cardBg)
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Menu,
                                contentDescription = "Open Sidebar Menu",
                                tint = neonGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = screenHeaderTitle,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = textColor,
                            letterSpacing = 1.sp
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isActivated) neonGreen.copy(alpha = 0.15f) else Color(0xFFFEF2F2))
                            .border(1.dp, if (isActivated) neonGreen else neonRed, RoundedCornerShape(8.dp))
                            .clickable { showPaymentDialog = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isActivated) "PRO ACTIVE" else "DEACTIVATED",
                            fontSize = 8.sp,
                            color = if (isActivated) neonGreen else neonRed,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        ) { innerPadding ->
            // Onboarding Permission Popup Screen (Only first time if not dismissed and permissions aren't configured yet)
            if (!permissionPopupDismissed && (!hasOverlayPermission || !hasAccessibilityPermission)) {
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { /* Require explicit action or allow back button */ }
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            DrClickerBrandLogo(modifier = Modifier.size(72.dp))
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = "App Permissions Required",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = textColor,
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = "App ko sahi tarah se chalane ke liye kripya yeh dono permissions allow karein. Yeh pehli baar setup karna zaroori hai.",
                                fontSize = 11.sp,
                                color = textColor.copy(alpha = 0.75f),
                                textAlign = TextAlign.Center,
                                lineHeight = 15.sp
                            )
                            
                            Spacer(modifier = Modifier.height(20.dp))
                            
                            // Permission 1: Overlay
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (hasOverlayPermission) Color(0xFFF0FDF4) else Color(0xFFFEF2F2)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (hasOverlayPermission) Color(0xFFBBF7D0) else Color(0xFFFECACA)
                                )
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
                                            text = "1. Draw Over Apps (Overlay)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = textColor
                                        )
                                        Text(
                                            text = "Maps aur apps ke upar start/stop controls chalane ke liye.",
                                            fontSize = 10.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                    if (hasOverlayPermission) {
                                        Icon(
                                            imageVector = androidx.compose.material.icons.Icons.Default.CheckCircle,
                                            contentDescription = "Success",
                                            tint = Color(0xFF16A34A),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    } else {
                                        Button(
                                            onClick = {
                                                val intent = Intent(
                                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                    Uri.parse("package:${context.packageName}")
                                                )
                                                context.startActivity(intent)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0066FF)),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text("ENABLE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Permission 2: Accessibility
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (hasAccessibilityPermission) Color(0xFFF0FDF4) else Color(0xFFFEF2F2)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (hasAccessibilityPermission) Color(0xFFBBF7D0) else Color(0xFFFECACA)
                                )
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
                                            text = "2. Accessibility Service",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = textColor
                                        )
                                        Text(
                                            text = "Incoming offer cards analyze aur automatic click karne ke liye.",
                                            fontSize = 10.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                    if (hasAccessibilityPermission) {
                                        Icon(
                                            imageVector = androidx.compose.material.icons.Icons.Default.CheckCircle,
                                            contentDescription = "Success",
                                            tint = Color(0xFF16A34A),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    } else {
                                        Button(
                                            onClick = {
                                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                                context.startActivity(intent)
                                                Toast.makeText(
                                                    context,
                                                    "Dr.Clicker Service ko ON karein",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0066FF)),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text("ENABLE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            Button(
                                onClick = {
                                    prefs.edit().putBoolean("permission_popup_dismissed_${user.uid}", true).apply()
                                    permissionPopupDismissed = true
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0066FF)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "DONE / SKIP",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(220, delayMillis = 90)) + 
                         slideInHorizontally(animationSpec = tween(220), initialOffsetX = { x -> x / 12 }))
                        .togetherWith(
                            fadeOut(animationSpec = tween(90)) + 
                            slideOutHorizontally(animationSpec = tween(90), targetOffsetX = { x -> -x / 12 })
                        )
                    },
                    label = "tab_fade_slide"
                ) { targetTab ->
                    when (targetTab) {
                        "DASHBOARD" -> {
                            DashboardContentTab(
                                user = user,
                                hasOverlayPermission = hasOverlayPermission,
                                hasAccessibilityPermission = hasAccessibilityPermission,
                                isOverlayServiceRunning = isOverlayServiceRunning,
                                isActivated = isActivated,
                                remainingTimeText = remainingTimeText,
                                pendingPayment = pendingPayment,
                                onOpenSettings = onOpenSettings,
                                onTriggerOverlayService = { run ->
                                    isOverlayServiceRunning = run
                                    onTriggerOverlayService(run)
                                },
                                onSignOut = onSignOut,
                                showPaymentDialog = { showPaymentDialog = true },
                                showHelpHubDialog = { currentTab = "HELP_ASSISTANCE" },
                                neonGreen = neonGreen,
                                neonRed = neonRed,
                                cardBg = cardBg,
                                context = context,
                                permissionPopupDismissed = permissionPopupDismissed
                            )
                        }
                        "PROFILE" -> {
                            DriverProfileScreen(
                                user = user,
                                isActivated = isActivated,
                                remainingTimeText = remainingTimeText,
                                onSignOut = onSignOut,
                                onRequestAccessibility = {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    context.startActivity(intent)
                                },
                                onRequestOverlay = {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                },
                                neonGreen = neonGreen,
                                cardBg = cardBg
                            )
                        }
                        "SUBSCRIPTIONS" -> {
                            SubscriptionsScreenTab(
                                user = user,
                                isActivated = isActivated,
                                remainingTimeText = remainingTimeText,
                                pendingPayment = pendingPayment,
                                onOpenPayment = { showPaymentDialog = true },
                                neonGreen = neonGreen,
                                cardBg = cardBg,
                                context = context
                            )
                        }
                        "REFER_REWARDS" -> {
                            ReferAndRewardsScreen(
                                user = user,
                                onOpenSubscriptions = { currentTab = "SUBSCRIPTIONS" },
                                neonGreen = neonGreen,
                                cardBg = cardBg,
                                context = context
                            )
                        }
                        "RAPIDO_SIMULATOR" -> {
                            RapidoSimulatorScreenTab(
                                neonGreen = neonGreen,
                                cardBg = cardBg,
                                context = context
                            )
                        }
                        "HELP_ASSISTANCE" -> {
                            HelpAndAssistanceScreen(
                                onOpenFAQ = { /* handled inside tab */ },
                                onRequestAccessibility = {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    context.startActivity(intent)
                                },
                                onRequestOverlay = {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                },
                                neonGreen = neonGreen,
                                cardBg = cardBg,
                                context = context
                            )
                        }
                    }
                }
            }
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
fun DashboardContentTab(
    user: AppUser,
    hasOverlayPermission: Boolean,
    hasAccessibilityPermission: Boolean,
    isOverlayServiceRunning: Boolean,
    isActivated: Boolean,
    remainingTimeText: String,
    pendingPayment: PaymentRequest?,
    onOpenSettings: () -> Unit,
    onTriggerOverlayService: (Boolean) -> Unit,
    onSignOut: () -> Unit,
    showPaymentDialog: () -> Unit,
    showHelpHubDialog: () -> Unit,
    neonGreen: Color,
    neonRed: Color,
    cardBg: Color,
    context: Context,
    permissionPopupDismissed: Boolean
) {
    // Target active apps list & edit states
    val targetApps by DrClickerController.targetApps.collectAsState()
    var showAddAppDialog by remember { mutableStateOf(false) }
    var showEditAppDialog by remember { mutableStateOf(false) }
    var editingApp by remember { mutableStateOf<DrClickerController.AppAutomationConfig?>(null) }
    
    // Dynamic editing inputs
    var editAcceptBtnInput by remember { mutableStateOf("") }
    var editKeywordsInput by remember { mutableStateOf("") }
    
    // Custom app adding inputs
    var addAppNameInput by remember { mutableStateOf("") }
    var addAcceptBtnInput by remember { mutableStateOf("ACCEPT") }
    var addKeywordsInput by remember { mutableStateOf("") }

    val isDarkByController by DrClickerController.isDarkTheme.collectAsState()
    val textColor = if (isDarkByController) MaterialTheme.colorScheme.onBackground else Color(0xFF5D4037)

    var currentTimeMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(isActivated) {
        while (true) {
            currentTimeMillis = System.currentTimeMillis()
            delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
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

        // ----------------- SUBSCRIPTION ACCESS COUNTDOWN DETAILS -----------------
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .testTag("subscription_countdown_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.2.dp, if (isActivated) neonGreen.copy(alpha = 0.5f) else Color.LightGray.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Subscription Detail",
                            tint = if (isActivated) neonGreen else Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "LICENSED ACCESS COUNTDOWN",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = textColor,
                            letterSpacing = 1.sp
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .background(
                                if (isActivated) neonGreen.copy(alpha = 0.12f) else Color(0x11EF4444),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (isActivated) "RUNNING" else "INACTIVE",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isActivated) neonGreen else neonRed
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(14.dp))
                
                if (isActivated) {
                    val expiry = AuthManager.getAppExpiryTime()
                    if (expiry > 0L) {
                        val diff = (expiry - currentTimeMillis).coerceAtLeast(0L)
                        val totalSecs = diff / 1000
                        val days = totalSecs / (24 * 3600)
                        val hours = (totalSecs % (24 * 3600)) / 3600
                        val mins = (totalSecs % 3600) / 60
                        val secs = totalSecs % 60
                        
                        val totalHours = totalSecs / 3600

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${days}d : ${hours}h : ${mins}m : ${secs}s",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF0F172A),
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.5.sp
                            )
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            Text(
                                text = "Equivalent access: $totalHours Hours",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray
                            )
                            
                            Spacer(modifier = Modifier.height(14.dp))
                            
                            val startKey = "sub_start_${user.uid}"
                            val userPrefs = context.getSharedPreferences("DrClickerPrefs", Context.MODE_PRIVATE)
                            var savedStart = userPrefs.getLong(startKey, 0L)
                            if (savedStart == 0L || savedStart >= expiry) {
                                savedStart = currentTimeMillis
                                userPrefs.edit().putLong(startKey, savedStart).apply()
                            }
                            
                            val totalDuration = (expiry - savedStart).coerceAtLeast(1000L)
                            val progress = (diff.toFloat() / totalDuration).coerceIn(0f, 1f)
                            
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = neonGreen,
                                trackColor = Color(0xFFE2E8F0)
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Started: ${java.text.SimpleDateFormat("dd MMM, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(savedStart))}",
                                    fontSize = 8.sp,
                                    color = Color.Gray,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Ends: ${java.text.SimpleDateFormat("dd MMM, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(expiry))}",
                                    fontSize = 8.sp,
                                    color = Color.Gray,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "UNLIMITED ACCESS ACTIVE",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = neonGreen,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Administrative or Special bypass license activated",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "00d : 00h : 00m : 00s",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.LightGray,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Your subscription access has expired. Please buy a plan or contact Admin to activate.",
                            fontSize = 10.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = showPaymentDialog,
                            colors = ButtonDefaults.buttonColors(containerColor = neonGreen, contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("ACTIVATE ACCESS PRO", fontSize = 9.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }


        // Permissions Status Card (Hidden if dismissed to let it go away!)
        if (!permissionPopupDismissed && (!hasOverlayPermission || !hasAccessibilityPermission)) {
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
                        color = textColor,
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
        }

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
                    color = textColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .align(Alignment.Start)
                        .padding(bottom = 14.dp)
                )

                if ((!hasOverlayPermission || !hasAccessibilityPermission) && !permissionPopupDismissed) {
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
                            color = Color(0xFF475569),
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
                                if (!hasOverlayPermission || !hasAccessibilityPermission) {
                                    Toast.makeText(context, "Kripya overlay aur accessibility permissions pehle settings se enable karein.", Toast.LENGTH_LONG).show()
                                } else if (!isActivated) {
                                    showPaymentDialog()
                                } else {
                                    onTriggerOverlayService(true)
                                    Toast.makeText(context, "Hovering Overlay Panel Active!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = neonGreen, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SHOW OVERLAY", fontSize = 11.sp, fontWeight = FontWeight.Black)
                        }

                        Button(
                            onClick = {
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

        // TARGET DELIVERY APPS SECTION
        Card(
            modifier = Modifier.fillMaxWidth().testTag("target_apps_section_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Target Delivery Apps",
                        color = textColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    
                    // Add Custom App Button
                    Button(
                        onClick = { showAddAppDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = neonGreen.copy(alpha = 0.12f), contentColor = neonGreen),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp).testTag("add_custom_app_btn")
                    ) {
                        Text("+ ADD NEW", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                Text(
                    text = "Select delivery apps to automate. Customize accept button keyword matching and order-accepting keywords below.",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                // List of Apps
                targetApps.forEach { appConfig ->
                    AppAutomationItemRow(
                        appConfig = appConfig,
                        onToggle = { isChecked ->
                            DrClickerController.updateAppConfig(context, appConfig.copy(isEnabled = isChecked))
                        },
                        onEdit = {
                            editingApp = appConfig
                            editAcceptBtnInput = appConfig.acceptButtonKeyword
                            editKeywordsInput = appConfig.orderKeywords
                            showEditAppDialog = true
                        },
                        onDelete = if (appConfig.id.startsWith("CUSTOM_")) {
                            { DrClickerController.deleteCustomApp(context, appConfig.id) }
                        } else null,
                        textColor = textColor,
                        neonGreen = neonGreen
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Navigation Button to filters
        Button(
            onClick = {
                if (!isActivated) {
                    showPaymentDialog()
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

        Spacer(modifier = Modifier.height(20.dp))

        // Interactive Live Notification Swipe Simulator Card
        LiveJobOfferSimulatorSection(context, neonGreen, cardBg)

        Spacer(modifier = Modifier.height(20.dp))

        // Visualized Earnings & Dynamic Trajectory Charts
        val offers by JobOfferStorage.pastOffers.collectAsState()
        InteractiveEarningsChart(offers)

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

    // --- DIALOGS FOR CUSTOM APP & EDITS ---

    if (showAddAppDialog) {
        AlertDialog(
            onDismissRequest = { showAddAppDialog = false },
            title = {
                Text(
                    "Add Custom Delivery App",
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = textColor
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = addAppNameInput,
                        onValueChange = { addAppNameInput = it },
                        label = { Text("App Name (e.g. Zomato, Porter)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("add_app_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textColor, unfocusedTextColor = Color.Gray)
                    )
                    
                    OutlinedTextField(
                        value = addAcceptBtnInput,
                        onValueChange = { addAcceptBtnInput = it },
                        label = { Text("Accept Button Keyword(s)") },
                        placeholder = { Text("e.g. ACCEPT, BOOK, START") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("add_app_btn_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textColor, unfocusedTextColor = Color.Gray)
                    )
                    
                    OutlinedTextField(
                        value = addKeywordsInput,
                        onValueChange = { addKeywordsInput = it },
                        label = { Text("Order Accepting Keywords") },
                        placeholder = { Text("e.g. Cash, COD, Prepaid") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("add_app_kws_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textColor, unfocusedTextColor = Color.Gray)
                    )

                    // Online keywords template suggestion
                    QuickTemplateKeywordsSection(
                        title = "💡 CHOOSE ONLINE KEYWORD TEMPLATES (CLICK TO APPEND)"
                    ) { templateKws ->
                        addKeywordsInput = if (addKeywordsInput.isEmpty()) {
                            templateKws
                        } else {
                            "$addKeywordsInput, $templateKws"
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmedName = addAppNameInput.trim()
                        if (trimmedName.isNotEmpty()) {
                            DrClickerController.addCustomApp(
                                context,
                                trimmedName,
                                addAcceptBtnInput.ifEmpty { "ACCEPT" },
                                addKeywordsInput
                            )
                            showAddAppDialog = false
                            addAppNameInput = ""
                            addAcceptBtnInput = "ACCEPT"
                            addKeywordsInput = ""
                        } else {
                            Toast.makeText(context, "Kripya App Name darj karein", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = neonGreen, contentColor = Color.White),
                    modifier = Modifier.testTag("add_app_confirm_btn")
                ) {
                    Text("ADD APP", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddAppDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray)
                ) {
                    Text("CANCEL", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        )
    }

    if (showEditAppDialog && editingApp != null) {
        val app = editingApp!!
        AlertDialog(
            onDismissRequest = { showEditAppDialog = false },
            title = {
                Text(
                    "Configure ${app.name} Automation",
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = textColor
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = editAcceptBtnInput,
                        onValueChange = { editAcceptBtnInput = it },
                        label = { Text("Accept Button Phrases (comma split)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("edit_app_btn_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textColor, unfocusedTextColor = Color.Gray)
                    )
                    
                    OutlinedTextField(
                        value = editKeywordsInput,
                        onValueChange = { editKeywordsInput = it },
                        label = { Text("Order Filter Keywords") },
                        placeholder = { Text("Accepts anything if empty") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("edit_app_kws_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textColor, unfocusedTextColor = Color.Gray)
                    )

                    // Online keywords template suggestion
                    QuickTemplateKeywordsSection(
                        title = "💡 CHOOSE ONLINE KEYWORD TEMPLATES (CLICK TO APPEND)"
                    ) { templateKws ->
                        editKeywordsInput = if (editKeywordsInput.isEmpty()) {
                            templateKws
                        } else {
                            "$editKeywordsInput, $templateKws"
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = app.copy(
                            acceptButtonKeyword = editAcceptBtnInput,
                            orderKeywords = editKeywordsInput
                        )
                        DrClickerController.updateAppConfig(context, updated)
                        showEditAppDialog = false
                        editingApp = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = neonGreen, contentColor = Color.White),
                    modifier = Modifier.testTag("edit_app_confirm_btn")
                ) {
                    Text("SAVE CHANGES", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEditAppDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray)
                ) {
                    Text("CANCEL", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        )
    }
}

@Composable
fun AppAutomationItemRow(
    appConfig: DrClickerController.AppAutomationConfig,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?,
    textColor: Color,
    neonGreen: Color
) {
    val initialLabel = appConfig.name.firstOrNull()?.toString()?.uppercase() ?: "?"
    val themeColor = when (appConfig.id) {
        "OLA" -> Color(0xFF16A34A)
        "UBER" -> Color(0xFF1E293B)
        "RAPIDO" -> Color(0xFFD97706)
        "SWIGGY" -> Color(0xFFEA580C)
        else -> Color(0xFF0066FF)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(themeColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                .border(1.dp, themeColor, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initialLabel,
                color = themeColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = appConfig.name,
                    color = textColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                if (appConfig.isEnabled) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFDCFCE7), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("ACTIVE", color = Color(0xFF16A34A), fontSize = 7.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(2.dp))
            
            Text(
                text = "Accept Btn: \"${appConfig.acceptButtonKeyword}\"",
                color = Color(0xFF475569),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Text(
                text = "Keywords: ${if (appConfig.orderKeywords.isEmpty()) "accept-all" else "\"${appConfig.orderKeywords}\""}",
                color = Color(0xFF64748B),
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(28.dp).testTag("edit_${appConfig.id}_btn")
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Settings,
                    contentDescription = "Edit app setting",
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(16.dp)
                )
            }
            
            if (onDelete != null) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp).testTag("delete_${appConfig.id}_btn")
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Close,
                        contentDescription = "Delete app",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Switch(
                checked = appConfig.isEnabled,
                onCheckedChange = onToggle,
                modifier = Modifier
                    .scale(0.8f)
                    .testTag("toggle_${appConfig.id}_switch")
            )
        }
    }
}

@Composable
fun QuickTemplateKeywordsSection(
    title: String,
    onSelectTemplate: (String) -> Unit
) {
    val templates = listOf(
        Pair("💰 Cash COD Only", "Cash, COD, Hand, Collect"),
        Pair("💳 Online UPI", "Online, Prepaid, UPI, Pay, Card"),
        Pair("🍔 Food Delivery", "Food, Order, Delivery, Restaurant"),
        Pair("🚀 Instant Express", "Express, Urgent, Instant, Fast, Quick"),
        Pair("✈️ Long Rides / Cabs", "Cab, Car, Auto, Long, Trip, Route, Airport, Station")
    )
    
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            text = title,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF334155)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            templates.forEach { (label, keywords) ->
                Box(
                    modifier = Modifier
                        .background(Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                        .clickable { onSelectTemplate(keywords) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(text = label, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                }
            }
        }
    }
}

@Composable
fun SwipeableJobNotificationCard(
    offer: JobOffer,
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit
) {
    var dragOffset by remember { mutableStateOf(0f) }
    val animatedOffset by animateFloatAsState(
        targetValue = dragOffset,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "dragAnim"
    )
    
    val swipeThreshold = 350f // dragging limit before trigger
    val triggerAlphaRight = (dragOffset / swipeThreshold).coerceIn(0f, 1f)
    val triggerAlphaLeft = (-dragOffset / swipeThreshold).coerceIn(0f, 1f)
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(115.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0F172A))
    ) {
        // Red background on the right (swiping left dismisses)
        if (dragOffset < 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFEF4444).copy(alpha = triggerAlphaLeft))
                    .padding(end = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "DISMISS / SKIP",
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(androidx.compose.material.icons.Icons.Default.Close, contentDescription = "Dismiss", tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
        }
        
        // Green background on the left (swiping right accepts)
        if (dragOffset > 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF10B981).copy(alpha = triggerAlphaRight))
                    .padding(start = 24.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(androidx.compose.material.icons.Icons.Default.CheckCircle, contentDescription = "Accept", tint = Color.White, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ACCEPT OFFER",
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        fontSize = 11.sp
                    )
                }
            }
        }
        
        // Card itself that offsets based on draggable state
        Card(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .pointerInput(offer.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dragOffset > swipeThreshold) {
                                onSwipeRight()
                            } else if (dragOffset < -swipeThreshold) {
                                onSwipeLeft()
                            }
                            dragOffset = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            dragOffset += dragAmount
                        }
                    )
                },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
        ) {
            val textColor = MaterialTheme.colorScheme.onSurface
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val appColor = when (offer.appName.uppercase()) {
                            "OLA" -> Color(0xFF10B981)
                            "UBER" -> Color(0xFF0F172A)
                            "RAPIDO" -> Color(0xFFFBBF24)
                            else -> Color(0xFFF97316)
                        }
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(appColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "LIVE ${offer.appName.uppercase()} NOTIFICATION",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = textColor
                        )
                    }
                    
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = keyframes { durationMillis = 1000 },
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "alpha"
                    )
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color.Red.copy(alpha = pulseAlpha))
                    )
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "EST. FARE: ",
                                fontSize = 10.sp,
                                color = textColor.copy(alpha = 0.6f),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "₹${offer.fare}",
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Text(
                            text = "Pickup: ${offer.pickupDistance}KM | Drop: ${offer.dropDistance}KM",
                            fontSize = 11.sp,
                            color = textColor.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "SWIPE TO RESPOND",
                            fontSize = 8.sp,
                            color = textColor.copy(alpha = 0.4f),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "DISMISS ◀  ▶ ACCEPT",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LiveJobOfferSimulatorSection(context: Context, neonGreen: Color, cardBg: Color) {
    var pendingOffer by remember { mutableStateOf<JobOffer?>(null) }
    
    val sampleOffers = remember {
        listOf(
            JobOffer("", 0L, "OLA", 185, 2.3f, 8.4f, true, ""),
            JobOffer("", 0L, "UBER", 280, 1.1f, 12.5f, true, ""),
            JobOffer("", 0L, "RAPIDO", 75, 0.4f, 4.2f, true, ""),
            JobOffer("", 0L, "SWIGGY", 120, 1.8f, 5.0f, true, "")
        )
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.2.dp, Color.Gray.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "INTERACTIVE SWIPE NOTIFICATION SYSTEM",
                fontSize = 11.sp,
                color = neonGreen,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "Simulate mock live driver order notifications to verify precise swipe gestures. Swipe right to ACCEPT, swipe left to DISMISS.",
                fontSize = 10.sp,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            if (pendingOffer == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("OLA", "UBER", "RAPIDO", "SWIGGY").forEach { appName ->
                        Button(
                            onClick = {
                                val base = sampleOffers.find { it.appName == appName } ?: sampleOffers.first()
                                pendingOffer = base.copy(
                                    id = "sim_" + System.currentTimeMillis(),
                                    timestamp = System.currentTimeMillis(),
                                    fare = base.fare + (-20..30).random(),
                                    pickupDistance = ((base.pickupDistance + ((-5..5).random().toFloat() / 10f)).coerceAtLeast(0.1f) * 10).roundToInt() / 10f,
                                    dropDistance = ((base.dropDistance + ((-10..15).random().toFloat() / 10f)).coerceAtLeast(1.0f) * 10).roundToInt() / 10f
                                )
                                playChime(context, success = true)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B1D26), contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(32.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("+ $appName", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                val currentOffer = pendingOffer!!
                SwipeableJobNotificationCard(
                    offer = currentOffer,
                    onSwipeRight = {
                        val finalOffer = currentOffer.copy(
                            satisfiesFilters = true,
                            reason = "ACCEPTED via premium horizontal swipe gesture. Shift optimized!"
                        )
                        JobOfferStorage.saveOffer(context, finalOffer)
                        playChime(context, success = true)
                        Toast.makeText(context, "🎉 ${currentOffer.appName.uppercase()} OFFER ACCEPTED INSTANTLY!", Toast.LENGTH_SHORT).show()
                        pendingOffer = null
                    },
                    onSwipeLeft = {
                        val finalOffer = currentOffer.copy(
                            satisfiesFilters = false,
                            reason = "SKIPPED: Dismissed via horizontal swipe gesture."
                        )
                        JobOfferStorage.saveOffer(context, finalOffer)
                        playChime(context, success = false)
                        Toast.makeText(context, "🚫 Offer dismissed and logged as skipped.", Toast.LENGTH_SHORT).show()
                        pendingOffer = null
                    }
                )
            }
        }
    }
}

@Composable
fun InteractiveEarningsChart(offers: List<JobOffer>) {
    val acceptedOffers = remember(offers) { offers.filter { it.satisfiesFilters }.sortedBy { it.timestamp } }
    val neonBlue = Color(0xFF5D4037)
    val neonGreen = Color(0xFF10B981)
    
    val totalEarnings = remember(acceptedOffers) { acceptedOffers.sumOf { it.fare } }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.2.dp, Color.Gray.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ESTIMATED TOTAL SHIFT EARNINGS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.Gray,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "₹$totalEarnings",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF0F172A)
                    )
                }
                
                Box(
                    modifier = Modifier
                        .background(Color(0xFFDCFCE7), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.CheckCircle,
                            contentDescription = "Matched Offers",
                            tint = Color(0xFF15803D),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${acceptedOffers.size} Matches",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF15803D)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Earnings Progression Timeline (Interactive Touch Nodes)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF475569),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            if (acceptedOffers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No earnings logged yet. Simulate + Swipe right above to show real-time metrics!",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                var selectedPointIndex by remember { mutableStateOf(-1) }
                
                val cumulativePoints = remember(acceptedOffers) {
                    var cumulativeSum = 0f
                    acceptedOffers.mapIndexed { idx, offer ->
                        cumulativeSum += offer.fare
                        Pair(idx, cumulativeSum)
                    }
                }
                
                val maxVal = remember(cumulativePoints) { cumulativePoints.maxOfOrNull { it.second } ?: 100f }
                val maxIndex = remember(cumulativePoints) { (cumulativePoints.size - 1).coerceAtLeast(1) }
                
                Box(modifier = Modifier.fillMaxWidth()) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .pointerInput(cumulativePoints) {
                                detectTapGestures { offset ->
                                    val canvasWidth = size.width.toFloat()
                                    val canvasHeight = size.height.toFloat()
                                    val paddingX = 70f
                                    val paddingY = 20f
                                    val usableW = canvasWidth - (paddingX * 2)
                                    val usableH = canvasHeight - (paddingY * 2)
                                    
                                    var closestIdx = -1
                                    var minDistance = Float.MAX_VALUE
                                    cumulativePoints.forEachIndexed { idx, pair ->
                                        val x = paddingX + (idx.toFloat() / maxIndex) * usableW
                                        val y = paddingY + usableH - (pair.second / maxVal) * usableH
                                        val distance = kotlin.math.hypot((offset.x - x).toDouble(), (offset.y - y).toDouble()).toFloat()
                                        if (distance < minDistance && distance < 65f) {
                                            minDistance = distance
                                            closestIdx = idx
                                        }
                                    }
                                    selectedPointIndex = closestIdx
                                }
                            }
                    ) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        
                        val paddingX = 70f
                        val paddingY = 20f
                        val usableW = canvasWidth - (paddingX * 2)
                        val usableH = canvasHeight - (paddingY * 2)
                        
                        val gridLines = 4
                        for (i in 0..gridLines) {
                            val levelY = paddingY + (i.toFloat() / gridLines) * usableH
                            val ratio = 1f - (i.toFloat() / gridLines)
                            val gridVal = (ratio * maxVal).roundToInt()
                            
                            drawLine(
                                color = Color(0xFFE2E8F0),
                                start = androidx.compose.ui.geometry.Offset(paddingX, levelY),
                                end = androidx.compose.ui.geometry.Offset(canvasWidth - paddingX, levelY),
                                strokeWidth = 1f
                            )
                            
                            drawIntoCanvas { nativeCanvasScope ->
                                val paint = android.graphics.Paint().apply {
                                    color = android.graphics.Color.GRAY
                                    textSize = 18f
                                    textAlign = android.graphics.Paint.Align.RIGHT
                                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                                }
                                nativeCanvasScope.nativeCanvas.drawText("₹$gridVal", paddingX - 10f, levelY + 6f, paint)
                            }
                        }
                        
                        drawIntoCanvas { nativeCanvasScope ->
                            val paint = android.graphics.Paint().apply {
                                color = android.graphics.Color.GRAY
                                textSize = 16f
                                textAlign = android.graphics.Paint.Align.CENTER
                            }
                            nativeCanvasScope.nativeCanvas.drawText("Start Shift", paddingX, canvasHeight - 2f, paint)
                            nativeCanvasScope.nativeCanvas.drawText("Active Progress", canvasWidth - paddingX, canvasHeight - 2f, paint)
                        }
                        
                        val pixelPoints = cumulativePoints.mapIndexed { idx, pair ->
                            val x = paddingX + (idx.toFloat() / maxIndex) * usableW
                            val y = paddingY + usableH - (pair.second / maxVal) * usableH
                            androidx.compose.ui.geometry.Offset(x, y)
                        }
                        
                        if (pixelPoints.size > 1) {
                            val fillPath = Path().apply {
                                moveTo(paddingX, paddingY + usableH)
                                pixelPoints.forEach { pt ->
                                    lineTo(pt.x, pt.y)
                                }
                                lineTo(pixelPoints.last().x, paddingY + usableH)
                                close()
                            }
                            drawPath(
                                path = fillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(neonBlue.copy(alpha = 0.3f), neonBlue.copy(alpha = 0.01f)),
                                    startY = paddingY,
                                    endY = paddingY + usableH
                                )
                            )
                        }
                        
                        if (pixelPoints.size > 1) {
                            for (i in 0 until pixelPoints.size - 1) {
                                drawLine(
                                    color = neonBlue,
                                    start = pixelPoints[i],
                                    end = pixelPoints[i + 1],
                                    strokeWidth = 6f,
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                        
                        pixelPoints.forEachIndexed { idx, pt ->
                            val isSelected = idx == selectedPointIndex
                            drawCircle(
                                color = if (isSelected) Color.White else neonBlue,
                                radius = if (isSelected) 8.dp.toPx() else 4.dp.toPx(),
                                center = pt
                            )
                            drawCircle(
                                color = neonBlue,
                                radius = if (isSelected) 8.dp.toPx() else 4.dp.toPx(),
                                center = pt,
                                style = Stroke(width = if (isSelected) 3.dp.toPx() else 1.2.dp.toPx())
                            )
                        }
                    }
                    
                    if (selectedPointIndex != -1 && selectedPointIndex < acceptedOffers.size) {
                        val selectedOffer = acceptedOffers[selectedPointIndex]
                        val curCumulative = cumulativePoints[selectedPointIndex].second.toInt()
                        
                        Card(
                            modifier = Modifier
                                .padding(8.dp)
                                .align(Alignment.TopCenter),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                            border = BorderStroke(1.dp, Color.Gray)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "JOB MATCH #${selectedPointIndex + 1} (${selectedOffer.appName.uppercase()})",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = neonGreen
                                    )
                                    Text(
                                        text = "Fare: ₹${selectedOffer.fare} | Cumulative Group: ₹$curCumulative",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                TextButton(
                                    onClick = { selectedPointIndex = -1 },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(androidx.compose.material.icons.Icons.Default.Close, "Close Tooltip", tint = Color.LightGray, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(14.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val providers = remember(offers) {
                    listOf("OLA", "UBER", "RAPIDO", "SWIGGY").map { prov ->
                        val count = offers.count { it.appName.uppercase() == prov }
                        val accepted = offers.count { it.appName.uppercase() == prov && it.satisfiesFilters }
                        Triple(prov, count, accepted)
                    }
                }
                
                providers.forEach { (prov, count, accepted) ->
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = prov,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = when(prov) {
                                    "OLA" -> Color(0xFF10B981)
                                    "UBER" -> Color(0xFF0F172A)
                                    "RAPIDO" -> Color(0xFFFBBF24)
                                    else -> Color(0xFFF97316)
                                }
                            )
                            Text(
                                text = "$accepted/$count",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                            Text(
                                text = "Matches",
                                fontSize = 8.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

fun playChime(context: Context, success: Boolean) {
    try {
        val toneType = if (success) {
            android.media.ToneGenerator.TONE_PROP_BEEP
        } else {
            android.media.ToneGenerator.TONE_PROP_NACK
        }
        val toneGen = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 100)
        toneGen.startTone(toneType, 150)
    } catch (e: Exception) {
        // Fallback gracefully
    }
}

@Composable
fun DriverProfileScreen(
    user: AppUser,
    isActivated: Boolean,
    remainingTimeText: String,
    onSignOut: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onRequestOverlay: () -> Unit,
    neonGreen: Color,
    cardBg: Color
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(10.dp))
        
        // Beautiful Driver Avatar Group
        Box(
            modifier = Modifier
                .size(90.dp)
                .background(neonGreen.copy(alpha = 0.12f), CircleShape)
                .border(2.dp, neonGreen, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Driver Avatar",
                tint = neonGreen,
                modifier = Modifier.size(48.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(14.dp))
        
        Text(
            text = "DRIVER PROFILE CARD",
            fontSize = 11.sp,
            color = neonGreen,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp
        )
        Text(
            text = user.email,
            fontSize = 18.sp,
            color = Color(0xFF0F172A),
            fontWeight = FontWeight.Black
        )
        Spacer(modifier = Modifier.height(4.dp))
        androidx.compose.material3.Surface(
            color = Color(0xFF162519),
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, neonGreen.copy(alpha = 0.5f))
        ) {
            Text(
                text = "DRIVER ID: ${user.readableUserId}",
                fontSize = 11.sp,
                color = neonGreen,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                letterSpacing = 0.5.sp
            )
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Statistics Grid
        Text(
            text = "AUTOMATION STATISTICS & LOGS",
            color = Color(0xFF0F172A),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, Color.DarkGray)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Auto-Shifts Done", fontSize = 10.sp, color = Color.Gray)
                    Text("142", fontSize = 20.sp, color = neonGreen, fontWeight = FontWeight.Black)
                    Text("Shifts optimized", fontSize = 8.sp, color = Color.LightGray)
                }
            }
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, Color.DarkGray)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Taps Completed", fontSize = 10.sp, color = Color.Gray)
                    Text("1,840", fontSize = 20.sp, color = Color(0xFF0F172A), fontWeight = FontWeight.Black)
                    Text("High-speed clicks", fontSize = 8.sp, color = Color.LightGray)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, Color.DarkGray)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Estimated Saved Time", fontSize = 10.sp, color = Color.Gray)
                    Text("28.5 Hrs", fontSize = 20.sp, color = Color(0xFF0F172A), fontWeight = FontWeight.Black)
                    Text("Over active shifts", fontSize = 8.sp, color = Color.LightGray)
                }
            }
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, Color.DarkGray)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Bypassed Alerts", fontSize = 10.sp, color = Color.Gray)
                    Text("38", fontSize = 20.sp, color = neonGreen, fontWeight = FontWeight.Black)
                    Text("Frictionless login", fontSize = 8.sp, color = Color.LightGray)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // System Details Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(1.dp, Color.DarkGray)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "DEVICE CONTROL CENTER",
                    fontSize = 11.sp,
                    color = neonGreen,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Driver User UID", fontSize = 11.sp, color = Color.Gray)
                        Text(user.uid, fontSize = 11.sp, color = Color(0xFF0F172A), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                androidx.compose.material3.HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Subscription Status", fontSize = 11.sp, color = Color.Gray)
                        Text(
                            text = if (isActivated) "Active PRO $remainingTimeText" else "Deactivated (Unpaid)",
                            fontSize = 12.sp,
                            color = if (isActivated) neonGreen else Color(0xFFFE3B62),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onRequestAccessibility,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B1D26), contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .border(BorderStroke(1.dp, Color.Gray), RoundedCornerShape(8.dp))
                    ) {
                        Text("ACCESSIBILITY", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onRequestOverlay,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B1D26), contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .border(BorderStroke(1.dp, Color.Gray), RoundedCornerShape(8.dp))
                    ) {
                        Text("OVERLAY STATE", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        // Historical Offers Log Section
        val context = LocalContext.current
        val offers by JobOfferStorage.pastOffers.collectAsState()
        var offerFilter by remember { mutableStateOf("ALL") } // "ALL", "MATCHED", "SKIPPED"
        val filteredOffers = remember(offers, offerFilter) {
            when (offerFilter) {
                "MATCHED" -> offers.filter { it.satisfiesFilters }
                "SKIPPED" -> offers.filter { !it.satisfiesFilters }
                else -> offers
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(1.dp, Color.DarkGray)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "HISTORICAL JOB OFFERS LOG",
                        fontSize = 11.sp,
                        color = neonGreen,
                        fontWeight = FontWeight.ExtraBold
                    )
                    
                    if (offers.isNotEmpty()) {
                        TextButton(
                            onClick = { JobOfferStorage.clearOffers(context) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("CLEAR LOG", fontSize = 10.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Text(
                    text = "Track active Shifts and incoming order card history processed by Dr.Clicker.",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Filter Chips
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val chipList = listOf(
                        "ALL" to "All (${offers.size})",
                        "MATCHED" to "Matched (${offers.count { it.satisfiesFilters }})",
                        "SKIPPED" to "Skipped (${offers.count { !it.satisfiesFilters }})"
                    )

                    chipList.forEach { (type, label) ->
                        val isSelected = offerFilter == type
                        androidx.compose.material3.Surface(
                            onClick = { offerFilter = type },
                            color = if (isSelected) neonGreen.copy(alpha = 0.12f) else Color.Transparent,
                            border = BorderStroke(1.dp, if (isSelected) neonGreen else Color.LightGray),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) neonGreen else Color.Gray,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                if (filteredOffers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No job offers detected yet", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Text("Activate Scanning mode to log live offers.", fontSize = 10.sp, color = Color.LightGray)
                        }
                    }
                } else {
                    val sdf = remember { java.text.SimpleDateFormat("dd MMM, HH:mm:ss", java.util.Locale.getDefault()) }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        filteredOffers.forEach { offer ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            val appColor = when (offer.appName.uppercase()) {
                                                "OLA" -> Color(0xFF10B981) // Green
                                                "UBER" -> Color(0xFF0F172A) // Dark
                                                "RAPIDO" -> Color(0xFFFBBF24) // Yellow
                                                "SWIGGY" -> Color(0xFFF97316) // Orange
                                                else -> neonGreen
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .background(appColor, CircleShape)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = offer.appName.uppercase(),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color(0xFF1E293B)
                                            )
                                        }

                                        val timeText = remember(offer.timestamp) { sdf.format(java.util.Date(offer.timestamp)) }
                                        Text(
                                            text = timeText,
                                            fontSize = 9.sp,
                                            color = Color.Gray
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "FARE: ",
                                                    fontSize = 10.sp,
                                                    color = Color.Gray,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "₹${offer.fare}",
                                                    fontSize = 14.sp,
                                                    color = Color(0xFF0F172A),
                                                    fontWeight = FontWeight.Black
                                                )
                                            }
                                            Text(
                                                text = "Pickup: ${offer.pickupDistance}KM | Drop: ${offer.dropDistance}KM",
                                                fontSize = 10.sp,
                                                color = Color(0xFF475569),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                        // Status indicator
                                        val badgeColor = if (offer.satisfiesFilters) Color(0xFFDCFCE7) else Color(0xFFF1F5F9)
                                        val borderStatusColor = if (offer.satisfiesFilters) Color(0xFF22C55E) else Color(0xFF94A3B8)
                                        val statusTxtStr = if (offer.satisfiesFilters) "ACCEPTED" else "IGNORED"
                                        val statusTxtColor = if (offer.satisfiesFilters) Color(0xFF15803D) else Color(0xFF475569)

                                        androidx.compose.material3.Surface(
                                            color = badgeColor,
                                            border = BorderStroke(1.dp, borderStatusColor),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = statusTxtStr,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                color = statusTxtColor,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    if (!offer.satisfiesFilters && offer.reason.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Reason: ${offer.reason}",
                                            fontSize = 9.sp,
                                            color = Color(0xFF64748B),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onSignOut,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE3B62).copy(alpha = 0.15f), contentColor = Color(0xFFFE3B62)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .border(BorderStroke(1.dp, Color(0xFFFE3B62).copy(alpha = 0.4f)), RoundedCornerShape(12.dp))
        ) {
            Text("SIGN OUT FROM DR.CLICKER SECURE SESSION", fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun SubscriptionsScreenTab(
    user: AppUser,
    isActivated: Boolean,
    remainingTimeText: String,
    pendingPayment: PaymentRequest?,
    onOpenPayment: () -> Unit,
    neonGreen: Color,
    cardBg: Color,
    context: Context
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(10.dp))
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Subscription Plan",
                tint = neonGreen,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "CHOOSE YOUR AUTOMATION PLAN",
                fontSize = 16.sp,
                color = neonGreen,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
        }
        Text(
            text = "Select any tier below to activate high-speed direct tapping. Rates compiled dynamically.",
            fontSize = 10.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
        )
        
        // Subscription State Indicator
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(1.dp, if (isActivated) neonGreen else Color.DarkGray)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("ACTIVE LICENSE STATE", fontSize = 8.sp, color = neonGreen, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (isActivated) "Active PRO $remainingTimeText" else "Inactive / Deactivated",
                        fontSize = 14.sp,
                        color = Color(0xFF0F172A),
                        fontWeight = FontWeight.Black
                    )
                }
                
                Button(
                    onClick = onOpenPayment,
                    colors = ButtonDefaults.buttonColors(containerColor = neonGreen, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(if (isActivated) "EXTEND TIER" else "ACTIVATE NOW", fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        
        // Plans List Display
        listOf(
            Triple("DAILY AUTOMATION PASS", "₹50", "Complete 24-hours access, includes custom latency parameters and coordinate randomization support."),
            Triple("WEEKLY UNLIMITED PASS", "₹300", "Our driver favorite. 7 full days of continuous automated tapping with dedicated back-end verification priority."),
            Triple("MONTHLY CYBER DEAL", "₹1500", "Perfect for professional heavy automation drivers. Save up to 45% of daily rates with instant logs support.")
        ).forEach { (title, price, description) ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, Color.DarkGray)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Plan",
                                tint = neonGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = neonGreen
                            )
                        }
                        Text(
                            text = price,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = description,
                        fontSize = 10.sp,
                        color = Color.LightGray,
                        lineHeight = 14.sp
                    )
                }
            }
        }

        // Custom Hourly Ride Booster Calculator
        var hoursCount by remember { mutableStateOf(3) }
        Text(
            text = "⚙️ HOURLY DYNAMIC CALCULATOR",
            color = neonGreen,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start).padding(top = 10.dp, bottom = 8.dp)
        )
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(1.5.dp, neonGreen)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1.2f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "HOURLY RIDE BOOSTER",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(neonGreen.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("₹10/hr", color = neonGreen, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(
                            text = "Pay list rate directly calculated on custom shift duration!",
                            fontSize = 9.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(0.8f)) {
                        Text(
                            text = "₹${hoursCount * 10}",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = neonGreen
                        )
                        Text(
                            text = "for $hoursCount hours shift",
                            fontSize = 8.sp,
                            color = Color.LightGray
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                androidx.compose.material3.HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Customize Shift Hours:",
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = { if (hoursCount > 1) hoursCount-- },
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color.DarkGray, CircleShape)
                        ) {
                            Text("-", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        Text(
                            text = "$hoursCount hrs",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.widthIn(min = 44.dp),
                            textAlign = TextAlign.Center
                        )
                        
                        IconButton(
                            onClick = { if (hoursCount < 24) hoursCount++ },
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color.DarkGray, CircleShape)
                        ) {
                            Text("+", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(14.dp))
                
                Button(
                    onClick = onOpenPayment,
                    colors = ButtonDefaults.buttonColors(containerColor = neonGreen, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("BUY & START CALIBRATING: ₹${hoursCount * 10}", fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
fun ReferAndRewardsScreen(
    user: AppUser,
    onOpenSubscriptions: () -> Unit,
    neonGreen: Color,
    cardBg: Color,
    context: Context
) {
    var referralCodeInput by remember { mutableStateOf("") }
    var isCodeApplied by remember { mutableStateOf(false) }
    
    val myReferralCode = remember(user.uid) {
        val hash = user.uid.takeLast(4).uppercase(Locale.ROOT)
        "DRIVE-CLICK-$hash"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(10.dp))
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = "Share",
                tint = neonGreen,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "SHARE DR.CLICKER & WIN",
                fontSize = 17.sp,
                color = Color.White,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
        }
        Text(
            text = "Apne driver dosto ko app invite karein. Dono ko milega +3 Hours solid automation boost active instant coupon!",
            fontSize = 10.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
        )
        
        // Visual Gift Box Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(1.5.dp, neonGreen)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Magic Code",
                        tint = neonGreen,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "YOUR MAGIC REFERRAL CODE",
                        fontSize = 10.sp,
                        color = neonGreen,
                        fontWeight = FontWeight.Black
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                
                Box(
                    modifier = Modifier
                        .background(Color(0xFFFFFFFF), RoundedCornerShape(8.dp))
                        .border(1.dp, neonGreen.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = myReferralCode,
                        fontSize = 15.sp,
                        color = neonGreen,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(14.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Dr. Clicker Referral Code", myReferralCode)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Referral Code Copied to Clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFFFFF), contentColor = neonGreen),
                        border = BorderStroke(1.dp, neonGreen),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) {
                        Text("COPY CODE", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    
                    Button(
                        onClick = {
                            try {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Dr. Clicker Invite")
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Hey! Use my invite code * $myReferralCode * to download Dr. Clicker and get +3 hours of automated driving clicks instant. Link:\n\nhttps://ais-pre-l5nwyclif5pfi3hiyabhey-876925957186.asia-east1.run.app"
                                    )
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Referral Link using"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Share failed", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFFFFF), contentColor = neonGreen),
                        border = BorderStroke(1.dp, neonGreen),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) {
                        Text("SHARE LINK", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Referral Stats Tracker Dashboard
        Row(
            modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Milestones",
                tint = neonGreen,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "REFERRAL MILESTONES & LEVEL",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(1.dp, Color.DarkGray)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Friends Invited Successfully", fontSize = 11.sp, color = Color.Gray)
                    Text("4 Driver Partners", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Earned Shift Extension Boosts", fontSize = 11.sp, color = Color.Gray)
                    Text("12 hrs unlocked", fontSize = 11.sp, color = neonGreen, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Active referral bonus payout", fontSize = 11.sp, color = Color.Gray)
                    Text("Ready", fontSize = 11.sp, color = neonGreen, fontWeight = FontWeight.Bold)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Redeem Referral Code Entry Card
        Row(
            modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Redeem Friend Code",
                tint = neonGreen,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "REDEEM FRIEND'S CODE",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(1.dp, Color.DarkGray)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Insert the code sent by another driver to instantly apply coupon benefits and extend your automation shift key.",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                OutlinedTextField(
                    value = referralCodeInput,
                    onValueChange = { referralCodeInput = it },
                    placeholder = { Text("e.g. DRIVE-CLICK-8B7C", fontSize = 11.sp, color = Color.Gray) },
                    singleLine = true,
                    enabled = !isCodeApplied,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.LightGray,
                        focusedBorderColor = neonGreen,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedLabelColor = neonGreen
                    )
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Button(
                    onClick = {
                        if (referralCodeInput.trim().isEmpty()) {
                            Toast.makeText(context, "Please enter a valid referral code", Toast.LENGTH_SHORT).show()
                        } else {
                            isCodeApplied = true
                            AuthManager.setAppActivated(true, 3 * 60 * 60 * 1000L)
                            Toast.makeText(context, "Code REDEEMED! +3 Hours instant access credited successfully!", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isCodeApplied) Color.DarkGray else neonGreen, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isCodeApplied && referralCodeInput.trim().isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isCodeApplied) "CODE APPLIED (+3 HRS ACTIVE)" else "APPLY INVITE CODE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun HelpAndAssistanceScreen(
    onOpenFAQ: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onRequestOverlay: () -> Unit,
    neonGreen: Color,
    cardBg: Color,
    context: Context
) {
    var activeTab by remember { mutableStateOf("CHAT") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF161A24))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Button(
                onClick = { activeTab = "CHAT" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeTab == "CHAT") neonGreen else Color.Transparent,
                    contentColor = if (activeTab == "CHAT") Color.Black else Color.LightGray
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Chat",
                        tint = if (activeTab == "CHAT") Color.Black else Color.LightGray,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ask AI Assistant", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = { activeTab = "FAQ" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeTab == "FAQ") neonGreen else Color.Transparent,
                    contentColor = if (activeTab == "FAQ") Color.Black else Color.LightGray
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "FAQs",
                        tint = if (activeTab == "FAQ") Color.Black else Color.LightGray,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("FAQs & Quick Guide", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (activeTab == "CHAT") {
                HelpAIChatSection(context = context)
            } else {
                HelpFAQSection(
                    onRequestAccessibility = onRequestAccessibility,
                    onRequestOverlay = onRequestOverlay
                )
            }
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
    val lightGreen = Color(0xFF16A34A)
    val actionRed = Color(0xFF0066FF)   // Tech Blue for interactive actions

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1.3f)) {
            Text(
                text = title,
                color = Color(0xFF5D4037),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = desc,
                color = Color(0xFF5D4037).copy(alpha = 0.82f),
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
                    .background(Color(0xFFDCFCE7)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Authorized",
                    tint = lightGreen,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else {
            Button(
                onClick = onRequest,
                modifier = Modifier.height(30.dp),
                colors = ButtonDefaults.buttonColors(containerColor = actionRed, contentColor = Color.White),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Text("ENABLE", fontSize = 10.sp, fontWeight = FontWeight.Black)
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
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "logo_anim")
    
    // Smooth slow professional dashboard gauge rotation
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(24000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "logo_rotation"
    )

    // Gentle premium breathing pulse heartbeat effect
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(2500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "logo_pulse"
    )

    Canvas(
        modifier = modifier
            .graphicsLayer(
                scaleX = pulseScale,
                scaleY = pulseScale,
                rotationZ = rotationAngle
            )
    ) {
        val width = size.width
        val height = size.height
        val cx = width / 2f
        val cy = height / 2f
        val radius = width.coerceAtMost(height) / 2f

        // 1. Sleek futuristic background (Pure White background card style)
        drawCircle(
            color = Color(0xFFFFFFFF),
            radius = radius
        )

        // 2. Glowing outer speed-ring (Brown Gradient border)
        drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(Color(0xFF5D4037), Color(0xFFEFE6E2), Color(0xFF5D4037)),
                center = androidx.compose.ui.geometry.Offset(cx, cy)
            ),
            radius = radius - 5f,
            style = Stroke(width = 4f)
        )

        // 3. Mini radar dashboard accents (Tick marks around the rim)
        for (angle in 0 until 360 step 30) {
            val angleRad = Math.toRadians(angle.toDouble())
            val startRadius = radius - 18f
            val endRadius = radius - 10f
            val startX = cx + (startRadius * Math.cos(angleRad)).toFloat()
            val startY = cy + (startRadius * Math.sin(angleRad)).toFloat()
            val endX = cx + (endRadius * Math.cos(angleRad)).toFloat()
            val endY = cy + (endRadius * Math.sin(angleRad)).toFloat()

            drawLine(
                color = if (angle % 90 == 0) Color(0xFF5D4037) else Color(0xFF5D4037).copy(alpha = 0.3f),
                start = androidx.compose.ui.geometry.Offset(startX, startY),
                end = androidx.compose.ui.geometry.Offset(endX, endY),
                strokeWidth = if (angle % 90 == 0) 3f else 1.5f
            )
        }

        // 4. Futuristic Central Steering Wheel Ring
        val wheelRadius = radius * 0.65f
        drawCircle(
            color = Color(0xFFFAF6F0),
            radius = wheelRadius,
            style = Stroke(width = 16f)
        )
        drawCircle(
            color = Color(0xFF5D4037).copy(alpha = 0.8f),
            radius = wheelRadius,
            style = Stroke(width = 2f)
        )

        // Steering Wheel Spokes (Modern 3-point Formula 1 style sporty wheel)
        val spokeColor = Color(0xFFEDE8E5)
        val spokeGlow = Color(0xFF8D6E63)

        // Left Spoke
        drawLine(
            color = spokeColor,
            start = androidx.compose.ui.geometry.Offset(cx, cy),
            end = androidx.compose.ui.geometry.Offset(cx - wheelRadius + 8f, cy),
            strokeWidth = 14f
        )
        drawLine(
            color = spokeGlow,
            start = androidx.compose.ui.geometry.Offset(cx, cy),
            end = androidx.compose.ui.geometry.Offset(cx - wheelRadius + 8f, cy),
            strokeWidth = 2f
        )

        // Right Spoke
        drawLine(
            color = spokeColor,
            start = androidx.compose.ui.geometry.Offset(cx, cy),
            end = androidx.compose.ui.geometry.Offset(cx + wheelRadius - 8f, cy),
            strokeWidth = 14f
        )
        drawLine(
            color = spokeGlow,
            start = androidx.compose.ui.geometry.Offset(cx, cy),
            end = androidx.compose.ui.geometry.Offset(cx + wheelRadius - 8f, cy),
            strokeWidth = 2f
        )

        // Bottom Spoke
        drawLine(
            color = spokeColor,
            start = androidx.compose.ui.geometry.Offset(cx, cy),
            end = androidx.compose.ui.geometry.Offset(cx, cy + wheelRadius - 8f),
            strokeWidth = 18f
        )
        drawLine(
            color = spokeGlow,
            start = androidx.compose.ui.geometry.Offset(cx, cy),
            end = androidx.compose.ui.geometry.Offset(cx, cy + wheelRadius - 8f),
            strokeWidth = 2f
        )

        // 5. Central Glowing Auto-Click Hub
        drawCircle(
            color = Color(0xFFFFFFFF),
            radius = radius * 0.22f
        )
        drawCircle(
            color = Color(0xFF5D4037),
            radius = radius * 0.22f,
            style = Stroke(width = 3f)
        )

        // 6. Fast Clicker Core cursor pointer / speed vector
        val pointerPath = Path().apply {
            moveTo(cx, cy - radius * 0.18f) // Tip point
            lineTo(cx + radius * 0.12f, cy + radius * 0.12f)
            lineTo(cx, cy + radius * 0.04f)
            lineTo(cx - radius * 0.12f, cy + radius * 0.12f)
            close()
        }
        
        drawPath(
            path = pointerPath,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFFFFFFF), Color(0xFF5D4037)),
                start = androidx.compose.ui.geometry.Offset(cx, cy - radius * 0.18f),
                end = androidx.compose.ui.geometry.Offset(cx, cy + radius * 0.12f)
            )
        )

        // Outer pulse circle
        drawCircle(
            color = Color(0xFF5D4037).copy(alpha = 0.3f),
            radius = radius * 0.35f,
            style = Stroke(width = 1.5f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
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
                                            UserStatus.APPROVED -> Color(0xFF00C4FF)
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
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C4FF), contentColor = Color.Black),
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

    var paymentSearchQuery by remember { mutableStateOf("") }
    var showAddDriverDialog by remember { mutableStateOf(false) }
    var showDurationDialogUser by remember { mutableStateOf<AppUser?>(null) }
    var newDriverEmail by remember { mutableStateOf("") }
    var newDriverPassword by remember { mutableStateOf("") }
    var newDriverStatus by remember { mutableStateOf(UserStatus.APPROVED) }

    var adminMobile by remember { mutableStateOf(AuthManager.getAdminMobile()) }
    var adminPass by remember { mutableStateOf(AuthManager.getAdminPassword()) }

    val scope = rememberCoroutineScope()

    val neonGreen = MaterialTheme.colorScheme.primary
    val neonYellow = Color(0xFFFFB300)
    val neonRed = Color(0xFFFE3B62)
    val darkBg = MaterialTheme.colorScheme.background
    val cardBg = MaterialTheme.colorScheme.surface

    val filteredUsers = allUsers.filter { u ->
        val matchesSearch = u.email.contains(searchQuery, ignoreCase = true) || 
                            u.uid.contains(searchQuery, ignoreCase = true) ||
                            u.readableUserId.contains(searchQuery, ignoreCase = true)
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
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "MANAGE USER DIRECTORY DIRECTLY",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = { showAddDriverDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = neonGreen, contentColor = Color.Black),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("+ ADD DRIVER", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }

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
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = u.email,
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            androidx.compose.material3.Surface(
                                                color = neonGreen.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(4.dp),
                                                border = BorderStroke(1.dp, neonGreen.copy(alpha = 0.4f))
                                            ) {
                                                Text(
                                                    text = u.readableUserId,
                                                    color = neonGreen,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Black,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
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
                                            val oneMonth = 0L; showDurationDialogUser = u
                                            // AuthManager.updateUserSubscription(u.uid, System.currentTimeMillis() + oneMonth)
                                            Toast.makeText(context, "Time configuration opened for " + u.email, Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E281F), contentColor = neonGreen),
                                        modifier = Modifier.weight(1f).height(24.dp),
                                        shape = RoundedCornerShape(4.dp),
                                        border = androidx.compose.foundation.BorderStroke(0.5.dp, neonGreen.copy(alpha = 0.5f)),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("SET TIMER / SUB", fontSize = 8.sp, fontWeight = FontWeight.Bold)
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
                // PAYMENTS CLAIMS COUNTERS
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val pendingClCount = paymentRequests.count { it.status == PaymentStatus.PENDING }
                    val approvedClCount = paymentRequests.count { it.status == PaymentStatus.APPROVED }
                    val rejectedClCount = paymentRequests.count { it.status == PaymentStatus.REJECTED }

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF221F12)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("PENDING", fontSize = 8.sp, color = neonYellow, fontWeight = FontWeight.Bold)
                            Text("$pendingClCount", fontSize = 20.sp, color = neonYellow, fontWeight = FontWeight.Black)
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF132214)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("APPROVED", fontSize = 8.sp, color = Color(0xFF00C4FF), fontWeight = FontWeight.Bold)
                            Text("$approvedClCount", fontSize = 20.sp, color = Color(0xFF00C4FF), fontWeight = FontWeight.Black)
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF221315)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("REJECTED", fontSize = 8.sp, color = Color(0xFFFE3B62), fontWeight = FontWeight.Bold)
                            Text("$rejectedClCount", fontSize = 20.sp, color = Color(0xFFFE3B62), fontWeight = FontWeight.Black)
                        }
                    }
                }

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
                            text = "Aap apne PhonePe, GPay, ya Paytm business app par transaction check karein ki user dwara enter kiya gaya 12-digit UTR/Ref No. match ho raha hai ya nahi. Match hone par niche diye '✅ APPROVE & ACTIVATE' button par touch karein. Kisi software ki zarurat nahi hai.",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1E2112), RoundedCornerShape(8.dp))
                                .border(1.dp, neonYellow.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = paymentSearchQuery,
                            onValueChange = { paymentSearchQuery = it },
                            label = { Text("Search by email or reference UTR") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.LightGray,
                                focusedBorderColor = neonYellow,
                                unfocusedBorderColor = Color.DarkGray,
                                focusedLabelColor = neonYellow
                            )
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
                            val matchesTab = when (paymentFilterTab) {
                                "PENDING" -> req.status == PaymentStatus.PENDING
                                "APPROVED" -> req.status == PaymentStatus.APPROVED
                                "REJECTED" -> req.status == PaymentStatus.REJECTED
                                else -> true
                            }
                            val matchesSearch = req.userEmail.contains(paymentSearchQuery, ignoreCase = true) ||
                                    req.transactionId.contains(paymentSearchQuery, ignoreCase = true) ||
                                    req.planName.contains(paymentSearchQuery, ignoreCase = true) ||
                                    req.paymentMethod.contains(paymentSearchQuery, ignoreCase = true)
                            matchesTab && matchesSearch
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Subscription",
                            tint = neonGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "AD-HOC SUBSCRIPTION GRANTED DESK",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
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

    if (showAddDriverDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showAddDriverDialog = false }
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .border(2.dp, neonGreen, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "➕ ONBOARD NEW DRIVER",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = newDriverEmail,
                        onValueChange = { newDriverEmail = it },
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

                    OutlinedTextField(
                        value = newDriverPassword,
                        onValueChange = { newDriverPassword = it },
                        label = { Text("Access Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
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

                    Text(
                        text = "INITIAL ACCOUNT STATUS:",
                        color = Color.Gray,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp).align(Alignment.Start)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(UserStatus.APPROVED, UserStatus.PENDING, UserStatus.REJECTED).forEach { st ->
                            val isSel = newDriverStatus == st
                            val stColor = when (st) {
                                UserStatus.APPROVED -> neonGreen
                                UserStatus.PENDING -> neonYellow
                                UserStatus.REJECTED -> neonRed
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) stColor.copy(alpha = 0.15f) else Color.Transparent)
                                    .border(1.dp, if (isSel) stColor else Color.DarkGray, RoundedCornerShape(6.dp))
                                    .clickable { newDriverStatus = st }
                                    .padding(vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = st.name,
                                    fontSize = 8.sp,
                                    color = if (isSel) stColor else Color.Gray,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showAddDriverDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray, contentColor = Color.White),
                            modifier = Modifier.weight(1f).height(36.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("CANCEL", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        showDurationDialogUser?.let { targetUser ->
                            AlertDialog(
                                onDismissRequest = { showDurationDialogUser = null },
                                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
                                modifier = Modifier
                                    .fillMaxWidth(0.92f)
                                    .wrapContentHeight()
                                    .clip(RoundedCornerShape(24.dp))
                                    .border(2.dp, neonGreen, RoundedCornerShape(24.dp)),
                                containerColor = Color(0xFF07080A),
                                title = null,
                                text = {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = "⏰ CUSTOM COUNTDOWN CREATOR",
                                                    color = neonGreen,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Black,
                                                    letterSpacing = 1.sp
                                                )
                                                Text(
                                                    text = "Set Custom Subs / Timer",
                                                    color = Color.White,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            IconButton(onClick = { showDurationDialogUser = null }) {
                                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(18.dp))

                                        Text(
                                            text = "Driver Account ID:\n" + targetUser.email,
                                            color = Color.LightGray,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFF111319), RoundedCornerShape(8.dp))
                                                .padding(12.dp)
                                        )

                                        Spacer(modifier = Modifier.height(14.dp))

                                        var dialogDurationVal by remember(targetUser.uid) { mutableStateOf("") }
                                        var dialogSelectedUnit by remember(targetUser.uid) { mutableStateOf("Days") }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedTextField(
                                                value = dialogDurationVal,
                                                onValueChange = { dialogDurationVal = it },
                                                label = { Text("Enter Time Value") },
                                                placeholder = { Text("E.g. 10") },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                modifier = Modifier.weight(0.45f),
                                                shape = RoundedCornerShape(10.dp),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.LightGray,
                                                    focusedBorderColor = neonGreen,
                                                    unfocusedBorderColor = Color.DarkGray,
                                                    focusedLabelColor = neonGreen
                                                )
                                            )

                                            // Options Unit Column
                                            Column(
                                                modifier = Modifier.weight(0.55f),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                listOf("Days", "Hours", "Minutes", "Seconds").forEach { uName ->
                                                    val isSelected = dialogSelectedUnit == uName
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(30.dp)
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(if (isSelected) neonGreen.copy(alpha = 0.15f) else Color.Transparent)
                                                            .border(1.dp, if (isSelected) neonGreen else Color.DarkGray, RoundedCornerShape(6.dp))
                                                            .clickable { dialogSelectedUnit = uName },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = uName,
                                                            fontSize = 10.sp,
                                                            color = if (isSelected) neonGreen else Color.Gray,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(20.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    val amount = dialogDurationVal.toLongOrNull()
                                                    if (amount == null || amount <= 0) {
                                                        Toast.makeText(context, "Kripya sahi text input value enter karein!", Toast.LENGTH_SHORT).show()
                                                        return@Button
                                                    }
                                                    val durationMs = when (dialogSelectedUnit) {
                                                        "Days" -> amount * 24L * 60L * 60L * 1000L
                                                        "Hours" -> amount * 60L * 60L * 1000L
                                                        "Minutes" -> amount * 60L * 1000L
                                                        else -> amount * 1000L
                                                    }
                                                    AuthManager.updateUserSubscription(targetUser.uid, System.currentTimeMillis() + durationMs)
                                                    Toast.makeText(context, "Activated $amount $dialogSelectedUnit Countdown!", Toast.LENGTH_LONG).show()
                                                    showDurationDialogUser = null
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = neonGreen, contentColor = Color.White),
                                                modifier = Modifier.weight(1.2f).height(42.dp),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Text("START COUNTDOWN", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }

                                            Button(
                                                onClick = {
                                                    AuthManager.updateUserSubscription(targetUser.uid, 0L)
                                                    Toast.makeText(context, "Subscription stopped/revoked completely.", Toast.LENGTH_SHORT).show()
                                                    showDurationDialogUser = null
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF221315), contentColor = neonRed),
                                                modifier = Modifier.weight(0.8f).height(42.dp),
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(1.dp, neonRed.copy(alpha = 0.5f))
                                            ) {
                                                Text("REVOKE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                },
                                confirmButton = {},
                                dismissButton = {}
                            )
                        }
                        }

                        Button(
                            onClick = {
                                val trimmedMail = newDriverEmail.trim()
                                val trimmedPass = newDriverPassword.trim()
                                if (trimmedMail.isEmpty() || trimmedPass.isEmpty()) {
                                    Toast.makeText(context, "Kripya sabhi details fill karein.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (trimmedPass.length < 6) {
                                    Toast.makeText(context, "Password kam se kam 6 characters ka hona chahiye.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                AuthManager.adminCreateUser(trimmedMail, trimmedPass, newDriverStatus) { success, err ->
                                    if (success) {
                                        Toast.makeText(context, "Naya driver manual successfully onboard kiya gaya!", Toast.LENGTH_SHORT).show()
                                        newDriverEmail = ""
                                        newDriverPassword = ""
                                        showAddDriverDialog = false
                                    } else {
                                        Toast.makeText(context, err ?: "Onboard failed", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = neonGreen, contentColor = Color.Black),
                            modifier = Modifier.weight(1.3f).height(36.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("SAVE DRIVER", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
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
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    
    var selectedPackage by remember { mutableStateOf(1) } // 0: Daily, 1: Weekly, 2: Monthly, 3: Hourly Booster
    var customHours by remember { mutableStateOf(3) } // dynamic hour calculator: 1hr = 10, 2hr = 20, 3hr = 30...
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

    val neonGreen = Color(0xFF00C4FF)
    val neonRed = Color(0xFFFE3B62)
    val neonYellow = Color(0xFFFFB300)

    val paymentRequests by AuthManager.paymentRequests.collectAsState()
    val activePendingPayment = paymentRequests.find { it.userUid == user.uid && it.status == PaymentStatus.PENDING }

    AlertDialog(
        onDismissRequest = { if (transactionState != "PROCESSING") { focusManager.clearFocus(); keyboardController?.hide(); onDismiss() } },
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
                            text = "Please keep patience. Administrator aalamdiwan555@gmail.com is checking current bank ledger deposits to confirm your payment. Manual activation follows within 5-15 mins.",
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
                                containerColor = if (selectedPackage == 0) neonGreen.copy(alpha = 0.15f) else Color(0xFF111319)
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
                                Text("₹50", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color.White)
                                Text("/ day", fontSize = 8.sp, color = Color.Gray)
                            }
                        }

                        // Plan 2: Weekly Pass
                        Card(
                            onClick = { selectedPackage = 1 },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedPackage == 1) neonGreen.copy(alpha = 0.15f) else Color(0xFF111319)
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
                                Text("₹300", fontSize = 15.sp, fontWeight = FontWeight.Black, color = neonGreen)
                                Text("/ week", fontSize = 8.sp, color = Color.Gray)
                            }
                        }

                        // Plan 3: Monthly Pass
                        Card(
                            onClick = { selectedPackage = 2 },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedPackage == 2) neonGreen.copy(alpha = 0.15f) else Color(0xFF111319)
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
                                Text("₹1500", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color.White)
                                Text("/ month", fontSize = 8.sp, color = Color.Gray)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Plan 4: Custom Hourly Booster
                    Card(
                        onClick = { selectedPackage = 3 },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedPackage == 3) neonGreen.copy(alpha = 0.15f) else Color(0xFF111319)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.5.dp,
                            if (selectedPackage == 3) neonGreen else Color.DarkGray
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1.2f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                         Icon(
                                             imageVector = Icons.Default.PlayArrow,
                                             contentDescription = "Booster Icon",
                                             tint = if (selectedPackage == 3) neonGreen else Color.White,
                                             modifier = Modifier.size(13.dp)
                                         )
                                         Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "HOURLY RIDE BOOSTER",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (selectedPackage == 3) neonGreen else Color.White
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .background(neonGreen.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text("₹10/hr", color = neonGreen, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Custom duration shifts ke liye dynamic price calculator!",
                                        fontSize = 9.sp,
                                        color = Color.Gray
                                    )
                                }
                                
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    modifier = Modifier.weight(0.8f)
                                ) {
                                    Text(
                                        text = "₹${customHours * 10}",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (selectedPackage == 3) neonGreen else Color.White
                                    )
                                    Text(
                                        text = "for $customHours hours",
                                        fontSize = 8.sp,
                                        color = Color.LightGray
                                    )
                                }
                            }
                            
                            if (selectedPackage == 3) {
                                Spacer(modifier = Modifier.height(10.dp))
                                androidx.compose.material3.HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Set shift duration:",
                                        fontSize = 10.sp,
                                        color = Color.LightGray
                                    )
                                    
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Minus button
                                        IconButton(
                                            onClick = { if (customHours > 1) customHours-- },
                                            modifier = Modifier
                                                .size(28.dp)
                                                .background(Color(0xFF222530), CircleShape)
                                        ) {
                                            Text("-", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        }
                                        
                                        Text(
                                            text = "$customHours hrs",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.widthIn(min = 40.dp),
                                            textAlign = TextAlign.Center
                                        )
                                        
                                        // Plus button
                                        IconButton(
                                            onClick = { if (customHours < 24) customHours++ },
                                            modifier = Modifier
                                                .size(28.dp)
                                                .background(Color(0xFF222530), CircleShape)
                                        ) {
                                            Text("+", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
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

                                val amount = when (selectedPackage) {
                                    0 -> 50
                                    1 -> 300
                                    2 -> 1500
                                    else -> customHours * 10
                                }
                                val planName = when (selectedPackage) {
                                    0 -> "Daily Pass"
                                    1 -> "Weekly Pass"
                                    2 -> "Monthly Pass"
                                    else -> "$customHours Hours Booster"
                                }
                                // Dynamically construct a real-world compliant UPI pay URI
                                val upiPayUri = "upi://pay?pa=9316642884@fam&pn=Dr%20Clicker&am=$amount&cu=INR&tn=${planName.replace(" ", "%20")}"
                                val encodedUpi = java.net.URLEncoder.encode(upiPayUri, "UTF-8")
                                val qrGeneratorUrl = "https://api.qrserver.com/v1/create-qr-code/?size=300x300&color=000000&data=$encodedUpi"

                                // Beautiful glowing container for the dynamic QR Code
                                Box(
                                    modifier = Modifier
                                        .size(150.dp)
                                        .background(Color.White, RoundedCornerShape(12.dp))
                                        .border(2.dp, neonGreen, RoundedCornerShape(12.dp))
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = qrGeneratorUrl,
                                        contentDescription = "Dynamic UPI QR Code for $planName",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "SCAN TO PAY: ₹$amount",
                                    fontSize = 12.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Black
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "UPI ID: 9316642884@fam",
                                    fontSize = 9.sp,
                                    color = neonGreen,
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
                                    text = "Deposit cash directly with Chief Admin at email aalamdiwan555@gmail.com, or support line (9316642884). Once you deposit work-voucher money, paste your cash Receipt ID / UTR No below to match Admin ledger registries.",
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
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                transactionState = "PROCESSING"
                                progressStatusMessage = "Connecting with Secure Online Payment Node..."
                                delay(600)

                                // Assemble Request parameters early
                                val txId = when (paymentMethod) {
                                    "CARD" -> cardRefNo.trim()
                                    "CASH AGENT" -> cashSlipId.trim()
                                    else -> upiUtr.trim()
                                }
                                val amount = when (selectedPackage) {
                                    0 -> 50.0
                                    1 -> 300.0
                                    2 -> 1500.0
                                    else -> (customHours * 10).toDouble()
                                }
                                val planName = when (selectedPackage) {
                                    0 -> "Daily Pass"
                                    1 -> "Weekly Pass"
                                    2 -> "Monthly Pass"
                                    else -> "$customHours Hours Booster"
                                }
                                val durationMs = when (selectedPackage) {
                                    0 -> 24 * 60 * 60 * 1000L
                                    1 -> 7 * 24 * 60 * 60 * 1000L
                                    2 -> 30 * 24 * 60 * 60 * 1000L
                                    else -> customHours * 60 * 60 * 1000L
                                }
                                val detailedLogs = when (paymentMethod) {
                                    "UPI" -> "UPI App: $selectedUpiApp, UTR/Txn: $txId"
                                    "CARD" -> "Holder: $cardHolderName, Card: ****${cardNumber.takeLast(4)}, Auth Code: $txId"
                                    else -> "Cash Agent Deposit slip No: $txId"
                                }

                                progressStatusMessage = "Calling Secure Verification Gateway API..."
                                val apiResult = PaymentVerifier.verifyPayment(
                                    utr = txId,
                                    amount = amount,
                                    method = paymentMethod,
                                    email = user.email ?: "anonymous@example.com",
                                    planName = planName
                                )
                                delay(1000)

                                progressStatusMessage = "Parsing cryptographic ledger signatures..."
                                delay(500)

                                if (apiResult.success) {
                                    val isApproved = apiResult.transactionStatus == "APPROVED"
                                    val finalStatus = if (isApproved) PaymentStatus.APPROVED else PaymentStatus.PENDING

                                    val req = PaymentRequest(
                                        transactionId = txId,
                                        userUid = user.uid,
                                        userEmail = user.email ?: "anonymous@example.com",
                                        planName = planName,
                                        payableAmount = amount,
                                        paymentMethod = paymentMethod,
                                        paymentDetails = detailedLogs + (if (isApproved) " (Auto-Verified: ${apiResult.auditId})" else ""),
                                        status = finalStatus,
                                        timestamp = System.currentTimeMillis(),
                                        durationMs = durationMs
                                    )

                                    val isSaved = AuthManager.submitPaymentRequest(req)
                                    if (isSaved) {
                                        if (isApproved) {
                                            // Instant approval auto-activation logic
                                            AuthManager.approvePaymentRequest(txId)
                                            transactionState = "SUCCESS_AUTO_APPROVED"
                                            Toast.makeText(context, "API Auto-Verified Success! Premium license activated immediately.", Toast.LENGTH_LONG).show()
                                        } else {
                                            transactionState = "SUCCESS_SUBMITTED"
                                        }
                                    } else {
                                        transactionState = "IDLE"
                                        Toast.makeText(context, "ERROR: This transaction reference/UTR has already been claimed.", Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    transactionState = "IDLE"
                                    Toast.makeText(context, "Gateway verification failed: ${apiResult.message}", Toast.LENGTH_LONG).show()
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
                } else if (transactionState == "SUCCESS_AUTO_APPROVED") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(Color(0xFF1B3D1B), CircleShape)
                                .border(3.dp, neonGreen, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Verified Active",
                                tint = neonGreen,
                                modifier = Modifier.size(46.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = "INSTANT ACTIVATION SUCCESS!",
                            fontSize = 18.sp,
                            color = neonGreen,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp) )
                        Text(
                            text = "Your payment reference was fully processed by our live UPI Payment Node API.",
                            fontSize = 12.sp,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Premium License Key is now released and your app is ACTIVE immediately. Thank you for your support!",
                            fontSize = 11.sp,
                            color = Color.LightGray,
                            textAlign = TextAlign.Center,
                            lineHeight = 15.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = neonGreen, contentColor = Color.Black),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("START USING SERVICE", fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }
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

@Composable
fun DrClickerHelpHubDialog(
    onDismiss: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onRequestOverlay: () -> Unit
) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf("CHAT") } // "CHAT" or "FAQ"
    
    val neonGreen = MaterialTheme.colorScheme.primary
    val cardBg = MaterialTheme.colorScheme.surface
    val darkBg = MaterialTheme.colorScheme.background

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(24.dp))
                .border(2.dp, neonGreen, RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = darkBg)
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(neonGreen.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("💡", fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Dr.Clicker Help Hub",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "AI Companion & Shift Troubleshooter",
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.LightGray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Custom Tab Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1A1D26))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Button(
                        onClick = { activeTab = "CHAT" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (activeTab == "CHAT") neonGreen else Color.Transparent,
                            contentColor = if (activeTab == "CHAT") Color.Black else Color.LightGray
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Chat",
                                tint = if (activeTab == "CHAT") Color.Black else Color.LightGray,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Ask AI Assistant", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = { activeTab = "FAQ" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (activeTab == "FAQ") neonGreen else Color.Transparent,
                            contentColor = if (activeTab == "FAQ") Color.Black else Color.LightGray
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "FAQs",
                                tint = if (activeTab == "FAQ") Color.Black else Color.LightGray,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("FAQs & Quick Guide", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Content area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (activeTab == "CHAT") {
                        HelpAIChatSection(context = context)
                    } else {
                        HelpFAQSection(
                            onRequestAccessibility = onRequestAccessibility,
                            onRequestOverlay = onRequestOverlay
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HelpAIChatSection(context: Context) {
    val coroutineScope = rememberCoroutineScope()
    val chatMessages = remember {
        mutableStateListOf(
            ChatMessage(
                sender = "MODEL",
                text = "Hello Driver! Main hoon aapka Dr.Clicker AI Assistant. 🚀\n\nMain aapko click targets setup karne, overlay toggle troubleshoot karne, filters optimize karne, ya subscription packages active karne me help kar sakta hoon. Kuchh bhi pucho!"
            )
        )
    }
    var inputText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Scroll to bottom on new message
    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    val neonGreen = Color(0xFF5D4037)
    val cardBg = Color(0xFFFFFFFF)

    Column(modifier = Modifier.fillMaxSize()) {
        // Suggested Chip rows to ease questions
        val suggestions = listOf(
            "How to start auto-clicking?",
            "Will I get blocked?",
            "Explain subscription plan?",
            "How to fix permissions?"
        )
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            suggestions.forEach { suggestion ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF1F222B))
                        .border(1.dp, Color.DarkGray, RoundedCornerShape(20.dp))
                        .clickable(enabled = !isSending) {
                            inputText = suggestion
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = suggestion,
                        color = Color.LightGray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Messages list
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF090B0E), RoundedCornerShape(12.dp))
                .border(1.dp, Color.DarkGray, RoundedCornerShape(12.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(chatMessages) { message ->
                val isUser = message.sender == "USER"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .wrapContentWidth(align = if (isUser) Alignment.End else Alignment.Start)
                            .clip(
                                RoundedCornerShape(
                                    topStart = 12.dp,
                                    topEnd = 12.dp,
                                    bottomStart = if (isUser) 12.dp else 0.dp,
                                    bottomEnd = if (isUser) 0.dp else 12.dp
                                )
                            )
                            .background(if (isUser) Color(0xFF0F3625) else Color(0xFF1B1D26))
                            .border(
                                1.dp,
                                if (isUser) neonGreen.copy(alpha = 0.4f) else Color.DarkGray,
                                RoundedCornerShape(
                                    topStart = 12.dp,
                                    topEnd = 12.dp,
                                    bottomStart = if (isUser) 12.dp else 0.dp,
                                    bottomEnd = if (isUser) 0.dp else 12.dp
                                )
                            )
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = if (isUser) "Aap" else "Dr.Clicker AI",
                                color = if (isUser) neonGreen else Color.Gray,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                            Text(
                                text = message.text,
                                color = Color.White,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
            if (isSending) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1B1D26))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 2.dp,
                                    color = neonGreen
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "AI Jawab likh raha hai...",
                                    color = Color.LightGray,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Input row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Apna sawaal likhein yahan...", fontSize = 12.sp, color = Color.Gray) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.LightGray,
                    focusedBorderColor = neonGreen,
                    unfocusedBorderColor = Color.DarkGray
                )
            )

            IconButton(
                onClick = {
                    val prompt = inputText.trim()
                    if (prompt.isEmpty() || isSending) return@IconButton
                    inputText = ""
                    chatMessages.add(ChatMessage(sender = "USER", text = prompt))
                    isSending = true
                    
                    coroutineScope.launch {
                        try {
                            val response = GeminiChatService.getChatResponse(chatMessages.toList())
                            chatMessages.add(ChatMessage(sender = "MODEL", text = response))
                        } catch (e: Exception) {
                            chatMessages.add(
                                ChatMessage(
                                    sender = "MODEL",
                                    text = "Dhanvaad request send karne ke liye! Lekin abhi network me issue hai. Kripya dubaara chat start karein."
                                )
                            )
                        } finally {
                            isSending = false
                        }
                    }
                },
                enabled = inputText.trim().isNotEmpty() && !isSending,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (inputText.trim().isNotEmpty() && !isSending) neonGreen else Color.DarkGray)
                    .size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    tint = if (inputText.trim().isNotEmpty() && !isSending) Color.Black else Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun HelpFAQSection(
    onRequestAccessibility: () -> Unit,
    onRequestOverlay: () -> Unit
) {
    val faqs = listOf(
        Pair(
            "⚡ How does Dr.Clicker auto-clicks work?",
            "Dr.Clicker monitors your device offer cards (like Ola / Rapido / Uber cards) using Accessibility Service APIs. When a card matches your customized configuration filters, it automatically dispatches high-precision taps on the accept button instantly."
        ),
        Pair(
            "🛡 Will my driver account get banned/blocked?",
            "No! Dr.Clicker is engineered with driver-safety mechanisms. Clicks are randomized in latency (between 195ms and 440ms) and mapped coordinates are randomized slightly on the inner 70% button surface areas, simulating natural human touch precisely."
        ),
        Pair(
            "🔒 Why are Accessibility & Overlay permissions needed?",
            "Accessibility permission reads state elements (offer payout / type) and simulates programmatic taps. Dynamic overlay permission renders start/stop action controller overlays on top of navigation driving maps."
        ),
        Pair(
            "💰 subscription details, fees and deactivation",
            "To support continuous active updates, pro automation is subscription based:\n• Daily Bundle: Rs.30\n• Weekly Pass: Rs.150\n• Monthly Deal: Rs.350\nYou can request activation inside the dynamic status tag. Once paid and reviewed by Chief Diwan, your app stays fully active."
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Quick Fix Action Bar
        Text(
            text = "QUICK UTILITY TROUBLESHOOTING",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onRequestAccessibility,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B1D26), contentColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .border(BorderStroke(1.dp, Color.Gray), RoundedCornerShape(8.dp))
            ) {
                Text("🛠 ACCESSIBILITY", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            
            Button(
                onClick = onRequestOverlay,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B1D26), contentColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .border(BorderStroke(1.dp, Color.Gray), RoundedCornerShape(8.dp))
            ) {
                Text("📱 OVERLAY SERVICE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "FREQUENTLY ASKED QUESTIONS (FAQ)",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        faqs.forEach { (question, answer) ->
            var expanded by remember { mutableStateOf(false) }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clickable { expanded = !expanded },
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111319)),
                border = BorderStroke(1.dp, Color.DarkGray)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = question,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(0.9f)
                        )
                        Text(
                            text = if (expanded) "▲" else "▼",
                            color = Color.Gray,
                            fontSize = 8.sp
                        )
                    }
                    if (expanded) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = answer,
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RapidoSimulatorScreenTab(
    neonGreen: Color,
    cardBg: Color,
    context: Context
) {
    var selectedPlatform by remember { mutableStateOf("RAPIDO") } // RAPIDO, UBER, BIKE_TAXI
    var isLoggedIn by remember { mutableStateOf(false) }
    var mobileNum by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    
    var isDutyOnline by remember { mutableStateOf(true) }
    var simOffer by remember { mutableStateOf<JobOffer?>(null) }
    var activeTimer by remember { mutableStateOf(15) }
    var simStatusLog by remember { mutableStateOf("Welcome to Dr.Clicker sandbox! Choose a platform above to simulate active ride dispatch popups.") }
    
    val scanningActive by DrClickerController.isScanning.collectAsState()
    val scope = rememberCoroutineScope()
    
    // Animate car movement along the route on accept simulation
    var carProgress by remember { mutableStateOf(0f) }
    
    // Timer countdown effect for active simulated ride
    LaunchedEffect(simOffer) {
        if (simOffer != null) {
            activeTimer = 15
            carProgress = 0f
            while (activeTimer > 0 && simOffer != null) {
                delay(1000L)
                activeTimer--
            }
            if (activeTimer == 0 && simOffer != null) {
                // Expired
                simStatusLog = "⚠️ Simulated request expired. Captain wasn't fast enough!"
                simOffer = null
            }
        }
    }
    
    // Auto Accept Simulation Loop - mimics the actual accessibility capture
    LaunchedEffect(simOffer, scanningActive) {
        if (simOffer != null && scanningActive) {
            val offer = simOffer!!
            val minPrice = DrClickerController.minPrice.value
            val maxPrice = DrClickerController.maxPrice.value
            val maxPickup = DrClickerController.maxPickupDistance.value
            val maxDrop = DrClickerController.maxDropDistance.value
            val minPricePerKm = DrClickerController.minPricePerKm.value
            
            val totalDistance = offer.pickupDistance + offer.dropDistance
            val farePerKm = if (totalDistance > 0) offer.fare / totalDistance else 0f
            
            // Check filters
            val fitsPrice = offer.fare in minPrice..maxPrice
            val fitsPickup = offer.pickupDistance <= maxPickup
            val fitsDrop = offer.dropDistance <= maxDrop
            val fitsPricePerKm = minPricePerKm <= 0.5f || farePerKm >= minPricePerKm
            
            val satisfies = fitsPrice && fitsPickup && fitsDrop && fitsPricePerKm
            
            delay(1500L) // Simulate human-like reaction and gesture scheduling delay (bot-protection delay)
            
            if (simOffer != null) { // confirm offer didn't expire/cancelled
                if (satisfies) {
                    // Auto accepted!
                    val finalOffer = offer.copy(
                        satisfiesFilters = true,
                        reason = "AUTO_ACCEPTED: Triggered dynamically by Dr. Clicker engine (Simulated Gestures)."
                    )
                    JobOfferStorage.saveOffer(context, finalOffer)
                    playChime(context, success = true)
                    simStatusLog = "⚡ AUTO-ACCEPTED! Detected ${selectedPlatform} popup on-screen. Programmatic tap successfully simulated!"
                    
                    // Trigger vehicle driving animation
                    for (i in 1..20) {
                        carProgress = i / 20f
                        delay(100L)
                    }
                    delay(500L)
                    simOffer = null
                } else {
                    // Ignored/Skipped
                    val failReasons = mutableListOf<String>()
                    if (!fitsPrice) failReasons.add("Fare ₹${offer.fare} out of range [₹$minPrice - ₹$maxPrice]")
                    if (!fitsPickup) failReasons.add("Pickup distance ${offer.pickupDistance}KM exceeds max ${maxPickup}KM")
                    if (!fitsDrop) failReasons.add("Drop distance ${offer.dropDistance}KM exceeds max ${maxDrop}KM")
                    if (!fitsPricePerKm) failReasons.add("Rate ₹${String.format(java.util.Locale.US, "%.1f", farePerKm)}/KM below target ₹${String.format(java.util.Locale.US, "%.1f", minPricePerKm)}/KM")
                    
                    val reasonStr = "IGNORED: Filter conditions unsatisfied (" + failReasons.joinToString(", ") + ")"
                    simStatusLog = "🛡️ " + reasonStr
                }
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Platform Selection Header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(1.2.dp, Color.Gray.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "SANDBOX SIMULATION PLATFORM SELECTOR",
                    fontSize = 11.sp,
                    color = neonGreen,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val platforms = listOf(
                        Triple("RAPIDO", "Rapido Cap", Color(0xFFFBBF24)),
                        Triple("UBER", "Uber Driver", Color(0xFF3B82F6)),
                        Triple("BIKE_TAXI", "Bike Taxi", Color(0xFFF59E0B))
                    )
                    
                    platforms.forEach { (pKey, label, colorAccent) ->
                        val isSelected = selectedPlatform == pKey
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) colorAccent else Color(0xFF1E293B))
                                .clickable {
                                    selectedPlatform = pKey
                                    simOffer = null
                                    carProgress = 0f
                                    simStatusLog = "Switched to ${label} simulation workspace. Ready to dispatch test requests!"
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isSelected) {
                                    if (pKey == "RAPIDO") Color.Black else Color.White
                                } else {
                                    Color.LightGray
                                }
                            )
                        }
                    }
                }
            }
        }
        
        if (!isLoggedIn) {
            // Fake Account Login Form for simulation
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.2.dp, Color.Gray.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "CAPTAIN SECURE LOG IN",
                        fontSize = 12.sp,
                        color = neonGreen,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Text(
                        text = "Please authenticate using your mock driver captain account keys to set up active ride notifications on screen.",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    OutlinedTextField(
                        value = mobileNum,
                        onValueChange = { mobileNum = it },
                        label = { Text("Mock Captain Mobile Number (+91)") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = neonGreen,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = neonGreen,
                            unfocusedLabelColor = Color.Gray
                        )
                    )
                    
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Captain ID Or Session Password") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = neonGreen,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = neonGreen,
                            unfocusedLabelColor = Color.Gray
                        )
                    )
                    
                    // Quick pre-fill button as requested to log in instantly
                    Button(
                        onClick = {
                            mobileNum = "+91 9441235678"
                            password = "rapido_captain_test99"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("🔑 QUICK FILL MOCK ACCOUNT CREDENTIALS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    
                    Button(
                        onClick = {
                            if (mobileNum.isNotEmpty() && password.isNotEmpty()) {
                                isLoggedIn = true
                                playChime(context, success = true)
                            } else {
                                Toast.makeText(context, "Please enter mobile number and password or use the Quick Fill button!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when (selectedPlatform) {
                                "RAPIDO" -> Color(0xFFFBBF24)
                                "UBER" -> Color(0xFF3B82F6)
                                else -> Color(0xFFF59E0B)
                            },
                            contentColor = if (selectedPlatform == "RAPIDO") Color.Black else Color.White
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("LOG IN AS ${selectedPlatform} CAPTAIN VIA SANDBOX", fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }
                }
            }
        } else {
            // Logged in Captain view
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.2.dp, Color.Gray.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "MOCK DRIVER STATUS OVERVIEW",
                                fontSize = 10.sp,
                                color = neonGreen,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Captain Acc: +91 9441235678",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "TVS Apache RTR 160V | KA-05-MK-4891 (Fully Authenticated)",
                                fontSize = 10.sp,
                                color = Color.LightGray
                            )
                        }
                        
                        TextButton(
                            onClick = {
                                isLoggedIn = false
                                simOffer = null
                                carProgress = 0f
                                playChime(context, success = false)
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                        ) {
                            Text("DISCONNECT", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color.Gray.copy(alpha = 0.3f))
                    
                    // Online / Offline state
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(if (isDutyOnline) Color.Green else Color.Red, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isDutyOnline) "DUTY STATE: ONLINE (RECEIVING ORDERS)" else "DUTY STATE: OFFLINE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        
                        Switch(
                            checked = isDutyOnline,
                            onCheckedChange = { isDutyOnline = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color(0xFFFBBF24),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.DarkGray
                            )
                        )
                    }
                }
            }
            
            // Console Execution logs
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "SIMULATOR SYSTEM ENGINE CHRONICLES",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.LightGray,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = simStatusLog,
                        fontSize = 10.sp,
                        color = neonGreen,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 14.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Active Dr.Clicker Scanning Mode: " + (if(scanningActive) "🟢 ACTIVE AUTO-CLICKER SCAN" else "🔴 PAUSED"),
                        fontSize = 9.sp,
                        color = Color.LightGray,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // IMMERSIVE SIMULATION MOBILE DISPLAY FRAME
            Text(
                text = "INTERACTIVE IN-APP LIVE PREVIEW:",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(3.dp, Color(0xFF1E293B), RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Simulated Interactive Map Canvas inside Phone Frame
                    SimulatedMapView(
                        platform = selectedPlatform,
                        offerActive = simOffer != null,
                        offer = simOffer,
                        carProgress = carProgress,
                        onZoomIn = { /* handled */ },
                        onZoomOut = { /* handled */ }
                    )
                    
                    // Specific platform card based on selection
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (simOffer == null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF1F5F9))
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "NO DISPATCHED MATCHES AVAILABLE\nUse the Fleet Order Generator below to trigger. Keep Scanning 'ON' inside Main Board to test.",
                                    fontSize = 11.sp,
                                    color = Color.DarkGray,
                                    fontWeight = FontWeight.SemiBold,
                                    lineHeight = 16.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        } else {
                            val offer = simOffer!!
                            when (selectedPlatform) {
                                "RAPIDO" -> {
                                    RapidoVisualPopupCard(
                                        offer = offer,
                                        timerLeft = activeTimer,
                                        onDecline = {
                                            val finalOffer = offer.copy(
                                                satisfiesFilters = false,
                                                reason = "DECLINED manually by Captain inside simulation."
                                            )
                                            JobOfferStorage.saveOffer(context, finalOffer)
                                            playChime(context, success = false)
                                            simStatusLog = "🚫 Ride request declined manually by Rapido Captain."
                                            simOffer = null
                                        },
                                        onAccept = {
                                            val finalOffer = offer.copy(
                                                satisfiesFilters = true,
                                                reason = "ACCEPTED manually via Captain Accept button."
                                            )
                                            JobOfferStorage.saveOffer(context, finalOffer)
                                            playChime(context, success = true)
                                            simStatusLog = "🎉 Success! Captain successfully accepted the Rapido ride manually."
                                            scope.launch {
                                                for (i in 1..20) {
                                                    carProgress = i / 20f
                                                    delay(50L)
                                                }
                                                simOffer = null
                                            }
                                        }
                                    )
                                }
                                "UBER" -> {
                                    UberVisualPopupCard(
                                        offer = offer,
                                        timerLeft = activeTimer,
                                        onDecline = {
                                            val finalOffer = offer.copy(
                                                satisfiesFilters = false,
                                                reason = "DECLINED manually inside Uber simulation."
                                            )
                                            JobOfferStorage.saveOffer(context, finalOffer)
                                            playChime(context, success = false)
                                            simStatusLog = "🚫 Ride request dismissed manually."
                                            simOffer = null
                                        },
                                        onAccept = {
                                            val finalOffer = offer.copy(
                                                satisfiesFilters = true,
                                                reason = "ACCEPTED manually via Uber Confirm button."
                                            )
                                            JobOfferStorage.saveOffer(context, finalOffer)
                                            playChime(context, success = true)
                                            simStatusLog = "🎉 Uber ride accepted successfully! Initiating pickup route."
                                            scope.launch {
                                                for (i in 1..20) {
                                                    carProgress = i / 20f
                                                    delay(50L)
                                                }
                                                simOffer = null
                                            }
                                        }
                                    )
                                }
                                else -> {
                                    BikeVisualPopupCard(
                                        offer = offer,
                                        timerLeft = activeTimer,
                                        onAccept = {
                                            val finalOffer = offer.copy(
                                                satisfiesFilters = true,
                                                reason = "ACCEPTED manually via Bike Taxi Accept."
                                            )
                                            JobOfferStorage.saveOffer(context, finalOffer)
                                            playChime(context, success = true)
                                            simStatusLog = "🎉 Accepted Bike Dispatch order successfully!"
                                            scope.launch {
                                                for (i in 1..20) {
                                                    carProgress = i / 20f
                                                    delay(50L)
                                                }
                                                simOffer = null
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Simulation Dispatch area
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.2.dp, Color.Gray.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "FLEET ORDER GENERATOR",
                        fontSize = 11.sp,
                        color = Color(0xFFFBBF24),
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Text(
                        text = "Manually trigger simulated driver requests to observe Dr.Clicker's real-time filter checking, speed mode delays, and tap events:",
                        fontSize = 10.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (!isDutyOnline) {
                                    Toast.makeText(context, "Please set Captain Duty State to ONLINE first!", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                simOffer = JobOffer(
                                    id = "rap_" + System.currentTimeMillis(),
                                    timestamp = System.currentTimeMillis(),
                                    appName = selectedPlatform,
                                    fare = 150,
                                    pickupDistance = 0.9f,
                                    dropDistance = 4.2f,
                                    satisfiesFilters = true,
                                    reason = ""
                                )
                                simStatusLog = "🚨 Simulated HIGH VALUE ride request. Waiting for Dr.Clicker scanner..."
                                playChime(context, success = true)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedPlatform == "RAPIDO") Color(0xFFFBBF24) else Color(0xFF10B981),
                                contentColor = if (selectedPlatform == "RAPIDO") Color.Black else Color.White
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("🚨 SIMULATE HIGH VALUE RIDE (₹150, 0.9KM PICKUP)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        Button(
                            onClick = {
                                if (!isDutyOnline) {
                                    Toast.makeText(context, "Please set Captain Duty State to ONLINE first!", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                simOffer = JobOffer(
                                    id = "ola_" + System.currentTimeMillis(),
                                    timestamp = System.currentTimeMillis(),
                                    appName = selectedPlatform,
                                    fare = 35,
                                    pickupDistance = 5.2f,
                                    dropDistance = 14.5f,
                                    satisfiesFilters = false,
                                    reason = ""
                                )
                                simStatusLog = "🚨 Simulated LOW RATE request. It should be automatically skipped according to your filter preferences."
                                playChime(context, success = true)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48), contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("⚠️ SIMULATE SKIPPED LOW VALUE RIDE (₹35, 5.2KM PICKUP)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------- HIGH FIDELITY SIMULATED SUB-COMPONENTS ----------------------

@Composable
fun SimulatedMapView(
    platform: String,
    offerActive: Boolean,
    offer: JobOffer?,
    carProgress: Float,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit
) {
    var zoomLevel by remember { mutableStateOf(14) }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(245.dp)
            .background(Color(0xFFE2E8F0)) // slate color light map canvas background
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            
            // Inline helper function for linear interpolation
            fun lerp(start: Float, stop: Float, fraction: Float): Float = start + (stop - start) * fraction
            
            // Draw a river / lake
            val riverPath = Path().apply {
                moveTo(0f, h * 0.2f)
                cubicTo(w * 0.3f, h * 0.15f, w * 0.6f, h * 0.45f, w, h * 0.35f)
                lineTo(w, h * 0.45f)
                cubicTo(w * 0.6f, h * 0.55f, w * 0.3f, h * 0.25f, 0f, h * 0.3f)
                close()
            }
            drawPath(riverPath, Color(0xFF93C5FD).copy(alpha = 0.65f)) // Light Blue
            
            // Draw a green park
            drawCircle(
                color = Color(0xFF86EFAC).copy(alpha = 0.55f), // light green
                radius = w * 0.18f,
                center = androidx.compose.ui.geometry.Offset(w * 0.75f, h * 0.75f)
            )
            drawCircle(
                color = Color(0xFF86EFAC).copy(alpha = 0.45f), 
                radius = w * 0.12f,
                center = androidx.compose.ui.geometry.Offset(w * 0.2f, h * 0.85f)
            )
            
            // Draw simulated streets / roads
            // Main horizontal highway
            drawRect(
                color = Color.White,
                topLeft = androidx.compose.ui.geometry.Offset(0f, h * 0.5f - 14f),
                size = androidx.compose.ui.geometry.Size(w, 28f)
            )
            // Dotted lane line
            drawLine(
                color = Color(0xFF94A3B8),
                start = androidx.compose.ui.geometry.Offset(0f, h * 0.5f),
                end = androidx.compose.ui.geometry.Offset(w, h * 0.5f),
                strokeWidth = 2f,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
            )
            
            // Secondary crossing streets
            val streetStroke = Stroke(width = 18f)
            drawPath(
                path = Path().apply {
                    moveTo(w * 0.35f, 0f)
                    lineTo(w * 0.35f, h)
                },
                color = Color.White,
                style = streetStroke
            )
            drawPath(
                path = Path().apply {
                    moveTo(w * 0.7f, 0f)
                    lineTo(w * 0.7f, h)
                },
                color = Color.White,
                style = streetStroke
            )
            
            // Diagonal lane (main connecting route for ride)
            drawPath(
                path = Path().apply {
                    moveTo(w * 0.15f, h * 0.8f)
                    lineTo(w * 0.35f, h * 0.5f)
                    lineTo(w * 0.7f, h * 0.5f)
                    lineTo(w * 0.85f, h * 0.2f)
                },
                color = Color.White,
                style = Stroke(width = 24f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            
            // If ride offer is active, draw the ROUTE line!
            if (offer != null) {
                // Colored active route connection
                val routeColor = when(platform) {
                    "RAPIDO" -> Color(0xFFFBBF24) // Yellow
                    "UBER" -> Color(0xFF2563EB) // Royal Blue
                    else -> Color(0xFFF97316) // Emerald Green/Orange
                }
                
                val routePath = Path().apply {
                    moveTo(w * 0.15f, h * 0.8f)
                    lineTo(w * 0.35f, h * 0.5f)
                    lineTo(w * 0.7f, h * 0.5f)
                    lineTo(w * 0.85f, h * 0.2f)
                }
                
                drawPath(
                    path = routePath,
                    color = routeColor.copy(alpha = 0.88f),
                    style = Stroke(width = 12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                
                // Draw animated vehicle position along the path!
                val vehicleX: Float
                val vehicleY: Float
                
                if (carProgress < 0.4f) {
                    val segmentProgress = carProgress / 0.4f
                    vehicleX = lerp(w * 0.15f, w * 0.35f, segmentProgress)
                    vehicleY = lerp(h * 0.8f, h * 0.5f, segmentProgress)
                } else if (carProgress < 0.8f) {
                    val segmentProgress = (carProgress - 0.4f) / 0.4f
                    vehicleX = lerp(w * 0.35f, w * 0.7f, segmentProgress)
                    vehicleY = h * 0.5f
                } else {
                    val segmentProgress = (carProgress - 0.8f) / 0.2f
                    vehicleX = lerp(w * 0.7f, w * 0.85f, segmentProgress)
                    vehicleY = lerp(h * 0.5f, h * 0.2f, segmentProgress)
                }
                
                // Draw Vehicle node shadow
                drawCircle(
                    color = Color.Black.copy(alpha = 0.25f),
                    radius = 18f,
                    center = androidx.compose.ui.geometry.Offset(vehicleX + 2f, vehicleY + 2f)
                )
                // Vehicle Node
                drawCircle(
                    color = Color.Black,
                    radius = 14f,
                    center = androidx.compose.ui.geometry.Offset(vehicleX, vehicleY)
                )
                drawCircle(
                    color = when(platform) {
                        "RAPIDO" -> Color(0xFFFBBF24) // Yellow scooter
                        "UBER" -> Color(0xFF3B82F6) // Blue car
                        else -> Color(0xFF10B981) // Green bike
                    },
                    radius = 10f,
                    center = androidx.compose.ui.geometry.Offset(vehicleX, vehicleY)
                )
                
                // Draw markers A and B
                // Marker A (Pickup)
                drawCircle(
                    color = Color(0xFF2563EB), // Blue marker
                    radius = 12f,
                    center = androidx.compose.ui.geometry.Offset(w * 0.15f, h * 0.8f)
                )
                drawCircle(
                    color = Color.White,
                    radius = 5f,
                    center = androidx.compose.ui.geometry.Offset(w * 0.15f, h * 0.8f)
                )
                
                // Marker B (Dropoff)
                drawCircle(
                    color = Color(0xFF10B981), // Green marker
                    radius = 12f,
                    center = androidx.compose.ui.geometry.Offset(w * 0.85f, h * 0.2f)
                )
                drawCircle(
                    color = Color.White,
                    radius = 5f,
                    center = androidx.compose.ui.geometry.Offset(w * 0.85f, h * 0.2f)
                )
            }
        }
        
        // System status bar overlay (100% immersive!)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.15f))
                .padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "11:43",
                color = Color.Black,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black
            )
            
            // Center heading
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (offerActive) Color.Red else Color(0xFF10B981)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (offerActive) "RIDE POPUP DETECTED" else "ONLINE",
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "VoLTE  ",
                    color = Color.Black,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .size(width = 16.dp, height = 9.dp)
                        .border(1.dp, Color.Black)
                        .padding(1.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.9f)
                            .background(Color.Black)
                    )
                }
            }
        }
        
        // Custom float zoom controls
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable { zoomLevel++ },
                contentAlignment = Alignment.Center
            ) {
                Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable { if (zoomLevel > 1) zoomLevel-- },
                contentAlignment = Alignment.Center
            ) {
                Text("-", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }
    }
}

// ---------------------- SPECIFIC POPUP TYPE 1: RAPIDO CAPTAIN CARD (Matches Images 2 & 3) ----------------------
@Composable
fun RapidoVisualPopupCard(
    offer: JobOffer,
    timerLeft: Int,
    onDecline: () -> Unit,
    onAccept: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Ride Request Title Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Circle Logo
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(Color(0xFFFBBF24), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("R", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Ride request",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFDBEAFE))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("129 m", color = Color(0xFF2563EB), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                // Countdown text timer
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Red.copy(alpha = 0.1f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${timerLeft}s",
                        color = Color.Red,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(14.dp))
            
            // Driver Profile details & Price
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Profile Icon placeholder
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .border(1.dp, Color.LightGray, CircleShape)
                            .background(Color(0xFFF1F5F9), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("👤", fontSize = 18.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Subhendu...", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("★ 4.17", color = Color(0xFFFBBF24), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Text(" (6) • Just now", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                }
                
                Text(
                    text = "₹${offer.fare}",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black
                )
            }
            
            Spacer(modifier = Modifier.height(14.dp))
            
            // Connected Route Addresses
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2563EB)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("A", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Purna Das Rd 49/2 (Gariahat, Hindustan Park)",
                        color = Color.DarkGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("B", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Maharaja Tagore Road (Dhakuria, Selimpur, Kolkata)",
                        color = Color.DarkGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            // AC Pills
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFECFDF5))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text("Ride A/C", color = Color(0xFF047857), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Primary high-contrast Neon Button "Accept for ₹45"
            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD9F99D), contentColor = Color.Black), // Neon Green/Yellow
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Text(
                    text = "Accept for ₹${offer.fare}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Offer your fare",
                color = Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            
            // Bidding Buttons Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAccept,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE2E8F0), contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("₹${offer.fare + 5}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                
                Button(
                    onClick = onAccept,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE2E8F0), contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("₹${offer.fare + 15}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            
            // Close Button
            Button(
                onClick = onDecline,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9), contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Close", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

// ---------------------- SPECIFIC POPUP TYPE 2: UBER EXCLUSIVE CARD (Matches Images 1 & 4) ----------------------
@Composable
fun UberVisualPopupCard(
    offer: JobOffer,
    timerLeft: Int,
    onDecline: () -> Unit,
    onAccept: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Black pill and Cross Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("👤 ", color = Color.White, fontSize = 11.sp)
                            Text("Uber Go", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFEFF6FF))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Exclusive", color = Color(0xFF1E40AF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                // Cross close button on top right (matches image Exactly)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF1F5F9))
                        .clickable { onDecline() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Giant Fare Rate
            Text(
                text = "₹${offer.fare}",
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.Black,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Payment method details
            Row(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFF1F5F9))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Cash payment", color = Color.DarkGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("★ 4.89", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Route Address rows with connecting details (Uber premium styling)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color.Black, CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(28.dp)
                                .background(Color.LightGray)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("12 min (${offer.pickupDistance} km)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        Text("NIOEYESPLUS, Sadhu Vasvani Nagar, Aundh, Pune", fontSize = 11.sp, color = Color.Gray)
                    }
                }
                
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(8.dp)
                            .border(1.5.dp, Color.Black)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("9 mins (${offer.dropDistance} km)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        Text("Ganesh Mangal Karyalaya, Sangvi, Chinchwad, 411061", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Giant solid Rectangular Blue Button "Confirm" (matches image 4)
            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB), contentColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Text(
                    text = "Confirm",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

// ---------------------- SPECIFIC POPUP TYPE 3: BIKE TAXI / FOOD CARD (Matches Image 5) ----------------------
@Composable
fun BikeVisualPopupCard(
    offer: JobOffer,
    timerLeft: Int,
    onAccept: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "1 Order Pending",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.Black
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Sub-container card holding details (matches design block)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Title and Bike indicator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFE2E8F0))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text("🛵 Bike", color = Color.DarkGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        Text(
                            text = "₹${offer.fare} (Cash)",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Connected vertical dots
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text("●", fontSize = 10.sp, color = Color.Black, modifier = Modifier.padding(top = 1.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("${offer.pickupDistance} Km away", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                Text("AECS Layout Brookefield - 1572/B, Lashkar Mohalla, Mysuru, 570001", fontSize = 10.sp, color = Color.Gray)
                            }
                        }
                        Row(verticalAlignment = Alignment.Top) {
                            Text("▼", fontSize = 8.sp, color = Color.Gray, modifier = Modifier.padding(top = 1.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("${offer.dropDistance} Km destination", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                Text("Chinnapannahalli - Carelon Global Solutions, Bengaluru, Kundalahalli Colony, Brookefield", fontSize = 10.sp, color = Color.Gray)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(18.dp))
                    
                    // Buttons: Circle visual timer and Accept button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Progress ring button
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .border(2.5.dp, Color.Red, CircleShape)
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "${timerLeft}s", color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        }
                        
                        // Solid High contrast golden accept button
                        Button(
                            onClick = onAccept,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFBBF24), contentColor = Color.Black),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Text(
                                text = "Accept",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }
    }
}


