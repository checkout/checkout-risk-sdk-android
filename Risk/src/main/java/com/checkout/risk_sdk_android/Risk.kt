package com.checkout.risk_sdk_android

import android.content.Context

public class Risk private constructor(private val riskInternal: RiskInternal) {
    public companion object {
        private var riskInstance: Risk? = null
        private lateinit var deviceDataService: DeviceDataService

        public suspend fun getInstance(
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

    public suspend fun publishData(): PublishDataResult {
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

public sealed class RiskInitialisationResult {
    public data class Success(val risk: Risk) : RiskInitialisationResult()

    public data object IntegrationDisabled : RiskInitialisationResult()

    public data class Failure(val message: String) : RiskInitialisationResult()
}

public sealed class PublishDataResult {
    public data class Success(val deviceSessionId: String) : PublishDataResult()

    public data class Failure(val message: String) : PublishDataResult()

    public data class Exception(val e: Throwable) : PublishDataResult()
}
