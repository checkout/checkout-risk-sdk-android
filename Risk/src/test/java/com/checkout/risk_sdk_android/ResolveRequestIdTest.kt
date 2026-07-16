package com.checkout.risk

import org.junit.Assert
import org.junit.Test

class ResolveRequestIdTest {
    @Test
    fun `prefers the PRO request id when present`() {
        Assert.assertEquals("pro-id", resolveRequestId("pro-id", "os-id"))
        Assert.assertEquals("pro-id", resolveRequestId("pro-id", null))
    }

    @Test
    fun `falls back to the OS request id when PRO is absent`() {
        Assert.assertEquals("os-id", resolveRequestId(null, "os-id"))
    }

    @Test
    fun `generates a fresh id when neither collector provided one`() {
        val id = resolveRequestId(null, null)

        Assert.assertTrue(id.isNotBlank())
        // A second call must not reuse the first — a new id is generated each time.
        Assert.assertNotEquals(id, resolveRequestId(null, null))
    }
}
