package dev.benderapps.heartteamprep.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubscriptionServiceTest {
    @Test
    fun describesBillingPeriods() {
        assertEquals("3 days", describeBillingPeriod("P3D"))
        assertEquals("1 day", describeBillingPeriod("P1D"))
        assertEquals("1 week", describeBillingPeriod("P1W"))
        assertEquals("2 months", describeBillingPeriod("P2M"))
        assertEquals("1 year", describeBillingPeriod("P1Y"))
    }

    @Test
    fun rejectsUnreadablePeriods() {
        assertNull(describeBillingPeriod(""))
        assertNull(describeBillingPeriod("3 days"))
        assertNull(describeBillingPeriod("P0D"))
    }
}
