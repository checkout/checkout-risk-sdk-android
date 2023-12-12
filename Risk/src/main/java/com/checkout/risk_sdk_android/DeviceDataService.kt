package com.checkout.risk_sdk_android

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
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
    suspend fun getConfiguration(): Result<FingerprintIntegration> = runCatching {
        println("merchantPublicKey: $merchantPublicKey")
        val response =
            deviceDataApi.getConfiguration(integrationType.type, merchantPublicKey)

        if (response.isSuccessful) {
            response.body()?.fingerprintIntegration!!
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
        @Query("integrationType") integrationType: String,
        @Header("Authorization") authHeader: String
    ): Response<DeviceDataConfiguration>
}
