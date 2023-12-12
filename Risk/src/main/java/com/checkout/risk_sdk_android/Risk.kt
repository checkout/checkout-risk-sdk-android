package com.checkout.risk_sdk_android

import android.content.Context

class Risk private constructor(
    private val fingerprintService: FingerprintService,
) {
    companion object {
        private var riskInstance: Risk? = null

        suspend fun getInstance(applicationContext: Context, config: RiskConfig): Risk? {
            return if (riskInstance !== null) {
                riskInstance
            } else {
                val deviceDataService = DeviceDataService(
                    getDeviceDataEndpoint(config.environment),
                    config.publicKey,
                    if (config.framesMode) RiskIntegrationType.FRAMES else RiskIntegrationType.STANDALONE
                )

                val fingerprintIntegration =
                    deviceDataService.getConfiguration().getOrNull()

                if (fingerprintIntegration !== null && fingerprintIntegration.enabled) {
                    val fingerprintService = FingerprintService(
                        applicationContext,
                        fingerprintIntegration.publicKey!!
                    )
                    riskInstance = Risk(fingerprintService)
                }

                return riskInstance
            }
        }
    }

    suspend fun publishData() {
        println("publishing data")
        fingerprintService.publishData()
            .onSuccess {
                persistFpData(it)
            }
            .onFailure {
                // handle failure, log
                println(it.message)
            }
    }

    private suspend fun persistFpData(
        fingerprintRequestID: String
    ) {
        println("data persisted")
    }
}
