package com.example

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import android.util.Log

// Chat message model
data class ChatMessage(
    val sender: String, // "USER" or "MODEL"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

// Gemini Request Payloads using Moshi compatible layout
data class GeminiPart(
    val text: String
)

data class GeminiContent(
    val role: String, // "user" or "model"
    val parts: List<GeminiPart>
)

data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiSystemInstruction? = null
)

data class GeminiSystemInstruction(
    val parts: List<GeminiPart>
)

object GeminiChatService {
    private const val TAG = "GeminiChatService"
    private const val MODEL = "gemini-3.5-flash"
    
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
        
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // Default System Instruction sets role/persona
    private val SYSTEM_ROLE = """
        You are 'Dr.Clicker AI Assistant', the dedicated, intelligent support partner of the Dr.Clicker driver automation app.
        Dr.Clicker is an advanced ergonomic auto-clicker assistance app for delivery drivers (such as Rapido, Ola, Uber, etc.), which analyzes offer cards using accessibility services, provides filters (like payment threshold, minimum distance, maximum payout, cash/online filter, etc.), and automates taps to optimize driver shifts.
        
        Instructions:
        1. Always be professional, helpful, polite, and encouraging!
        2. Answer queries related to:
           - App configuration (Offer filtering, distance, cash vs online)
           - Floating overlay panel settings
           - Accessibility settings (Why "Dr.Clicker Accessibility Service" is needed, how to turn it on)
           - Deactivation & Payment activation (Subscription fee packages: Daily Rs.30, Weekly Rs.150, Monthly Rs.350; pending live ledger verification)
           - General troubleshooting of driver shifts & delivery optimization
        3. Keep responses punchy, concise, formatted nicely with brief bullets, and direct.
        4. Do NOT give raw code unless specifically asked, instead focus on driving instructions and simple steps in both English and Hinglish (mix of Hindi & English) as drivers love it!
    """.trimIndent()

    suspend fun getChatResponse(history: List<ChatMessage>): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "Gemini API Key is empty or placeholder! Please set it in secrets")
            return@withContext "Apologies! Dr.Clicker Assistant's connection keys are not configured in your AI Studio secrets environment. Please contact chief administrator or set GEMINI_API_KEY."
        }

        // Map ChatMessage list to Gemini REST API contents list
        val contents = history.map { 
            GeminiContent(
                role = if (it.sender == "USER") "user" else "model",
                parts = listOf(GeminiPart(text = it.text))
            )
        }

        val requestPayload = GeminiRequest(
            contents = contents,
            systemInstruction = GeminiSystemInstruction(
                parts = listOf(GeminiPart(text = SYSTEM_ROLE))
            )
        )

        val jsonAdapter = moshi.adapter(GeminiRequest::class.java)
        val jsonString = jsonAdapter.toJson(requestPayload)
        
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey"
        
        val requestBody = jsonString.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful || body == null) {
                    Log.e(TAG, "Request failed: ${response.code} body: $body")
                    return@withContext "Gateway limit or network glitch (Code: ${response.code}). Please try again."
                }

                // Parse response manually or with generic map to be extremely safe against complex Gson or Moshi serialization mismatches
                val rootJson = moshi.adapter(Map::class.java).fromJson(body)
                val candidates = rootJson?.get("candidates") as? List<*>
                val firstCandidate = candidates?.firstOrNull() as? Map<*, *>
                val content = firstCandidate?.get("content") as? Map<*, *>
                val parts = content?.get("parts") as? List<*>
                val firstPart = parts?.firstOrNull() as? Map<*, *>
                val text = firstPart?.get("text") as? String

                if (text != null) {
                    text
                } else {
                    Log.e(TAG, "Could not locate text block in response layout: $body")
                    "Main model dispatch node can't formulate text right now. Please try in a moment!"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calling API", e)
            "Network error: ${e.localizedMessage ?: "Connection Timeout"}. Check internet connectivity."
        }
    }
}
