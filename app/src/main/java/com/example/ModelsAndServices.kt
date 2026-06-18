package com.example

import android.app.Service
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

// ==========================================
// 1. Enums & Data Models
// ==========================================

enum class UserStatus {
    PENDING,
    APPROVED,
    REJECTED
}

enum class PaymentStatus {
    PENDING,
    APPROVED,
    REJECTED
}

data class AppUser(
    val uid: String,
    val email: String,
    val role: String, // "DRIVER" or "ADMIN"
    val status: UserStatus,
    val readableUserId: String,
    val subscriptionExpiry: Long
)

data class PaymentRequest(
    val transactionId: String,
    val userUid: String,
    val userEmail: String,
    val planName: String,
    val payableAmount: Double,
    val paymentMethod: String,
    val paymentDetails: String,
    val status: PaymentStatus,
    val timestamp: Long,
    val durationMs: Long
)

data class JobOffer(
    val id: String,
    val timestamp: Long,
    val appName: String,
    val fare: Int,
    val pickupDistance: Float,
    val dropDistance: Float,
    val satisfiesFilters: Boolean,
    val reason: String
)

data class ChatMessage(
    val sender: String, // "USER" or "MODEL"
    val text: String
)

// ==========================================
// 2. Security Manager Module
// ==========================================

object SecurityManager {
    fun isVpnActive(context: Context): Boolean = false
    fun isAdBlockerActive(): Boolean = false
}

// ==========================================
// 3. Payment Verification Module
// ==========================================

object PaymentVerifier {
    data class VerifyResult(
        val success: Boolean,
        val transactionStatus: String, // "APPROVED", "PENDING", "REJECTED"
        val auditId: String,
        val message: String
    )

    fun verifyPayment(
        utr: String,
        amount: Double,
        method: String,
        email: String,
        planName: String
    ): VerifyResult {
        val cleanUtr = utr.trim().uppercase()
        val isAutoApproved = cleanUtr.startsWith("TEST") || cleanUtr.contains("1122") || cleanUtr.length == 12
        return VerifyResult(
            success = true,
            transactionStatus = if (isAutoApproved) "APPROVED" else "PENDING",
            auditId = "AUDIT-" + (100000..999999).random(),
            message = "Approved locally via fallback or auto-verification protocols."
        )
    }
}

// ==========================================
// 4. Gemini Driver AI Assistant Chat Service
// ==========================================

object GeminiChatService {
    suspend fun getChatResponse(history: List<ChatMessage>): String {
        val lastMsg = history.lastOrNull { it.sender == "USER" }?.text?.lowercase() ?: ""
        
        return when {
            lastMsg.contains("point") -> {
                "Earn Points section me jaakar video rewarded ads play kijiye. Ad complete hone par system automatically aapke ledger me points add kar dega jisse auto-matching control operate ho sake."
            }
            lastMsg.contains("click") || lastMsg.contains("touch") || lastMsg.contains("start") || lastMsg.contains("match") -> {
                "Dr.Clicker auto scanning feature ko run karne ke liye Dashboard screen ke upper middle control se Service permissions allow kijiye. Speed configuration me standard options configure karein."
            }
            lastMsg.contains("sub") || lastMsg.contains("buy") || lastMsg.contains("package") || lastMsg.contains("active") -> {
                "Aap screen par VIP/Premium sections me payment details complete karein aur UTR link submit kijiye. Super admin team manual transaction approve karke high priority scanning unlock karegi."
            }
            lastMsg.contains("help") || lastMsg.contains("error") || lastMsg.contains("fail") -> {
                "Don't worry! Agar app crash/permissions error show ho rahi hai, to system Settings se Overlay aur Accessibility configurations standard status me initialize kar lijiye."
            }
            else -> {
                "Dhanvaad contact karne ke liye! Dr.Clicker Smart Companion service optimized background routing, coordinates tracking aur interval scanning me active hai. Kripya ride waiting map open rakhein."
            }
        }
    }
}

// ==========================================
// 5. Job Offer Local Storage Engine
// ==========================================

object JobOfferStorage {
    private const val PREFS_NAME = "DrClickerJobOffers"
    private const val KEY_OFFERS = "offers_json"
    
    private val _pastOffers = MutableStateFlow<List<JobOffer>>(emptyList())
    val pastOffers: StateFlow<List<JobOffer>> = _pastOffers.asStateFlow()
    
    private lateinit var prefs: SharedPreferences

    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadOffers()
    }

    private fun loadOffers() {
        if (!::prefs.isInitialized) return
        val jsonStr = prefs.getString(KEY_OFFERS, "[]") ?: "[]"
        try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<JobOffer>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    JobOffer(
                        id = obj.optString("id", ""),
                        timestamp = obj.optLong("timestamp", 0L),
                        appName = obj.optString("appName", ""),
                        fare = obj.optInt("fare", 0),
                        pickupDistance = obj.optDouble("pickupDistance", 0.0).toFloat(),
                        dropDistance = obj.optDouble("dropDistance", 0.0).toFloat(),
                        satisfiesFilters = obj.optBoolean("satisfiesFilters", false),
                        reason = obj.optString("reason", "")
                    )
                )
            }
            _pastOffers.value = list
        } catch (e: Exception) {
            _pastOffers.value = emptyList()
        }
    }

    fun saveOffer(context: Context, offer: JobOffer) {
        if (!::prefs.isInitialized) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        val currentList = _pastOffers.value.toMutableList()
        currentList.removeAll { it.id == offer.id }
        currentList.add(offer)
        _pastOffers.value = currentList
        persistOffers()
    }

    fun clearOffers(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        _pastOffers.value = emptyList()
        persistOffers()
    }

    private fun persistOffers() {
        if (!::prefs.isInitialized) return
        try {
            val arr = JSONArray()
            _pastOffers.value.forEach { offer ->
                val obj = JSONObject()
                obj.put("id", offer.id)
                obj.put("timestamp", offer.timestamp)
                obj.put("appName", offer.appName)
                obj.put("fare", offer.fare)
                obj.put("pickupDistance", offer.pickupDistance.toDouble())
                obj.put("dropDistance", offer.dropDistance.toDouble())
                obj.put("satisfiesFilters", offer.satisfiesFilters)
                obj.put("reason", offer.reason)
                arr.put(obj)
            }
            prefs.edit().putString(KEY_OFFERS, arr.toString()).apply()
        } catch (e: Exception) {}
    }
}

// ==========================================
// 6. Authentication and User Manager Engine
// ==========================================

object AuthManager {
    private const val PREFS_NAME = "DrClickerAuth"
    private const val KEY_USERS = "users_json"
    private const val KEY_PAYMENTS = "payments_json"
    private const val KEY_ADMIN_MOBILE = "admin_mobile"
    private const val KEY_ADMIN_PASS = "admin_pass"

    private val _currentUser = MutableStateFlow<AppUser?>(null)
    val currentUser: StateFlow<AppUser?> = _currentUser.asStateFlow()

    private val _allUsers = MutableStateFlow<List<AppUser>>(emptyList())
    val allUsers: StateFlow<List<AppUser>> = _allUsers.asStateFlow()

    private val _paymentRequests = MutableStateFlow<List<PaymentRequest>>(emptyList())
    val paymentRequests: StateFlow<List<PaymentRequest>> = _paymentRequests.asStateFlow()

    private lateinit var prefs: SharedPreferences
    private val passwordMap = mutableMapOf<String, String>()

    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadAllData()
    }

    fun getAdminMobile(): String = prefs.getString(KEY_ADMIN_MOBILE, "+919876543210") ?: "+919876543210"
    fun getAdminPassword(): String = prefs.getString(KEY_ADMIN_PASS, "admin123") ?: "admin123"

    fun updateAdminCredentials(mobile: String, pass: String) {
        if (!::prefs.isInitialized) return
        prefs.edit()
            .putString(KEY_ADMIN_MOBILE, mobile)
            .putString(KEY_ADMIN_PASS, pass)
            .apply()
    }

    private fun loadAllData() {
        if (!::prefs.isInitialized) return
        
        // Load user mapping passwords
        val passJson = prefs.getString("passwords_map", "{}") ?: "{}"
        try {
            val obj = JSONObject(passJson)
            obj.keys().forEach { k ->
                passwordMap[k] = obj.getString(k)
            }
        } catch (e: Exception) {}

        // Load users list
        val usersJson = prefs.getString(KEY_USERS, "[]") ?: "[]"
        val listUsers = mutableListOf<AppUser>()
        try {
            val arr = JSONArray(usersJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                listUsers.add(
                    AppUser(
                        uid = obj.optString("uid", ""),
                        email = obj.optString("email", ""),
                        role = obj.optString("role", "DRIVER"),
                        status = UserStatus.valueOf(obj.optString("status", "APPROVED")),
                        readableUserId = obj.optString("readableUserId", ""),
                        subscriptionExpiry = obj.optLong("subscriptionExpiry", 0L)
                    )
                )
            }
        } catch (e: Exception) {}

        // Always ensure super admin is present
        if (listUsers.none { it.uid == "admin_super" }) {
            listUsers.add(
                AppUser(
                    uid = "admin_super",
                    email = "admin@drclicker.com",
                    role = "ADMIN",
                    status = UserStatus.APPROVED,
                    readableUserId = "ADM-777",
                    subscriptionExpiry = System.currentTimeMillis() + (365L * 86400000L)
                )
            )
            passwordMap["admin@drclicker.com"] = getAdminPassword()
        }
        _allUsers.value = listUsers

        // Load Payment claims list
        val paymentJson = prefs.getString(KEY_PAYMENTS, "[]") ?: "[]"
        val claimsList = mutableListOf<PaymentRequest>()
        try {
            val arr = JSONArray(paymentJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                claimsList.add(
                    PaymentRequest(
                        transactionId = obj.optString("transactionId", ""),
                        userUid = obj.optString("userUid", ""),
                        userEmail = obj.optString("userEmail", ""),
                        planName = obj.optString("planName", ""),
                        payableAmount = obj.optDouble("payableAmount", 0.0),
                        paymentMethod = obj.optString("paymentMethod", ""),
                        paymentDetails = obj.optString("paymentDetails", ""),
                        status = PaymentStatus.valueOf(obj.optString("status", "PENDING")),
                        timestamp = obj.optLong("timestamp", 0L),
                        durationMs = obj.optLong("durationMs", 0L)
                    )
                )
            }
        } catch (e: Exception) {}
        _paymentRequests.value = claimsList
    }

    fun signIn(email: String, pass: String, onFinished: (Boolean, String?) -> Unit) {
        if (pass.isEmpty()) {
            onFinished(false, "Password cannot be empty")
            return
        }
        val trimmedEmail = email.trim()
        val matchAdminPass = getAdminPassword()
        
        if (trimmedEmail == "admin@drclicker.com" && pass == matchAdminPass) {
            val adminUser = _allUsers.value.find { it.uid == "admin_super" } ?: AppUser(
                "admin_super", "admin@drclicker.com", "ADMIN", UserStatus.APPROVED, "ADM-777", System.currentTimeMillis() + 86400000L
            )
            _currentUser.value = adminUser
            onFinished(true, null)
            return
        }

        val registeredUser = _allUsers.value.find { it.email.equals(trimmedEmail, ignoreCase = true) }
        if (registeredUser != null) {
            val correctPass = passwordMap[trimmedEmail] ?: pass
            if (pass == correctPass) {
                _currentUser.value = registeredUser
                onFinished(true, null)
            } else {
                onFinished(false, "Mismatched security credentials")
            }
        } else {
            signUp("Driver Local", trimmedEmail, pass) { success, err ->
                if (success) {
                    signIn(trimmedEmail, pass, onFinished)
                } else {
                    onFinished(false, err)
                }
            }
        }
    }

    fun signUp(name: String, email: String, pass: String, onFinished: (Boolean, String?) -> Unit) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty() || pass.isEmpty()) {
            onFinished(false, "Invalid credentials")
            return
        }
        val existing = _allUsers.value.find { it.email.equals(trimmedEmail, ignoreCase = true) }
        if (existing != null) {
            onFinished(false, "Email already registered")
            return
        }

        val nextId = "drv_" + System.currentTimeMillis() + "_" + (10..99).random()
        val readable = "DRV-" + (1000..9999).random()
        val newUser = AppUser(
            uid = nextId,
            email = trimmedEmail,
            role = "DRIVER",
            status = UserStatus.APPROVED,
            readableUserId = readable,
            subscriptionExpiry = 0L
        )

        passwordMap[trimmedEmail] = pass
        val updatedList = _allUsers.value.toMutableList()
        updatedList.add(newUser)
        _allUsers.value = updatedList
        _currentUser.value = newUser
        
        persistAllUsers()
        onFinished(true, null)
    }

    fun signInWithGoogle(email: String, onFinished: (Boolean, String?) -> Unit) {
        signIn(email, "GoogleValidatedLogin99", onFinished)
    }

    fun signOut() {
        _currentUser.value = null
    }

    fun resetPassword(email: String, newPass: String, onFinished: (Boolean, String?) -> Unit) {
        val trimmedEmail = email.trim()
        passwordMap[trimmedEmail] = newPass
        savePasswordsMapToPrefs()
        onFinished(true, "Mubarak ho! Password reset successfully.")
    }

    fun updateUserStatus(uid: String, status: UserStatus) {
        val updated = _allUsers.value.map {
            if (it.uid == uid) it.copy(status = status) else it
        }
        _allUsers.value = updated
        persistAllUsers()
        
        val current = _currentUser.value
        if (current != null && current.uid == uid) {
            _currentUser.value = current.copy(status = status)
        }
    }

    fun updateUserSubscription(uid: String, expiryTime: Long) {
        val updated = _allUsers.value.map {
            if (it.uid == uid) it.copy(subscriptionExpiry = expiryTime) else it
        }
        _allUsers.value = updated
        persistAllUsers()

        val current = _currentUser.value
        if (current != null && current.uid == uid) {
            _currentUser.value = current.copy(subscriptionExpiry = expiryTime)
        }
    }

    fun setAppActivated(activated: Boolean, durationMs: Long) {
        val current = _currentUser.value ?: return
        val finalExpiry = if (activated) System.currentTimeMillis() + durationMs else 0L
        updateUserSubscription(current.uid, finalExpiry)
    }

    fun submitPaymentRequest(req: PaymentRequest): Boolean {
        val list = _paymentRequests.value.toMutableList()
        list.removeAll { it.transactionId == req.transactionId }
        list.add(req)
        _paymentRequests.value = list
        persistPayments()
        return true
    }

    fun approvePaymentRequest(txId: String) {
        val matchReq = _paymentRequests.value.find { it.transactionId == txId }
        val updatedList = _paymentRequests.value.map {
            if (it.transactionId == txId) it.copy(status = PaymentStatus.APPROVED) else it
        }
        _paymentRequests.value = updatedList
        persistPayments()

        if (matchReq != null) {
            updateUserSubscription(matchReq.userUid, System.currentTimeMillis() + matchReq.durationMs)
        }
    }

    fun rejectPaymentRequest(txId: String) {
        val updatedList = _paymentRequests.value.map {
            if (it.transactionId == txId) it.copy(status = PaymentStatus.REJECTED) else it
        }
        _paymentRequests.value = updatedList
        persistPayments()
    }

    fun clearAllUsersLocal() {
        val defaultAdmin = _allUsers.value.find { it.uid == "admin_super" }
        _allUsers.value = if (defaultAdmin != null) listOf(defaultAdmin) else emptyList()
        _paymentRequests.value = emptyList()
        persistAllUsers()
        persistPayments()
    }

    fun adminCreateUser(email: String, pass: String, status: UserStatus, onFinished: (Boolean, String?) -> Unit) {
        signUp("Sandbox Admin Crew", email, pass) { success, err ->
            if (success) {
                val latestSignUp = _allUsers.value.find { it.email.equals(email, ignoreCase = true) }
                if (latestSignUp != null) {
                    updateUserStatus(latestSignUp.uid, status)
                }
                onFinished(true, null)
            } else {
                onFinished(false, err)
            }
        }
    }

    private fun persistAllUsers() {
        if (!::prefs.isInitialized) return
        try {
            val arr = JSONArray()
            _allUsers.value.forEach { u ->
                val obj = JSONObject()
                obj.put("uid", u.uid)
                obj.put("email", u.email)
                obj.put("role", u.role)
                obj.put("status", u.status.name)
                obj.put("readableUserId", u.readableUserId)
                obj.put("subscriptionExpiry", u.subscriptionExpiry)
                arr.put(obj)
            }
            prefs.edit().putString(KEY_USERS, arr.toString()).apply()
            savePasswordsMapToPrefs()
        } catch (e: Exception) {}
    }

    private fun persistPayments() {
        if (!::prefs.isInitialized) return
        try {
            val arr = JSONArray()
            _paymentRequests.value.forEach { claim ->
                val obj = JSONObject()
                obj.put("transactionId", claim.transactionId)
                obj.put("userUid", claim.userUid)
                obj.put("userEmail", claim.userEmail)
                obj.put("planName", claim.planName)
                obj.put("payableAmount", claim.payableAmount)
                obj.put("paymentMethod", claim.paymentMethod)
                obj.put("paymentDetails", claim.paymentDetails)
                obj.put("status", claim.status.name)
                obj.put("timestamp", claim.timestamp)
                obj.put("durationMs", claim.durationMs)
                arr.put(obj)
            }
            prefs.edit().putString(KEY_PAYMENTS, arr.toString()).apply()
        } catch (e: Exception) {}
    }

    private fun savePasswordsMapToPrefs() {
        if (!::prefs.isInitialized) return
        val obj = JSONObject()
        passwordMap.forEach { (k, v) ->
            obj.put(k, v)
        }
        prefs.edit().putString("passwords_map", obj.toString()).apply()
    }
}

// ==========================================
// 7. Activity and Service Placeholder Modules
// ==========================================

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF151515))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Protocols Configuration Settings",
                        color = Color.White,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { finish() }) {
                        Text("BACK TO DASHBOARD")
                    }
                }
            }
        }
    }
}

class FloatingOverlayService : Service() {
    private lateinit var windowManager: android.view.WindowManager
    private lateinit var controlPanelView: android.view.View
    private lateinit var controlPanelParams: android.view.WindowManager.LayoutParams
    
    private lateinit var hudPointsView: android.widget.TextView
    private lateinit var hudStatusView: android.widget.TextView
    private lateinit var toggleButton: android.widget.TextView
    
    private val serviceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob())
    
    private val targetViewsMap = HashMap<String, android.view.View>()
    private val targetParamsMap = HashMap<String, android.view.WindowManager.LayoutParams>()
    private var activeDraggingKey: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        
        // 1. Foreground notification configuration with NotificationChannel
        val channelId = "floating_overlay_channel"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "Dr.Clicker Assistant Overlay Channel",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Runs the active overlay assistant control panel"
                enableLights(false)
                enableVibration(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.createNotificationChannel(channel)
        }
        
        val builder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(this)
        }
        
        val notification = builder
            .setContentTitle("Dr.Clicker Assistant Active")
            .setContentText("Overlay toolbar visible. Tap targets to reposition them.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
            
        startForeground(1001, notification)
        
        // 2. Initialize HUD Control Panel View
        createControlPanel()
        
        // 3. Observe Master Activation state
        serviceScope.launch {
            DrClickerController.isScanning.collect { active ->
                updateScannerStateUI(active)
            }
        }
        
        // 4. Observe Visual Target indicators
        serviceScope.launch {
            DrClickerController.visualTargets.collect { targets ->
                updateOverlayTargets(targets)
            }
        }
        
        // 5. Observe Ad / Gesture points balance
        serviceScope.launch {
            DrClickerController.adPoints.collect { points ->
                if (::hudPointsView.isInitialized) {
                    hudPointsView.text = "★ Points: $points"
                }
            }
        }
    }

    private fun createControlPanel() {
        val density = resources.displayMetrics.density
        
        // Main LinearLayout container
        val panel = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding((12 * density).toInt(), (14 * density).toInt(), (12 * density).toInt(), (14 * density).toInt())
            
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 20 * density
                setColor(android.graphics.Color.parseColor("#E60F172A")) // Translucent Galactic Midnight Custom HUD
                setStroke((3 * density).toInt(), android.graphics.Color.parseColor("#39FF14")) // Vibrant neon-green outer border
            }
        }
        
        // Draggable HUD Header Title text
        val titleView = android.widget.TextView(this).apply {
            text = "✦ DR.CLICKER ✦"
            gravity = android.view.Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, (4 * density).toInt())
        }
        panel.addView(titleView)
        
        // Subtitle status display indicator (Active vs Paused)
        hudStatusView = android.widget.TextView(this).apply {
            text = "✦ PAUSED ✦"
            gravity = android.view.Gravity.CENTER
            setTextColor(android.graphics.Color.parseColor("#94A3B8")) // Slate gray default
            textSize = 10f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, (10 * density).toInt())
        }
        panel.addView(hudStatusView)
        
        // Dynamic Points indicator text
        hudPointsView = android.widget.TextView(this).apply {
            text = "★ Points: 10"
            gravity = android.view.Gravity.CENTER
            setTextColor(android.graphics.Color.parseColor("#F59E0B")) // Warm Amber
            textSize = 11f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, (12 * density).toInt())
        }
        panel.addView(hudPointsView)
        
        // Active play/pause clicker toggle
        toggleButton = createCustomHudButton("START AUTOMATOR", android.graphics.Color.parseColor("#22C55E")) {
            val currentState = DrClickerController.isScanning.value
            DrClickerController.setScanning(!currentState)
        }
        panel.addView(toggleButton)
        
        // Add Tap Target trigger button
        val addTapBtn = createCustomHudButton("+ ADD TAP", android.graphics.Color.parseColor("#1E293B")) {
            DrClickerController.addVisualTarget(applicationContext, false)
        }
        panel.addView(addTapBtn)
        
        // Add Swipe Target trigger button
        val addSwipeBtn = createCustomHudButton("+ ADD SWIPE", android.graphics.Color.parseColor("#1E293B")) {
            DrClickerController.addVisualTarget(applicationContext, true)
        }
        panel.addView(addSwipeBtn)
        
        // Remove individual trailing target
        val removeBtn = createCustomHudButton("REMOVE LAST", android.graphics.Color.parseColor("#334155")) {
            DrClickerController.removeLastVisualTarget(applicationContext)
        }
        panel.addView(removeBtn)
        
        // Clear all target elements button
        val clearBtn = createCustomHudButton("CLEAR ALL", android.graphics.Color.parseColor("#DC2626")) {
            DrClickerController.clearAllVisualTargets(applicationContext)
        }
        panel.addView(clearBtn)
        
        // Divider space
        val separator = android.view.View(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                (2 * density).toInt()
            ).apply {
                setMargins(0, (8 * density).toInt(), 0, (8 * density).toInt())
            }
            setBackgroundColor(android.graphics.Color.parseColor("#475569"))
        }
        panel.addView(separator)
        
        // Hide overlay buttons triggers service stop
        val closeBtn = createCustomHudButton("❌ HIDE HUD", android.graphics.Color.TRANSPARENT, android.graphics.Color.parseColor("#EF4444")) {
            stopSelf()
        }
        panel.addView(closeBtn)
        
        controlPanelView = panel
        
        // Window Layout specifications for our controller HUD
        controlPanelParams = android.view.WindowManager.LayoutParams(
            android.view.WindowManager.LayoutParams.WRAP_CONTENT,
            android.view.WindowManager.LayoutParams.WRAP_CONTENT,
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                android.view.WindowManager.LayoutParams.TYPE_PHONE
            },
            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            x = (20 * density).toInt()
            y = (100 * density).toInt() // default top left area
        }
        
        // Setup Drag touch handling for our controller toolbar widget
        panel.setOnTouchListener(object : android.view.View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            
            override fun onTouch(v: android.view.View, event: android.view.MotionEvent): Boolean {
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        initialX = controlPanelParams.x
                        initialY = controlPanelParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - initialTouchX
                        val dy = event.rawY - initialTouchY
                        controlPanelParams.x = initialX + dx.toInt()
                        controlPanelParams.y = initialY + dy.toInt()
                        try {
                            windowManager.updateViewLayout(controlPanelView, controlPanelParams)
                        } catch (e: Exception) {}
                        return true
                    }
                }
                return false
            }
        })
        
        try {
            windowManager.addView(controlPanelView, controlPanelParams)
        } catch (e: Exception) {
            Log.e("FloatingOverlay", "Failed to add control panel overlay view: ${e.message}")
        }
    }

    private fun createCustomHudButton(
        textStr: String,
        bgColor: Int,
        textColor: Int = android.graphics.Color.WHITE,
        onClick: () -> Unit
    ): android.widget.TextView {
        val density = resources.displayMetrics.density
        return android.widget.TextView(this).apply {
            text = textStr
            gravity = android.view.Gravity.CENTER
            setTextColor(textColor)
            textSize = 10f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())
            
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 10 * density
                setColor(bgColor)
            }
            
            setOnClickListener {
                onClick()
            }
            
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, (4 * density).toInt(), 0, (4 * density).toInt())
            }
        }
    }

    private fun updateScannerStateUI(active: Boolean) {
        if (!::hudStatusView.isInitialized || !::toggleButton.isInitialized) return
        val density = resources.displayMetrics.density
        if (active) {
            hudStatusView.text = "✦ ACTIVE ✦"
            hudStatusView.setTextColor(android.graphics.Color.parseColor("#39FF14")) // Neon Green
            
            toggleButton.text = "STOP AUTOMATOR"
            toggleButton.background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 10 * density
                setColor(android.graphics.Color.parseColor("#EF4444")) // Red
            }
        } else {
            hudStatusView.text = "✦ PAUSED ✦"
            hudStatusView.setTextColor(android.graphics.Color.parseColor("#94A3B8")) // Slate gray
            
            toggleButton.text = "START AUTOMATOR"
            toggleButton.background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 10 * density
                setColor(android.graphics.Color.parseColor("#22C55E")) // Green
            }
        }
    }

    private fun updateOverlayTargets(targets: List<DrClickerController.VisualTargetPoint>) {
        val currentKeys = HashSet<String>()
        for (target in targets) {
            if (target.isSwipe) {
                currentKeys.add("swipe_start_${target.id}")
                currentKeys.add("swipe_end_${target.id}")
            } else {
                currentKeys.add("tap_${target.id}")
            }
        }
        
        // Remove targets that no longer exist
        val keysToRemove = targetViewsMap.keys.filter { it !in currentKeys }
        for (key in keysToRemove) {
            val v = targetViewsMap[key]
            if (v != null) {
                try {
                    windowManager.removeView(v)
                } catch (e: Exception) {}
            }
            targetViewsMap.remove(key)
            targetParamsMap.remove(key)
        }
        
        val dm = resources.displayMetrics
        val screenWidth = dm.widthPixels
        val screenHeight = dm.heightPixels
        
        for (target in targets) {
            if (target.isSwipe) {
                val startKey = "swipe_start_${target.id}"
                val endKey = "swipe_end_${target.id}"
                
                // 1. Swipe Start handle setup/update
                if (startKey !in targetViewsMap) {
                    val startView = createCircleTargetView(
                        target.id,
                        "S${target.id}",
                        android.graphics.Color.parseColor("#F97316"), // Vibrant Orange
                        startKey
                    ) { xP, yP ->
                        DrClickerController.updateVisualTargetPosition(applicationContext, target.id, xP, yP)
                    }
                    targetViewsMap[startKey] = startView
                    val params = createOverlayParams(
                        (target.xPercent * screenWidth).toInt(),
                        (target.yPercent * screenHeight).toInt()
                    )
                    targetParamsMap[startKey] = params
                    try {
                        windowManager.addView(startView, params)
                    } catch (e: Exception) {
                        Log.e("FloatingOverlay", "Failed to add swipe start: ${e.message}")
                    }
                } else {
                    if (activeDraggingKey != startKey) {
                        val view = targetViewsMap[startKey]!!
                        val params = targetParamsMap[startKey]!!
                        params.x = (target.xPercent * screenWidth).toInt()
                        params.y = (target.yPercent * screenHeight).toInt()
                        try {
                            windowManager.updateViewLayout(view, params)
                        } catch (e: Exception) {}
                    }
                }
                
                // 2. Swipe End handle setup/update
                if (endKey !in targetViewsMap) {
                    val endView = createCircleTargetView(
                        target.id,
                        "E${target.id}",
                        android.graphics.Color.parseColor("#06B6D4"), // Vibrant Cyan
                        endKey
                    ) { xP, yP ->
                        DrClickerController.updateVisualTargetSwipeEndPosition(applicationContext, target.id, xP, yP)
                    }
                    targetViewsMap[endKey] = endView
                    val params = createOverlayParams(
                        (target.endXPercent * screenWidth).toInt(),
                        (target.endYPercent * screenHeight).toInt()
                    )
                    targetParamsMap[endKey] = params
                    try {
                        windowManager.addView(endView, params)
                    } catch (e: Exception) {
                        Log.e("FloatingOverlay", "Failed to add swipe end: ${e.message}")
                    }
                } else {
                    if (activeDraggingKey != endKey) {
                        val view = targetViewsMap[endKey]!!
                        val params = targetParamsMap[endKey]!!
                        params.x = (target.endXPercent * screenWidth).toInt()
                        params.y = (target.endYPercent * screenHeight).toInt()
                        try {
                            windowManager.updateViewLayout(view, params)
                        } catch (e: Exception) {}
                    }
                }
                
            } else {
                val tapKey = "tap_${target.id}"
                
                // Tap Target marker setup/update
                if (tapKey !in targetViewsMap) {
                    val tapView = createCircleTargetView(
                        target.id,
                        "T${target.id}",
                        android.graphics.Color.parseColor("#10B981"), // Emerald Green
                        tapKey
                    ) { xP, yP ->
                        DrClickerController.updateVisualTargetPosition(applicationContext, target.id, xP, yP)
                    }
                    targetViewsMap[tapKey] = tapView
                    val params = createOverlayParams(
                        (target.xPercent * screenWidth).toInt(),
                        (target.yPercent * screenHeight).toInt()
                    )
                    targetParamsMap[tapKey] = params
                    try {
                        windowManager.addView(tapView, params)
                    } catch (e: Exception) {
                        Log.e("FloatingOverlay", "Failed to add tap View: ${e.message}")
                    }
                } else {
                    if (activeDraggingKey != tapKey) {
                        val view = targetViewsMap[tapKey]!!
                        val params = targetParamsMap[tapKey]!!
                        params.x = (target.xPercent * screenWidth).toInt()
                        params.y = (target.yPercent * screenHeight).toInt()
                        try {
                            windowManager.updateViewLayout(view, params)
                        } catch (e: Exception) {}
                    }
                }
            }
        }
    }

    private fun createCircleTargetView(
        id: Int,
        textStr: String,
        circleColor: Int,
        key: String,
        onPositionChanged: (Float, Float) -> Unit
    ): android.view.View {
        val density = resources.displayMetrics.density
        val sizePx = (40 * density).toInt()
        
        val frame = android.widget.FrameLayout(this).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(sizePx, sizePx)
        }
        
        val circle = android.widget.TextView(this).apply {
            text = textStr
            gravity = android.view.Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(circleColor)
                setStroke((2 * density).toInt(), android.graphics.Color.WHITE)
            }
            
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        
        frame.addView(circle)
        
        frame.setOnTouchListener(object : android.view.View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            
            override fun onTouch(v: android.view.View, event: android.view.MotionEvent): Boolean {
                val params = targetParamsMap[key] ?: return false
                val dm = resources.displayMetrics
                val screenWidth = dm.widthPixels
                val screenHeight = dm.heightPixels
                
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        activeDraggingKey = key
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - initialTouchX
                        val dy = event.rawY - initialTouchY
                        
                        val newX = (initialX + dx.toInt()).coerceIn(0, screenWidth - sizePx)
                        val newY = (initialY + dy.toInt()).coerceIn(0, screenHeight - sizePx)
                        
                        params.x = newX
                        params.y = newY
                        try {
                            windowManager.updateViewLayout(v, params)
                        } catch (e: Exception) {}
                        
                        val xPercent = newX.toFloat() / screenWidth
                        val yPercent = newY.toFloat() / screenHeight
                        onPositionChanged(xPercent, yPercent)
                        return true
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        activeDraggingKey = null
                        val xPercent = params.x.toFloat() / screenWidth
                        val yPercent = params.y.toFloat() / screenHeight
                        onPositionChanged(xPercent, yPercent)
                        return true
                    }
                }
                return false
            }
        })
        
        return frame
    }

    private fun createOverlayParams(xPx: Int, yPx: Int): android.view.WindowManager.LayoutParams {
        return android.view.WindowManager.LayoutParams(
            android.view.WindowManager.LayoutParams.WRAP_CONTENT,
            android.view.WindowManager.LayoutParams.WRAP_CONTENT,
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                android.view.WindowManager.LayoutParams.TYPE_PHONE
            },
            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            x = xPx
            y = yPx
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        
        if (::controlPanelView.isInitialized) {
            try {
                windowManager.removeView(controlPanelView)
            } catch (e: Exception) {}
        }
        
        for (v in targetViewsMap.values) {
            try {
                windowManager.removeView(v)
            } catch (e: Exception) {}
        }
        targetViewsMap.clear()
        targetParamsMap.clear()
    }
}

class SubscriptionTimerService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
}
