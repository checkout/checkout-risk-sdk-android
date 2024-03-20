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
            val startBlockTime = System.nanoTime()

            when (val deviceDataConfig = deviceDataService.getConfiguration()) {
                is NetworkResult.Success -> {
                    val endBlockTime = System.nanoTime()
                    val blockTime = (endBlockTime - startBlockTime) / 1_000_000.0

                    if (!deviceDataConfig.data.fingerprintIntegration.enabled ||
                        deviceDataConfig.data.fingerprintIntegration.publicKey == null
                    ) {
                        loggerService.log(
                            riskEvent = RiskEvent.PUBLISH_DISABLED,
                            blockTime = blockTime,
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

                    val startFpLoadTime = System.nanoTime()

                    val fingerprintService =
                        FingerprintService(
                            applicationContext,
                            internalConfig,
                            deviceDataConfig.data.fingerprintIntegration.publicKey,
                        )

                    val endFpLoadTime = System.nanoTime()
                    val fpLoadTime = (endFpLoadTime - startFpLoadTime) / 1_000_000.0

                    return Risk(RiskInternal(fingerprintService, deviceDataService, loggerService, blockTime, fpLoadTime))
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

                else -> {
                    loggerService.log(
                        riskEvent = RiskEvent.LOAD_FAILURE,
                        error =
                        RiskLogError(
                            reason = "getConfiguration",
                            message = "Unknown error",
                            status = null,
                            type = "Device Data Service Error",
                            innerExceptionType = "Unknown error",
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
    private val blockTime: Double,
    private val fpLoadTime: Double
) {
    suspend fun publishData(cardToken: String?): PublishDataResult {
        val startFpPublishTime = System.nanoTime()
        when (val fingerprintResult = fingerprintService.publishData()) {
            is FingerprintResult.Success -> {

                val endFpPublishTime = System.nanoTime()
                val fpPublishTime = (endFpPublishTime - startFpPublishTime) / 1_000_000.0
                loggerService.log(
                    blockTime = blockTime,
                    fpLoadTime = fpLoadTime,
                    fpPublishTime = fpPublishTime,
                    riskEvent = RiskEvent.COLLECTED,
                    requestID = fingerprintResult.requestId,
                )

                val startDeviceDataPersistTime = System.nanoTime()
                when (
                    val persistResult =
                        deviceDataService.persistFingerprintData(
                            fingerprintResult.requestId,
                            cardToken,
                        )
                ) {
                    is NetworkResult.Success -> {
                        val endDeviceDataPersistTime = System.nanoTime()
                        val deviceDataPersistTime =
                            (endDeviceDataPersistTime - startDeviceDataPersistTime) / 1_000_000.0
                        loggerService.log(
                            blockTime = blockTime,
                            fpLoadTime = fpLoadTime,
                            fpPublishTime = fpPublishTime,
                            deviceDataPersistTime = deviceDataPersistTime,
                            riskEvent = RiskEvent.PUBLISHED,
                            requestID = fingerprintResult.requestId,
                            deviceSessionID = persistResult.data.deviceSessionId,
                        )
                        return PublishDataResult.Success(persistResult.data.deviceSessionId)
                    }

                    is NetworkResult.Error -> {
                        loggerService.log(
                            blockTime = blockTime,
                            fpLoadTime = fpLoadTime,
                            fpPublishTime = fpPublishTime,
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
                        return PublishDataResult.PublishFailure
                    }

                    is NetworkResult.Exception -> {
                        loggerService.log(
                            blockTime = blockTime,
                            fpLoadTime = fpLoadTime,
                            fpPublishTime = fpPublishTime,
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
                        return PublishDataResult.PublishFailure
                    }

                    else -> {
                        loggerService.log(
                            blockTime = blockTime,
                            fpLoadTime = fpLoadTime,
                            fpPublishTime = fpPublishTime,
                            riskEvent = RiskEvent.PUBLISH_FAILURE,
                            error =
                            RiskLogError(
                                reason = "persistFingerprintData",
                                message = "Unknown error",
                                status = null,
                                type = "Device Data Service Error",
                                innerExceptionType = "Unknown error",
                            ),
                        )
                        return PublishDataResult.PublishFailure
                    }
                }
            }

            is FingerprintResult.Failure -> {
                loggerService.log(
                    blockTime = blockTime,
                    fpLoadTime = fpLoadTime,
                    riskEvent = RiskEvent.PUBLISH_FAILURE,
                    error =
                    RiskLogError(
                        reason = "publishData",
                        message = fingerprintResult.description,
                        status = null,
                        type = "Fingerprint Service Error",
                    ),
                )
                return PublishDataResult.PublishFailure
            }

            else -> {
                loggerService.log(
                    blockTime = blockTime,
                    fpLoadTime = fpLoadTime,
                    riskEvent = RiskEvent.PUBLISH_FAILURE,
                    error =
                    RiskLogError(
                        reason = "publishData",
                        message = "Unknown error",
                        status = null,
                        type = "Fingerprint Service Error",
                    ),
                )
                return PublishDataResult.PublishFailure
            }
        }
    }
}

public sealed class PublishDataResult {
    public data class Success(val deviceSessionId: String) : PublishDataResult()

    public data object PublishFailure : PublishDataResult()
}
