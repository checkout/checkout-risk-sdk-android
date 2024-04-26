package com.checkout.risk

public data class RiskConfig(
    val publicKey: String,
    val environment: RiskEnvironment,
    val framesOptions: FramesOptions? = null,
)

public data class FramesOptions(
    val version: String,
    val productIdentifier: String,
    val correlationId: String,
)

internal interface RiskSDKInternalConfig {
    val merchantPublicKey: String
    val environment: RiskEnvironment
    val deviceDataEndpoint: String
    val fingerprintEndpoint: String
    val integrationType: RiskIntegrationType
    val sourceType: SourceType
    val framesOptions: FramesOptions?
}

internal data class RiskSDKInternalConfigImpl(
    val config: RiskConfig,
) : RiskSDKInternalConfig {
    private val framesMode: Boolean = config.framesOptions !== null

    override val framesOptions: FramesOptions? = config.framesOptions
    override var merchantPublicKey: String = config.publicKey
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
