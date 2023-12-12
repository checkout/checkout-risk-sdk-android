package com.checkout.risk_sdk_android

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.InputStreamReader

class DeviceDataServiceTest {
    private val mockWebServer: MockWebServer = MockWebServer()

    @Before
    fun setUp() {
        mockWebServer.start()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `getConfiguration() should return configuration when successful`() {
        val response = MockResponse()
            .setResponseCode(200)
            .setBody(MockResponseFileReader("fingerprint_response_200.json").content)

        mockWebServer.enqueue(response)

        val deviceDataService = DeviceDataService(
            mockWebServer.url("/").toString(),
            "pk_test_key",
            RiskIntegrationType.STANDALONE
        )

        runTest {
            deviceDataService.getConfiguration().getOrNull()?.let {
                Assert.assertEquals(true, it.fingerprintIntegration.enabled)
                Assert.assertEquals("pk_test_key", it.fingerprintIntegration.publicKey)
            }
        }

        val request = mockWebServer.takeRequest()

        Assert.assertEquals("GET", request.method)
        Assert.assertEquals(
            "/collect/configuration?integrationType=RiskAndroidStandalone",
            request.path
        )
    }

    @Test
    fun `getConfiguration() should throw exception when unsuccessful`() {
        val response = MockResponse()
            .setResponseCode(500)

        mockWebServer.enqueue(response)

        val deviceDataService = DeviceDataService(
            mockWebServer.url("/").toString(),
            "pk_test_key",
            RiskIntegrationType.STANDALONE
        )

        runTest {
            deviceDataService.getConfiguration().exceptionOrNull()?.let {
                Assert.assertEquals("Server Error", it.message)
            }
        }
    }
}


class MockResponseFileReader(path: String) {

    val content: String

    init {
        val reader = InputStreamReader(this.javaClass.classLoader?.getResourceAsStream(path))
        content = reader.readText()
        reader.close()
    }
}