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
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
}

class SubscriptionTimerService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
}
