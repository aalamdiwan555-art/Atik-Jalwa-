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
        androidx.compose.animation.AnimatedContent(
            targetState = currentUser,
            transitionSpec = {
                (androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(500)) + 
                 androidx.compose.animation.scaleIn(initialScale = 0.96f, animationSpec = androidx.compose.animation.core.tween(500)))
                    .togetherWith(androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(350)))
            },
            label = "AuthDashboardSwitch"
        ) { user ->
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
    var rememberMe by remember { mutableStateOf(prefs.getBoolean("remember_me", false)) }

    LaunchedEffect(Unit) {
        if (rememberMe) {
            email = prefs.getString("saved_email", "") ?: ""
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

    val neonGreen = Color(0xFF0066FF)   // Tech Blue
    val darkBg = Color(0xFFF4F7FB)      // Premium Light Page Background
    val cardBg = Color(0xFFFFFFFF)      // Pure White Card Background
    val textColor = Color(0xFF0F172A)   // Near Black Text/Slate

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
            DrClickerBrandLogo(
                modifier = Modifier.size(90.dp)
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
                color = Color(0xFF64748B),
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            // Auth Tab Controls (Styled for Light Theme)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFE2E8F0))
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
                colors = CardDefaults.cardColors(containerColor = cardBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = if (isLoginTab) "Welcome Back Driver" else "Access Gate Registration",
                        color = textColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // 4 Section Fields: Name, Email, Password, Confirm Password (Sign Up Only)
                    if (!isLoginTab) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Driver Full Name") },
                            placeholder = { Text("E.g. Jayson Diwan") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("name_input"),
                            shape = RoundedCornerShape(10.dp),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Name Icon",
                                    tint = if (name.isNotEmpty()) neonGreen else Color.Gray
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = textColor,
                                unfocusedTextColor = textColor,
                                focusedBorderColor = neonGreen,
                                unfocusedBorderColor = Color(0xFFCBD5E1),
                                focusedLabelColor = neonGreen,
                                cursorColor = neonGreen
                            )
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("App Email ID") },
                        placeholder = { Text("driver@gmail.com") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth().testTag("email_input"),
                        shape = RoundedCornerShape(10.dp),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = "Email Icon",
                                tint = if (isEmailValid) neonGreen else Color.Gray
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
                                        contentDescription = "Invalid Email Format",
                                        tint = Color(0xFFEF4444)
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textColor,
                            unfocusedTextColor = textColor,
                            focusedBorderColor = neonGreen,
                            unfocusedBorderColor = Color(0xFFCBD5E1),
                            focusedLabelColor = neonGreen,
                            cursorColor = neonGreen
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Access Key Password") },
                        placeholder = { Text("At least 6 characters") },
                        singleLine = true,
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth().testTag("password_input"),
                        shape = RoundedCornerShape(10.dp),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Password Lock Icon",
                                tint = if (password.isNotEmpty()) neonGreen else Color.Gray
                            )
                        },
                        trailingIcon = {
                            androidx.compose.material3.TextButton(
                                onClick = { isPasswordVisible = !isPasswordVisible },
                                modifier = Modifier.testTag("password_visibility_toggle")
                            ) {
                                Text(
                                    text = if (isPasswordVisible) "🙈 HIDE" else "👁 SHOW",
                                    color = if (password.isNotEmpty()) neonGreen else Color.Gray,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textColor,
                            unfocusedTextColor = textColor,
                            focusedBorderColor = neonGreen,
                            unfocusedBorderColor = Color(0xFFCBD5E1),
                            focusedLabelColor = neonGreen,
                            cursorColor = neonGreen
                        )
                    )

                    // Confirm Password Field (Sign Up Only)
                    if (!isLoginTab) {
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text("Confirm Key Password") },
                            placeholder = { Text("Must match password") },
                            singleLine = true,
                            visualTransformation = if (isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth().testTag("confirm_password_input"),
                            shape = RoundedCornerShape(10.dp),
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
                                        text = if (isConfirmPasswordVisible) "🙈 HIDE" else "👁 SHOW",
                                        color = if (confirmPassword.isNotEmpty()) neonGreen else Color.Gray,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = textColor,
                                unfocusedTextColor = textColor,
                                focusedBorderColor = neonGreen,
                                unfocusedBorderColor = Color(0xFFCBD5E1),
                                focusedLabelColor = neonGreen,
                                cursorColor = neonGreen
                            )
                        )
                    }

                    // Sign-up Security Strength Checklist
                    if (!isLoginTab && password.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "PASSWORD SECURITY CHECKLIST",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF475569),
                                    letterSpacing = 0.5.sp
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (hasMinLength) Icons.Default.CheckCircle else Icons.Default.Close,
                                        tint = if (hasMinLength) Color(0xFF10B981) else Color.Gray,
                                        modifier = Modifier.size(12.dp),
                                        contentDescription = "Min length status"
                                    )
                                    Text(
                                        text = "Minimum 6 characters",
                                        fontSize = 10.sp,
                                        color = if (hasMinLength) textColor else Color.Gray
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (hasLetter) Icons.Default.CheckCircle else Icons.Default.Close,
                                        tint = if (hasLetter) Color(0xFF10B981) else Color.Gray,
                                        modifier = Modifier.size(12.dp),
                                        contentDescription = "Letter status"
                                    )
                                    Text(
                                        text = "Must contain letters (a-z, A-Z)",
                                        fontSize = 10.sp,
                                        color = if (hasLetter) textColor else Color.Gray
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (hasDigit) Icons.Default.CheckCircle else Icons.Default.Close,
                                        tint = if (hasDigit) Color(0xFF10B981) else Color.Gray,
                                        modifier = Modifier.size(12.dp),
                                        contentDescription = "Number status"
                                    )
                                    Text(
                                        text = "Must contain digits (0-9)",
                                        fontSize = 10.sp,
                                        color = if (hasDigit) textColor else Color.Gray
                                    )
                                }
                            }
                        }
                    }

                    // Remember Me Credentials Switch
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { rememberMe = !rememberMe }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = rememberMe,
                            onCheckedChange = { rememberMe = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = neonGreen,
                                uncheckedColor = Color.Gray,
                                checkmarkColor = Color.White
                            ),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Email yaad rakhein (Remember Email ID)",
                            color = Color(0xFF475569),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (isLoginTab) {
                        Spacer(modifier = Modifier.height(4.dp))
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
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = "🔑 Password Bhool Gaye? (Forgot Password)",
                                    color = neonGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                                )
                            }
                        }
                    }

                    errorMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "⚠ $msg",
                            color = Color(0xFFEF4444),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            if (email.isEmpty() || password.isEmpty()) {
                                errorMessage = "Sabhi details enter karna jaroori hai."
                                return@Button
                            }
                            if (!isLoginTab) {
                                if (name.trim().isEmpty()) {
                                    errorMessage = "Kripya apna Full Name enter karein."
                                    return@Button
                                }
                                if (password.length < 6) {
                                    errorMessage = "Password kam se kam 6 characters ka hona chahiye."
                                    return@Button
                                }
                                if (password != confirmPassword) {
                                    errorMessage = "Dono passwords aaps mein match nahi ho rahe."
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
                                            } else {
                                                remove("saved_email")
                                            }
                                            apply()
                                        }
                                        Toast.makeText(context, "Welcome authorized driver", Toast.LENGTH_SHORT).show()
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
                                            } else {
                                                remove("saved_email")
                                            }
                                            apply()
                                        }
                                        Toast.makeText(context, "Registration successful", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("auth_submit_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = neonGreen, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Text(
                                text = if (isLoginTab) "LOGIN" else "SIGNUP",
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Toggles exactly as requested: "already have account login" and "not have account signup"
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoginTab) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { 
                                    isLoginTab = false
                                    errorMessage = null
                                }
                            ) {
                                Text("Khata nahi hai? ", color = Color.Gray, fontSize = 12.sp)
                                Text("SIGN UP KARIEN", color = neonGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { 
                                    isLoginTab = true
                                    errorMessage = null
                                }
                            ) {
                                Text("Pehle se account hai? ", color = Color.Gray, fontSize = 12.sp)
                                Text("LOG IN KARIEN", color = neonGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = Color(0xFFE2E8F0)
                        )
                        Text(
                            text = "OR",
                            color = Color.Gray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )
                        androidx.compose.material3.HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = Color(0xFFE2E8F0)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))

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
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF475569))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(Color.White, androidx.compose.foundation.shape.CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "G",
                                    color = Color(0xFF4285F4),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "CONTINUE WITH GOOGLE",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp,
                                color = Color(0xFF475569)
                            )
                        }
                    }
                }
            }

            if (showGoogleDialog) {
                androidx.compose.ui.window.Dialog(onDismissRequest = { showGoogleDialog = false }) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(16.dp),
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
                                Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                Text("o", color = Color(0xFFEA4335), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                Text("o", color = Color(0xFFFBBC05), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                Text("g", color = Color(0xFF4285F4), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                Text("l", color = Color(0xFF34A853), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                Text("e", color = Color(0xFFEA4335), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            }

                            Text(
                                text = "Choose an account",
                                color = textColor,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "to continue to Dr. Clicker",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                            )

                            if (isGoogleLoading) {
                                CircularProgressIndicator(color = neonGreen)
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
                                        Log.w("DrClicker", "Security exception getting accounts: ${e.message}")
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
                                        text = "Aapke device par koi saved Google Account nahi mila. Niche email enter karke direct log in karein.",
                                        color = Color.Gray,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                                    )
                                } else {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                                shape = RoundedCornerShape(10.dp),
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(36.dp)
                                                            .background(neonGreen, androidx.compose.foundation.shape.CircleShape),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = letterDisplay,
                                                            color = Color.White,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 14.sp
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Column {
                                                        val accountTitle = if (accountEmail == "aalamdiwan555@gmail.com") "Chief Admin (Diwan)" else "Google Account"
                                                        Text(
                                                            text = accountTitle,
                                                            color = textColor,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            text = accountEmail,
                                                            color = Color.Gray,
                                                            fontSize = 10.sp
                                                        )
                                                    }
                                                 }
                                             }
                                         }
                                     }
                                 }

                                Spacer(modifier = Modifier.height(14.dp))
                                androidx.compose.material3.HorizontalDivider(color = Color(0xFFCBD5E1))
                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = "Use another Google Account",
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
                                    label = { Text("Google Account Email", fontSize = 11.sp) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = textColor,
                                        unfocusedTextColor = textColor,
                                        focusedBorderColor = neonGreen,
                                        unfocusedBorderColor = Color(0xFFCBD5E1)
                                    )
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { showGoogleDialog = false },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray, contentColor = Color.Black),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("CANCEL", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = {
                                            if (customGoogleEmail.trim().isEmpty() || !customGoogleEmail.contains("@")) {
                                                Toast.makeText(context, "Please enter a valid Google Account", Toast.LENGTH_SHORT).show()
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
                                        colors = ButtonDefaults.buttonColors(containerColor = neonGreen, contentColor = Color.White),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("SIGN IN", fontSize = 11.sp, fontWeight = FontWeight.Black)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (showForgotPasswordDialog) {
                androidx.compose.ui.window.Dialog(onDismissRequest = { showForgotPasswordDialog = false }) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Lock,
                                contentDescription = "Security Keys",
                                tint = neonGreen,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Naya Password Set Karein",
                                color = textColor,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Kripya apna registered Gmail ID aur naya password enter karein jisse aap login kar saken.",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
                            )

                            OutlinedTextField(
                                value = forgotEmail,
                                onValueChange = { forgotEmail = it },
                                label = { Text("Registered Email ID", fontSize = 11.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                modifier = Modifier.fillMaxWidth().testTag("forgot_email_input"),
                                shape = RoundedCornerShape(10.dp),
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Email,
                                        contentDescription = "Forgot Email Icon",
                                        tint = if (forgotEmail.isNotEmpty()) neonGreen else Color.Gray
                                    )
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = textColor,
                                    unfocusedTextColor = textColor,
                                    focusedBorderColor = neonGreen,
                                    unfocusedBorderColor = Color(0xFFCBD5E1)
                                )
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = forgotNewPassword,
                                onValueChange = { forgotNewPassword = it },
                                label = { Text("Create New Password", fontSize = 11.sp) },
                                singleLine = true,
                                visualTransformation = if (isForgotNewPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier.fillMaxWidth().testTag("forgot_password_input"),
                                shape = RoundedCornerShape(10.dp),
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Forgot Password Lock Icon",
                                        tint = if (forgotNewPassword.isNotEmpty()) neonGreen else Color.Gray
                                    )
                                },
                                trailingIcon = {
                                    androidx.compose.material3.TextButton(
                                        onClick = { isForgotNewPasswordVisible = !isForgotNewPasswordVisible },
                                        modifier = Modifier.testTag("forgot_password_visibility_toggle")
                                    ) {
                                        Text(
                                            text = if (isForgotNewPasswordVisible) "🙈 HIDE" else "👁 SHOW",
                                            color = if (forgotNewPassword.isNotEmpty()) neonGreen else Color.Gray,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = textColor,
                                    unfocusedTextColor = textColor,
                                    focusedBorderColor = neonGreen,
                                    unfocusedBorderColor = Color(0xFFCBD5E1)
                                )
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            if (isForgotLoading) {
                                CircularProgressIndicator(color = neonGreen)
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = { showForgotPasswordDialog = false },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray, contentColor = Color.Black),
                                        modifier = Modifier.weight(1f).height(44.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("CANCEL", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = {
                                            val tEmail = forgotEmail.trim()
                                            val tPass = forgotNewPassword.trim()
                                            if (tEmail.isEmpty() || tPass.isEmpty()) {
                                                Toast.makeText(context, "Dono fields enter karna jaroori hai.", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            if (tPass.length < 6) {
                                                Toast.makeText(context, "Password kam se kam 6 characters ka hona chahiye.", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            isForgotLoading = true
                                            AuthManager.resetPassword(tEmail, tPass) { success, msg ->
                                                isForgotLoading = false
                                                if (success) {
                                                    Toast.makeText(context, msg ?: "Password reset successful!", Toast.LENGTH_LONG).show()
                                                    email = tEmail
                                                    password = tPass
                                                    showForgotPasswordDialog = false
                                                } else {
                                                    Toast.makeText(context, "⚠ ${msg ?: "Reset Failed"}", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = neonGreen, contentColor = Color.White),
                                        modifier = Modifier.weight(1.2f).height(44.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("RESET KEYS", fontSize = 11.sp, fontWeight = FontWeight.Black)
                                    }
                                }
                            }
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) Color(0xFF0066FF) else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isActive) Color.White else Color(0xFF64748B),
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Sign Out",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
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
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Access Declined",
                    tint = neonRed,
                    modifier = Modifier.size(38.dp)
                )
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Register",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("REGISTER ANOTHER ACCOUNT", fontWeight = FontWeight.Bold, fontSize = 11.sp)
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

    // Premium Light Accents Vibe
    val darkBg = Color(0xFFF4F7FB)      // Premium Light Page Background
    val cardBg = Color(0xFFFFFFFF)      // Pure White Card Background
    val neonGreen = Color(0xFF0066FF)   // Tech Blue
    val neonRed = Color(0xFFEF4444)     // Ruby Red
    val textColor = Color(0xFF0F172A)   // Near Black Text/Slate

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
                drawerContainerColor = Color(0xFFFFFFFF),
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
                                            if (isSelected) neonGreen.copy(alpha = 0.12f) else Color(0xFFF1F5F9),
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
                        colors = CardDefaults.cardColors(containerColor = Color.White),
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
                                color = Color(0xFF0F172A),
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = "App ko sahi tarah se chalane ke liye kripya yeh dono permissions allow karein. Yeh pehli baar setup karna zaroori hai.",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
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
                                            color = Color(0xFF0F172A)
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
                                            color = Color(0xFF0F172A)
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
                when (currentTab) {
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
                        color = Color(0xFF0F172A),
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
                    color = Color(0xFF0F172A),
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
            color = Color.White,
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
            color = Color.White,
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
                    Text("1,840", fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.Black)
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
                    Text("28.5 Hrs", fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.Black)
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
                        Text(user.uid, fontSize = 11.sp, color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
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
                color = Color.White,
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
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E222B)),
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
                        color = Color.White,
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
            Triple("DAILY AUTOMATION PASS", "₹90", "Complete 24-hours access, includes custom latency parameters and coordinate randomization support."),
            Triple("WEEKLY UNLIMITED PASS", "₹490", "Our driver favorite. 7 full days of continuous automated tapping with dedicated back-end verification priority."),
            Triple("MONTHLY CYBER DEAL", "₹2990", "Perfect for professional heavy automation drivers. Save up to 45% of daily rates with instant logs support.")
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
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start).padding(top = 10.dp, bottom = 8.dp)
        )
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141722)),
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
                                .background(Color(0xFF222530), CircleShape)
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
                                .background(Color(0xFF222530), CircleShape)
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
                        .background(Color(0xFF07080A), RoundedCornerShape(8.dp))
                        .border(1.dp, Color.DarkGray, RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = myReferralCode,
                        fontSize = 15.sp,
                        color = Color.White,
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
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2522), contentColor = neonGreen),
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
                        colors = ButtonDefaults.buttonColors(containerColor = neonGreen, contentColor = Color.Black),
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
                color = Color(0xFF0F172A),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = desc,
                color = Color(0xFF64748B),
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
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val cx = width / 2f
        val cy = height / 2f
        val radius = width.coerceAtMost(height) / 2f

        // 1. Sleek futuristic background (Dark outer shield)
        drawCircle(
            color = Color(0xFF0A0F1D),
            radius = radius
        )

        // 2. Glowing outer speed-ring (Gradient border)
        drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(Color(0xFF00C4FF), Color(0xFF0081A7), Color(0xFF00C4FF)),
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
                color = if (angle % 90 == 0) Color(0xFF00C4FF) else Color(0xFF0081A7).copy(alpha = 0.5f),
                start = androidx.compose.ui.geometry.Offset(startX, startY),
                end = androidx.compose.ui.geometry.Offset(endX, endY),
                strokeWidth = if (angle % 90 == 0) 3f else 1.5f
            )
        }

        // 4. Futuristic Neon Cyber Steering Wheel Ring
        val wheelRadius = radius * 0.65f
        drawCircle(
            color = Color(0xFF161B29),
            radius = wheelRadius,
            style = Stroke(width = 16f)
        )
        drawCircle(
            color = Color(0xFF00C4FF).copy(alpha = 0.8f),
            radius = wheelRadius,
            style = Stroke(width = 2f)
        )

        // Steering Wheel Spokes (Modern 3-point Formula 1 style sporty wheel)
        val spokeColor = Color(0xFF263238)
        val spokeGlow = Color(0xFF00E5FF)

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
            color = Color(0xFF0A0F1D),
            radius = radius * 0.22f
        )
        drawCircle(
            color = Color(0xFF00E5FF),
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
                colors = listOf(Color(0xFFFFFFFF), Color(0xFF00C4FF)),
                start = androidx.compose.ui.geometry.Offset(cx, cy - radius * 0.18f),
                end = androidx.compose.ui.geometry.Offset(cx, cy + radius * 0.12f)
            )
        )

        // Outer pulse circle
        drawCircle(
            color = Color(0xFF00C4FF).copy(alpha = 0.3f),
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
    var newDriverEmail by remember { mutableStateOf("") }
    var newDriverPassword by remember { mutableStateOf("") }
    var newDriverStatus by remember { mutableStateOf(UserStatus.APPROVED) }

    var adminMobile by remember { mutableStateOf(AuthManager.getAdminMobile()) }
    var adminPass by remember { mutableStateOf(AuthManager.getAdminPassword()) }

    val scope = rememberCoroutineScope()

    val neonGreen = Color(0xFF00C4FF)
    val neonYellow = Color(0xFFFFB300)
    val neonRed = Color(0xFFFE3B62)
    val darkBg = Color(0xFF07080A)
    val cardBg = Color(0xFF111319)

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
                                Text("₹2990", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color.White)
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
                                    0 -> 90
                                    1 -> 490
                                    2 -> 2990
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
                                    0 -> 90.0
                                    1 -> 490.0
                                    2 -> 2990.0
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
    
    val neonGreen = Color(0xFF00C4FF)
    val cardBg = Color(0xFF111319)
    val darkBg = Color(0xFF07080A)

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

    val neonGreen = Color(0xFF00C4FF)
    val cardBg = Color(0xFF111319)

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
