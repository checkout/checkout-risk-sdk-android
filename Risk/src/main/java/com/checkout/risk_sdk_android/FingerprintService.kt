package com.checkout.risk_sdk_android

import android.content.Context
import com.fingerprintjs.android.fpjs_pro.Configuration
import com.fingerprintjs.android.fpjs_pro.FingerprintJS
import com.fingerprintjs.android.fpjs_pro.FingerprintJSFactory
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Service for interacting with FingerprintJS.
 *
 * @param context The Android context.
 * @param fingerprintPublicKey The API key for FingerprintJS.
 * @param fingerprintEndpoint The endpoint for FingerprintJS.
 */
internal class FingerprintService(
    context: Context,
    fingerprintPublicKey: String,
    fingerprintEndpoint: String
) {
    private val client: FingerprintJS = FingerprintJSFactory(context).createInstance(
        Configuration(
            apiKey = fingerprintPublicKey,
            endpointUrl = fingerprintEndpoint
        )
    )

    /**
     * Publishes fingerprint data asynchronously.
     *
     * @return FingerprintResult containing the requestId on success, or a message on failure.
     */
    suspend fun publishData(): FingerprintResult =
        suspendCoroutine { continuation ->
            client.getVisitorId(
                listener = {
                    continuation.resume(FingerprintResult.Success(it.requestId))
                },
                errorListener = {
                    continuation.resume(
                        FingerprintResult.Failure(
                            it.description ?: "Unknown error"
                        )
                    )
                }
            )
        }
}


internal sealed class FingerprintResult {
    data class Success(val requestId: String) : FingerprintResult()
    data class Failure(val message: String) : FingerprintResult()
}
