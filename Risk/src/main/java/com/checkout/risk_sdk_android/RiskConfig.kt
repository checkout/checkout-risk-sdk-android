package com.checkout.risk_sdk_android

fun getDeviceDataEndpoint(environment: RiskEnvironment) =
    when (environment) {
        RiskEnvironment.QA -> "https://prism-qa.ckotech.co"
        RiskEnvironment.SANDBOX -> "https://risk.sandbox.checkout.com"
        RiskEnvironment.PRODUCTION -> "https://risk.checkout.com"
    }

fun getFingerprintEndpoint(environment: RiskEnvironment) =
    when (environment) {
        RiskEnvironment.QA -> "https://fpjs.cko-qa.ckotech.co"
        RiskEnvironment.SANDBOX -> "https://fpjs.sandbox.checkout.com"
        RiskEnvironment.PRODUCTION -> "https://fpjs.checkout.com"
    }

data class RiskConfig(
    val publicKey: String,
    val environment: RiskEnvironment,
    val framesMode: Boolean,
)

enum class RiskEnvironment {
    QA,
    SANDBOX,
    PRODUCTION,
}

enum class RiskIntegrationType(val type: String) {
    STANDALONE("RiskAndroidStandalone"),
    FRAMES("RiskAndroidInFramesAndroid"),
}

enum class SourceType {
    CARD_TOKEN,
    RISK_SDK,
}
