package com.checkout.risk_sdk_android

import android.content.Context
import com.fingerprintjs.android.fpjs_pro.Configuration
import com.fingerprintjs.android.fpjs_pro.FingerprintJS
import com.fingerprintjs.android.fpjs_pro.FingerprintJSFactory
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Service for interacting with FingerprintJS.
 *
 * @param context The Android context.
 * @param fingerprintPublicKey The API key for FingerprintJS.
 * @param fingerprintEndpoint The endpoint for FingerprintJS.
 */
class FingerprintService(
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
     * @return Result containing the requestId on success, or an exception on failure.
     */
    suspend fun publishData(): Result<String> = runCatching {
        suspendCoroutine { continuation ->
            client.getVisitorId(
                listener = {
                    continuation.resume(it.requestId)
                },
                errorListener = {
                    continuation.resumeWithException(FingerprintServiceException(it.description))
                }
            )
        }
    }
}

class FingerprintServiceException(message: String?) : Exception(message)
