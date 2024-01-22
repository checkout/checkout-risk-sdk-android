package com.checkout.risk_sdk_android

import android.content.Context
import android.os.Build
//import com.checkout.BuildConfig
import com.checkout.eventlogger.BuildConfig
import com.checkout.eventlogger.CheckoutEventLogger
import com.checkout.eventlogger.Environment
import com.checkout.eventlogger.domain.model.Event
import com.checkout.eventlogger.domain.model.MonitoringLevel
import com.checkout.eventlogger.domain.model.RemoteProcessorMetadata
import java.util.*

private const val PRODUCT_NAME = Constants.productName
private const val PRODUCT_IDENTIFIER = "com.checkout.risk_sdk_android"
private const val PRODUCT_VERSION = BuildConfig.PRODUCT_VERSION

enum class RiskEvent(val rawValue: String) {
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
    val type: String? // Error type
)

interface LoggerServiceProtocol {
    fun log(
        riskEvent: RiskEvent,
        deviceSessionID: String? = null,
        requestID: String? = null,
        error: RiskLogError? = null
    )
}

/**
 * Service for logging events to DataDog with Checkout Event Logger.
 *
 * @param internalConfig The RiskConfig.
 */
class LoggerService(private val internalConfig: RiskSDKInternalConfig) : LoggerServiceProtocol {

    private val logger = CheckoutEventLogger(Constants.productName).also {
//        TODO: Uncomment the next line for debugging

//            if (BuildConfig.DEFAULT_LOGCAT_MONITORING_ENABLED) enableLocalProcessor(MonitoringLevel.DEBUG)
        it.enableLocalProcessor(MonitoringLevel.DEBUG)
    }

    init {
        setup()
    }

    private fun setup() {

        val logEnvironment: Environment = when (internalConfig.environment) {
            RiskEnvironment.QA, RiskEnvironment.SANDBOX -> Environment.SANDBOX
            RiskEnvironment.PRODUCTION -> Environment.PRODUCTION
        }
        val deviceName = getDeviceModel()
        val appPackageName = "testing"
        val appPackageVersion = "0.0.1"
        val appInstallId = "appInstallId"
        val osVersion = Build.VERSION.RELEASE

        logger.enableRemoteProcessor(
            environment = logEnvironment,
            remoteProcessorMetadata = RemoteProcessorMetadata(
                productIdentifier = Constants.productName,
                productVersion = Constants.version,
                environment = internalConfig.environment.rawValue,
                appPackageName = appPackageName,
                appPackageVersion = appPackageVersion,
                appInstallId = appInstallId,
                deviceName = deviceName,
                platform = "Android",
                osVersion = osVersion
            )
        )
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

    data class SampleEvent(
        override val monitoringLevel: MonitoringLevel,
        override val properties: Map<String, Any>,
        override val time: Date,
        override val typeIdentifier: String
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

        // TODO: Uncomment the next line for debugging
//        if (BuildConfig.DEFAULT_LOGCAT_MONITORING_ENABLED) monitoringLevel = MonitoringLevel.DEBUG

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

        return SampleEvent(
            monitoringLevel = monitoringLevel,
            properties = properties,
            time = Date(),
            typeIdentifier = Constants.loggerTypeIdentifier
        )
    }

    private fun getMaskedPublicKey(publicKey: String): String {
        return "${publicKey.take(8)}********${publicKey.takeLast(6)}"
    }

    private fun getDDTags(environment: String): String {
        return "team:prism,service:prism.risk.android,version:${Constants.version},env:$environment"
    }

    private fun getDeviceModel(): String {
        return Build.MODEL
    }

    fun toLoggingName(environment: Environment) {
        when (environment) {
            Environment.PRODUCTION -> "production"
            Environment.SANDBOX -> "sandbox"
        }
    }

}
