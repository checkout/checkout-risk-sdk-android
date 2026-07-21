package com.checkout.risk

import com.google.gson.Gson
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.InputStreamReader

internal class RiskSDKInternalConfigTestImpl(
    override val merchantPublicKey: String,
    override val framesOptions: FramesOptions? = null,
    override val environment: RiskEnvironment,
    override val deviceDataEndpoint: String,
    override val fingerprintEndpoint: String,
    override val integrationType: RiskIntegrationType,
    override val sourceType: SourceType,
) : RiskSDKInternalConfig

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

        val internalConfig =
            RiskSDKInternalConfigTestImpl(
                merchantPublicKey = "pk_test_key",
                environment = RiskEnvironment.QA,
                deviceDataEndpoint = mockWebServer.url("/").toString(),
                fingerprintEndpoint = mockWebServer.url("/").toString(),
                integrationType = RiskIntegrationType.STANDALONE,
                sourceType = SourceType.RISK_SDK,
            )

        val deviceDataService = DeviceDataService(internalConfig)

        runTest {
            deviceDataService.getConfiguration().let {
                if (it is NetworkResult.Success) {
                    Assert.assertEquals(
                        listOf("fingerprint", "simple"),
                        it.data.dataCollectors,
                    )
                    Assert.assertEquals("pk_test_key", it.data.publicKey)
                }
            }
        }

        val request = mockWebServer.takeRequest()
        Assert.assertEquals("GET", request.method)
        Assert.assertEquals(
            "/collect/configurations",
            request.path.toString().split('?')[0],
        )
    }

    @Test
    fun `getConfiguration() should parse the simple collector config`() {
        val response =
            MockResponse()
                .setResponseCode(200)
                .setBody(MockResponseFileReader("getConfiguration_simple_response_200.json").content)

        mockWebServer.enqueue(response)

        val internalConfig =
            RiskSDKInternalConfigTestImpl(
                merchantPublicKey = "pk_test_key",
                environment = RiskEnvironment.QA,
                deviceDataEndpoint = mockWebServer.url("/").toString(),
                fingerprintEndpoint = mockWebServer.url("/").toString(),
                integrationType = RiskIntegrationType.STANDALONE,
                sourceType = SourceType.RISK_SDK,
            )

        val deviceDataService = DeviceDataService(internalConfig)

        runTest {
            val result = deviceDataService.getConfiguration()
            Assert.assertTrue(result is NetworkResult.Success)

            val config = (result as NetworkResult.Success).data
            Assert.assertEquals(listOf("simple", "fingerprint"), config.dataCollectors)
            Assert.assertEquals(
                listOf("procCpuInfoV2", "canvas.value.text"),
                config.simple?.dropFieldPaths,
            )
            Assert.assertEquals(1000L, config.simple?.timeoutMs)
        }
    }

    @Test
    fun `getConfiguration() leaves the simple config null when absent`() {
        val response =
            MockResponse()
                .setResponseCode(200)
                .setBody(MockResponseFileReader("getConfiguration_response_200.json").content)

        mockWebServer.enqueue(response)

        val internalConfig =
            RiskSDKInternalConfigTestImpl(
                merchantPublicKey = "pk_test_key",
                environment = RiskEnvironment.QA,
                deviceDataEndpoint = mockWebServer.url("/").toString(),
                fingerprintEndpoint = mockWebServer.url("/").toString(),
                integrationType = RiskIntegrationType.STANDALONE,
                sourceType = SourceType.RISK_SDK,
            )

        val deviceDataService = DeviceDataService(internalConfig)

        runTest {
            val result = deviceDataService.getConfiguration()
            Assert.assertTrue(result is NetworkResult.Success)
            Assert.assertNull((result as NetworkResult.Success).data.simple)
        }
    }

    @Test
    fun `getConfiguration() should return a network error when unsuccessful`() {
        val response =
            MockResponse()
                .setResponseCode(500)

        mockWebServer.enqueue(response)

        val internalConfig =
            RiskSDKInternalConfigTestImpl(
                merchantPublicKey = "pk_test_key",
                environment = RiskEnvironment.QA,
                deviceDataEndpoint = mockWebServer.url("/").toString(),
                fingerprintEndpoint = mockWebServer.url("/").toString(),
                integrationType = RiskIntegrationType.STANDALONE,
                sourceType = SourceType.RISK_SDK,
            )

        val deviceDataService = DeviceDataService(internalConfig)

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

        val internalConfig =
            RiskSDKInternalConfigTestImpl(
                merchantPublicKey = "pk_test_key",
                environment = RiskEnvironment.QA,
                deviceDataEndpoint = mockWebServer.url("/").toString(),
                fingerprintEndpoint = mockWebServer.url("/").toString(),
                integrationType = RiskIntegrationType.STANDALONE,
                sourceType = SourceType.RISK_SDK,
            )

        val deviceDataService = DeviceDataService(internalConfig)

        val collectors =
            listOf(
                CollectorData(collector = "fingerprint", sealedResult = null),
                CollectorData(collector = "simple", sealedResult = "c2VhbGVkX29z"),
            )

        runTest {
            deviceDataService.persistFingerprintData("fp_data", "card_token", collectors).let {
                if (it is NetworkResult.Success) {
                    Assert.assertEquals(
                        PersistFingerprintDataResponse("dsid_1234567890"),
                        it.data,
                    )
                }
            }
        }

        val request = mockWebServer.takeRequest()

        Assert.assertEquals("PUT", request.method)
        Assert.assertEquals(
            "/collect/fingerprint/v2?riskSdkVersion=${Constants.RISK_PACKAGE_VERSION}",
            request.path,
        )

        val expected =
            Gson().toJson(
                PersistFingerprintDataRequest(
                    fpRequestId = "fp_data",
                    integrationType = "RiskAndroidStandalone",
                    cardToken = "card_token",
                    collectors = collectors,
                ),
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

        val internalConfig =
            RiskSDKInternalConfigTestImpl(
                merchantPublicKey = "pk_test_key",
                environment = RiskEnvironment.QA,
                deviceDataEndpoint = mockWebServer.url("/").toString(),
                fingerprintEndpoint = mockWebServer.url("/").toString(),
                integrationType = RiskIntegrationType.STANDALONE,
                sourceType = SourceType.RISK_SDK,
            )

        val deviceDataService = DeviceDataService(internalConfig)

        runTest {
            deviceDataService.persistFingerprintData(
                "fp_data",
                "card_token",
                listOf(CollectorData(collector = "fingerprint", sealedResult = null)),
            ).let {
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
