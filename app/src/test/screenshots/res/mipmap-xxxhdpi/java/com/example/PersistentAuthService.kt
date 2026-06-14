package com.example

import android.content.Context
import android.util.Log

/**
 * Service to handle persistent authentication using session tokens.
 * Checks local storage on application launch and automatically logs the user in (bypassing
 * the login screen) if a valid session token is found and the user has an active subscription.
 */
object PersistentAuthService {
    private const val TAG = "PersistentAuthService"
    private const val PREF_AUTH = "DrClickerAuthPrefs"
    private const val KEY_SESSION_TOKEN = "auth_session_token"
    private const val KEY_CURRENT_USER_UID = "current_user_uid"

    /**
     * Set/Update the persistent session token for a logged-in user ID.
     */
    fun createSession(context: Context, uid: String) {
        try {
            val prefs = context.getSharedPreferences(PREF_AUTH, Context.MODE_PRIVATE)
            val sessionToken = "sess_${uid}_${System.currentTimeMillis()}"
            prefs.edit()
                .putString(KEY_SESSION_TOKEN, sessionToken)
                .putString(KEY_CURRENT_USER_UID, uid)
                .apply()
            Log.d(TAG, "Persistent session token successfully created for uid: $uid")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating session token: ${e.message}", e)
        }
    }

    /**
     * Remove the active persistent session token upon sign-out.
     */
    fun clearSession(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREF_AUTH, Context.MODE_PRIVATE)
            prefs.edit()
                .remove(KEY_SESSION_TOKEN)
                .remove(KEY_CURRENT_USER_UID)
                .apply()
            Log.d(TAG, "Persistent session token cleared successfully from preferences")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing session token: ${e.message}", e)
        }
    }

    /**
     * Verifies if there's a stored session token and if the user possesses an active subscription.
     * Restores state and returns true to signify a successful login screen bypass.
     */
    fun checkAndBypassLogin(context: Context): Boolean {
        try {
            val prefs = context.getSharedPreferences(PREF_AUTH, Context.MODE_PRIVATE)
            val sessionToken = prefs.getString(KEY_SESSION_TOKEN, null)
            val storedUid = prefs.getString(KEY_CURRENT_USER_UID, null)

            Log.d(TAG, "Validating session launch - Token: $sessionToken, UID: $storedUid")

            if (sessionToken.isNullOrEmpty() || storedUid.isNullOrEmpty()) {
                Log.d(TAG, "No persistent session available. Starting from normal login path.")
                return false
            }

            // Verify the token structure (e.g. format and matching internal UID)
            if (!sessionToken.startsWith("sess_") || !sessionToken.contains(storedUid)) {
                Log.w(TAG, "Invalid session token format. Purging session state.")
                clearSession(context)
                return false
            }

            // Initialize AuthManager to load users list, etc.
            AuthManager.initialize(context)

            // Look up the user record to verify their current license/subscription boundaries
            val allUsers = AuthManager.allUsers.value
            val user = allUsers.find { it.uid == storedUid }

            if (user == null) {
                Log.w(TAG, "Session user record not found in system user base.")
                clearSession(context)
                return false
            }

            // Admin accounts and active subscription users can fully bypass the login screen
            val isBypassAllowed = if (user.role == "ADMIN") {
                true
            } else {
                user.subscriptionExpiry > System.currentTimeMillis()
            }

            if (isBypassAllowed) {
                Log.i(TAG, "Active session verified with valid premium subscription: ${user.email}. Bypassing login.")
                // Restore current active memory representation in AuthManager
                AuthManager.refreshFromLocalStorage()
                return true
            } else {
                Log.w(TAG, "Session found but subscription is inactive or expired. Bypassing login disabled.")
                // We preserve the identity mapping for possible direct plan upgrades / renewals, 
                // but return false to show login screen so they can authenticate or purchase.
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failure under persistent auth service bypass checker: ${e.message}", e)
            return false
        }
    }
}
