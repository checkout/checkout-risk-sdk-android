package com.checkout.risk

import android.content.Context
import android.util.Base64
import com.fingerprintjs.android.fingerprint.Fingerprinter
import com.fingerprintjs.android.fingerprint.FingerprinterFactory
import com.fingerprintjs.android.fingerprint.signal_providers.StabilityLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Service wrapping the open-source
 * [fingerprintjs-android](https://github.com/fingerprintjs/fingerprintjs-android)
 * library — the `simple` collector.
 *
 * Unlike the PRO collector, this computes a device id entirely on-device with no backend
 * call, so its ability to dedupe similar devices is reduced. Its purpose here is to provide
 * a second, free device id alongside PRO for downstream comparison.
 *
 * @param context The Android context.
 * @param dropFieldPaths Dot-notation paths into the `device` data that the `configurations`
 * endpoint asked to drop before encoding (e.g. "canvas.value.geometry"). Paths that do not
 * resolve against the collected data are ignored.
 * @param timeoutMs Optional collection budget in milliseconds, supplied by the `configurations`
 * endpoint. When set (and positive), collection that overruns it is abandoned and reported as a
 * failure rather than blocking `publishData`. Null means collect with no time limit.
 */
internal class SimpleService(
    context: Context,
    private val dropFieldPaths: List<String> = emptyList(),
    private val timeoutMs: Long? = null,
) {
    private val fingerprinter: Fingerprinter = FingerprinterFactory.create(context)

    /**
     * Collects the open-source device data asynchronously.
     *
     * The raw device signals (manufacturer, model, RAM, sensors, locale, …) are gathered
     * alongside the computed device id and assembled into a `device` object, mirroring the
     * `simple` collector in the JS SDK. That payload is JSON-serialised and base64-encoded
     * into [SimpleResult.Success.sealedResult] so it can travel in the `sealed_result`
     * field of the `fingerprint/v2` collectors payload.
     *
     * The generated [SimpleResult.Success.requestId] matches the `requestId` embedded in
     * the payload; the caller uses it as the root `fp_request_id` when the PRO collector is absent.
     *
     * When [timeoutMs] is configured, collection is bounded by it and a timeout is surfaced as
     * [SimpleResult.Failure] so a slow device cannot hold up the publish of the other collectors.
     *
     * @return SimpleResult containing the payload on success, or a message on failure.
     */
    suspend fun publishData(): SimpleResult =
        try {
            collect()
                ?: SimpleResult.Failure(
                    "Timed out collecting simple device signals after ${timeoutMs}ms",
                )
        } catch (e: Throwable) {
            SimpleResult.Failure(e.message ?: "Unknown error")
        }

    /**
     * Gathers the device id and signals and assembles the sealed payload, honouring [timeoutMs]
     * when the backend supplied a positive value. Returns null only when that timeout elapses
     * before collection completes.
     */
    private suspend fun collect(): SimpleResult.Success? {
        val gather: suspend () -> SimpleResult.Success = {
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

            SimpleResult.Success(
                deviceId = deviceId,
                requestId = requestId,
                sealedResult = sealedResult,
            )
        }

        return if (timeoutMs != null && timeoutMs > 0) {
            withTimeoutOrNull(timeoutMs) { gather() }
        } else {
            gather()
        }
    }

    private suspend fun awaitDeviceId(): String =
        suspendCancellableCoroutine { continuation ->
            fingerprinter.getDeviceId(version = Fingerprinter.Version.V_5) { result ->
                if (continuation.isActive) {
                    continuation.resume(result.deviceId)
                }
            }
        }
}

internal sealed class SimpleResult {
    data class Success(
        val deviceId: String,
        val requestId: String,
        val sealedResult: String,
    ) : SimpleResult()

    data class Failure(val description: String) : SimpleResult()
}
