package com.checkout.risk

data class RiskSDKInternalConfig(
    private val config: RiskConfig
) {
    val merchantPublicKey = config.publicKey
    val environment = config.environment
    val integrationType =
        if (config.framesMode) RiskIntegrationType.FRAMES else RiskIntegrationType.STANDALONE
    val sourceType: SourceType =
        if (config.framesMode) SourceType.CARD_TOKEN else SourceType.RISK_SDK

    val deviceDataEndpoint = when (config.environment) {
        RiskEnvironment.QA -> "https://prism-qa.ckotech.co/collect"
        RiskEnvironment.SANDBOX -> "https://risk.sandbox.checkout.com/collect"
        RiskEnvironment.PRODUCTION -> "https://prism-qa.ckotech.co/collect"
    }

    val fingerprintEndpoint = when (config.environment) {
        RiskEnvironment.QA -> "https://fpjs.cko-qa.ckotech.co"
        RiskEnvironment.SANDBOX -> "https://fpjs.sandbox.checkout.com"
        RiskEnvironment.PRODUCTION -> "https://fpjs.checkout.com"
    }

}

data class RiskConfig(
    val publicKey: String,
    val environment: RiskEnvironment,
    val framesMode: Boolean
)

enum class RiskEnvironment {
    QA,
    SANDBOX,
    PRODUCTION
}

enum class RiskIntegrationType(val type: String) {
    STANDALONE("RiskAndroidStandalone"),
    FRAMES("RiskAndroidInFramesAndroid")
}

enum class SourceType {
    CARD_TOKEN,
    RISK_SDK
}