package com.example

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class UserStatus {
    PENDING,
    APPROVED,
    REJECTED
}

data class AppUser(
    val uid: String,
    val email: String,
    val status: UserStatus,
    val role: String = "USER",
    val subscriptionExpiry: Long = 0L,
    val customUserId: String = "",
    val name: String = ""
) {
    val readableUserId: String
        get() = if (customUserId.isNotEmpty()) customUserId else {
            val hash = kotlin.math.abs(email.trim().lowercase().hashCode()) % 100000
            val padded = String.format("%05d", hash)
            "DC-$padded"
        }
}

enum class PaymentStatus {
    PENDING,
    APPROVED,
    REJECTED
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

object AuthManager {
    private const val TAG = "AuthManager"
    private const val PREF_AUTH = "DrClickerAuthPrefs"
    private const val KEY_CURRENT_USER_UID = "current_user_uid"
    private const val KEY_USER_LIST = "all_registered_users"
    private const val KEY_PAYMENT_REQUESTS = "all_payment_transaction_requests"
    private const val KEY_ADMIN_MOBILE = "admin_mobile_num"
    private const val KEY_ADMIN_PASSWORD = "admin_password_key"

    private lateinit var prefs: SharedPreferences
    private var firebaseAuth: FirebaseAuth? = null
    private var firebaseFirestore: FirebaseFirestore? = null
    private var isFirebaseAvailable = false
    private var isFirestoreAvailable = false
    private var applicationContext: Context? = null

    private val _currentUser = MutableStateFlow<AppUser?>(null)
    val currentUser: StateFlow<AppUser?> = _currentUser.asStateFlow()

    private val _allUsers = MutableStateFlow<List<AppUser>>(emptyList())
    val allUsers: StateFlow<List<AppUser>> = _allUsers.asStateFlow()

    private val _paymentRequests = MutableStateFlow<List<PaymentRequest>>(emptyList())
    val paymentRequests: StateFlow<List<PaymentRequest>> = _paymentRequests.asStateFlow()

    // Strong reference to live state observer for local storage
    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_USER_LIST || key == KEY_PAYMENT_REQUESTS || key == KEY_CURRENT_USER_UID || key == "is_app_activated" || key == "app_activated_expiry") {
            Log.d(TAG, "Local storage changed externally for key: $key. Refreshing state.")
            refreshFromLocalStorage()
        }
    }

    fun refreshFromLocalStorage() {
        if (!::prefs.isInitialized) return
        loadUsersFromStore()
        loadPaymentRequestsFromStore()
        restoreActiveSession()
    }

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
        prefs = context.applicationContext.getSharedPreferences(PREF_AUTH, Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
        
        // 1. Safe detection and initialization of Firebase App SDK
        try {
            val apps = FirebaseApp.getApps(context)
            if (apps.isNotEmpty()) {
                firebaseAuth = FirebaseAuth.getInstance()
                firebaseFirestore = FirebaseFirestore.getInstance()
                isFirebaseAvailable = true
                isFirestoreAvailable = true
                Log.d(TAG, "Firebase Auth & Firestore libraries successfully bound and ready.")
            } else {
                // Initialize programmatically from BuildConfig if available
                val apiKey = try { BuildConfig.FIREBASE_API_KEY } catch (e: Exception) { "" }
                val projectId = try { BuildConfig.FIREBASE_PROJECT_ID } catch (e: Exception) { "" }
                val appId = try { BuildConfig.FIREBASE_APP_ID } catch (e: Exception) { "" }

                if (apiKey.isNotEmpty() && apiKey != "MY_FIREBASE_API_KEY" &&
                    projectId.isNotEmpty() && projectId != "MY_FIREBASE_PROJECT_ID" &&
                    appId.isNotEmpty() && appId != "MY_FIREBASE_APP_ID"
                ) {
                    val options = com.google.firebase.FirebaseOptions.Builder()
                        .setApiKey(apiKey)
                        .setProjectId(projectId)
                        .setApplicationId(appId)
                        .build()
                    FirebaseApp.initializeApp(context, options)
                    firebaseAuth = FirebaseAuth.getInstance()
                    firebaseFirestore = FirebaseFirestore.getInstance()
                    isFirebaseAvailable = true
                    isFirestoreAvailable = true
                    Log.i(TAG, "Firebase successfully initialized dynamically from BuildConfig secrets!")
                } else {
                    // Try to restore from dynamic user Settings inputs saved in SharedPreferences
                    val savedApiKey = prefs.getString("dynamic_firebase_api_key", "") ?: ""
                    val savedProjectId = prefs.getString("dynamic_firebase_project_id", "") ?: ""
                    val savedAppId = prefs.getString("dynamic_firebase_app_id", "") ?: ""

                    if (savedApiKey.isNotEmpty() && savedProjectId.isNotEmpty() && savedAppId.isNotEmpty()) {
                        val options = com.google.firebase.FirebaseOptions.Builder()
                            .setApiKey(savedApiKey)
                            .setProjectId(savedProjectId)
                            .setApplicationId(savedAppId)
                            .build()
                        FirebaseApp.initializeApp(context, options)
                        firebaseAuth = FirebaseAuth.getInstance()
                        firebaseFirestore = FirebaseFirestore.getInstance()
                        isFirebaseAvailable = true
                        isFirestoreAvailable = true
                        Log.i(TAG, "Firebase successfully initialized dynamically from custom Settings!")
                    } else {
                        Log.w(TAG, "No default or dynamic FirebaseApp configuration found. Running in localized high-fidelity debug simulator.")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firebase initialization error (${e.message}). Falling back to local debug mode.")
            isFirebaseAvailable = false
            isFirestoreAvailable = false
        }

        // 2. Load registered users catalog
        loadUsersFromStore()

        // 3. Pre-populate only the main Admin account if empty
        if (_allUsers.value.isEmpty()) {
            val defaultUsers = listOf(
                AppUser("admin_diwan", "aalamdiwan555@gmail.com", UserStatus.APPROVED, "ADMIN")
            )
            saveUsersToStore(defaultUsers)
        }

        // 4. Load payment verification claims
        loadPaymentRequestsFromStore()

        // 5. Try to restore active user session
        restoreActiveSession()

        // 6. Start listening to users collections in Cloud Firestore
        startFirestoreUsersListener()
    }

    private fun loadUsersFromStore() {
        val userStrings = prefs.getStringSet(KEY_USER_LIST, emptySet()) ?: emptySet()
        val list = mutableListOf<AppUser>()
        for (str in userStrings) {
            // Format: uid|email|status|role|subscriptionExpiry|customUserId|name
            val parts = str.split("|")
            if (parts.size >= 4) {
                try {
                    val exp = if (parts.size >= 5) parts[4].toLongOrNull() ?: 0L else 0L
                    val customId = if (parts.size >= 6) parts[5] else ""
                    val nameVal = if (parts.size >= 7) parts[6] else ""
                    list.add(
                        AppUser(
                          uid = parts[0],
                          email = parts[1],
                          status = UserStatus.valueOf(parts[2]),
                          role = parts[3],
                          subscriptionExpiry = exp,
                          customUserId = customId,
                          name = nameVal
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed parsing user string: $str")
                }
            }
        }
        _allUsers.value = list
    }

    private fun saveUsersToStore(users: List<AppUser>, syncToCloud: Boolean = true) {
        _allUsers.value = users
        val stringSet = users.map { "${it.uid}|${it.email}|${it.status.name}|${it.role}|${it.subscriptionExpiry}|${it.customUserId}|${it.name}" }.toSet()
        prefs.edit().putStringSet(KEY_USER_LIST, stringSet).apply()
        if (syncToCloud) {
            users.forEach { syncUserToFirestore(it) }
        }
    }

    private fun loadPaymentRequestsFromStore() {
        val strings = prefs.getStringSet(KEY_PAYMENT_REQUESTS, emptySet()) ?: emptySet()
        val list = mutableListOf<PaymentRequest>()
        for (str in strings) {
            // Format: transactionId|userUid|userEmail|planName|payableAmount|paymentMethod|paymentDetails|status|timestamp|durationMs
            val parts = str.split("|")
            if (parts.size >= 10) {
                try {
                    list.add(
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
                } catch (e: Exception) {
                    Log.e(TAG, "Failed parsing payment request: $str")
                }
            }
        }
        // Sort newest transactions first
        _paymentRequests.value = list.sortedByDescending { it.timestamp }
    }

    private fun savePaymentRequestsToStore(list: List<PaymentRequest>) {
        _paymentRequests.value = list.sortedByDescending { it.timestamp }
        val stringSet = list.map {
            "${it.transactionId}|${it.userUid}|${it.userEmail}|${it.planName}|${it.payableAmount}|${it.paymentMethod}|${it.paymentDetails}|${it.status.name}|${it.timestamp}|${it.durationMs}"
        }.toSet()
        prefs.edit().putStringSet(KEY_PAYMENT_REQUESTS, stringSet).apply()
    }

    fun submitPaymentRequest(req: PaymentRequest): Boolean {
        // Prevent duplicate transactionId submissions
        val currentList = _paymentRequests.value
        if (currentList.any { it.transactionId == req.transactionId }) {
            return false
        }
        val newList = currentList.toMutableList().apply { add(req) }
        savePaymentRequestsToStore(newList)
        return true
    }

    fun approvePaymentRequest(trxId: String): Boolean {
        val currentList = _paymentRequests.value.toMutableList()
        val index = currentList.indexOfFirst { it.transactionId == trxId }
        if (index != -1) {
            val req = currentList[index]
            val updatedReq = req.copy(status = PaymentStatus.APPROVED)
            currentList[index] = updatedReq
            savePaymentRequestsToStore(currentList)

            // Auto-activate or extend driver's premium subscription
            val userRecord = _allUsers.value.find { it.uid == req.userUid }
            val currentExpiry = userRecord?.subscriptionExpiry ?: 0L
            val baseTime = if (currentExpiry > System.currentTimeMillis()) currentExpiry else System.currentTimeMillis()
            val finalExpiry = baseTime + req.durationMs
            
            updateUserSubscription(req.userUid, finalExpiry)
            return true
        }
        return false
    }

    fun rejectPaymentRequest(trxId: String): Boolean {
        val currentList = _paymentRequests.value.toMutableList()
        val index = currentList.indexOfFirst { it.transactionId == trxId }
        if (index != -1) {
            val req = currentList[index]
            val updatedReq = req.copy(status = PaymentStatus.REJECTED)
            currentList[index] = updatedReq
            savePaymentRequestsToStore(currentList)
            return true
        }
        return false
    }

    private fun restoreActiveSession() {
        val storedUid = prefs.getString(KEY_CURRENT_USER_UID, null)
        val sessionToken = prefs.getString("auth_session_token", null)
        
        if (storedUid != null) {
            // Verify session token integrity
            val isSessionValid = sessionToken != null && sessionToken.startsWith("sess_") && sessionToken.contains(storedUid)
            
            if (isSessionValid) {
                // Find matching user record
                val user = if (storedUid == "admin_super") {
                    AppUser(
                        uid = "admin_super",
                        email = "Admin (${getAdminMobile()})",
                        status = UserStatus.APPROVED,
                        role = "ADMIN"
                    )
                } else if (storedUid == "admin_diwan") {
                    AppUser(
                        uid = "admin_diwan",
                        email = "aalamdiwan555@gmail.com",
                        status = UserStatus.APPROVED,
                        role = "ADMIN"
                    )
                } else {
                    _allUsers.value.find { it.uid == storedUid }
                }

                if (user != null) {
                    // Check if subscription resides in active state
                    val isSubscriptionValid = if (user.role == "ADMIN") {
                        true
                    } else {
                        user.subscriptionExpiry > System.currentTimeMillis() || isAppActivated()
                    }

                    if (isSubscriptionValid) {
                        Log.i(TAG, "Persistent login verified with valid subscription for user: ${user.email}. Bypassing login screen.")
                        _currentUser.value = user
                        return
                    } else {
                        Log.i(TAG, "Unsubscribing/Expiring session login bypass since subscription is inactive. Login screen mandatory.")
                        _currentUser.value = null
                    }
                } else {
                    Log.w(TAG, "No associated user found for active session token.")
                    _currentUser.value = null
                }
            } else {
                Log.d(TAG, "No valid session token found for stored authentication. Login screen is required.")
                _currentUser.value = null
            }
        }
    }

    fun getLocalPassword(email: String): String? {
        if (!::prefs.isInitialized) return null
        return prefs.getString("local_user_pass_${email.trim().lowercase()}", null)
    }

    fun saveLocalPassword(email: String, pass: String) {
        if (!::prefs.isInitialized) return
        prefs.edit().putString("local_user_pass_${email.trim().lowercase()}", pass).apply()
    }

    fun resetPassword(email: String, newPass: String, onResult: (Boolean, String?) -> Unit) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.equals("aalamdiwan555@gmail.com", ignoreCase = true)) {
            onResult(false, "Administrator password reset cannot be performed here.")
            return
        }

        if (isFirebaseAvailable && firebaseAuth != null) {
            firebaseAuth?.sendPasswordResetEmail(trimmedEmail)
                ?.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        onResult(true, "Password reset instruction email has been sent successfully.")
                    } else {
                        onResult(false, task.exception?.localizedMessage ?: "Failed to trigger Firebase reset.")
                    }
                }
        } else {
            val existing = _allUsers.value.find { it.email.equals(trimmedEmail, ignoreCase = true) }
            if (existing != null) {
                saveLocalPassword(trimmedEmail, newPass)
                onResult(true, "Password local reset is successful! Please use your new password.")
            } else {
                onResult(false, "No registered account found with email '$trimmedEmail'.")
            }
        }
    }

    fun signUp(name: String, email: String, password: String, onResult: (Boolean, String?) -> Unit) {
        val trimmedEmail = email.trim()
        val trimmedName = name.trim()
        if (isFirebaseAvailable && firebaseAuth != null) {
            firebaseAuth?.createUserWithEmailAndPassword(trimmedEmail, password)
                ?.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val fbUser = task.result?.user
                        if (fbUser != null) {
                            val uid = fbUser.uid
                            val isDefaultAdmin = trimmedEmail.startsWith("admin")
                            val newUser = AppUser(
                                uid = uid,
                                email = trimmedEmail,
                                status = if (isDefaultAdmin) UserStatus.APPROVED else UserStatus.PENDING,
                                role = if (isDefaultAdmin) "ADMIN" else "USER",
                                name = trimmedName
                            )
                            val updatedList = _allUsers.value.toMutableList().apply { add(newUser) }
                            saveUsersToStore(updatedList)
                            saveLocalPassword(trimmedEmail, password)
                            _currentUser.value = newUser
                            applicationContext?.let { PersistentAuthService.createSession(it, uid) }
                            onResult(true, null)
                        } else {
                            onResult(false, "User accounts mismatch")
                        }
                    } else {
                        onResult(false, task.exception?.localizedMessage ?: "Registration failed")
                    }
                }
        } else {
            // Local high-fidelity simulation
            val existing = _allUsers.value.find { it.email.equals(trimmedEmail, ignoreCase = true) }
            if (existing != null) {
                onResult(false, "Yeh email already registered hai! Please log in karein.")
                return
            }

            val isDefaultAdmin = trimmedEmail.startsWith("admin")
            val rawUid = "sim_" + System.currentTimeMillis()
            val newUser = AppUser(
                uid = rawUid,
                email = trimmedEmail,
                status = if (isDefaultAdmin) UserStatus.APPROVED else UserStatus.PENDING,
                role = if (isDefaultAdmin) "ADMIN" else "USER",
                name = trimmedName
            )

            val updatedList = _allUsers.value.toMutableList().apply { add(newUser) }
            saveUsersToStore(updatedList)
            saveLocalPassword(trimmedEmail, password)
            
            _currentUser.value = newUser
            prefs.edit().putString(KEY_CURRENT_USER_UID, rawUid).apply()
            applicationContext?.let { PersistentAuthService.createSession(it, rawUid) }
            
            onResult(true, null)
        }
    }

    fun signIn(email: String, password: String, onResult: (Boolean, String?) -> Unit) {
        val trimmedEmail = email.trim()
        
        // Exact override check for Primary Admin Diwan
        if (trimmedEmail.equals("aalamdiwan555@gmail.com", ignoreCase = true)) {
            if (password != "1qwwq11qw") {
                onResult(false, "Ghalat Password! Chief Admin ke liye sahi password enter karein.")
                return
            }
            val adminUser = AppUser(
                uid = "admin_diwan",
                email = "aalamdiwan555@gmail.com",
                status = UserStatus.APPROVED,
                role = "ADMIN"
            )
            _currentUser.value = adminUser
            prefs.edit().putString(KEY_CURRENT_USER_UID, "admin_diwan").apply()
            applicationContext?.let { PersistentAuthService.createSession(it, "admin_diwan") }
            
            val exists = _allUsers.value.any { it.uid == "admin_diwan" }
            if (!exists) {
                val updatedList = _allUsers.value.toMutableList().apply { add(adminUser) }
                saveUsersToStore(updatedList)
            }
            onResult(true, null)
            return
        }

        if (isFirebaseAvailable && firebaseAuth != null) {
            firebaseAuth?.signInWithEmailAndPassword(trimmedEmail, password)
                ?.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val fbUser = task.result?.user
                        if (fbUser != null) {
                            val uid = fbUser.uid
                            val existing = _allUsers.value.find { it.uid == uid }
                            if (existing != null) {
                                _currentUser.value = existing
                                applicationContext?.let { PersistentAuthService.createSession(it, existing.uid) }
                                onResult(true, null)
                            } else {
                                val isDefaultAdmin = trimmedEmail.startsWith("admin")
                                val newUser = AppUser(
                                    uid = uid,
                                    email = trimmedEmail,
                                    status = if (isDefaultAdmin) UserStatus.APPROVED else UserStatus.PENDING,
                                    role = if (isDefaultAdmin) "ADMIN" else "USER"
                                )
                                val updatedList = _allUsers.value.toMutableList().apply { add(newUser) }
                                saveUsersToStore(updatedList)
                                _currentUser.value = newUser
                                applicationContext?.let { PersistentAuthService.createSession(it, newUser.uid) }
                                onResult(true, null)
                            }
                        }
                    } else {
                        onResult(false, task.exception?.localizedMessage ?: "Sign in failed")
                    }
                }
        } else {
            // Local simulation with strict local password verification
            val existing = _allUsers.value.find { it.email.equals(trimmedEmail, ignoreCase = true) }
            if (existing != null) {
                val savedPass = getLocalPassword(trimmedEmail)
                if (savedPass != null && savedPass != password) {
                    onResult(false, "Ghalat Password! Kripya sahi password enter karein.")
                } else {
                    if (savedPass == null) {
                        saveLocalPassword(trimmedEmail, password)
                    }
                    _currentUser.value = existing
                    prefs.edit().putString(KEY_CURRENT_USER_UID, existing.uid).apply()
                    applicationContext?.let { PersistentAuthService.createSession(it, existing.uid) }
                    onResult(true, null)
                }
            } else {
                onResult(false, "User is email se registered nahi hai. Naya account create karein.")
            }
        }
    }

    fun signOut() {
        if (isFirebaseAvailable && firebaseAuth != null) {
            firebaseAuth?.signOut()
        }
        _currentUser.value = null
        prefs.edit().remove(KEY_CURRENT_USER_UID).apply()
        applicationContext?.let { PersistentAuthService.clearSession(it) }
    }

    fun signInWithGoogle(email: String, onResult: (Boolean, String?) -> Unit) {
        val trimmedEmail = email.trim()
        val isAdmin = trimmedEmail.equals("aalamdiwan555@gmail.com", ignoreCase = true)
        val targetUid = if (isAdmin) "admin_diwan" else "google_" + kotlin.math.abs(trimmedEmail.hashCode())
        val googleUser = AppUser(
            uid = targetUid,
            email = trimmedEmail,
            status = UserStatus.APPROVED,
            role = if (isAdmin) "ADMIN" else "USER"
        )
        _currentUser.value = googleUser
        prefs.edit().putString(KEY_CURRENT_USER_UID, targetUid).apply()
        applicationContext?.let { PersistentAuthService.createSession(it, targetUid) }
        
        val exists = _allUsers.value.any { it.email.equals(trimmedEmail, ignoreCase = true) }
        if (!exists) {
            val updatedList = _allUsers.value.toMutableList().apply { add(googleUser) }
            saveUsersToStore(updatedList, syncToCloud = true)
        } else {
            val updatedList = _allUsers.value.map {
                if (it.email.equals(trimmedEmail, ignoreCase = true)) {
                    it.copy(role = if (isAdmin) "ADMIN" else it.role, status = UserStatus.APPROVED)
                } else it
            }
            saveUsersToStore(updatedList, syncToCloud = true)
        }
        onResult(true, null)
    }

    // Admin Accept / Reject triggers
    fun updateUserStatus(uid: String, newStatus: UserStatus) {
        val updated = _allUsers.value.map {
            if (it.uid == uid) it.copy(status = newStatus) else it
        }
        saveUsersToStore(updated)
        
        // If current user, update live
        val cur = _currentUser.value
        if (cur != null && cur.uid == uid) {
            _currentUser.value = cur.copy(status = newStatus)
        }
    }

    fun clearAllUsersLocal() {
        saveUsersToStore(emptyList())
        _currentUser.value = null
        prefs.edit().remove(KEY_CURRENT_USER_UID).apply()
        
        // Re-populate system default admin
        val defaultUsers = listOf(
            AppUser("admin_diwan", "aalamdiwan555@gmail.com", UserStatus.APPROVED, "ADMIN")
        )
        saveUsersToStore(defaultUsers)
    }

    fun isAppActivated(): Boolean {
        if (!::prefs.isInitialized) return false
        val cur = _currentUser.value
        if (cur?.role == "ADMIN") return true
        
        // Check current user-specific subscription first
        if (cur != null) {
            val userRecord = _allUsers.value.find { it.uid == cur.uid }
            val expiry = userRecord?.subscriptionExpiry ?: 0L
            return expiry > System.currentTimeMillis()
        }
        
        // Fallback to global/device-wide key (backwards compatible)
        val isActivated = prefs.getBoolean("is_app_activated", false)
        if (!isActivated) return false
        val expiry = prefs.getLong("app_activated_expiry", 0L)
        if (expiry == 0L) return true
        return System.currentTimeMillis() < expiry
    }

    fun getAppExpiryTime(): Long {
        if (!::prefs.isInitialized) return 0L
        val cur = _currentUser.value
        if (cur != null) {
            val userRecord = _allUsers.value.find { it.uid == cur.uid }
            if (userRecord != null && userRecord.subscriptionExpiry > 0L) {
                return userRecord.subscriptionExpiry
            }
        }
        return prefs.getLong("app_activated_expiry", 0L)
    }

    fun setAppActivated(activated: Boolean, durationMs: Long = 0L) {
        if (!::prefs.isInitialized) return
        val expiry = if (durationMs > 0L) System.currentTimeMillis() + durationMs else 0L
        
        // Sync to current logged-in user record if available
        val cur = _currentUser.value
        if (cur != null) {
            updateUserSubscription(cur.uid, expiry)
        }
        
        prefs.edit()
            .putBoolean("is_app_activated", activated)
            .putLong("app_activated_expiry", if (activated) expiry else 0L)
            .apply()
    }

    fun getSubscriptionStartTime(uid: String): Long {
        if (!::prefs.isInitialized) return 0L
        return prefs.getLong("sub_start_$uid", 0L)
    }

    fun updateUserSubscription(uid: String, expiryTime: Long) {
        if (!::prefs.isInitialized) return
        val currentList = _allUsers.value.toMutableList()
        val index = currentList.indexOfFirst { it.uid == uid }
        if (index != -1) {
            val updatedUser = currentList[index].copy(subscriptionExpiry = expiryTime)
            currentList[index] = updatedUser
            saveUsersToStore(currentList)
            
            // Persist subscription start time in local storage (SharedPreferences)
            if (expiryTime > System.currentTimeMillis()) {
                val existingStart = prefs.getLong("sub_start_$uid", 0L)
                if (existingStart == 0L || existingStart >= expiryTime) {
                    prefs.edit().putLong("sub_start_$uid", System.currentTimeMillis()).apply()
                }
            } else {
                prefs.edit().remove("sub_start_$uid").apply()
            }
            
            // Refreshes session state if editing currently authenticated user
            val cur = _currentUser.value
            if (cur != null && cur.uid == uid) {
                _currentUser.value = updatedUser
            }
        }
    }

    fun getAdminMobile(): String {
        return prefs.getString(KEY_ADMIN_MOBILE, "9316642884") ?: "9316642884"
    }

    fun getAdminPassword(): String {
        return prefs.getString(KEY_ADMIN_PASSWORD, "admin123") ?: "admin123"
    }

    fun updateAdminCredentials(newMobile: String, newPass: String) {
        prefs.edit()
            .putString(KEY_ADMIN_MOBILE, newMobile)
            .putString(KEY_ADMIN_PASSWORD, newPass)
            .apply()
        
        // If current user is super admin, refresh credentials representation
        val cur = _currentUser.value
        if (cur != null && cur.uid == "admin_super") {
            _currentUser.value = cur.copy(email = "Admin ($newMobile)")
        }
    }

    fun signInWithMobile(mobile: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        val expectedMobile = getAdminMobile()
        val expectedPass = getAdminPassword()
        if (mobile == expectedMobile && pass == expectedPass) {
            val adminUser = AppUser(
                uid = "admin_super",
                email = "Admin ($mobile)",
                status = UserStatus.APPROVED,
                role = "ADMIN"
            )
            _currentUser.value = adminUser
            prefs.edit().putString(KEY_CURRENT_USER_UID, "admin_super").apply()
            applicationContext?.let { PersistentAuthService.createSession(it, "admin_super") }
            
            // Ensure this admin is saved as well in SharedPreferences registry
            val exists = _allUsers.value.any { it.uid == "admin_super" }
            if (!exists) {
                val updatedList = _allUsers.value.toMutableList().apply {
                    add(adminUser)
                }
                saveUsersToStore(updatedList)
            }
            onResult(true, null)
        } else {
            onResult(false, "Authentication Failed. Incorrect mobile number or password.")
        }
    }

    fun adminCreateUser(email: String, pass: String, status: UserStatus, onResult: (Boolean, String?) -> Unit) {
        val trimmedEmail = email.trim()
        val existing = _allUsers.value.find { it.email.equals(trimmedEmail, ignoreCase = true) }
        if (existing != null) {
            onResult(false, "Yeh email matching user already registered hai.")
            return
        }

        val rawUid = "sim_" + System.currentTimeMillis()
        val newUser = AppUser(
            uid = rawUid,
            email = trimmedEmail,
            status = status,
            role = "USER"
        )

        val updatedList = _allUsers.value.toMutableList().apply { add(newUser) }
        saveUsersToStore(updatedList)
        saveLocalPassword(trimmedEmail, pass)
        onResult(true, null)
    }

    fun getDynamicFirebaseConfig(): Triple<String, String, String> {
        if (!::prefs.isInitialized) return Triple("", "", "")
        val apiKey = prefs.getString("dynamic_firebase_api_key", "") ?: ""
        val projectId = prefs.getString("dynamic_firebase_project_id", "") ?: ""
        val appId = prefs.getString("dynamic_firebase_app_id", "") ?: ""
        return Triple(apiKey, projectId, appId)
    }

    fun saveDynamicFirebaseConfig(apiKey: String, projectId: String, appId: String): Boolean {
        if (!::prefs.isInitialized) return false
        prefs.edit()
            .putString("dynamic_firebase_api_key", apiKey.trim())
            .putString("dynamic_firebase_project_id", projectId.trim())
            .putString("dynamic_firebase_app_id", appId.trim())
            .apply()
        
        val ctx = applicationContext
        return if (ctx != null && apiKey.trim().isNotEmpty() && projectId.trim().isNotEmpty() && appId.trim().isNotEmpty()) {
            try {
                val options = com.google.firebase.FirebaseOptions.Builder()
                    .setApiKey(apiKey.trim())
                    .setProjectId(projectId.trim())
                    .setApplicationId(appId.trim())
                    .build()
                
                try {
                    val app = FirebaseApp.getInstance()
                    app.delete()
                } catch (e: Exception) {}
                
                FirebaseApp.initializeApp(ctx, options)
                firebaseAuth = FirebaseAuth.getInstance()
                firebaseFirestore = FirebaseFirestore.getInstance()
                isFirebaseAvailable = true
                isFirestoreAvailable = true
                Log.i(TAG, "Firebase Auth and Firestore successfully re-initialized dynamically from Settings input!")
                startFirestoreUsersListener()
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to re-initialize Firebase dynamically: ${e.message}")
                isFirebaseAvailable = false
                isFirestoreAvailable = false
                false
            }
        } else {
            isFirebaseAvailable = false
            isFirestoreAvailable = false
            false
        }
    }

    fun isFirebaseActive(): Boolean {
        return isFirebaseAvailable && firebaseAuth != null
    }

    fun syncUserToFirestore(user: AppUser) {
        if (isFirestoreAvailable && firebaseFirestore != null) {
            try {
                val data = hashMapOf(
                    "uid" to user.uid,
                    "email" to user.email,
                    "status" to user.status.name,
                    "role" to user.role,
                    "subscriptionExpiry" to user.subscriptionExpiry,
                    "customUserId" to user.customUserId,
                    "name" to user.name
                )
                firebaseFirestore?.collection("users")?.document(user.uid)?.set(data)
                    ?.addOnSuccessListener {
                        Log.d(TAG, "User ${user.email} synced to Firestore successfully.")
                    }
                    ?.addOnFailureListener { e ->
                        Log.w(TAG, "Error syncing user to Firestore: ${e.message}")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Firestore sync exception: ${e.message}")
            }
        }
    }

    fun startFirestoreUsersListener() {
        if (isFirestoreAvailable && firebaseFirestore != null) {
            try {
                firebaseFirestore?.collection("users")
                    ?.addSnapshotListener { snapshots, e ->
                        if (e != null) {
                            Log.w(TAG, "Listen failed.", e)
                            return@addSnapshotListener
                        }
                        if (snapshots != null) {
                            val list = mutableListOf<AppUser>()
                            for (doc in snapshots) {
                                try {
                                    val uid = doc.getString("uid") ?: doc.id
                                    val email = doc.getString("email") ?: ""
                                    val statusStr = doc.getString("status") ?: "PENDING"
                                    val roleStr = doc.getString("role") ?: "USER"
                                    val exp = doc.getLong("subscriptionExpiry") ?: 0L
                                    val customId = doc.getString("customUserId") ?: ""
                                    val nameVal = doc.getString("name") ?: ""
                                    list.add(
                                        AppUser(
                                            uid = uid,
                                            email = email,
                                            status = UserStatus.valueOf(statusStr),
                                            role = roleStr,
                                            subscriptionExpiry = exp,
                                            customUserId = customId,
                                            name = nameVal
                                        )
                                    )
                                } catch (ex: Exception) {
                                    Log.e(TAG, "Error parsing Firestore user document: ${ex.message}")
                                }
                            }
                            if (list.isNotEmpty()) {
                                saveUsersToStore(list, syncToCloud = false)
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Firestore listener setup failed: ${e.message}")
            }
        }
    }
}
