package com.checkout.risk_sdk_android

import android.content.Context

class Risk private constructor(
    private val fingerprintService: FingerprintService,
) {
    companion object {
        private lateinit var riskInstance: Risk

        suspend fun getInstance(applicationContext: Context, config: RiskConfig): Risk? {
            return if (::riskInstance.isInitialized) {
                riskInstance
            } else {
                val config = RiskSDKInternalConfig(config)
                val deviceDataService = DeviceDataService(config)

                val fingerprintIntegration =
                    deviceDataService.getConfiguration().getOrThrow().fingerprintIntegration

                if (fingerprintIntegration.enabled) {
                    val fingerprintService = FingerprintService(
                        applicationContext,
                        fingerprintIntegration.publicKey
                    )
                    riskInstance = Risk(fingerprintService)
                }


                return riskInstance
            }
        }
    }

    suspend fun publishData() {
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
