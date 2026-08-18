package com.checkout.risk

import android.content.Context
import android.util.Base64
import android.util.Log
import com.fingerprintjs.android.fingerprint.DeviceIdResult
import com.fingerprintjs.android.fingerprint.Fingerprinter
import com.fingerprintjs.android.fingerprint.FingerprinterFactory
import com.fingerprintjs.android.fingerprint.signal_providers.StabilityLevel
import kotlinx.coroutines.CancellationException
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
 * @param dropFieldPaths Paths the `configurations` endpoint asked to drop before encoding.
 * Paths are rooted **at the `device` object**, not at the payload root, so the path for the
 * Android ID is "androidId" and NOT "device.androidId". Dot notation addresses nested objects
 * within `device`. A path that does not resolve is not silently discarded — see
 * [SimpleCollectorPayload.build].
 * @param timeoutMs Collection budget in milliseconds, supplied by the `configurations`
 * endpoint. Collection that overruns it is abandoned and reported as a failure rather than
 * blocking `publishData`. A null or non-positive value means "not configured" and falls back to
 * [DEFAULT_TIMEOUT_MS]; there is deliberately no way to express "no limit", because this runs on
 * the payment path.
 */
internal class SimpleService(
    context: Context,
    private val dropFieldPaths: List<String> = emptyList(),
    timeoutMs: Long? = DEFAULT_TIMEOUT_MS,
) {
    private val fingerprinter: Fingerprinter = FingerprinterFactory.create(context)
    private val effectiveTimeoutMs: Long = timeoutMs?.takeIf { it > 0 } ?: DEFAULT_TIMEOUT_MS

    /**
     * Collects the open-source device data asynchronously.
     *
     * The device ids computed by the SDK ([DeviceIdResult]), a selected set of device signals, and
     * the device fingerprint hash are assembled into the payload and JSON-serialised (with explicit
     * key names, so no reflection is involved and R8/ProGuard in a consumer app cannot rename the
     * keys). That payload is base64-encoded into [SimpleResult.Success.sealedResult] so it can travel
     * in the `sealed_result` field of the `fingerprint/v2` collectors payload.
     *
     * The generated [SimpleResult.Success.requestId] matches the `requestId` embedded in
     * the payload; the caller uses it as the root `fp_request_id` when the PRO collector is absent.
     *
     * [timeoutMs] is always configured, collection is bounded by it and a timeout is surfaced as
     * [SimpleResult.Failure] so a slow device cannot hold up the publish of the other collectors.
     *
     * Cancellation by the caller is not a collection failure, so [CancellationException] is
     * rethrown rather than reported as [SimpleResult.Failure] — otherwise a cancelled publish
     * would be logged as a genuine `PUBLISH_FAILURE`. Our own [timeoutMs] expiry does not reach
     * here; `withTimeoutOrNull` absorbs it and yields the timeout failure above.
     *
     * @return SimpleResult containing the payload on success, or a message on failure.
     */
    suspend fun publishData(): SimpleResult =
        try {
            collect()
                ?: SimpleResult.Failure(
                    "Timed out collecting simple device data after ${effectiveTimeoutMs}ms",
                )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            SimpleResult.Failure(e.message ?: "Unknown error")
        }

    /**
     * Gathers the device ids and fingerprint hash and assembles the sealed payload, always
     * bounded by [effectiveTimeoutMs]. Returns null only when that timeout elapses before
     * collection completes.
     */
    private suspend fun collect(): SimpleResult? =
        withTimeoutOrNull(effectiveTimeoutMs) {
            val deviceIdResult = awaitDeviceId()
            val fingerprint = awaitFingerprint()

            if (deviceIdResult.deviceId.isEmpty() || fingerprint.isEmpty()) {
                return@withTimeoutOrNull SimpleResult.Failure(
                    "Simple collector returned empty results (deviceId blank: " +
                        "${deviceIdResult.deviceId.isEmpty()}, fingerprint blank: " +
                        "${fingerprint.isEmpty()}); treating as a collection failure",
                )
            }

            val deviceSignals = collectDeviceSignals()

            val requestId = UUID.randomUUID().toString()
            val payloadJson =
                SimpleCollectorPayload.build(
                    deviceIdResult,
                    fingerprint,
                    deviceSignals,
                    requestId,
                    dropFieldPaths,
                    onUnresolvedDropPath = { path ->
                        Log.w(
                            LOG_TAG,
                            "drop_field_paths entry did not resolve against the collected " +
                                "device data and was ignored: '$path'. Paths are rooted at the " +
                                "device object (use \"androidId\", not \"device.androidId\").",
                        )
                    },
                )
            val sealedResult =
                Base64.encodeToString(payloadJson.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

            SimpleResult.Success(
                deviceId = deviceIdResult.deviceId,
                requestId = requestId,
                sealedResult = sealedResult,
            )
        }

    /**
     * Reads the specific device signals we send from the fingerprintjs provider, off the main
     * thread (the provider getters do blocking I/O — /proc, sensors, the battery intent).
     *
     * Only these signals are touched, so no permission-gated signal (e.g. the fingerprint-sensor
     * status) is ever computed, and no permission beyond those the library already declares is
     * required. Each value is the SDK's own hashable string, read by direct property/method calls
     * — there is no reflection for R8 to break. The keys are literal strings owned by us.
     */
    private suspend fun collectDeviceSignals(): Map<String, String> =
        withContext(Dispatchers.IO) {
            val provider =
                fingerprinter.getFingerprintingSignalsProvider()
                    ?: return@withContext emptyMap<String, String>()
            linkedMapOf(
                "manufacturerName" to provider.manufacturerNameSignal.getHashableString(),
                "modelName" to provider.modelNameSignal.getHashableString(),
                "totalRam" to provider.totalRamSignal.getHashableString(),
                "androidVersion" to provider.androidVersionSignal.getHashableString(),
                "kernelName" to provider.kernelVersionSignal.getHashableString(),
                "batteryHealth" to provider.batteryHealthSignal.getHashableString(),
                "dateFormat" to provider.dateFormatSignal.getHashableString(),
                "httpProxy" to provider.httpProxySignal.getHashableString(),
                "dataRoaming" to provider.dataRoamingEnabledSignal.getHashableString(),
                "sdkVersion" to provider.sdkVersionSignal.getHashableString(),
                "timezone" to provider.timezoneSignal.getHashableString(),
            )
        }

    private suspend fun awaitDeviceId(): DeviceIdResult =
        suspendCancellableCoroutine { continuation ->
            fingerprinter.getDeviceId(version = Fingerprinter.Version.V_5) { result ->
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }
        }

    private suspend fun awaitFingerprint(): String =
        suspendCancellableCoroutine { continuation ->
            fingerprinter.getFingerprint(
                version = Fingerprinter.Version.V_5,
                stabilityLevel = StabilityLevel.OPTIMAL, // pinning to default behaviour to be explicit
            ) { fingerprint ->
                if (continuation.isActive) {
                    continuation.resume(fingerprint)
                }
            }
        }

    internal companion object {
        const val DEFAULT_TIMEOUT_MS = 1000L
        private const val LOG_TAG = "RiskSimpleService"
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
