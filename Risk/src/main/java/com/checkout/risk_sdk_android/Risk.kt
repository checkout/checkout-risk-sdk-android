package com.checkout.risk_sdk_android

import android.content.Context

class Risk private constructor(private val riskInternal: RiskInternal) {
    companion object {
        private var riskInstance: Risk? = null
        private lateinit var deviceDataService: DeviceDataService

        suspend fun getInstance(
            applicationContext: Context,
            config: RiskConfig,
        ): RiskInitialisationResult {
            riskInstance?.let {
                return RiskInitialisationResult.Success(it)
            }

            deviceDataService =
                DeviceDataService(
                    getDeviceDataEndpoint(config.environment),
                    config.publicKey,
                    if (config.framesMode) RiskIntegrationType.FRAMES else RiskIntegrationType.STANDALONE,
                )

            when (val deviceDataConfig = deviceDataService.getConfiguration()) {
                is NetworkResult.Success -> {
                    val fingerprintService =
                        FingerprintService(
                            applicationContext,
                            deviceDataConfig.data.fingerprintIntegration.publicKey!!,
                            getFingerprintEndpoint(config.environment),
                        )

                    riskInstance = Risk(RiskInternal(fingerprintService, deviceDataService))

                    if (!deviceDataConfig.data.fingerprintIntegration.enabled) {
                        return RiskInitialisationResult.IntegrationDisabled
                    }

                    return RiskInitialisationResult.Success(riskInstance!!)
                }

                is NetworkResult.Error -> return RiskInitialisationResult.Failure(deviceDataConfig.message)
                is NetworkResult.Exception -> return RiskInitialisationResult.Failure(
                    deviceDataConfig.e.message ?: "Unknown error",
                )
            }
        }
    }

    suspend fun publishData(): PublishDataResult {
        return riskInternal.publishData()
    }
}

internal class RiskInternal(
    private val fingerprintService: FingerprintService,
    private val deviceDataService: DeviceDataService,
) {
    suspend fun publishData(): PublishDataResult =
        when (val fingerprintResult = fingerprintService.publishData()) {
            is FingerprintResult.Success ->
                when (
                    val persistResult =
                        deviceDataService.persistFingerprintData(fingerprintResult.requestId)
                ) {
                    is NetworkResult.Success -> {
                        PublishDataResult.Success(persistResult.data.deviceSessionId)
                    }

                    is NetworkResult.Error -> {
                        PublishDataResult.Failure(persistResult.message)
                    }

                    is NetworkResult.Exception -> {
                        PublishDataResult.Exception(persistResult.e)
                    }
                }

            is FingerprintResult.Failure -> PublishDataResult.Failure(fingerprintResult.description)
        }
}

sealed class RiskInitialisationResult {
    data class Success(val risk: Risk) : RiskInitialisationResult()

    data object IntegrationDisabled : RiskInitialisationResult()

    data class Failure(val message: String) : RiskInitialisationResult()
}

sealed class PublishDataResult {
    data class Success(val deviceSessionId: String) : PublishDataResult()

    data class Failure(val message: String) : PublishDataResult()

    data class Exception(val e: Throwable) : PublishDataResult()
}
