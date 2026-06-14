package com.example

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*

class SubscriptionTimerService : Service() {

    companion object {
        private const val TAG = "SubscriptionTimerService"
        private const val CHECK_INTERVAL_MS = 5000L // check every 5 seconds
    }

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "SubscriptionTimerService created")
        startTimerLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "SubscriptionTimerService started")
        return START_STICKY
    }

    private fun startTimerLoop() {
        serviceScope.launch {
            while (isActive) {
                try {
                    // Hook to automatically re-validate and refresh memory states from physical local storage (disk) on each check
                    AuthManager.refreshFromLocalStorage()

                    val isActivated = AuthManager.isAppActivated()
                    if (isActivated) {
                        val expiry = AuthManager.getAppExpiryTime()
                        if (expiry > 0L) {
                            val currentTime = System.currentTimeMillis()
                            if (currentTime >= expiry) {
                                Log.i(TAG, "Subscription expired in background. Updating local storage and preserving login session.")
                                
                                // Deactivate global state in SharedPreferences
                                AuthManager.setAppActivated(false)
                                
                                // Update active user model and SharedPreferences registry
                                val currentUid = AuthManager.currentUser.value?.uid
                                if (currentUid != null) {
                                    AuthManager.updateUserSubscription(currentUid, 0L)
                                    // Instantly refresh states to reflect immediately across all bindings
                                    AuthManager.refreshFromLocalStorage()
                                }
                            } else {
                                val remainingMs = expiry - currentTime
                                Log.d(TAG, "Subscription checked: Active. Over $remainingMs ms remaining. Synced with local storage successfully.")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in subscription background loop: ${e.message}", e)
                }
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "SubscriptionTimerService destroyed")
        serviceJob.cancel()
    }
}
