package com.checkout.risk

import com.fingerprintjs.android.fingerprint.fingerprinting_signals.ManufacturerNameSignal
import com.fingerprintjs.android.fingerprint.fingerprinting_signals.ModelNameSignal
import com.fingerprintjs.android.fingerprint.fingerprinting_signals.TotalRamSignal
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert
import org.junit.Test

class SimpleCollectorPayloadTest {
    private val signals =
        listOf(
            ManufacturerNameSignal("Google"),
            ModelNameSignal("Pixel 7"),
            TotalRamSignal(8_000_000_000L),
        )

    private fun buildDevice(
        dropFieldPaths: List<String> = emptyList(),
    ): JsonObject =
        JsonParser
            .parseString(
                SimpleCollectorPayload.build(
                    deviceId = "device-id-123",
                    requestId = "req-abc",
                    signals = signals,
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
    fun `build() includes the device id as visitor_id`() {
        val device = buildDevice().getAsJsonObject("device")

        Assert.assertEquals("device-id-123", device.get("visitor_id").asString)
    }

    @Test
    fun `build() maps each signal to a camelCase key and its hashable value`() {
        val device = buildDevice().getAsJsonObject("device")

        Assert.assertEquals("Google", device.get("manufacturerName").asString)
        Assert.assertEquals("Pixel 7", device.get("modelName").asString)
        Assert.assertEquals("8000000000", device.get("totalRam").asString)
    }

    @Test
    fun `build() drops a top-level device field named in dropFieldPaths`() {
        val device = buildDevice(dropFieldPaths = listOf("modelName")).getAsJsonObject("device")

        Assert.assertFalse(device.has("modelName"))
        // Other fields are untouched.
        Assert.assertTrue(device.has("manufacturerName"))
        Assert.assertEquals("device-id-123", device.get("visitor_id").asString)
    }

    @Test
    fun `build() ignores drop paths that do not resolve`() {
        val device =
            buildDevice(
                dropFieldPaths = listOf("doesNotExist", "manufacturerName.nested.path"),
            ).getAsJsonObject("device")

        Assert.assertTrue(device.has("manufacturerName"))
        Assert.assertEquals("Google", device.get("manufacturerName").asString)
    }

    @Test
    fun `deviceKey() strips the Signal suffix and lowercases the first letter`() {
        Assert.assertEquals(
            "manufacturerName",
            SimpleCollectorPayload.deviceKey(ManufacturerNameSignal("x")),
        )
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
        val root = JsonParser.parseString("""{ "manufacturerName": "Google" }""").asJsonObject

        SimpleCollectorPayload.dropPath(root, "manufacturerName.value")

        Assert.assertTrue(root.has("manufacturerName"))
        Assert.assertEquals("Google", root.get("manufacturerName").asString)
    }
}
