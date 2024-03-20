package com.checkout.risk

import android.content.Context
import com.checkout.eventlogger.BuildConfig
import com.checkout.eventlogger.CheckoutEventLogger
import com.checkout.eventlogger.Environment
import com.checkout.eventlogger.domain.model.Event
import com.checkout.eventlogger.domain.model.MonitoringLevel
import com.checkout.eventlogger.domain.model.RemoteProcessorMetadata
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import com.checkout.risk.BuildConfig as RiskBuildConfig

private const val PRODUCT_NAME = Constants.PRODUCT_NAME
private const val PRODUCT_IDENTIFIER = RiskBuildConfig.LIBRARY_PACKAGE_NAME
private const val CKO_LOGGER_PRODUCT_VERSION = BuildConfig.VERSION_NAME

internal enum class RiskEvent(val rawValue: String) {
    PUBLISH_DISABLED("riskDataPublishDisabled"),
    PUBLISHED("riskDataPublished"),
    PUBLISH_FAILURE("riskDataPublishFailure"),
    COLLECTED("riskDataCollected"),
    LOAD_FAILURE("riskLoadFailure"),
}

internal data class RiskLogError(
    val reason: String,
    // service method
    val message: String,
    // description of error
    val status: Int?,
    // status code
    val type: String?,
    // Error type
    val innerExceptionType: String? = null,
    // Inner exception type
)

internal interface LoggerServiceProtocol {
    fun log(
        riskEvent: RiskEvent,
        blockTime: Double? = null,
        deviceDataPersistTime: Double? = null,
        fpLoadTime: Double? = null,
        fpPublishTime: Double? = null,
        deviceSessionID: String? = null,
        requestID: String? = null,
        error: RiskLogError? = null,
    )
}

/**
 * Service for logging events to DataDog with Checkout Event Logger.
 *
 * @param internalConfig The RiskConfig.
 */
internal class LoggerService(
    private val internalConfig: RiskSDKInternalConfig,
    context: Context,
) :
    LoggerServiceProtocol {
    private val logger =
        CheckoutEventLogger(PRODUCT_NAME).also {

            if (RiskBuildConfig.DEFAULT_LOGCAT_MONITORING_ENABLED) {
                it.enableLocalProcessor(MonitoringLevel.DEBUG)
            }
        }

    init {

        val logEnvironment: Environment =
            when (internalConfig.environment) {
                RiskEnvironment.QA, RiskEnvironment.SANDBOX -> Environment.SANDBOX
                RiskEnvironment.PRODUCTION -> Environment.PRODUCTION
            }

        initialise(
            context = context,
            environment = logEnvironment,
            identifier = PRODUCT_IDENTIFIER,
            version = CKO_LOGGER_PRODUCT_VERSION
        )
    }

    private fun initialise(
        context: Context,
        environment: Environment,
        identifier: String,
        version: String,
    ) {
        logger.enableRemoteProcessor(
            environment.toLoggingEnvironment(),
            provideProcessorMetadata(context, environment, identifier, version),
        )
    }

    private fun provideProcessorMetadata(
        context: Context,
        environment: Environment,
        identifier: String,
        version: String,
    ) = RemoteProcessorMetadata.from(
        context,
        environment.toLoggingName(),
        identifier,
        version,
    )

    internal fun Environment.toLoggingEnvironment() =
        when (this) {
            Environment.PRODUCTION -> com.checkout.eventlogger.Environment.PRODUCTION
            Environment.SANDBOX -> com.checkout.eventlogger.Environment.SANDBOX
        }

    internal fun Environment.toLoggingName() =
        when (this) {
            Environment.PRODUCTION -> "production"
            Environment.SANDBOX -> "sandbox"
        }

    override fun log(
        riskEvent: RiskEvent,
        blockTime: Double?,
        deviceDataPersistTime: Double?,
        fpLoadTime: Double?,
        fpPublishTime: Double?,
        deviceSessionID: String?,
        requestID: String?,
        error: RiskLogError?,
    ) {
        var totalLatency = 0.00
        arrayOf(blockTime, deviceDataPersistTime, fpLoadTime, fpPublishTime).forEach { item ->
            totalLatency += item ?: 0.00
        }

        val event = formatEvent(
            riskEvent,
            blockTime,
            deviceDataPersistTime,
            fpLoadTime,
            fpPublishTime,
            totalLatency,
            deviceSessionID,
            requestID,
            error
        )

        logger.logEvent(event)
    }

    private data class LoggingEvent(
        override val monitoringLevel: MonitoringLevel,
        override val properties: Map<String, Any> = emptyMap(),
        override val time: Date = Date(),
        override val typeIdentifier: String,
    ) : Event

    private fun formatEvent(
        riskEvent: RiskEvent,
        blockTime: Double?,
        deviceDataPersistTime: Double?,
        fpLoadTime: Double?,
        fpPublishTime: Double?,
        totalLatency: Double,
        deviceSessionID: String?,
        requestID: String?,
        error: RiskLogError?,
    ): Event {
        val timeZoneLog = TimeZone.getDefault().id
        val maskedPublicKey = getMaskedPublicKey(internalConfig.merchantPublicKey)
        val ddTags = getDDTags(internalConfig.environment.name.lowercase(Locale.ROOT))
        val monitoringLevel: MonitoringLevel =
            when (riskEvent) {
                RiskEvent.PUBLISHED, RiskEvent.COLLECTED -> MonitoringLevel.INFO
                RiskEvent.PUBLISH_FAILURE, RiskEvent.LOAD_FAILURE -> MonitoringLevel.ERROR
                RiskEvent.PUBLISH_DISABLED -> MonitoringLevel.WARN
            }

        val properties: Map<String, Any> =
            when (riskEvent) {
                RiskEvent.PUBLISHED, RiskEvent.COLLECTED ->
                    mapOf(
                        "Block" to blockTime,
                        "DeviceDataPersist" to deviceDataPersistTime,
                        "FpLoad" to fpLoadTime,
                        "FpPublish" to fpPublishTime,
                        "Total" to totalLatency,
                        "EventType" to riskEvent.rawValue,
                        "FramesMode" to internalConfig.framesMode,
                        "MaskedPublicKey" to maskedPublicKey,
                        "ddTags" to ddTags,
                        "RiskSDKVersion" to Constants.RISK_PACKAGE_VERSION,
                        "Timezone" to timeZoneLog,
                        "FpRequestId" to requestID,
                        "DeviceSessionId" to deviceSessionID,
                    ).filterValues { it != null }.mapValues { it.value!! }

                RiskEvent.PUBLISH_FAILURE, RiskEvent.LOAD_FAILURE, RiskEvent.PUBLISH_DISABLED ->
                    mapOf(
                        "Block" to blockTime,
                        "DeviceDataPersist" to deviceDataPersistTime,
                        "FpLoad" to fpLoadTime,
                        "FpPublish" to fpPublishTime,
                        "Total" to totalLatency,
                        "EventType" to riskEvent.rawValue,
                        "FramesMode" to internalConfig.framesMode,
                        "MaskedPublicKey" to maskedPublicKey,
                        "ddTags" to ddTags,
                        "RiskSDKVersion" to Constants.RISK_PACKAGE_VERSION,
                        "Timezone" to timeZoneLog,
                        "ErrorMessage" to error?.message,
                        "ErrorType" to error?.type,
                        "ErrorReason" to error?.reason,
                        "InnerExceptionType" to error?.innerExceptionType,
                    ).filterValues { it != null }.mapValues { it.value!! }
            }

        return LoggingEvent(
            monitoringLevel = monitoringLevel,
            properties = properties,
            typeIdentifier = Constants.LOGGER_TYPE_IDENTIFIER,
        )
    }

    private fun getMaskedPublicKey(publicKey: String): String {
        return "${publicKey.take(8)}********${publicKey.takeLast(6)}"
    }

    private fun getDDTags(environment: String): String {
        return "team:prism,service:prism.risk.android,version:${Constants.RISK_PACKAGE_VERSION},env:$environment"
    }
}
