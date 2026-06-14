package com.example

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

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

object JobOfferStorage {
    private const val TAG = "JobOfferStorage"
    private const val PREFS_NAME = "DrClickerJobOffers"
    private const val KEY_OFFERS_LIST = "offers_list"
    private const val MAX_OFFERS_COUNT = 100

    private val _pastOffers = MutableStateFlow<List<JobOffer>>(emptyList())
    val pastOffers: StateFlow<List<JobOffer>> = _pastOffers.asStateFlow()

    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        loadOffers(context)
        initialized = true
        Log.d(TAG, "JobOfferStorage successfully initialized. Loaded ${_pastOffers.value.size} past offers.")
    }

    @Synchronized
    fun loadOffers(context: Context) {
        try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString(KEY_OFFERS_LIST, "[]") ?: "[]"
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<JobOffer>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    JobOffer(
                        id = obj.optString("id", "${System.currentTimeMillis()}_$i"),
                        timestamp = obj.optLong("timestamp", 0L),
                        appName = obj.optString("appName", "Unknown"),
                        fare = obj.optInt("fare", 0),
                        pickupDistance = obj.optDouble("pickupDistance", 0.0).toFloat(),
                        dropDistance = obj.optDouble("dropDistance", 0.0).toFloat(),
                        satisfiesFilters = obj.optBoolean("satisfiesFilters", false),
                        reason = obj.optString("reason", "")
                    )
                )
            }
            _pastOffers.value = list.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading past offers: ${e.message}", e)
        }
    }

    @Synchronized
    fun saveOffer(context: Context, offer: JobOffer) {
        try {
            // First load current offers if for any reason in-memory flow is out of sync
            val list = _pastOffers.value.toMutableList()
            list.add(0, offer) // Add latest log entry at index 0
            if (list.size > MAX_OFFERS_COUNT) {
                list.removeAt(list.lastIndex)
            }
            _pastOffers.value = list

            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonArray = JSONArray()
            for (item in list) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("timestamp", item.timestamp)
                    put("appName", item.appName)
                    put("fare", item.fare)
                    put("pickupDistance", item.pickupDistance.toDouble())
                    put("dropDistance", item.dropDistance.toDouble())
                    put("satisfiesFilters", item.satisfiesFilters)
                    put("reason", item.reason)
                }
                jsonArray.put(obj)
            }
            prefs.edit().putString(KEY_OFFERS_LIST, jsonArray.toString()).apply()
            Log.d(TAG, "Saved new job offer locally: App=${offer.appName}, Fare=${offer.fare}, Matched=${offer.satisfiesFilters}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving past offer: ${e.message}", e)
        }
    }

    @Synchronized
    fun clearOffers(context: Context) {
        try {
            _pastOffers.value = emptyList()
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove(KEY_OFFERS_LIST).apply()
            Log.d(TAG, "Job offers history cleared successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing past offers: ${e.message}", e)
        }
    }
}
