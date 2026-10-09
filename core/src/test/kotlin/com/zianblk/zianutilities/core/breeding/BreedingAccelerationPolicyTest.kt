package com.zianblk.zianutilities.core.breeding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class BreedingAccelerationPolicyTest {
    private val policy = BreedingAccelerationPolicy()

    @Test fun freeBreedingLastsOneDay() {
        assertEquals(1440L, policy.durationMinutes(0, 0))
    }

    @Test fun sixTicketsReduceBreedingToTwoHours() {
        assertEquals(120L, policy.durationMinutes(0, 6))
    }

    @Test fun oneTicketIsMoreValuableThanOneCoin() {
        assertTrue(policy.durationMinutes(0, 1) < policy.durationMinutes(1, 0))
    }

    @Test fun bothLimitsAreEnforced() {
        assertFailsWith<IllegalArgumentException> { policy.durationMinutes(13, 0) }
        assertFailsWith<IllegalArgumentException> { policy.durationMinutes(0, 7) }
    }

    @Test fun combinedReductionsNeverPassFloor() {
        assertEquals(120L, policy.durationMinutes(12, 6))
    }

    @Test fun noPaymentWhenAlreadyAtFloor() {
        assertFalse(policy.permittedAdditional(0, 6, 1, 0))
        assertFalse(policy.permittedAdditional(12, 0, 1, 0))
        assertTrue(policy.permittedAdditional(0, 0, 1, 0))
    }

    @Test fun readyAtUsesAbsoluteTime() {
        assertEquals(7_200_000L, policy.effectiveReadyAtMillis(0, 0, 6))
    }

    @Test fun concurrentLimitsAreOneAndTwo() {
        assertEquals(1, policy.defaultConcurrentLimit)
        assertEquals(2, policy.vipConcurrentLimit)
    }
}
