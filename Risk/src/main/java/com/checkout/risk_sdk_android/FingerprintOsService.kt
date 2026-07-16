package com.checkout.risk

import android.content.Context
import android.util.Base64
import com.fingerprintjs.android.fingerprint.Fingerprinter
import com.fingerprintjs.android.fingerprint.FingerprinterFactory
import com.fingerprintjs.android.fingerprint.signal_providers.StabilityLevel
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
 * @param dropFieldPaths Dot-notation paths into the `device` data that the `configurations`
 * endpoint asked to drop before encoding (e.g. "canvas.value.geometry"). Paths that do not
 * resolve against the collected data are ignored.
 */
internal class FingerprintOsService(
    context: Context,
    private val dropFieldPaths: List<String> = emptyList(),
) {
    private val fingerprinter: Fingerprinter = FingerprinterFactory.create(context)

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
            val payloadJson =
                SimpleCollectorPayload.build(deviceId, requestId, signals, dropFieldPaths)
            val sealedResult =
                Base64.encodeToString(payloadJson.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

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
}

internal sealed class FingerprintOsResult {
    data class Success(
        val deviceId: String,
        val requestId: String,
        val sealedResult: String,
    ) : FingerprintOsResult()

    data class Failure(val description: String) : FingerprintOsResult()
}
