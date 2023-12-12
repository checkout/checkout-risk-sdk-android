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
 * @param config The internal configuration for the Risk SDK.
 */
class DeviceDataService(private val config: RiskSDKInternalConfig) {
    private val deviceDataApi = DeviceDataApi(config)

    /**
     * Retrieves the device data configuration.
     *
     * @return Result containing the FingerprintIntegration on success, or an exception on failure.
     */
    suspend fun getConfiguration(): Result<FingerprintIntegration> = runCatching {
        val response =
            deviceDataApi.getConfiguration(config.integrationType.type, config.merchantPublicKey)

        if (response.isSuccessful) {
            response.body()?.fingerprintIntegration!!
        } else {
            throw ApiException(response.code(), response.message())
        }
    }

}

private sealed interface DeviceDataApi {
    companion object {
        operator fun invoke(config: RiskSDKInternalConfig): DeviceDataApi {
            return getRetrofitClient(config.deviceDataEndpoint)
                .create(DeviceDataApi::class.java)
        }
    }

    @GET("configuration")
    suspend fun getConfiguration(
        @Query("integrationType") integrationType: String,
        @Header("Authorization") authHeader: String
    ): Response<DeviceDataConfiguration>
}
