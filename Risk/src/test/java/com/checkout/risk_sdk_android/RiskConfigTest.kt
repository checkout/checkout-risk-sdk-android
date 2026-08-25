package com.checkout.risk

import org.junit.Assert
import org.junit.Test

class RiskConfigTest {
    @Test
    fun `uses shared device data endpoint when no mssd is provided`() {
        Assert.assertEquals(
            "https://prism-qa.ckotech.co",
            endpointFor(RiskEnvironment.QA, mssd = null),
        )
        Assert.assertEquals(
            "https://risk.sandbox.checkout.com",
            endpointFor(RiskEnvironment.SANDBOX, mssd = null),
        )
        Assert.assertEquals(
            "https://risk.checkout.com",
            endpointFor(RiskEnvironment.PRODUCTION, mssd = null),
        )
    }

    @Test
    fun `uses merchant specific device data endpoint when mssd is provided`() {
        Assert.assertEquals(
            "https://merchant.devices-egw.cko-qa.ckotech.co",
            endpointFor(RiskEnvironment.QA, mssd = "merchant"),
        )
        Assert.assertEquals(
            "https://merchant.devices.api.sandbox.checkout.com",
            endpointFor(RiskEnvironment.SANDBOX, mssd = "merchant"),
        )
        Assert.assertEquals(
            "https://merchant.devices.api.checkout.com",
            endpointFor(RiskEnvironment.PRODUCTION, mssd = "merchant"),
        )
    }

    @Test
    fun `treats a blank mssd as absent`() {
        Assert.assertEquals(
            "https://risk.checkout.com",
            endpointFor(RiskEnvironment.PRODUCTION, mssd = "  "),
        )
    }

    private fun endpointFor(
        environment: RiskEnvironment,
        mssd: String?,
    ): String =
        RiskSDKInternalConfigImpl(
            RiskConfig(publicKey = "pk_test_xxx", environment = environment, mssd = mssd),
        ).deviceDataEndpoint
}
