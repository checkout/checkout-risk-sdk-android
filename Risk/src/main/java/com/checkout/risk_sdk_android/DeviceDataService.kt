package com.checkout.risk

import com.google.gson.annotations.SerializedName
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Query
import java.util.TimeZone

/**
 * Service for retrieving device data configuration.
 *
 * @param internalConfig The RiskSDKInternalConfig interface.
 * */
internal class DeviceDataService(
    internalConfig: RiskSDKInternalConfig,
) {
    private val deviceDataApi = DeviceDataApi(internalConfig.deviceDataEndpoint)
    private val merchantPublicKey = internalConfig.merchantPublicKey
    private val integrationType = internalConfig.integrationType

    /**
     * Retrieves the device data configuration.
     *
     * @return Result containing the DeviceDataConfiguration on success, or an exception on failure.
     */
    suspend fun getConfiguration(): NetworkResult<DeviceDataConfiguration> =
        executeApiCall {
            deviceDataApi.getConfiguration(
                merchantPublicKey,
                riskSdkVersion = Constants.RISK_PACKAGE_VERSION,
                timezone = TimeZone.getDefault().id,
                integrationType = integrationType.type,
            )
        }

    /**
     * Persists the collected device data to the `fingerprint/v2` endpoint.
     *
     * @param requestId The root fingerprint request id (from the PRO collector when present).
     * @param cardToken The optional card token (frames integration).
     * @param collectors The per-collector payloads that were gathered in parallel.
     *
     * @return Result containing PersistFingerprintDataResponse on success, or an exception on failure.
     */
    suspend fun persistFingerprintData(
        requestId: String,
        cardToken: String?,
        collectors: List<CollectorData>,
    ): NetworkResult<PersistFingerprintDataResponse> =
        executeApiCall {
            deviceDataApi.persistFingerprintData(
                merchantPublicKey,
                riskSdkVersion = Constants.RISK_PACKAGE_VERSION,
                PersistFingerprintDataRequest(
                    fpRequestId = requestId,
                    integrationType = integrationType.type,
                    cardToken = cardToken,
                    collectors = collectors,
                ),
            )
        }

    private suspend fun <T : Any> executeApiCall(execute: suspend () -> Response<T>): NetworkResult<T> {
        return try {
            val response = execute()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                NetworkResult.Success(body)
            } else {
                NetworkResult.Error(
                    code = response.code(),
                    message = response.message(),
                    innerException = Exception(),
                )
            }
        } catch (e: HttpException) {
            NetworkResult.Error(code = e.code(), message = e.message(), innerException = e)
        } catch (e: Throwable) {
            NetworkResult.Exception(e)
        }
    }
}

private sealed interface DeviceDataApi {
    companion object {
        operator fun invoke(baseUrl: String): DeviceDataApi {
            return getRetrofitClient(baseUrl)
                .create(DeviceDataApi::class.java)
        }
    }

    @GET("/collect/configurations")
    suspend fun getConfiguration(
        @Header("Authorization") authHeader: String,
        @Query("integrationType") integrationType: String,
        @Query("riskSdkVersion") riskSdkVersion: String,
        @Query("timezone") timezone: String,
    ): Response<DeviceDataConfiguration>

    @PUT("/collect/fingerprint/v2")
    suspend fun persistFingerprintData(
        @Header("Authorization") authHeader: String,
        @Query("riskSdkVersion") riskSdkVersion: String,
        @Body fingerprintData: PersistFingerprintDataRequest,
    ): Response<PersistFingerprintDataResponse>
}

/**
 * Response of the `configurations` endpoint. [dataCollectors] lists the collectors
 * enabled for the merchant (e.g. "fingerprint", "simple"); a collector is
 * considered enabled when its name is present in this list.
 */
internal data class DeviceDataConfiguration(
    @SerializedName("data_collectors")
    val dataCollectors: List<String> = emptyList(),
    @SerializedName("public_key")
    val publicKey: String?,
    @SerializedName("simple")
    val simple: SimpleCollectorConfig? = null,
)

/**
 * Per-collector configuration for the `simple` collector.
 *
 * @property dropFieldPaths Dot-notation paths into the collected `device` data that must be
 * removed before the payload is base64-encoded (e.g. "canvas.value.geometry").
 * @property timeoutMs Collection timeout budget in milliseconds.
 */
internal data class SimpleCollectorConfig(
    @SerializedName("drop_field_paths")
    val dropFieldPaths: List<String> = emptyList(),
    @SerializedName("timeout_ms")
    val timeoutMs: Long? = null,
)

internal data class PersistFingerprintDataResponse(
    @SerializedName("device_session_id")
    val deviceSessionId: String,
)

internal data class PersistFingerprintDataRequest(
    @SerializedName("fp_request_id")
    val fpRequestId: String,
    @SerializedName("integration_type")
    val integrationType: String,
    @SerializedName("card_token")
    val cardToken: String?,
    @SerializedName("collectors")
    val collectors: List<CollectorData>,
)

/**
 * A single collector's contribution to the publish payload. The backend derives the
 * `device_collector_provider` recorded on prism_events from [collector].
 *
 * For the PRO collector the raw identifier travels in the root `fp_request_id`, so
 * [sealedResult] is null. For the open-source `simple` collector the collected
 * device data (device id plus raw signals) is JSON-serialised and base64-encoded into
 * [sealedResult].
 */
internal data class CollectorData(
    @SerializedName("collector")
    val collector: String,
    @SerializedName("sealed_result")
    val sealedResult: String?,
)

internal sealed class NetworkResult<out T> {
    class Success<T>(val data: T) : NetworkResult<T>() {
        override fun toString(): String {
            return "Success(data=$data)"
        }
    }

    class Error(
        val code: Int,
        val message: String,
        val innerException: Throwable? = null,
    ) :
        NetworkResult<Nothing>()

    class Exception(val e: Throwable) : NetworkResult<Nothing>()
}
