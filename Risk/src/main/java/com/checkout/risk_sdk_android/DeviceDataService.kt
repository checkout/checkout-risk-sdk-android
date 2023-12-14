package com.checkout.risk_sdk_android

import com.google.gson.annotations.SerializedName
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Query

/**
 * Service for retrieving device data configuration.
 *
 * @param deviceDataEndpoint The endpoint for retrieving device data configuration.
 * @param merchantPublicKey The merchant public key.
 * @param integrationType The integration type.
 * */
internal class DeviceDataService(
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
    suspend fun getConfiguration(): NetworkResult<DeviceDataConfiguration> = executeApiCall {
        deviceDataApi.getConfiguration(merchantPublicKey, integrationType.type)
    }

    /**
     * Persists the fingerprint data.
     *
     * @param requestId The requestId.
     *
     * @return Result containing PersistFingerprintDataResponse on success, or an exception on failure.
     */
    suspend fun persistFingerprintData(requestId: String): NetworkResult<PersistFingerprintDataResponse> =
        executeApiCall {
            deviceDataApi.persistFingerprintData(
                merchantPublicKey,
                PersistFingerprintDataRequest(requestId, integrationType.type, null)
            )
        }

    private suspend fun <T : Any> executeApiCall(
        execute: suspend () -> Response<T>
    ): NetworkResult<T> {
        return try {
            val response = execute()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                NetworkResult.Success(body)
            } else {
                NetworkResult.Error(code = response.code(), message = response.message())
            }
        } catch (e: HttpException) {
            NetworkResult.Error(code = e.code(), message = e.message())
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

internal data class DeviceDataConfiguration(
    @SerializedName("fingerprint_integration")
    val fingerprintIntegration: FingerprintIntegration
)

internal data class FingerprintIntegration(
    @SerializedName("enabled")
    val enabled: Boolean,
    @SerializedName("public_key")
    val publicKey: String?
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
)

internal sealed class NetworkResult<T : Any> {
    class Success<T : Any>(val data: T) : NetworkResult<T>() {
        override fun toString(): String {
            return "Success(data=$data)"
        }
    }

    class Error<T : Any>(val code: Int, val message: String) : NetworkResult<T>()
    class Exception<T : Any>(val e: Throwable) : NetworkResult<T>()
}
