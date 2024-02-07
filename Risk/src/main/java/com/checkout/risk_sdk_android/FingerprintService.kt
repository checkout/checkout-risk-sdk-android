package com.checkout.risk

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
 * @param internalConfig The Internal config for the SDK (RiskSDKInternalConfigImpl).
 */
internal class FingerprintService(
    context: Context,
    private val internalConfig: RiskSDKInternalConfig,
    fingerprintPublicKey: String,
) {
    private val client: FingerprintJS =
        FingerprintJSFactory(context).createInstance(
            Configuration(
                apiKey = fingerprintPublicKey,
                endpointUrl = internalConfig.fingerprintEndpoint,
            ),
        )

    /**
     * Publishes fingerprint data asynchronously.
     *
     * @return FingerprintResult containing the requestId on success, or a message on failure.
     */
    suspend fun publishData(): FingerprintResult =
        suspendCoroutine { continuation ->
            client.getVisitorId(
                tags = generateMetaData(),
                listener = {
                    continuation.resume(FingerprintResult.Success(it.requestId))
                },
                errorListener = {
                    continuation.resume(
                        FingerprintResult.Failure(
                            it.description ?: "Unknown error",
                        ),
                    )
                },
            )
        }

    private fun generateMetaData(): Map<String, String> {
        return mapOf(
            "fpjsSource" to internalConfig.sourceType.rawValue,
            "fpjsTimestamp" to System.currentTimeMillis().toString(),
        )
    }
}

internal sealed class FingerprintResult {
    data class Success(val requestId: String) : FingerprintResult()

    data class Failure(val description: String) : FingerprintResult()
}
