package com.checkout.risk_sdk_android

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Query

data class DeviceDataConfiguration(
    @SerializedName("fingerprint_integration")
    val fingerprintIntegration: FingerprintIntegration
)

data class FingerprintIntegration(
    @SerializedName("enabled")
    val enabled: Boolean,
    @SerializedName("public_key")
    val publicKey: String?
)

/**
 * Service for retrieving device data configuration.
 *
 * @param deviceDataEndpoint The endpoint for retrieving device data configuration.
 * @param merchantPublicKey The merchant public key.
 * @param integrationType The integration type.
 * */
class DeviceDataService(
    deviceDataEndpoint: String,
    private val merchantPublicKey: String,
    private val integrationType: RiskIntegrationType
) {
    private val deviceDataApi = DeviceDataApi(deviceDataEndpoint)

    /**
     * Retrieves the device data configuration.
     *
     * @return Result containing the FingerprintIntegration on success, or an exception on failure.
     */
    suspend fun getConfiguration(): Result<DeviceDataConfiguration> = executeApiCall {
        deviceDataApi.getConfiguration(merchantPublicKey, integrationType.type)
    }

    /**
     * Persists the fingerprint data.
     *
     * @param requestId The requestId.
     *
     * @return Result containing PersistFingerprintDataResponse on success, or an exception on failure.
     */
    suspend fun persistFingerprintData(requestId: String): Result<PersistFingerprintDataResponse> =
        executeApiCall {
            deviceDataApi.persistFingerprintData(
                merchantPublicKey,
                PersistFingerprintDataRequest(requestId, integrationType.type, null)
            )
        }

    private suspend fun <T> executeApiCall(apiCall: suspend () -> Response<T>): Result<T> =
        runCatching {
            val response = apiCall()

            if (response.isSuccessful) {
                response.body()!!
            } else {
                throw ApiException(response.code(), response.message())
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

    @GET("/collect/configuration")
    suspend fun getConfiguration(
        @Header("Authorization") authHeader: String,
        @Query("integrationType") integrationType: String,
    ): Response<DeviceDataConfiguration>

    @PUT("/collect/fingerprint")
    suspend fun persistFingerprintData(
        @Header("Authorization") authHeader: String,
        @Body fingerprintData: PersistFingerprintDataRequest
    ): Response<PersistFingerprintDataResponse>
}

data class PersistFingerprintDataResponse(
    @SerializedName("device_session_id")
    val deviceSessionId: String,

    )

data class PersistFingerprintDataRequest(
    @SerializedName("fp_request_id")
    val fpRequestId: String,
    @SerializedName("integration_type")
    val integrationType: String,
    @SerializedName("card_token")
    val cardToken: String?,
)