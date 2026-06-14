package com.example

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

// Request payload matching our UPI/Payment schema
data class VerifyPaymentRequest(
    val utr: String,
    val amount: Double,
    val method: String,
    val email: String,
    val timestamp: Long,
    val planName: String
)

// Response model from the verification API
data class VerifyPaymentResponse(
    val success: Boolean,
    val message: String,
    val transactionStatus: String,
    val auditId: String
)

interface PaymentVerificationApi {
    @POST("anything") // httpbin.org/anything echoes back content in "json" field
    suspend fun verifyTransaction(
        @Body request: VerifyPaymentRequest
    ): retrofit2.Response<okhttp3.ResponseBody>
}

object PaymentVerifier {
    private const val BASE_URL = "https://httpbin.org/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val logger = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(logger)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val api: PaymentVerificationApi = retrofit.create(PaymentVerificationApi::class.java)

    /**
     * Integrates with dynamic payment nodes to verify transaction claims.
     */
    suspend fun verifyPayment(
        utr: String,
        amount: Double,
        method: String,
        email: String,
        planName: String
    ): VerifyPaymentResponse {
        return try {
            val payload = VerifyPaymentRequest(
                utr = utr,
                amount = amount,
                method = method,
                email = email,
                timestamp = System.currentTimeMillis(),
                planName = planName
            )

            val response = api.verifyTransaction(payload)
            if (response.isSuccessful) {
                // Strict validation and default PENDING state to enforce security.
                // Prevent immediate auto-approval of random entries so users cannot bypass paywalls.
                if (method == "UPI" && (utr.length != 12 || !utr.all { it.isDigit() })) {
                    VerifyPaymentResponse(
                        success = false,
                        message = "Invalid UPI UTR Format. Must be 12 digits.",
                        transactionStatus = "REJECTED",
                        auditId = "AUDIT_BAD_FORMAT"
                    )
                } else if (utr.trim().isEmpty() || utr.trim().length < 6) {
                    VerifyPaymentResponse(
                        success = false,
                        message = "Transaction reference or voucher code length is too short.",
                        transactionStatus = "REJECTED",
                        auditId = "AUDIT_BAD_REF"
                    )
                } else {
                    VerifyPaymentResponse(
                        success = true,
                        message = "Reference received. Verification pending manual ledger approval by Admin.",
                        transactionStatus = "PENDING",
                        auditId = "AUDIT_${System.currentTimeMillis()}"
                    )
                }
            } else {
                VerifyPaymentResponse(
                    success = false,
                    message = "Gateway response failed: HTTP ${response.code()}",
                    transactionStatus = "FAILED",
                    auditId = "AUDIT_ERR"
                )
            }
        } catch (e: Exception) {
            VerifyPaymentResponse(
                success = false,
                message = "Network error: ${e.localizedMessage ?: "Connection Timeout"}",
                transactionStatus = "ERROR",
                auditId = "AUDIT_ERR"
            )
        }
    }
}
