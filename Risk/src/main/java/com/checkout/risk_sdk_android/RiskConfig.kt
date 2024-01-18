package com.checkout.risk_sdk_android

internal fun getDeviceDataEndpoint(environment: RiskEnvironment) =
    when (environment) {
        RiskEnvironment.QA -> "https://prism-qa.ckotech.co"
        RiskEnvironment.SANDBOX -> "https://risk.sandbox.checkout.com"
        RiskEnvironment.PRODUCTION -> "https://risk.checkout.com"
    }

internal fun getFingerprintEndpoint(environment: RiskEnvironment) =
    when (environment) {
        RiskEnvironment.QA -> "https://fpjs.cko-qa.ckotech.co"
        RiskEnvironment.SANDBOX -> "https://fpjs.sandbox.checkout.com"
        RiskEnvironment.PRODUCTION -> "https://fpjs.checkout.com"
    }

public data class RiskConfig(
    val publicKey: String,
    val environment: RiskEnvironment,
    val framesMode: Boolean,
)

public enum class RiskEnvironment {
    QA,
    SANDBOX,
    PRODUCTION,
}

internal enum class RiskIntegrationType(val type: String) {
    STANDALONE("RiskAndroidStandalone"),
    FRAMES("RiskAndroidInFramesAndroid"),
}

internal enum class SourceType {
    CARD_TOKEN,
    RISK_SDK,
}
