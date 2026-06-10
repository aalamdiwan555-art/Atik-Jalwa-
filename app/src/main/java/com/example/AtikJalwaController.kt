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

object AtikJalwaController {
    private const val TAG = "AtikJalwaController"
    private const val PREFS_NAME = "AtikJalwaPrefs"

    // Preferences keys matching Module 3 specifications Exactly
    private const val KEY_MIN_PRICE = "Min_Price"
    private const val KEY_MAX_PRICE = "Max_Price"
    private const val KEY_MAX_PICKUP_DIST = "Max_Pickup_Distance"
    private const val KEY_MAX_DROP_DIST = "Max_Drop_Distance"

    private lateinit var prefs: SharedPreferences

    // Scanning state (Master Toggle)
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // Configurable Filters
    private val _minPrice = MutableStateFlow(0)
    val minPrice: StateFlow<Int> = _minPrice.asStateFlow()

    private val _maxPrice = MutableStateFlow(Int.MAX_VALUE)
    val maxPrice: StateFlow<Int> = _maxPrice.asStateFlow()

    private val _maxPickupDistance = MutableStateFlow(10.0f) // default to 10.0 KM
    val maxPickupDistance: StateFlow<Float> = _maxPickupDistance.asStateFlow()

    private val _maxDropDistance = MutableStateFlow(25.0f) // default to 25.0 KM
    val maxDropDistance: StateFlow<Float> = _maxDropDistance.asStateFlow()

    // Log tracking for real-time dashboard visualization
    private val _scanLogs = MutableStateFlow<List<LogEntry>>(emptyList())
    val scanLogs: StateFlow<List<LogEntry>> = _scanLogs.asStateFlow()

    data class LogEntry(
        val timestamp: String,
        val text: String,
        val isMatch: Boolean
    )

    fun initialize(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadFilters()
        logEvent("Atik Jalwa initialized with loaded filters", false)
    }

    private fun loadFilters() {
        _minPrice.value = prefs.getInt(KEY_MIN_PRICE, 100) // Default 100 ₹
        _maxPrice.value = prefs.getInt(KEY_MAX_PRICE, Int.MAX_VALUE)
        _maxPickupDistance.value = prefs.getFloat(KEY_MAX_PICKUP_DIST, 5.0f) // Default 5.0 KM
        _maxDropDistance.value = prefs.getFloat(KEY_MAX_DROP_DIST, 15.0f) // Default 15.0 KM
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
