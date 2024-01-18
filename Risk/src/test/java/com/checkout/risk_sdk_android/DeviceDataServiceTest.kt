package com.checkout.risk_sdk_android

import com.google.gson.Gson
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
        val response =
            MockResponse()
                .setResponseCode(200)
                .setBody(MockResponseFileReader("getConfiguration_response_200.json").content)

        mockWebServer.enqueue(response)

        val deviceDataService =
            DeviceDataService(
                mockWebServer.url("/").toString(),
                "pk_test_key",
                RiskIntegrationType.STANDALONE,
            )

        runTest {
            deviceDataService.getConfiguration().let {
                if (it is NetworkResult.Success) {
                    Assert.assertEquals(true, it.data.fingerprintIntegration.enabled)
                    Assert.assertEquals("pk_test_key", it.data.fingerprintIntegration.publicKey)
                }
            }
        }

        val request = mockWebServer.takeRequest()

        Assert.assertEquals("GET", request.method)
        Assert.assertEquals(
            "/collect/configuration?integrationType=RiskAndroidStandalone",
            request.path,
        )
    }

    @Test
    fun `getConfiguration() should return a network error when unsuccessful`() {
        val response =
            MockResponse()
                .setResponseCode(500)

        mockWebServer.enqueue(response)

        val deviceDataService =
            DeviceDataService(
                mockWebServer.url("/").toString(),
                "pk_test_key",
                RiskIntegrationType.STANDALONE,
            )

        runTest {
            deviceDataService.getConfiguration().let {
                if (it is NetworkResult.Error) {
                    Assert.assertEquals("Server Error", it.message)
                }
            }
        }
    }

    @Test
    fun `persistFpData() should return success when successful`() {
        val response =
            MockResponse()
                .setResponseCode(200)
                .setBody(MockResponseFileReader("persistFingerprintData_response_200.json").content)

        mockWebServer.enqueue(response)

        val deviceDataService =
            DeviceDataService(
                mockWebServer.url("/").toString(),
                "pk_test_key",
                RiskIntegrationType.STANDALONE,
            )

        runTest {
            deviceDataService.persistFingerprintData("fp_data").let {
                if (it is NetworkResult.Success) {
                    Assert.assertEquals(
                        PersistFingerprintDataResponse("1234567890"),
                        it.data,
                    )
                }
            }
        }

        val request = mockWebServer.takeRequest()

        Assert.assertEquals("PUT", request.method)
        Assert.assertEquals(
            "/collect/fingerprint",
            request.path,
        )

        val expected =
            Gson().toJson(
                object {
                    val fp_request_id = "fp_data"
                    val integration_type = "RiskAndroidStandalone"
                },
            )

        Assert.assertEquals(
            expected,
            request.body.readUtf8(),
        )
    }

    @Test
    fun `persistFpData() should throw exception when unsuccessful`() {
        val response =
            MockResponse()
                .setResponseCode(500)

        mockWebServer.enqueue(response)

        val deviceDataService =
            DeviceDataService(
                mockWebServer.url("/").toString(),
                "pk_test_key",
                RiskIntegrationType.STANDALONE,
            )

        runTest {
            deviceDataService.persistFingerprintData("fp_data").let {
                if (it is NetworkResult.Error) {
                    Assert.assertEquals("Server Error", it.message)
                }
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
