package com.checkout.risk

import com.fingerprintjs.android.fingerprint.DeviceIdResult
import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * Pure (Android-free) assembly of the `simple` collector payload, kept separate from
 * [SimpleService] so the serialisation and drop-field logic can be unit tested without a device.
 * The service layer owns the Android-only concerns (running the fingerprinter and base64-encoding
 * the result).
 */
internal object SimpleCollectorPayload {
    private val gson = Gson()

    /**
     * Builds the JSON payload (pre-base64) for the collector:
     * `{ "requestId": <id>, "device": { "visitor_id": <id>, "gsfId": …, <signal>: …, }, "fingerprint": <hash> }`,
     * with every [dropFieldPaths] entry removed from the `device` object.
     *
     * The `device` object is assembled with explicit, literal key names (not derived from the SDK
     * types by reflection), so R8/ProGuard in a consumer app cannot rename the keys.
     *
     * @param deviceIdResult The ids computed on-device; [DeviceIdResult.deviceId] is sent as
     * `device.visitor_id`.
     * @param fingerprint The device fingerprint hash computed by the SDK.
     * @param deviceSignals The selected device signals to include under `device`, keyed by the
     * literal field name to send (insertion order is preserved in the output).
     * @param requestId The client-generated request id, echoed at the payload root.
     * @param dropFieldPaths Dot-notation paths into `device` to remove before encoding.
     */
    fun build(
        deviceIdResult: DeviceIdResult,
        fingerprint: String,
        deviceSignals: Map<String, String>,
        requestId: String,
        dropFieldPaths: List<String>,
    ): String {
        val device =
            JsonObject().apply {
                addProperty("visitor_id", deviceIdResult.deviceId)
                addProperty("gsfId", deviceIdResult.gsfId)
                addProperty("androidId", deviceIdResult.androidId)
                addProperty("mediaDrmId", deviceIdResult.mediaDrmId)
                deviceSignals.forEach { (key, value) -> addProperty(key, value) }
            }
        dropFieldPaths.forEach { path -> dropPath(device, path) }

        val payload =
            JsonObject().apply {
                addProperty("requestId", requestId)
                add("device", device)
                addProperty("fingerprint", fingerprint)
            }
        return gson.toJson(payload)
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
