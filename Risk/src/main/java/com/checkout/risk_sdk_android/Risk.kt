package com.checkout.risk_sdk_android

import android.content.Context

class Risk private constructor(
    private val fingerprintService: FingerprintService,
) {
    companion object {
        private var riskInstance: Risk? = null
        private lateinit var deviceDataService: DeviceDataService

        suspend fun getInstance(applicationContext: Context, config: RiskConfig): Risk? {
            return if (riskInstance !== null) {
                riskInstance
            } else {
                deviceDataService = DeviceDataService(
                    getDeviceDataEndpoint(config.environment),
                    config.publicKey,
                    if (config.framesMode) RiskIntegrationType.FRAMES else RiskIntegrationType.STANDALONE
                )

                val deviceDataConfig =
                    deviceDataService.getConfiguration().getOrNull()

                deviceDataConfig?.let {
                    val fingerprintService = FingerprintService(
                        applicationContext,
                        deviceDataConfig.fingerprintIntegration.publicKey!!,
                        getFingerprintEndpoint(config.environment)
                    )
                    riskInstance = Risk(fingerprintService)


                    if (it.fingerprintIntegration.enabled) {
                        val fingerprintService = FingerprintService(
                            applicationContext,
                            it.fingerprintIntegration.publicKey!!,
                            getFingerprintEndpoint(config.environment)
                        )
                        riskInstance = Risk(fingerprintService)
                    }
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
    ): Result<PersistFingerprintDataResponse> {
        // TODO: add once device data persistence is implemented
        println("data persisted $fingerprintRequestID")

        return deviceDataService.persistFingerprintData(fingerprintRequestID)
    }
}
