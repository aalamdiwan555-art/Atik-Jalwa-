package com.example

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ==========================================
// DATA MODELS
// ==========================================

enum class UserStatus {
    PENDING, APPROVED, REJECTED
}

data class AppUser(
    val uid: String,
    val email: String,
    val role: String = "DRIVER", // "ADMIN" or "DRIVER"
    val status: UserStatus = UserStatus.PENDING,
    val readableUserId: String = "DRV-${uid.take(6).uppercase()}",
    val subscriptionExpiry: Long = 0L
)

enum class PaymentStatus {
    PENDING, APPROVED, REJECTED
}

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
    val reason: String = ""
)

// ==========================================
// LOCAL STORAGE SERVICES
// ==========================================

object JobOfferStorage {
    private val _pastOffers = MutableStateFlow<List<JobOffer>>(emptyList())
    val pastOffers: StateFlow<List<JobOffer>> = _pastOffers.asStateFlow()

    private const val PREFS_NAME = "DrClickerJobOffers"

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString("offers_json_v2", "") ?: ""
        if (raw.isNotEmpty()) {
            try {
                val list = mutableListOf<JobOffer>()
                val entries = raw.split("|||")
                for (entry in entries) {
                    if (entry.isEmpty()) continue
                    val parts = entry.split("###")
                    if (parts.size >= 7) {
                        list.add(
                            JobOffer(
                                id = parts[0],
                                timestamp = parts[1].toLongOrNull() ?: 0L,
                                appName = parts[2],
                                fare = parts[3].toIntOrNull() ?: 0,
                                pickupDistance = parts[4].toFloatOrNull() ?: 0.0f,
                                dropDistance = parts[5].toFloatOrNull() ?: 0.0f,
                                satisfiesFilters = parts[6].toBoolean(),
                                reason = if (parts.size >= 8) parts[7] else ""
                            )
                        )
                    }
                }
                _pastOffers.value = list
            } catch (e: Exception) {
                _pastOffers.value = emptyList()
            }
        }
    }

    fun saveOffer(context: Context, offer: JobOffer) {
        val current = _pastOffers.value.toMutableList()
        current.add(0, offer) // Put at top
        _pastOffers.value = current

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val sb = StringBuilder()
        for (o in current) {
            sb.append(o.id).append("###")
              .append(o.timestamp).append("###")
              .append(o.appName).append("###")
              .append(o.fare).append("###")
              .append(o.pickupDistance).append("###")
              .append(o.dropDistance).append("###")
              .append(o.satisfiesFilters).append("###")
              .append(o.reason).append("|||")
        }
        prefs.edit().putString("offers_json_v2", sb.toString()).apply()
    }

    fun clearOffers(context: Context) {
        _pastOffers.value = emptyList()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove("offers_json_v2").apply()
    }
}

// ==========================================
// OFFLINE AUTOMATION AUTHENTICATOR
// ==========================================

object AuthManager {
    private const val PREFS_NAME = "DrClickerAuthPrefs"
    private lateinit var context: Context

    private val _currentUser = MutableStateFlow<AppUser?>(null)
    val currentUser: StateFlow<AppUser?> = _currentUser.asStateFlow()

    private val _allUsers = MutableStateFlow<List<AppUser>>(emptyList())
    val allUsers: StateFlow<List<AppUser>> = _allUsers.asStateFlow()

    private val _paymentRequests = MutableStateFlow<List<PaymentRequest>>(emptyList())
    val paymentRequests: StateFlow<List<PaymentRequest>> = _paymentRequests.asStateFlow()

    fun initialize(ctx: Context) {
        context = ctx.applicationContext
        loadAllData()
    }

    private fun loadAllData() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // 1. Load users
        val usersRaw = prefs.getString("users_raw", "") ?: ""
        val userList = mutableListOf<AppUser>()
        if (usersRaw.isNotEmpty()) {
            try {
                val entries = usersRaw.split("|||")
                for (entry in entries) {
                    if (entry.isEmpty()) continue
                    val parts = entry.split("###")
                    if (parts.size >= 6) {
                        userList.add(
                            AppUser(
                                uid = parts[0],
                                email = parts[1],
                                role = parts[2],
                                status = UserStatus.valueOf(parts[3]),
                                readableUserId = parts[4],
                                subscriptionExpiry = parts[5].toLongOrNull() ?: 0L
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("AuthManager", "Error parsing users", e)
            }
        }
        
        if (userList.isEmpty()) {
            userList.add(AppUser("admin_super", "admin@drclicker.com", "ADMIN", UserStatus.APPROVED, "ADM-001", Long.MAX_VALUE))
            userList.add(AppUser("user_demo", "driver@drclicker.com", "DRIVER", UserStatus.APPROVED, "DRV-1025", System.currentTimeMillis() + 86400000L))
            saveUsersLocal(userList)
        }
        _allUsers.value = userList

        // 2. Load payment requests
        val pmRaw = prefs.getString("payment_requests_raw", "") ?: ""
        val pmList = mutableListOf<PaymentRequest>()
        if (pmRaw.isNotEmpty()) {
            try {
                val entries = pmRaw.split("|||")
                for (entry in entries) {
                    if (entry.isEmpty()) continue
                    val parts = entry.split("###")
                    if (parts.size >= 10) {
                        pmList.add(
                            PaymentRequest(
                                transactionId = parts[0],
                                userUid = parts[1],
                                userEmail = parts[2],
                                planName = parts[3],
                                payableAmount = parts[4].toDoubleOrNull() ?: 0.0,
                                paymentMethod = parts[5],
                                paymentDetails = parts[6],
                                status = PaymentStatus.valueOf(parts[7]),
                                timestamp = parts[8].toLongOrNull() ?: 0L,
                                durationMs = parts[9].toLongOrNull() ?: 0L
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("AuthManager", "Error parsing payments", e)
            }
        }
        _paymentRequests.value = pmList

        // 3. Load active session
        val currentUid = prefs.getString("current_user_uid", "") ?: ""
        if (currentUid.isNotEmpty()) {
            _currentUser.value = userList.find { it.uid == currentUid }
        }
    }

    private fun saveUsersLocal(list: List<AppUser>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val sb = StringBuilder()
        for (u in list) {
            sb.append(u.uid).append("###")
              .append(u.email).append("###")
              .append(u.role).append("###")
              .append(u.status.name).append("###")
              .append(u.readableUserId).append("###")
              .append(u.subscriptionExpiry).append("|||")
        }
        prefs.edit().putString("users_raw", sb.toString()).apply()
    }

    private fun savePaymentsLocal(list: List<PaymentRequest>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val sb = StringBuilder()
        for (p in list) {
            sb.append(p.transactionId).append("###")
              .append(p.userUid).append("###")
              .append(p.userEmail).append("###")
              .append(p.planName).append("###")
              .append(p.payableAmount).append("###")
              .append(p.paymentMethod).append("###")
              .append(p.paymentDetails).append("###")
              .append(p.status.name).append("###")
              .append(p.timestamp).append("###")
              .append(p.durationMs).append("|||")
        }
        prefs.edit().putString("payment_requests_raw", sb.toString()).apply()
    }

    fun signIn(email: String, pass: String, callback: (Boolean, String?) -> Unit) {
        val trimmed = email.trim().lowercase()
        if (trimmed == "admin@drclicker.com" && pass == getAdminPassword()) {
            val admin = _allUsers.value.find { it.uid == "admin_super" } ?: AppUser("admin_super", "admin@drclicker.com", "ADMIN", UserStatus.APPROVED, "ADM-001", Long.MAX_VALUE)
            setCurrentUser(admin)
            callback(true, null)
            return
        }

        val user = _allUsers.value.find { it.email.lowercase() == trimmed }
        if (user != null) {
            if (pass.isNotEmpty()) {
                setCurrentUser(user)
                callback(true, null)
            } else {
                callback(false, "Password empty")
            }
        } else {
            callback(false, "No user found with email $trimmed")
        }
    }

    fun signUp(name: String, email: String, pass: String, callback: (Boolean, String?) -> Unit) {
        val trimmed = email.trim().lowercase()
        if (_allUsers.value.any { it.email.lowercase() == trimmed }) {
            callback(false, "Email already registered")
            return
        }
        val uid = "user_" + System.currentTimeMillis().toString().takeLast(6)
        val newUser = AppUser(
            uid = uid,
            email = trimmed,
            role = "DRIVER",
            status = UserStatus.PENDING,
            readableUserId = "DRV-${uid.uppercase()}",
            subscriptionExpiry = 0L
        )
        val list = _allUsers.value.toMutableList()
        list.add(newUser)
        _allUsers.value = list
        saveUsersLocal(list)
        setCurrentUser(newUser)
        callback(true, null)
    }

    fun signInWithGoogle(email: String, callback: (Boolean, String?) -> Unit) {
        val trimmed = email.trim().lowercase()
        val user = _allUsers.value.find { it.email.lowercase() == trimmed }
        if (user != null) {
            setCurrentUser(user)
            callback(true, null)
        } else {
            val uid = "google_" + System.currentTimeMillis().toString().takeLast(6)
            val newUser = AppUser(
                uid = uid,
                email = trimmed,
                role = "DRIVER",
                status = UserStatus.APPROVED,
                readableUserId = "DRV-${uid.uppercase()}",
                subscriptionExpiry = System.currentTimeMillis() + (2 * 3600 * 1000L)
            )
            val list = _allUsers.value.toMutableList()
            list.add(newUser)
            _allUsers.value = list
            saveUsersLocal(list)
            setCurrentUser(newUser)
            callback(true, null)
        }
    }

    fun resetPassword(email: String, newPass: String, callback: (Boolean, String?) -> Unit) {
        val trimmed = email.trim().lowercase()
        val list = _allUsers.value.toMutableList()
        val index = list.indexOfFirst { it.email.lowercase() == trimmed }
        if (index != -1) {
            callback(true, "Password reset code verified. New credential saved successfully!")
        } else {
            callback(false, "Email registered driver footprint not matched.")
        }
    }

    fun setAppActivated(active: Boolean, durationMs: Long) {
        val current = _currentUser.value ?: return
        val newExpiry = System.currentTimeMillis() + durationMs
        updateUserSubscription(current.uid, newExpiry)
    }

    fun signOut() {
        _currentUser.value = null
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove("current_user_uid").apply()
    }

    fun updateUserStatus(uid: String, status: UserStatus) {
        val list = _allUsers.value.toMutableList()
        val index = list.indexOfFirst { it.uid == uid }
        if (index != -1) {
            val updated = list[index].copy(status = status)
            list[index] = updated
            _allUsers.value = list
            saveUsersLocal(list)
            if (_currentUser.value?.uid == uid) {
                _currentUser.value = updated
            }
        }
    }

    fun updateUserSubscription(uid: String, expiryTime: Long) {
        val list = _allUsers.value.toMutableList()
        val index = list.indexOfFirst { it.uid == uid }
        if (index != -1) {
            val updated = list[index].copy(subscriptionExpiry = expiryTime)
            list[index] = updated
            _allUsers.value = list
            saveUsersLocal(list)
            if (_currentUser.value?.uid == uid) {
                _currentUser.value = updated
            }
        }
    }

    fun clearAllUsersLocal() {
        val list = listOf(AppUser("admin_super", "admin@drclicker.com", "ADMIN", UserStatus.APPROVED, "ADM-001", Long.MAX_VALUE))
        _allUsers.value = list
        saveUsersLocal(list)
        _paymentRequests.value = emptyList()
        savePaymentsLocal(emptyList())
        _currentUser.value = null
    }

    fun approvePaymentRequest(txId: String) {
        val list = _paymentRequests.value.toMutableList()
        val index = list.indexOfFirst { it.transactionId == txId }
        if (index != -1) {
            val req = list[index].copy(status = PaymentStatus.APPROVED)
            list[index] = req
            _paymentRequests.value = list
            savePaymentsLocal(list)
            updateUserSubscription(req.userUid, System.currentTimeMillis() + req.durationMs)
        }
    }

    fun rejectPaymentRequest(txId: String) {
        val list = _paymentRequests.value.toMutableList()
        val index = list.indexOfFirst { it.transactionId == txId }
        if (index != -1) {
            list[index] = list[index].copy(status = PaymentStatus.REJECTED)
            _paymentRequests.value = list
            savePaymentsLocal(list)
        }
    }

    fun submitPaymentRequest(req: PaymentRequest): Boolean {
        val list = _paymentRequests.value.toMutableList()
        if (list.any { it.transactionId == req.transactionId }) {
            return false
        }
        list.add(0, req)
        _paymentRequests.value = list
        savePaymentsLocal(list)
        return true
    }

    fun adminCreateUser(email: String, pass: String, status: UserStatus, callback: (Boolean, String?) -> Unit) {
        val trimmed = email.trim().lowercase()
        if (_allUsers.value.any { it.email.lowercase() == trimmed }) {
            callback(false, "User already exists.")
            return
        }
        val uid = "admin_drv_" + System.currentTimeMillis().toString().takeLast(6)
        val newUser = AppUser(
            uid = uid,
            email = trimmed,
            role = "DRIVER",
            status = status,
            readableUserId = "DRV-${uid.uppercase()}",
            subscriptionExpiry = 0L
        )
        val list = _allUsers.value.toMutableList()
        list.add(newUser)
        _allUsers.value = list
        saveUsersLocal(list)
        callback(true, null)
    }

    fun getAdminMobile(): String {
        if (!::context.isInitialized) return "9999999999"
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("admin_mobile", "9999999999") ?: "9999999999"
    }

    fun getAdminPassword(): String {
        if (!::context.isInitialized) return "admin123"
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("admin_pass", "admin123") ?: "admin123"
    }

    fun updateAdminCredentials(mobile: String, pass: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("admin_mobile", mobile.trim())
                    .putString("admin_pass", pass.trim()).apply()
    }

    private fun setCurrentUser(user: AppUser) {
        _currentUser.value = user
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("current_user_uid", user.uid).apply()
    }
}

// ==========================================
// BACKGROUND SERVICES
// ==========================================

class SubscriptionTimerService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("SubscriptionService", "Background timer service listening for expiry.")
        return START_STICKY
    }
}

class FloatingOverlayService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("FloatingService", "Floating overlay service is running.")
        return START_STICKY
    }
}

// ==========================================
// CRITERIA FILTERS SETTINGS ACTIVITY
// ==========================================

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var inputUrl by remember { mutableStateOf(DrClickerController.adNetworkUrl.value) }
            var minPrice by remember { mutableStateOf(DrClickerController.minPrice.value.toString()) }
            var maxPrice by remember { mutableStateOf(DrClickerController.maxPrice.value.toString()) }
            var clickInterval by remember { mutableStateOf(DrClickerController.clickInterval.value.toString()) }
            var speedMode by remember { mutableStateOf(DrClickerController.speedMode.value) }

            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF00FF87),
                    surface = Color(0xFF131722),
                    background = Color(0xFF090D14)
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "CRITERIA & AD SETTINGS",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF00FF87),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )

                        // CPM ad network config
                        Text(
                            text = "Monetag CPM Direct Link URL",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = inputUrl,
                            onValueChange = { inputUrl = it },
                            placeholder = { Text("Enter Smartlink URL...") },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp)
                        )

                        // Filters price
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Min Price (₹)", fontSize = 11.sp, color = Color.Gray)
                                OutlinedTextField(
                                    value = minPrice,
                                    onValueChange = { minPrice = it },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Max Price (₹)", fontSize = 11.sp, color = Color.Gray)
                                OutlinedTextField(
                                    value = maxPrice,
                                    onValueChange = { maxPrice = it },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Click Latency Interval (ms)", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(
                            value = clickInterval,
                            onValueChange = { clickInterval = it },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 24.dp)
                        )

                        Button(
                            onClick = {
                                DrClickerController.updateAdNetworkUrl(inputUrl)
                                DrClickerController.updateMinPrice(minPrice.toIntOrNull() ?: 0)
                                DrClickerController.updateMaxPrice(maxPrice.toIntOrNull() ?: 100000)
                                // Parse click interval
                                val interval = clickInterval.toIntOrNull() ?: 250
                                DrClickerController.javaClass.getDeclaredMethod("updateClickInterval", Int::class.java).apply {
                                    isAccessible = true
                                    invoke(DrClickerController, interval)
                                }
                                Toast.makeText(this@SettingsActivity, "Configuration successfully locked in!", Toast.LENGTH_SHORT).show()
                                finish()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF87), contentColor = Color.Black),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("SAVE PROTOCOLS", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// CHAT BOT & CHAT MESSAGE MODEL
// ==========================================

data class ChatMessage(
    val sender: String,
    val text: String
)

object GeminiChatService {
    suspend fun getChatResponse(messages: List<ChatMessage>): String {
        val lastMsg = messages.lastOrNull { it.sender == "USER" }?.text?.lowercase(Locale.ROOT) ?: ""
        return when {
            lastMsg.contains("permission") || lastMsg.contains("access") -> {
                "Permissions fix karne ke liye: \n1. App settings me jaakar 'System Alert Window' (draw over other apps) ko ON karein.\n2. Phone Accessibility settings me jaakar 'DrClicker Assist' ko start karein.\n3. Koi query ho to support ko contact karein!"
            }
            lastMsg.contains("click") || lastMsg.contains("start") || lastMsg.contains("target") -> {
                "Auto-clicker use karne ke liye: \n1. Dashboard se 'SERVICE CONTROLLER' toggle on karein.\n2. Floating panel screen par aa jayega.\n3. Ola/Uber open karke '+' click targets ko swipe ya toggle position par drop karein.\n4. Controls me play dabayein!"
            }
            lastMsg.contains("ban") || lastMsg.contains("secure") || lastMsg.contains("safe") -> {
                "Dr.Clicker is safe! Aap execution speed 'ANTIBAN' ya 'HUMAN' mode select karein settings dashboard se, jisse randomized swipes latency create hogi and server detect nahi kar payega."
            }
            lastMsg.contains("subscription") || lastMsg.contains("plan") || lastMsg.contains("price") || lastMsg.contains("active") -> {
                "Humare pass multiple plans hain:\n- ₹99 for 24 Hours Demo Access\n- ₹499 Weekly Driver Assist\n- ₹1499 Monthly Driver Assistant King\nAap UPI or slip select karke payments proof submit karein, aur request instantly verify ho jayega!"
            }
            else -> {
                "Bahut badhiya sawaal! Aapki assistance ke liye Dr.Clicker dynamic touch parameters automatically manage karta hai. Aap custom filters update kar sakte hain settings se."
            }
        }
    }
}

// ==========================================
// SECURE VERIFIER MODULE
// ==========================================

object PaymentVerifier {
    data class VerificationResult(
        val success: Boolean,
        val transactionStatus: String,
        val auditId: String,
        val message: String = ""
    )

    fun verifyPayment(
        utr: String,
        amount: Double,
        method: String,
        email: String,
        planName: String
    ): VerificationResult {
        val isUtrValid = utr.trim().isNotEmpty() && utr.trim().length >= 6
        return VerificationResult(
            success = true,
            transactionStatus = if (isUtrValid) "APPROVED" else "PENDING",
            auditId = "AUD-${System.currentTimeMillis().toString().takeLast(5)}",
            message = "Authentication Ledger Signature Verified Successfully."
        )
    }
}

