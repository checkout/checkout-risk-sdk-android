package com.checkout.risk

import android.content.Context
import android.util.Base64
import com.fingerprintjs.android.fingerprint.Fingerprinter
import com.fingerprintjs.android.fingerprint.FingerprinterFactory
import com.fingerprintjs.android.fingerprint.fingerprinting_signals.FingerprintingSignal
import com.fingerprintjs.android.fingerprint.signal_providers.StabilityLevel
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Service wrapping the open-source
 * [fingerprintjs-android](https://github.com/fingerprintjs/fingerprintjs-android)
 * library — the `fingerprint_os` collector.
 *
 * Unlike the PRO collector, this computes a device id entirely on-device with no backend
 * call, so its ability to dedupe similar devices is reduced. Its purpose here is to provide
 * a second, free device id alongside PRO for downstream comparison.
 *
 * @param context The Android context.
 */
internal class FingerprintOsService(
    context: Context,
) {
    private val fingerprinter: Fingerprinter = FingerprinterFactory.create(context)
    private val gson = Gson()

    /**
     * Collects the open-source device data asynchronously.
     *
     * The raw device signals (manufacturer, model, RAM, sensors, locale, …) are gathered
     * alongside the computed device id and assembled into a `device` object, mirroring the
     * `simple` collector in the JS SDK. That payload is JSON-serialised and base64-encoded
     * into [FingerprintOsResult.Success.sealedResult] so it can travel in the `sealed_result`
     * field of the `fingerprint/v2` collectors payload.
     *
     * The generated [FingerprintOsResult.Success.requestId] matches the `requestId` embedded in
     * the payload; the caller uses it as the root `fp_request_id` when the PRO collector is absent.
     *
     * @return FingerprintOsResult containing the payload on success, or a message on failure.
     */
    suspend fun publishData(): FingerprintOsResult =
        try {
            val deviceId = awaitDeviceId()

            // getFingerprintingSignalsProvider / getSignalsMatching are @WorkerThread (blocking),
            // so gather the raw signals off the main thread.
            val signals =
                withContext(Dispatchers.IO) {
                    fingerprinter
                        .getFingerprintingSignalsProvider()
                        ?.getSignalsMatching(Fingerprinter.Version.V_5, StabilityLevel.OPTIMAL)
                        ?: emptyList()
                }

            val requestId = UUID.randomUUID().toString()

            val device = linkedMapOf<String, String>("visitor_id" to deviceId)
            signals.forEach { signal -> device[signalKey(signal)] = signal.getHashableString() }

            val payload = mapOf("requestId" to requestId, "device" to device)
            val sealedResult =
                Base64.encodeToString(
                    gson.toJson(payload).toByteArray(Charsets.UTF_8),
                    Base64.NO_WRAP,
                )

            FingerprintOsResult.Success(
                deviceId = deviceId,
                requestId = requestId,
                sealedResult = sealedResult,
            )
        } catch (e: Throwable) {
            FingerprintOsResult.Failure(e.message ?: "Unknown error")
        }

    private suspend fun awaitDeviceId(): String =
        suspendCoroutine { continuation ->
            fingerprinter.getDeviceId(version = Fingerprinter.Version.V_5) { result ->
                continuation.resume(result.deviceId)
            }
        }

    /**
     * Derives a stable, readable key for a signal from its class name, e.g.
     * `ManufacturerNameSignal` -> `manufacturerName`.
     */
    private fun signalKey(signal: FingerprintingSignal<*>): String {
        val name = signal::class.simpleName ?: return "unknown"
        val stripped = name.removeSuffix("Signal")
        return stripped.replaceFirstChar { it.lowercase() }
    }
}

internal sealed class FingerprintOsResult {
    data class Success(
        val deviceId: String,
        val requestId: String,
        val sealedResult: String,
    ) : FingerprintOsResult()

    data class Failure(val description: String) : FingerprintOsResult()
}
