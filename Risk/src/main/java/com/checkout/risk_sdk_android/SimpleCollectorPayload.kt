package com.checkout.risk

import com.fingerprintjs.android.fingerprint.fingerprinting_signals.FingerprintingSignal
import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * Pure (Android-free) assembly of the `simple` / `fingerprint_os` collector payload, kept
 * separate from [FingerprintOsService] so the serialisation and drop-field logic can be unit
 * tested without a device. The service layer owns the Android-only concerns (running the
 * fingerprinter and base64-encoding the result).
 */
internal object SimpleCollectorPayload {
    private val gson = Gson()

    /**
     * Builds the JSON payload (pre-base64) for the collector:
     * `{ "requestId": <id>, "device": { "visitor_id": <id>, <signal>: <value>, … } }`,
     * with every [dropFieldPaths] entry removed from the `device` object.
     *
     * @param deviceId The on-device computed id, sent as `device.visitor_id`.
     * @param requestId The client-generated request id, echoed at the payload root.
     * @param signals The raw device signals gathered from fingerprintjs-android.
     * @param dropFieldPaths Dot-notation paths into `device` to remove before encoding.
     */
    fun build(
        deviceId: String,
        requestId: String,
        signals: List<FingerprintingSignal<*>>,
        dropFieldPaths: List<String>,
    ): String {
        val device = linkedMapOf<String, String>("visitor_id" to deviceId)
        signals.forEach { signal -> device[deviceKey(signal)] = signal.getHashableString() }

        val deviceJson = gson.toJsonTree(device).asJsonObject
        dropFieldPaths.forEach { path -> dropPath(deviceJson, path) }

        val payload =
            JsonObject().apply {
                addProperty("requestId", requestId)
                add("device", deviceJson)
            }
        return gson.toJson(payload)
    }

    /**
     * Derives a stable, readable key for a signal from its class name, e.g.
     * `ManufacturerNameSignal` -> `manufacturerName`.
     */
    fun deviceKey(signal: FingerprintingSignal<*>): String {
        val name = signal::class.simpleName ?: return "unknown"
        return name.removeSuffix("Signal").replaceFirstChar { it.lowercase() }
    }

    /**
     * Removes the field at [path] (dot-notation) from [root], walking nested objects. A path
     * that does not fully resolve to an existing field is a no-op, so stale or platform-specific
     * paths returned by the backend are simply ignored.
     */
    fun dropPath(
        root: JsonObject,
        path: String,
    ) {
        val segments = path.split('.')
        var parent: JsonObject = root
        for (i in 0 until segments.size - 1) {
            val child = parent.get(segments[i])
            if (child == null || !child.isJsonObject) return
            parent = child.asJsonObject
        }
        parent.remove(segments.last())
    }
}
