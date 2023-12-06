package com.checkout.risk

import android.content.Context
import com.fingerprintjs.android.fpjs_pro.Configuration
import com.fingerprintjs.android.fpjs_pro.FingerprintJS
import com.fingerprintjs.android.fpjs_pro.FingerprintJSFactory
import kotlin.coroutines.suspendCoroutine

/**
 * Service for interacting with FingerprintJS.
 *
 * @param context The Android context.
 * @param fingerprintPublicKey The API key for FingerprintJS.
 */
class FingerprintService(
    context: Context,
    fingerprintPublicKey: String,
) {
    private val client: FingerprintJS = FingerprintJSFactory(context).createInstance(
        Configuration(apiKey = fingerprintPublicKey)
    )

    /**
     * Publishes fingerprint data asynchronously.
     *
     * @return Result containing the requestId on success, or an exception on failure.
     */
    suspend fun publishData(): Result<String> = runCatching {
        suspendCoroutine { continuation ->
//            client.getVisitorId(
//                listener = {
//                    continuation.resume(it.requestId)
//                },
//                errorListener = {
//                    continuation.resumeWithException(Exception(it.description))
//                }
//            )
        }
    }
}

