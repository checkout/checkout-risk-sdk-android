package com.checkout.risk_sdk_android

public data class RiskConfig(
    val publicKey: String,
    val environment: RiskEnvironment,
    val framesMode: Boolean,
)

internal data class RiskSDKInternalConfig(
    val config: RiskConfig
) {
    var merchantPublicKey: String = config.publicKey
    val framesMode: Boolean = config.framesMode
    val environment: RiskEnvironment = config.environment
    val deviceDataEndpoint: String
    val fingerprintEndpoint: String
    val integrationType: RiskIntegrationType = if (framesMode) RiskIntegrationType.FRAMES else RiskIntegrationType.STANDALONE
    val sourceType: SourceType = if (framesMode) SourceType.CARD_TOKEN else SourceType.RISK_SDK

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

enum class RiskEnvironment(val rawValue: String) {
    QA("qa"),
    SANDBOX("sandbox"),
    PRODUCTION("prod")
}

internal enum class RiskIntegrationType(val type: String) {
    STANDALONE("RiskAndroidStandalone"),
    FRAMES("RiskAndroidInFramesAndroid"),
}

internal enum class SourceType(val type: String) {
    CARD_TOKEN("card_token"),
    RISK_SDK("riskandroid")
}
