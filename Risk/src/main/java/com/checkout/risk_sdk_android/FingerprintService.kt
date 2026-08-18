package com.checkout.risk

import android.content.Context
import com.fingerprintjs.android.fpjs_pro.Configuration
import com.fingerprintjs.android.fpjs_pro.FingerprintJS
import com.fingerprintjs.android.fpjs_pro.FingerprintJSFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Service for interacting with FingerprintJS.
 *
 * @param context The Android context.
 * @param fingerprintPublicKey The API key for FingerprintJS.
 * @param internalConfig The Internal config for the SDK (RiskSDKInternalConfigImpl).
 * @param timeoutMs The timeout value in ms - this returns
 */
internal class FingerprintService(
    context: Context,
    private val internalConfig: RiskSDKInternalConfig,
    fingerprintPublicKey: String,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
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
     * Cancellation by the caller is not a collection failure, so [CancellationException] is
     * rethrown rather than reported as a [FingerprintResult.Failure]. Every other throwable becomes
     * a Failure: PRO is a closed binary, and an escaping exception would otherwise cancel the
     * sibling collector and surface to the merchant as a thrown exception rather than a publish
     * failure. Our own [timeoutMs] expiry does not reach the catch; `withTimeoutOrNull` absorbs it
     * and yields the timeout failure above.
     *
     * @return FingerprintResult containing the requestId on success, or a message on failure.
     */
    suspend fun publishData(): FingerprintResult =
        try {
            withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine { continuation ->
                    client.getVisitorId(
                        tags = generateMetaData(),
                        listener = {
                            if (continuation.isActive) {
                                continuation.resume(FingerprintResult.Success(it.requestId))
                            }
                        },
                        errorListener = {
                            if (continuation.isActive) {
                                continuation.resume(
                                    FingerprintResult.Failure(
                                        it.description ?: "Unknown error",
                                    ),
                                )
                            }
                        },
                    )
                }
            } ?: FingerprintResult.Failure(
                "Timed out collecting fingerprint device data after ${timeoutMs}ms",
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            FingerprintResult.Failure(e.message ?: "Unknown error")
        }

    private fun generateMetaData(): Map<String, String> {
        return mapOf(
            "fpjsSource" to internalConfig.sourceType.rawValue,
            "fpjsTimestamp" to System.currentTimeMillis().toString(),
        )
    }

    internal companion object {
        const val DEFAULT_TIMEOUT_MS = TIMEOUT_DURATION_SECONDS * 1_000L
    }
}

internal sealed class FingerprintResult {
    data class Success(val requestId: String) : FingerprintResult()

    data class Failure(val description: String) : FingerprintResult()
}
