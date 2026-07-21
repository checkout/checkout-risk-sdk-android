package com.checkout.risk

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.UUID

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
                    val blockTime = elapsedMs(startBlockTime)

                    val dataCollectors = deviceDataConfig.data.dataCollectors
                    val publicKey = deviceDataConfig.data.publicKey

                    // A collector is enabled when its name is present in `data_collectors`.
                    // The PRO collector additionally needs a fingerprint public key.
                    val proEnabled =
                        dataCollectors.contains(DeviceCollector.FINGERPRINT.collectorName) &&
                            publicKey != null
                    val simpleEnabled =
                        dataCollectors.contains(DeviceCollector.SIMPLE.collectorName)

                    if (!proEnabled && !simpleEnabled) {
                        loggerService.log(
                            riskEvent = RiskEvent.PUBLISH_DISABLED,
                            blockTime = blockTime,
                            error =
                                RiskLogError(
                                    reason = "getConfiguration",
                                    message = "No device collectors enabled",
                                    status = null,
                                    type = "Device Data Service Error",
                                ),
                        )
                        return null
                    }

                    val startFpLoadTime = System.nanoTime()

                    val fingerprintService =
                        if (proEnabled) {
                            FingerprintService(applicationContext, internalConfig, publicKey!!)
                        } else {
                            null
                        }

                    val simpleService =
                        if (simpleEnabled) {
                            SimpleService(
                                applicationContext,
                                dropFieldPaths = deviceDataConfig.data.simple?.dropFieldPaths ?: emptyList(),
                            )
                        } else {
                            null
                        }

                    val fpLoadTime = elapsedMs(startFpLoadTime)

                    return Risk(
                        RiskInternal(
                            fingerprintService,
                            simpleService,
                            deviceDataService,
                            loggerService,
                            blockTime,
                            fpLoadTime,
                        ),
                    )
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

        private fun elapsedMs(startNanos: Long): Double = (System.nanoTime() - startNanos) / 1_000_000.0
    }

    public suspend fun publishData(cardToken: String? = null): PublishDataResult {
        return riskInternal.publishData(cardToken)
    }
}

internal class RiskInternal(
    private val fingerprintService: FingerprintService?,
    private val simpleService: SimpleService?,
    private val deviceDataService: DeviceDataService,
    private val loggerService: LoggerServiceProtocol,
    private val blockTime: Double,
    private val fpLoadTime: Double,
) {
    suspend fun publishData(cardToken: String?): PublishDataResult =
        coroutineScope {
            val startFpPublishTime = System.nanoTime()

            // Run every enabled collector concurrently; awaiting both just waits for the
            // slowest. One collector failing does not prevent the others from publishing.
            val proDeferred = fingerprintService?.let { service -> async { service.publishData() } }
            val simpleDeferred = simpleService?.let { service -> async { service.publishData() } }

            val proResult = proDeferred?.await()
            val simpleResult = simpleDeferred?.await()

            val fpPublishTime = elapsedMs(startFpPublishTime)

            val collectors = mutableListOf<CollectorData>()
            val providers = mutableListOf<String>()
            var proRequestId: String? = null
            var simpleRequestId: String? = null

            when (proResult) {
                is FingerprintResult.Success -> {
                    proRequestId = proResult.requestId
                    collectors.add(CollectorData(DeviceCollector.FINGERPRINT.collectorName, sealedResult = null))
                    providers.add(DeviceCollector.FINGERPRINT.collectorName)
                }

                is FingerprintResult.Failure -> {
                    loggerService.log(
                        blockTime = blockTime,
                        fpLoadTime = fpLoadTime,
                        fpPublishTime = fpPublishTime,
                        riskEvent = RiskEvent.PUBLISH_FAILURE,
                        error =
                            RiskLogError(
                                reason = "publishData",
                                message = proResult.description,
                                status = null,
                                type = "Fingerprint Service Error",
                            ),
                    )
                }

                null -> Unit // PRO collector not enabled
                else -> {} // Something has gone wrong
            }

            when (simpleResult) {
                is SimpleResult.Success -> {
                    simpleRequestId = simpleResult.requestId
                    collectors.add(
                        CollectorData(
                            DeviceCollector.SIMPLE.collectorName,
                            sealedResult = simpleResult.sealedResult,
                        ),
                    )
                    providers.add(DeviceCollector.SIMPLE.collectorName)
                }

                is SimpleResult.Failure -> {
                    loggerService.log(
                        blockTime = blockTime,
                        fpLoadTime = fpLoadTime,
                        fpPublishTime = fpPublishTime,
                        riskEvent = RiskEvent.PUBLISH_FAILURE,
                        error =
                            RiskLogError(
                                reason = "publishData",
                                message = simpleResult.description,
                                status = null,
                                type = "Simple Service Error",
                            ),
                    )
                }

                null -> Unit // simple collector not enabled
            }

            if (collectors.isEmpty()) {
                // Every enabled collector failed; the failures are already logged above.
                return@coroutineScope PublishDataResult.PublishFailure
            }

            // The backend keys device data on fp_request_id. PRO provides one; when only the
            // simple collector ran there is no server-side request id, so reuse the client-generated
            // id embedded in the simple collector's sealed_result payload.
            val requestId = resolveRequestId(proRequestId, simpleRequestId)

            loggerService.log(
                blockTime = blockTime,
                fpLoadTime = fpLoadTime,
                fpPublishTime = fpPublishTime,
                riskEvent = RiskEvent.COLLECTED,
                requestID = requestId,
                deviceCollectorProviders = providers,
            )

            val startDeviceDataPersistTime = System.nanoTime()
            when (
                val persistResult =
                    deviceDataService.persistFingerprintData(requestId, cardToken, collectors)
            ) {
                is NetworkResult.Success -> {
                    val deviceDataPersistTime = elapsedMs(startDeviceDataPersistTime)
                    loggerService.log(
                        blockTime = blockTime,
                        fpLoadTime = fpLoadTime,
                        fpPublishTime = fpPublishTime,
                        deviceDataPersistTime = deviceDataPersistTime,
                        riskEvent = RiskEvent.PUBLISHED,
                        requestID = requestId,
                        deviceSessionID = persistResult.data.deviceSessionId,
                        deviceCollectorProviders = providers,
                    )
                    PublishDataResult.Success(persistResult.data.deviceSessionId)
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
                    PublishDataResult.PublishFailure
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
                    PublishDataResult.PublishFailure
                }
            }
        }

    private fun elapsedMs(startNanos: Long): Double = (System.nanoTime() - startNanos) / 1_000_000.0
}

/**
 * Resolves the root `fp_request_id`. The PRO collector's server-side id wins; otherwise the
 * simple collector's client-generated id (which matches the one embedded in its sealed_result) is
 * used; failing both, a fresh id is generated.
 */
internal fun resolveRequestId(
    proRequestId: String?,
    simpleRequestId: String?,
): String = proRequestId ?: simpleRequestId ?: UUID.randomUUID().toString()

public sealed class PublishDataResult {
    public data class Success(val deviceSessionId: String) : PublishDataResult()

    public data object PublishFailure : PublishDataResult()
}
