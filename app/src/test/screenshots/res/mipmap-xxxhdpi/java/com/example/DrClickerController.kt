package com.example

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DrClickerController {
    private const val TAG = "DrClickerController"
    private const val PREFS_NAME = "DrClickerPrefs"

    // Preferences keys matching specifications
    private const val KEY_MIN_PRICE = "Min_Price"
    private const val KEY_MAX_PRICE = "Max_Price"
    private const val KEY_MAX_PICKUP_DIST = "Max_Pickup_Distance"
    private const val KEY_MAX_DROP_DIST = "Max_Drop_Distance"
    private const val KEY_SPEED_MODE = "Speed_Mode"
    private const val KEY_DARK_THEME = "Dark_Theme"
    private const val KEY_MIN_PRICE_PER_KM = "Min_Price_Per_Km"
    private const val KEY_CLICK_INTERVAL = "Click_Interval"
    private const val KEY_RANDOM_JITTER = "Random_Jitter"

    private lateinit var prefs: SharedPreferences

    // Theme Mode
    private val _isDarkTheme = MutableStateFlow(false)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    // Scanning state (Master Toggle)
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // Configurable Filters
    private val _minPrice = MutableStateFlow(0)
    val minPrice: StateFlow<Int> = _minPrice.asStateFlow()

    private val _maxPrice = MutableStateFlow(Int.MAX_VALUE)
    val maxPrice: StateFlow<Int> = _maxPrice.asStateFlow()

    private val _maxPickupDistance = MutableStateFlow(15.0f) // default to 15.0 KM
    val maxPickupDistance: StateFlow<Float> = _maxPickupDistance.asStateFlow()

    private val _maxDropDistance = MutableStateFlow(30.0f) // default to 30.0 KM
    val maxDropDistance: StateFlow<Float> = _maxDropDistance.asStateFlow()

    private val _minPricePerKm = MutableStateFlow(0.0f) // default to 0 / no restriction
    val minPricePerKm: StateFlow<Float> = _minPricePerKm.asStateFlow()

    private val _clickInterval = MutableStateFlow(250) // default 250ms interval rate
    val clickInterval: StateFlow<Int> = _clickInterval.asStateFlow()

    private val _randomJitter = MutableStateFlow(15) // default 15px coordinate jitter radius
    val randomJitter: StateFlow<Int> = _randomJitter.asStateFlow()

    // Execution Speed mode: "INSTANT" (0-20ms), "ANTIBAN" (100-150ms randomized), "HUMAN" (randomized)
    private val _speedMode = MutableStateFlow("ANTIBAN")
    val speedMode: StateFlow<String> = _speedMode.asStateFlow()

    // Log tracking for real-time dashboard visualization
    private val _scanLogs = MutableStateFlow<List<LogEntry>>(emptyList())
    val scanLogs: StateFlow<List<LogEntry>> = _scanLogs.asStateFlow()

    data class LogEntry(
        val timestamp: String,
        val text: String,
        val isMatch: Boolean
    )

    data class AppAutomationConfig(
        val id: String,
        val name: String,
        val isEnabled: Boolean,
        val acceptButtonKeyword: String,
        val orderKeywords: String
    )

    data class VisualTargetPoint(
        val id: Int,
        val isSwipe: Boolean,
        val xPercent: Float,
        val yPercent: Float,
        val endXPercent: Float = 0.7f,
        val endYPercent: Float = 0.5f,
        val delayMs: Int = 250
    )

    private val _visualTargets = MutableStateFlow<List<VisualTargetPoint>>(emptyList())
    val visualTargets: StateFlow<List<VisualTargetPoint>> = _visualTargets.asStateFlow()

    private val _targetApps = MutableStateFlow<List<AppAutomationConfig>>(emptyList())
    val targetApps: StateFlow<List<AppAutomationConfig>> = _targetApps.asStateFlow()

    fun initialize(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadFilters()
        loadTargetApps(context)
        loadVisualTargets(context)
        logEvent("Dr.Clicker initialized with loaded filters and targets", false)
    }

    fun loadVisualTargets(context: Context) {
        val sharedPrefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val dataStr = sharedPrefs.getString("saved_visual_targets_v2", "") ?: ""
        if (dataStr.isEmpty()) {
            _visualTargets.value = emptyList()
            return
        }
        try {
            val list = mutableListOf<VisualTargetPoint>()
            val parts = dataStr.split("|")
            for (p in parts) {
                if (p.isEmpty()) continue
                val fields = p.split(",")
                if (fields.size >= 7) {
                    list.add(
                        VisualTargetPoint(
                            id = fields[0].toInt(),
                            isSwipe = fields[1].toBoolean(),
                            xPercent = fields[2].toFloat(),
                            yPercent = fields[3].toFloat(),
                            endXPercent = fields[4].toFloat(),
                            endYPercent = fields[5].toFloat(),
                            delayMs = fields[6].toInt()
                        )
                    )
                }
            }
            _visualTargets.value = list
        } catch (e: Exception) {
            Log.e(TAG, "Error loading visual targets: ${e.message}")
        }
    }

    fun saveVisualTargets(context: Context) {
        val sharedPrefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val sb = java.lang.StringBuilder()
        _visualTargets.value.forEachIndexed { index, t ->
            if (index > 0) sb.append("|")
            sb.append("${t.id},${t.isSwipe},${t.xPercent},${t.yPercent},${t.endXPercent},${t.endYPercent},${t.delayMs}")
        }
        sharedPrefs.edit().putString("saved_visual_targets_v2", sb.toString()).apply()
    }

    fun addVisualTarget(context: Context, isSwipe: Boolean) {
        val current = _visualTargets.value.toMutableList()
        val nextId = if (current.isEmpty()) 1 else current.maxOf { it.id } + 1
        current.add(
            VisualTargetPoint(
                id = nextId,
                isSwipe = isSwipe,
                xPercent = 0.5f,
                yPercent = 0.35f + (nextId * 0.04f).coerceAtMost(0.4f),
                endXPercent = 0.75f,
                endYPercent = 0.35f + (nextId * 0.04f).coerceAtMost(0.4f),
                delayMs = _clickInterval.value
            )
        )
        _visualTargets.value = current
        saveVisualTargets(context)
        logEvent("Added Visual Target #${nextId} (Swipe=$isSwipe)", false)
    }

    fun removeLastVisualTarget(context: Context) {
        val current = _visualTargets.value.toMutableList()
        if (current.isNotEmpty()) {
            val removed = current.removeAt(current.lastIndex)
            _visualTargets.value = current
            saveVisualTargets(context)
            logEvent("Removed Visual Target #${removed.id}", false)
        }
    }

    fun clearAllVisualTargets(context: Context) {
        _visualTargets.value = emptyList()
        saveVisualTargets(context)
        logEvent("Cleared all visual targets", false)
    }

    fun updateVisualTargetPosition(context: Context, id: Int, xP: Float, yP: Float) {
        val current = _visualTargets.value.map {
            if (it.id == id) {
                it.copy(xPercent = xP.coerceIn(0f, 1f), yPercent = yP.coerceIn(0f, 1f))
            } else it
        }
        _visualTargets.value = current
        saveVisualTargets(context)
    }

    fun updateVisualTargetSwipeEndPosition(context: Context, id: Int, xP: Float, yP: Float) {
        val current = _visualTargets.value.map {
            if (it.id == id) {
                it.copy(endXPercent = xP.coerceIn(0f, 1f), endYPercent = yP.coerceIn(0f, 1f))
            } else it
        }
        _visualTargets.value = current
        saveVisualTargets(context)
    }

    fun updateVisualTargetDelay(context: Context, id: Int, delay: Int) {
        val current = _visualTargets.value.map {
            if (it.id == id) {
                it.copy(delayMs = delay.coerceAtLeast(10))
            } else it
        }
        _visualTargets.value = current
        saveVisualTargets(context)
    }

    fun loadTargetApps(context: Context) {
        val sharedPrefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Force upgrade presets with comprehensive target words and matching conditions requested by user
        val keyVersionChecked = "v2_comprehensive_keywords_loaded"
        val isUpgraded = sharedPrefs.getBoolean(keyVersionChecked, false)
        
        val defaultApps = listOf(
            AppAutomationConfig(
                id = "OLA",
                name = "Ola",
                isEnabled = true,
                acceptButtonKeyword = "ACCEPT, ACCEPT RIDE, ACCEPT ORDER, TAP TO ACCEPT, SLIDE TO ACCEPT, SWIPE TO ACCEPT, CONFIRM, CONFIRM BOOKING, BOOK RIDE, ARRIVED",
                orderKeywords = "Ola, Ride, Cash, Online, New Ride, New Order, Incoming Request, New Request, Order Assigned, Booking Received, New Trip, Naya Order, Nayi Ride, Naya Request, Request Received, Earnings, Earning, Est. Earnings, Estimated Fare, Order Pay, You Will Earn, Trip Fare, Kamai, Surge Pay, Bonus, Incentive, Rain Incentive, High Demand Bonus"
            ),
            AppAutomationConfig(
                id = "UBER",
                name = "Uber",
                isEnabled = true,
                acceptButtonKeyword = "ACCEPT, ACCEPT RIDE, ACCEPT ORDER, TAP TO ACCEPT, SLIDE TO ACCEPT, SWIPE TO ACCEPT, CONFIRM, CONFIRM BOOKING, GO TO STORE, START TRIP, BOOK RIDE, ARRIVED",
                orderKeywords = "Uber, Trip, Cash, Online, New Ride, New Order, Incoming Request, New Request, Order Assigned, Booking Received, New Trip, Naya Order, Nayi Ride, Naya Request, Request Received, Earnings, Earning, Est. Earnings, Estimated Fare, Order Pay, You Will Earn, Trip Fare, Kamai, Surge Pay, Bonus, Incentive, Rain Incentive, High Demand Bonus"
            ),
            AppAutomationConfig(
                id = "RAPIDO",
                name = "Rapido",
                isEnabled = true,
                acceptButtonKeyword = "ACCEPT, ACCEPT RIDE, ACCEPT ORDER, TAP TO ACCEPT, SLIDE TO ACCEPT, SWIPE TO ACCEPT, CONFIRM, CONFIRM BOOKING, GO TO STORE, START TRIP, BOOK RIDE, ARRIVED",
                orderKeywords = "Rapido, Ride, Cash, Online, New Ride, New Order, Incoming Request, New Request, Order Assigned, Booking Received, New Trip, Naya Order, Nayi Ride, Naya Request, Request Received, Earnings, Earning, Est. Earnings, Estimated Fare, Order Pay, You Will Earn, Trip Fare, Kamai, Surge Pay, Bonus, Incentive, Rain Incentive, High Demand Bonus"
            ),
            AppAutomationConfig(
                id = "SWIGGY",
                name = "Swiggy",
                isEnabled = true,
                acceptButtonKeyword = "ACCEPT, CONFIRM, ACCEPT ORDER, TAP TO ACCEPT, SLIDE TO ACCEPT, SWIPE TO ACCEPT, GO TO STORE, START TRIP, ARRIVED",
                orderKeywords = "Swiggy, Food, Delivery, Cash, Online, New Ride, New Order, Incoming Request, New Request, Order Assigned, Booking Received, New Trip, Naya Order, Nayi Ride, Naya Request, Request Received, Earnings, Earning, Est. Earnings, Estimated Fare, Order Pay, You Will Earn, Trip Fare, Kamai, Surge Pay, Bonus, Incentive, Rain Incentive, High Demand Bonus"
            )
        )
        
        if (!isUpgraded) {
            sharedPrefs.edit().apply {
                putBoolean(keyVersionChecked, true)
                for (app in defaultApps) {
                    putBoolean("app_enabled_${app.id}", app.isEnabled)
                    putString("app_btn_${app.id}", app.acceptButtonKeyword)
                    putString("app_kws_${app.id}", app.orderKeywords)
                }
            }.apply()
        }
        
        val appsList = mutableListOf<AppAutomationConfig>()
        
        for (app in defaultApps) {
            val enabled = sharedPrefs.getBoolean("app_enabled_${app.id}", app.isEnabled)
            val btn = sharedPrefs.getString("app_btn_${app.id}", app.acceptButtonKeyword) ?: app.acceptButtonKeyword
            val kws = sharedPrefs.getString("app_kws_${app.id}", app.orderKeywords) ?: app.orderKeywords
            appsList.add(AppAutomationConfig(app.id, app.name, enabled, btn, kws))
        }
        
        val customAppsStr = sharedPrefs.getString("custom_apps_list", "") ?: ""
        if (customAppsStr.isNotEmpty()) {
            val customIds = customAppsStr.split(",").filter { it.isNotEmpty() }
            for (id in customIds) {
                val name = sharedPrefs.getString("app_name_$id", id) ?: id
                val enabled = sharedPrefs.getBoolean("app_enabled_$id", true)
                val btn = sharedPrefs.getString("app_btn_$id", "ACCEPT") ?: "ACCEPT"
                val kws = sharedPrefs.getString("app_kws_$id", "") ?: ""
                appsList.add(AppAutomationConfig(id, name, enabled, btn, kws))
            }
        }
        _targetApps.value = appsList
    }

    fun updateAppConfig(context: Context, appConfig: AppAutomationConfig) {
        val sharedPrefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit().apply {
            putBoolean("app_enabled_${appConfig.id}", appConfig.isEnabled)
            putString("app_btn_${appConfig.id}", appConfig.acceptButtonKeyword)
            putString("app_kws_${appConfig.id}", appConfig.orderKeywords)
        }.apply()
        
        loadTargetApps(context)
        logEvent("App updated: ${appConfig.name} (${if (appConfig.isEnabled) "ACTIVE" else "MUTED"})", false)
    }

    fun addCustomApp(context: Context, name: String, acceptButton: String, orderKeywords: String) {
        val sharedPrefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        val id = "CUSTOM_" + cleanName.replace(" ", "_").uppercase(Locale.getDefault())
        
        val currentCustomList = sharedPrefs.getString("custom_apps_list", "") ?: ""
        val listSplit = currentCustomList.split(",").filter { it.isNotEmpty() }
        val newCustomList = if (listSplit.contains(id)) {
            currentCustomList
        } else if (currentCustomList.isEmpty()) {
            id
        } else {
            "$currentCustomList,$id"
        }
        
        sharedPrefs.edit().apply {
            putString("custom_apps_list", newCustomList)
            putString("app_name_$id", cleanName)
            putBoolean("app_enabled_$id", true)
            putString("app_btn_$id", acceptButton)
            putString("app_kws_$id", orderKeywords)
        }.apply()
        
        loadTargetApps(context)
        logEvent("Custom App Added: $cleanName", true)
    }

    fun deleteCustomApp(context: Context, id: String) {
        val sharedPrefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentCustomList = sharedPrefs.getString("custom_apps_list", "") ?: ""
        val newCustomList = currentCustomList.split(",")
            .filter { it != id && it.isNotEmpty() }
            .joinToString(",")
        
        val name = sharedPrefs.getString("app_name_$id", id) ?: id
        sharedPrefs.edit().apply {
            putString("custom_apps_list", newCustomList)
            remove("app_name_$id")
            remove("app_enabled_$id")
            remove("app_btn_$id")
            remove("app_kws_$id")
        }.apply()
        
        loadTargetApps(context)
        logEvent("Custom App Removed: $name", false)
    }

    private fun loadFilters() {
        _minPrice.value = prefs.getInt(KEY_MIN_PRICE, 0) // Default 0 ₹ (most lowest price)
        _maxPrice.value = prefs.getInt(KEY_MAX_PRICE, Int.MAX_VALUE)
        _maxPickupDistance.value = prefs.getFloat(KEY_MAX_PICKUP_DIST, 15.0f) // default to 15.0 KM
        _maxDropDistance.value = prefs.getFloat(KEY_MAX_DROP_DIST, 30.0f) // default to 30.0 KM
        _speedMode.value = prefs.getString(KEY_SPEED_MODE, "ANTIBAN") ?: "ANTIBAN"
        _isDarkTheme.value = prefs.getBoolean(KEY_DARK_THEME, false)
        _minPricePerKm.value = prefs.getFloat(KEY_MIN_PRICE_PER_KM, 0.0f)
        _clickInterval.value = prefs.getInt(KEY_CLICK_INTERVAL, 250)
        _randomJitter.value = prefs.getInt(KEY_RANDOM_JITTER, 15)
    }

    fun updateDarkTheme(enabled: Boolean) {
        _isDarkTheme.value = enabled
        prefs.edit().putBoolean(KEY_DARK_THEME, enabled).apply()
        logEvent("Theme option updated: " + if (enabled) "Dark Theme Night Shift Mode" else "Light Contrast Warm Coffee Mode", false)
    }

    fun updateSpeedMode(mode: String) {
        _speedMode.value = mode
        prefs.edit().putString(KEY_SPEED_MODE, mode).apply()
        logEvent("Execution speed updated: $mode", false)
    }

    fun setScanning(active: Boolean) {
        _isScanning.value = active
        logEvent("Master Toggle changed: " + if (active) "ACTIVE / START SCANNING" else "HALTED / STOP SCANNING", active)
    }

    fun updateMinPrice(valPrice: Int) {
        _minPrice.value = valPrice
        prefs.edit().putInt(KEY_MIN_PRICE, valPrice).apply()
        logEvent("Filter updated: Min Payout = ₹$valPrice", false)
    }

    fun updateMaxPrice(valPrice: Int) {
        val finalPrice = if (valPrice <= 0) Int.MAX_VALUE else valPrice
        _maxPrice.value = finalPrice
        prefs.edit().putInt(KEY_MAX_PRICE, finalPrice).apply()
        logEvent("Filter updated: Max Payout = " + (if (finalPrice == Int.MAX_VALUE) "∞" else "₹$finalPrice"), false)
    }

    fun updateMaxPickupDistance(kms: Float) {
        _maxPickupDistance.value = kms
        prefs.edit().putFloat(KEY_MAX_PICKUP_DIST, kms).apply()
        logEvent("Filter updated: Max Pickup Proximity = ${kms}KM", false)
    }

    fun updateMaxDropDistance(kms: Float) {
        _maxDropDistance.value = kms
        prefs.edit().putFloat(KEY_MAX_DROP_DIST, kms).apply()
        logEvent("Filter updated: Max Drop Distance = ${kms}KM", false)
    }

    fun updateMinPricePerKm(value: Float) {
        _minPricePerKm.value = value
        prefs.edit().putFloat(KEY_MIN_PRICE_PER_KM, value).apply()
        logEvent("Filter updated: Min Rate = ₹${String.format(java.util.Locale.getDefault(), "%.1f", value)}/KM", false)
    }

    fun updateClickInterval(value: Int) {
        _clickInterval.value = value
        prefs.edit().putInt(KEY_CLICK_INTERVAL, value).apply()
        logEvent("Interval updated: Auto-Click rate = ${value}ms", false)
    }

    fun updateRandomJitter(value: Int) {
        _randomJitter.value = value
        prefs.edit().putInt(KEY_RANDOM_JITTER, value).apply()
        logEvent("Jitter updated: Coordinate offset bounds = ±${value}px", false)
    }

    fun logEvent(message: String, isMatch: Boolean) {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        val timeStr = sdf.format(Date())
        Log.d(TAG, "[$timeStr] $message")
        
        val currentList = _scanLogs.value.toMutableList()
        currentList.add(0, LogEntry(timeStr, message, isMatch))
        if (currentList.size > 50) { // Limit to last 50 logs for memory efficiency
            currentList.removeAt(currentList.lastIndex)
        }
        _scanLogs.value = currentList
    }
}
