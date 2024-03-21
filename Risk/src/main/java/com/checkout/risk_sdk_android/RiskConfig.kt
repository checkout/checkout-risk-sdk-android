package com.checkout.risk

public data class RiskConfig(
    val publicKey: String,
    val environment: RiskEnvironment,
    val framesMode: Boolean = false,
    val correlationId: String? = null
)

internal interface RiskSDKInternalConfig {
    val merchantPublicKey: String
    val framesMode: Boolean
    val environment: RiskEnvironment
    val deviceDataEndpoint: String
    val fingerprintEndpoint: String
    val integrationType: RiskIntegrationType
    val sourceType: SourceType
    val correlationId: String?
}

internal data class RiskSDKInternalConfigImpl(
    val config: RiskConfig,
) : RiskSDKInternalConfig {
    override val correlationId: String? = if (config.framesMode) (config.correlationId) else null
    override var merchantPublicKey: String = config.publicKey
    override val framesMode: Boolean = config.framesMode
    override val environment: RiskEnvironment = config.environment
    override val deviceDataEndpoint: String
    override val fingerprintEndpoint: String
    override val integrationType: RiskIntegrationType =
        if (framesMode) RiskIntegrationType.FRAMES else RiskIntegrationType.STANDALONE
    override val sourceType: SourceType =
        if (framesMode) SourceType.CARD_TOKEN else SourceType.RISK_SDK

    init {
        when (environment) {
            RiskEnvironment.QA -> {
                deviceDataEndpoint = "https://prism-qa.ckotech.co"
                fingerprintEndpoint = "https://fpjs.cko-qa.ckotech.co"
            }

            RiskEnvironment.SANDBOX -> {
                deviceDataEndpoint = "https://risk.sandbox.checkout.com"
                fingerprintEndpoint = "https://fpjs.sandbox.checkout.com"
            }

            RiskEnvironment.PRODUCTION -> {
                deviceDataEndpoint = "https://risk.checkout.com"
                fingerprintEndpoint = "https://fpjs.checkout.com"
            }
        }
    }
}

public enum class RiskEnvironment(public val rawValue: String) {
    QA("qa"),
    SANDBOX("sandbox"),
    PRODUCTION("prod"),
}

internal enum class RiskIntegrationType(val type: String) {
    STANDALONE("RiskAndroidStandalone"),
    FRAMES("RiskAndroidInFramesAndroid"),
}

internal enum class SourceType(val rawValue: String) {
    CARD_TOKEN("card_token"),
    RISK_SDK("riskandroid"),
}
