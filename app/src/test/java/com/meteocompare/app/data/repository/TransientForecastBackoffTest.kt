package com.meteocompare.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransientForecastBackoffTest {
    @Test
    fun `exponential cooldown is per request and resets on success`() {
        val policy = TransientForecastBackoff()
        assertNull(policy.blockedStatus("paris", 1_000L))
        policy.failed("paris", 503, 1_000L)
        assertEquals(503, policy.blockedStatus("paris", 30_999L))
        assertNull(policy.blockedStatus("paris", 31_000L))
        assertNull(policy.blockedStatus("marseille", 2_000L))

        policy.failed("paris", 429, 31_000L)
        assertEquals(429, policy.blockedStatus("paris", 90_999L))
        assertNull(policy.blockedStatus("paris", 91_000L))
        policy.succeeded("paris")
        policy.failed("paris", 503, 91_000L)
        assertNull(policy.blockedStatus("paris", 121_000L))
    }

    @Test
    fun `delay is bounded and entries cannot grow indefinitely`() {
        val policy = TransientForecastBackoff()
        repeat(24) { policy.failed("paris", 503, 0L) }
        assertEquals(503, policy.blockedStatus("paris", 14 * 60_000L))
        assertNull(policy.blockedStatus("paris", 15 * 60_000L))
        repeat(70) { policy.failed("city-$it", 503, 0L) }
        assertNull(policy.blockedStatus("city-0", 1L))
        assertEquals(503, policy.blockedStatus("city-69", 1L))
    }
}
