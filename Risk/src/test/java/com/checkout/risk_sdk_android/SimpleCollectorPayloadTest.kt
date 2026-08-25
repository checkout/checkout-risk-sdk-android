package com.checkout.risk

import com.fingerprintjs.android.fingerprint.DeviceIdResult
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert
import org.junit.Test

class SimpleCollectorPayloadTest {
    private val deviceIdResult =
        DeviceIdResult(
            deviceId = "device-id-123",
            gsfId = "gsf-456",
            androidId = "android-789",
            mediaDrmId = "drm-abc",
        )

    // Mirrors the signal set gathered by SimpleService.collectDeviceSignals().
    private val deviceSignals =
        linkedMapOf(
            "manufacturerName" to "Google",
            "modelName" to "Pixel 7",
            "totalRam" to "8000000000",
            "androidVersion" to "14",
            "kernelName" to "6.1.25-android14",
            "batteryHealth" to "GOOD",
            "dateFormat" to "dd/MM/yyyy",
            "httpProxy" to ":0",
            "dataRoaming" to "0",
            "sdkVersion" to "34",
            "timezone" to "Europe/London",
        )

    private fun buildDevice(
        dropFieldPaths: List<String> = emptyList(),
    ): JsonObject =
        JsonParser
            .parseString(
                SimpleCollectorPayload.build(
                    deviceIdResult = deviceIdResult,
                    fingerprint = "fp-hash-xyz",
                    deviceSignals = deviceSignals,
                    requestId = "req-abc",
                    dropFieldPaths = dropFieldPaths,
                ),
            ).asJsonObject

    @Test
    fun `build() nests device data under device with requestId at the root`() {
        val payload = buildDevice()

        Assert.assertEquals("req-abc", payload.get("requestId").asString)
        Assert.assertTrue(payload.get("device").isJsonObject)
    }

    @Test
    fun `build() maps the device ids under device and the fingerprint at the root`() {
        val payload = buildDevice()
        val device = payload.getAsJsonObject("device")

        Assert.assertEquals("device-id-123", device.get("visitor_id").asString)
        Assert.assertEquals("gsf-456", device.get("gsfId").asString)
        Assert.assertEquals("android-789", device.get("androidId").asString)
        Assert.assertEquals("drm-abc", device.get("mediaDrmId").asString)
        // The fingerprint hash is a sibling of `device`, not nested inside it.
        Assert.assertEquals("fp-hash-xyz", payload.get("fingerprint").asString)
        Assert.assertFalse(device.has("fingerprint"))
    }

    @Test
    fun `build() includes every selected device signal under device`() {
        val device = buildDevice().getAsJsonObject("device")

        deviceSignals.forEach { (key, value) ->
            Assert.assertEquals(value, device.get(key).asString)
        }
    }

    @Test
    fun `build() drops a top-level device field named in dropFieldPaths`() {
        val device = buildDevice(dropFieldPaths = listOf("androidId")).getAsJsonObject("device")

        Assert.assertFalse(device.has("androidId"))
        // Other fields are untouched.
        Assert.assertTrue(device.has("gsfId"))
        Assert.assertEquals("device-id-123", device.get("visitor_id").asString)
    }

    @Test
    fun `build() ignores drop paths that do not resolve`() {
        val device =
            buildDevice(
                dropFieldPaths = listOf("doesNotExist", "gsfId.nested.path"),
            ).getAsJsonObject("device")

        Assert.assertTrue(device.has("gsfId"))
        Assert.assertEquals("gsf-456", device.get("gsfId").asString)
    }

    @Test
    fun `drops fields from an example simple SDK response, leaving the rest intact`() {
        // An example of the device payload the simple collector assembles, loaded from a
        // resource file so it can be swapped for data captured off a real device.
        val payload =
            JsonParser
                .parseString(MockResponseFileReader("simple_device_example.json").content)
                .asJsonObject
        val device = payload.getAsJsonObject("device")

        // The drop_field_paths the `configurations` endpoint would return for this collector:
        // one path that resolves against the device data, and one that does not.
        val dropFieldPaths = listOf("mediaDrmId", "canvas.value.text")
        dropFieldPaths.forEach { path -> SimpleCollectorPayload.dropPath(device, path) }

        // The resolving path is removed.
        Assert.assertFalse(device.has("mediaDrmId"))
        // The non-resolving path is a no-op and drops nothing else.
        Assert.assertTrue(device.has("visitor_id"))
        Assert.assertTrue(device.has("gsfId"))
        Assert.assertTrue(device.has("androidId"))
        Assert.assertEquals("a1b2c3d4e5f6a7b8", device.get("visitor_id").asString)
        Assert.assertEquals("req-example-001", payload.get("requestId").asString)
        // The fingerprint hash sits alongside `device` at the payload root.
        Assert.assertEquals("e3b0c44298fc1c149afbf4c8996fb924", payload.get("fingerprint").asString)
    }

    @Test
    fun `dropPath() removes a deeply nested field`() {
        val root =
            JsonParser
                .parseString(
                    """{ "canvas": { "value": { "geometry": "g", "text": "t" } } }""",
                ).asJsonObject

        SimpleCollectorPayload.dropPath(root, "canvas.value.geometry")

        val canvasValue = root.getAsJsonObject("canvas").getAsJsonObject("value")
        Assert.assertFalse(canvasValue.has("geometry"))
        Assert.assertTrue(canvasValue.has("text"))
    }

    @Test
    fun `dropPath() is a no-op when an intermediate segment is not an object`() {
        val root = JsonParser.parseString("""{ "gsfId": "gsf-456" }""").asJsonObject

        SimpleCollectorPayload.dropPath(root, "gsfId.value")

        Assert.assertTrue(root.has("gsfId"))
        Assert.assertEquals("gsf-456", root.get("gsfId").asString)
    }
}
