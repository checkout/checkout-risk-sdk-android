package com.checkout.risk

import android.content.Context
import android.util.Base64
import com.fingerprintjs.android.fingerprint.Fingerprinter
import com.fingerprintjs.android.fingerprint.FingerprinterFactory
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

    /**
     * Collects the open-source device id asynchronously.
     *
     * The raw device id is base64-encoded into [FingerprintOsResult.Success.sealedResult] so it
     * can travel in the `sealed_result` field of the `fingerprint/v2` collectors payload.
     *
     * @return FingerprintOsResult containing the device id on success, or a message on failure.
     */
    suspend fun publishData(): FingerprintOsResult =
        suspendCoroutine { continuation ->
            try {
                fingerprinter.getDeviceId(version = Fingerprinter.Version.V_5) { result ->
                    val sealedResult =
                        Base64.encodeToString(
                            result.deviceId.toByteArray(Charsets.UTF_8),
                            Base64.NO_WRAP,
                        )
                    continuation.resume(
                        FingerprintOsResult.Success(
                            deviceId = result.deviceId,
                            sealedResult = sealedResult,
                        ),
                    )
                }
            } catch (e: Throwable) {
                continuation.resume(
                    FingerprintOsResult.Failure(e.message ?: "Unknown error"),
                )
            }
        }
}

internal sealed class FingerprintOsResult {
    data class Success(val deviceId: String, val sealedResult: String) : FingerprintOsResult()

    data class Failure(val description: String) : FingerprintOsResult()
}
