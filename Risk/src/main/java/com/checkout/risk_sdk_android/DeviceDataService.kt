package com.checkout.risk_sdk_android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class FingerprintIntegration(val enabled: Boolean, val publicKey: String)

data class DeviceDataConfiguration(val fingerprintIntegration: FingerprintIntegration)

class DeviceDataService(
    private val config: RiskSDKInternalConfig
) {
    suspend fun getConfiguration(): Result<DeviceDataConfiguration> {
        val endpoint =
            "${config.deviceDataEndpoint}/configuration?integrationType=${config.integrationType.type}"
        val authToken = config.merchantPublicKey

        return withContext(Dispatchers.IO) {
            delay(2000)

            Result.success(
                DeviceDataConfiguration(FingerprintIntegration(true, "fp_public_key"))
            )
        }
    }

    fun persistFpData(cardToken: String, fingerprintRequestId: String) {}
}