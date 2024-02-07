package com.checkout.risk

import android.content.Context

public class Risk private constructor(private val riskInternal: RiskInternal) {
    public companion object {
        private var riskInstance: Risk? = null
        private lateinit var deviceDataService: DeviceDataService

        public suspend fun getInstance(
            applicationContext: Context,
            config: RiskConfig,
        ): Risk? {
            riskInstance?.let {
                return it
            }

            val internalConfig = RiskSDKInternalConfigImpl(config)
            val loggerService = LoggerService(internalConfig, applicationContext)
            deviceDataService = DeviceDataService(internalConfig)

            when (val deviceDataConfig = deviceDataService.getConfiguration()) {
                is NetworkResult.Success -> {
                    if (!deviceDataConfig.data.fingerprintIntegration.enabled ||
                        deviceDataConfig.data.fingerprintIntegration.publicKey == null
                    ) {
                        loggerService.log(
                            riskEvent = RiskEvent.PUBLISH_DISABLED,
                            error =
                                RiskLogError(
                                    reason = "getConfiguration",
                                    message = "Fingerprint integration disabled",
                                    status = null,
                                    type = "Device Data Service Error",
                                ),
                        )
                        return null
                    }

                    val fingerprintService =
                        FingerprintService(
                            applicationContext,
                            internalConfig,
                            deviceDataConfig.data.fingerprintIntegration.publicKey,
                        )

                    return Risk(RiskInternal(fingerprintService, deviceDataService, loggerService))
                }

                is NetworkResult.Error -> {
                    loggerService.log(
                        riskEvent = RiskEvent.LOAD_FAILURE,
                        error =
                            RiskLogError(
                                reason = "getConfiguration",
                                message = deviceDataConfig.message,
                                status = null,
                                type = "Device Data Service Error",
                                innerExceptionType = deviceDataConfig.innerException?.javaClass?.name,
                            ),
                    )
                    return null
                }

                is NetworkResult.Exception -> {
                    loggerService.log(
                        riskEvent = RiskEvent.LOAD_FAILURE,
                        error =
                            RiskLogError(
                                reason = "getConfiguration",
                                message = deviceDataConfig.e.message ?: "Unknown error",
                                status = null,
                                type = "Device Data Service Error",
                                innerExceptionType = deviceDataConfig.e.javaClass.name,
                            ),
                    )
                    return null
                }
            }
        }
    }

    public suspend fun publishData(cardToken: String? = null): PublishDataResult {
        return riskInternal.publishData(cardToken)
    }
}

internal class RiskInternal(
    private val fingerprintService: FingerprintService,
    private val deviceDataService: DeviceDataService,
    private val loggerService: LoggerServiceProtocol,
) {
    suspend fun publishData(cardToken: String?): PublishDataResult =
        when (val fingerprintResult = fingerprintService.publishData()) {
            is FingerprintResult.Success -> {
                loggerService.log(
                    riskEvent = RiskEvent.COLLECTED,
                    requestID = fingerprintResult.requestId,
                )
                when (
                    val persistResult =
                        deviceDataService.persistFingerprintData(
                            fingerprintResult.requestId,
                            cardToken,
                        )
                ) {
                    is NetworkResult.Success -> {
                        loggerService.log(
                            riskEvent = RiskEvent.PUBLISHED,
                            requestID = fingerprintResult.requestId,
                            deviceSessionID = persistResult.data.deviceSessionId,
                        )
                        PublishDataResult.Success(persistResult.data.deviceSessionId)
                    }

                    is NetworkResult.Error -> {
                        loggerService.log(
                            riskEvent = RiskEvent.PUBLISH_FAILURE,
                            error =
                                RiskLogError(
                                    reason = "persistFingerprintData",
                                    message = persistResult.message,
                                    status = null,
                                    type = "Device Data Service Error",
                                    innerExceptionType = persistResult.innerException?.javaClass?.name,
                                ),
                        )
                        PublishDataResult.PublishFailure
                    }

                    is NetworkResult.Exception -> {
                        loggerService.log(
                            riskEvent = RiskEvent.PUBLISH_FAILURE,
                            error =
                                RiskLogError(
                                    reason = "persistFingerprintData",
                                    message = persistResult.e.message ?: "Unknown error",
                                    status = persistResult.e.hashCode(),
                                    type = "Device Data Service Error",
                                    innerExceptionType = persistResult.e.javaClass.name,
                                ),
                        )
                        PublishDataResult.PublishFailure
                    }
                }
            }

            is FingerprintResult.Failure -> {
                loggerService.log(
                    riskEvent = RiskEvent.PUBLISH_FAILURE,
                    error =
                        RiskLogError(
                            reason = "publishData",
                            message = fingerprintResult.description,
                            status = null,
                            type = "Fingerprint Service Error",
                        ),
                )
                PublishDataResult.PublishFailure
            }
        }
}

public data class PublishRiskData(val deviceSessionId: String)

public sealed class PublishDataResult {
    public data class Success(val deviceSessionId: String) : PublishDataResult()

    public data object PublishFailure : PublishDataResult()
}
