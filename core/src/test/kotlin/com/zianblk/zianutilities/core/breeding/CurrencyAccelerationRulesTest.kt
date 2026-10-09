package com.zianblk.zianutilities.core.breeding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CurrencyAccelerationRulesTest {
    private val rules = CurrencyAccelerationRules(
        reductionMinutesByCurrency = mapOf(
            "coppercoin" to 2L,
            "diamondcoin" to 20L,
            "netheriteticket" to 220L,
            "custom_avecoin" to 17L
        )
    )

    @Test fun freeBreedingIsTwentyFourHours() {
        assertEquals(1440L, rules.remainingMinutes(emptyMap()))
    }

    @Test fun independentCurrencyTiersAreSupported() {
        assertEquals(1438L, rules.remainingMinutes(mapOf("coppercoin" to 1L)))
        assertEquals(1420L, rules.remainingMinutes(mapOf("diamondcoin" to 1L)))
        assertEquals(1423L, rules.remainingMinutes(mapOf("custom_avecoin" to 1L)))
    }

    @Test fun ticketsReachFloor() {
        assertEquals(120L, rules.remainingMinutes(mapOf("netheriteticket" to 6L)))
        assertFalse(rules.canAccelerate(mapOf("netheriteticket" to 6L), "coppercoin", 1L))
    }

    @Test fun quantityIsUncappedAndOverflowSafe() {
        assertEquals(120L, rules.remainingMinutes(mapOf("coppercoin" to Long.MAX_VALUE)))
        assertEquals(120L, rules.remainingMinutes(mapOf("diamondcoin" to 10_000_000L)))
    }

    @Test fun mixedCurrencyAmountsAndConfigurableRates() {
        assertEquals(1300L, rules.remainingMinutes(mapOf("coppercoin" to 10L, "diamondcoin" to 6L)))
        val tuned = rules.copy(reductionMinutesByCurrency = mapOf("coppercoin" to 7L))
        assertEquals(1433L, tuned.remainingMinutes(mapOf("coppercoin" to 1L)))
    }

    @Test fun invalidPurchasesAreRejected() {
        assertFalse(rules.canAccelerate(emptyMap(), "unknown", 1))
        assertFalse(rules.canAccelerate(emptyMap(), "coppercoin", 0))
        assertFailsWith<IllegalArgumentException> { rules.remainingMinutes(mapOf("coppercoin" to -1)) }
        assertFailsWith<IllegalArgumentException> { rules.remainingMinutes(mapOf("unknown" to 1)) }
        assertTrue(rules.canAccelerate(emptyMap(), "coppercoin", 1))
    }
}
