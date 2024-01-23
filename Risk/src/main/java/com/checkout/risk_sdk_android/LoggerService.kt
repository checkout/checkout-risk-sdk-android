package com.checkout.risk_sdk_android

import android.content.Context
import com.checkout.risk_sdk_android.BuildConfig as RiskBuildConfig
import com.checkout.eventlogger.BuildConfig as CKOEventLoggerBuildConfig
import com.checkout.eventlogger.CheckoutEventLogger
import com.checkout.eventlogger.Environment
import com.checkout.eventlogger.domain.model.Event
import com.checkout.eventlogger.domain.model.MonitoringLevel
import com.checkout.eventlogger.domain.model.RemoteProcessorConfig
import com.checkout.eventlogger.domain.model.RemoteProcessorMetadata
import java.util.*

private const val PRODUCT_NAME = Constants.productName
private const val PRODUCT_IDENTIFIER = RiskBuildConfig.LIBRARY_PACKAGE_NAME
private const val PRODUCT_VERSION = CKOEventLoggerBuildConfig.PRODUCT_VERSION

internal enum class RiskEvent(val rawValue: String) {
    PUBLISH_DISABLED("riskDataPublishDisabled"),
    PUBLISHED("riskDataPublished"),
    PUBLISH_FAILURE("riskDataPublishFailure"),
    COLLECTED("riskDataCollected"),
    LOAD_FAILURE("riskLoadFailure")
}

data class RiskLogError(
    val reason: String, // service method
    val message: String, // description of error
    val status: Int?, // status code
    val type: String?, // Error type
)

internal interface LoggerServiceProtocol {
    fun log(
        riskEvent: RiskEvent,
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
internal class LoggerService(private val internalConfig: RiskSDKInternalConfig, context: Context) :
    LoggerServiceProtocol {

    private val logger = CheckoutEventLogger(PRODUCT_NAME).also {

       if (RiskBuildConfig.DEFAULT_LOGCAT_MONITORING_ENABLED) {
           it.enableLocalProcessor(MonitoringLevel.DEBUG)
       }

    }

    init {

        val logEnvironment: Environment = when (internalConfig.environment) {
            RiskEnvironment.QA, RiskEnvironment.SANDBOX -> Environment.SANDBOX
            RiskEnvironment.PRODUCTION -> Environment.PRODUCTION
        }

        initialise(context = context, environment = logEnvironment)
    }

    private fun initialise(context: Context, environment: Environment) {
        environment.toLoggingEnvironment()?.let { loggingEnvironment ->
            val remoteProcessorMetadata = RemoteProcessorMetadata.from(
                context,
                environment.toEnvironmentName().toString(),
                PRODUCT_IDENTIFIER,
                PRODUCT_VERSION
            )
            val remoteProcessorConfig = RemoteProcessorConfig(
                loggingEnvironment,
                sendStackTraceData = false,
            )
            logger.enableRemoteProcessor(
                remoteProcessorConfig,
                remoteProcessorMetadata
            )
        }
    }

    private fun Environment.toEnvironmentName() = when (this) {
        is Environment.SANDBOX -> "sandbox"
        is Environment.PRODUCTION -> "production"
        else -> null
    }

    private fun Environment.toLoggingEnvironment() = when (this) {
        is Environment.SANDBOX -> Environment.SANDBOX
        is Environment.PRODUCTION -> Environment.PRODUCTION
        else -> null
    }

    override fun log(
        riskEvent: RiskEvent,
        deviceSessionID: String?,
        requestID: String?,
        error: RiskLogError?
    ) {
        val event = formatEvent(riskEvent, deviceSessionID, requestID, error)

        logger.logEvent(event)
    }
    private data class LoggingEvent(
        override val monitoringLevel: MonitoringLevel,
        override val properties: Map<String, Any> = emptyMap(),
        override val time: Date = Date(),
        override val typeIdentifier: String,
    ) : Event {

        override fun asSummary(): String {
            return properties.toString()
        }
    }

    private fun formatEvent(
        riskEvent: RiskEvent,
        deviceSessionID: String?,
        requestID: String?,
        error: RiskLogError?
    ): Event {
        val maskedPublicKey = getMaskedPublicKey(internalConfig.merchantPublicKey)
        val ddTags = getDDTags(internalConfig.environment.name.lowercase(Locale.ROOT))
        var monitoringLevel: MonitoringLevel

        monitoringLevel = when (riskEvent) {
            RiskEvent.PUBLISHED, RiskEvent.COLLECTED -> MonitoringLevel.INFO
            RiskEvent.PUBLISH_FAILURE, RiskEvent.LOAD_FAILURE -> MonitoringLevel.ERROR
            RiskEvent.PUBLISH_DISABLED -> MonitoringLevel.WARN
        }

        val properties: Map<String, Any> = when (riskEvent) {
            RiskEvent.PUBLISHED, RiskEvent.COLLECTED -> mapOf(
                "EventType" to riskEvent.rawValue,
                "FramesMode" to internalConfig.framesMode,
                "MaskedPublicKey" to maskedPublicKey,
                "ddTags" to ddTags,
                "DeviceSessionId" to deviceSessionID,
                "RequestId" to requestID
            ).filterValues { it != null }.mapValues { it.value!! }

            RiskEvent.PUBLISH_FAILURE, RiskEvent.LOAD_FAILURE, RiskEvent.PUBLISH_DISABLED -> mapOf(
                "EventType" to riskEvent.rawValue,
                "FramesMode" to internalConfig.framesMode,
                "ErrorMessage" to error?.message,
                "ErrorType" to error?.type,
                "ErrorReason" to error?.reason
            ).filterValues { it != null }.mapValues { it.value!! }
        }

        return LoggingEvent(
            monitoringLevel = monitoringLevel,
            properties = properties,
            typeIdentifier = Constants.loggerTypeIdentifier
        )
    }

    private fun getMaskedPublicKey(publicKey: String): String {
        return "${publicKey.take(8)}********${publicKey.takeLast(6)}"
    }

    private fun getDDTags(environment: String): String {
        return "team:prism,service:prism.risk.android,version:${Constants.riskPackageVersion},env:$environment"
    }
}
