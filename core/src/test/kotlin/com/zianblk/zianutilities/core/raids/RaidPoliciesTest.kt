package com.zianblk.zianutilities.core.raids

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RaidPoliciesTest {
    @Test fun `tiers one to four are battles and five and up are duels`() {
        for (t in 1..4) assertFalse(RaidTierPolicy.isDuel(t, 5), "tier $t")
        for (t in 5..7) assertTrue(RaidTierPolicy.isDuel(t, 5), "tier $t")
        assertEquals(5, RaidTierPolicy.tierOfOrdinal(4))
    }

    @Test fun `odd values never become a duel`() {
        assertFalse(RaidTierPolicy.isDuel(0, 5)); assertFalse(RaidTierPolicy.isDuel(8, 5)); assertFalse(RaidTierPolicy.isDuel(-1, 5))
        assertFalse(RaidTierPolicy.isDuel(6, 0)); assertFalse(RaidTierPolicy.isDuel(6, 9)); assertTrue(RaidTierPolicy.isDuel(1, 1))
    }

    @Test fun `required damage is a proportion and the threshold itself pays`() {
        assertTrue(RaidRewardPolicy.closeAsSuccess(DuelResult.LOST, 0.25, 0.25))
        assertFalse(RaidRewardPolicy.closeAsSuccess(DuelResult.LOST, 0.2499, 0.25))
        assertTrue(RaidRewardPolicy.closeAsSuccess(DuelResult.TIMEOUT, 0.6, 0.25))
        assertTrue(RaidRewardPolicy.closeAsSuccess(DuelResult.SURRENDERED, 0.3, 0.25))
    }

    @Test fun `a win always pays, an abort never does, and no damage never pays`() {
        assertTrue(RaidRewardPolicy.closeAsSuccess(DuelResult.WON, 1.0, 0.9))
        assertFalse(RaidRewardPolicy.closeAsSuccess(DuelResult.ABORTED, 0.9, 0.1))
        assertFalse(RaidRewardPolicy.closeAsSuccess(DuelResult.LOST, 0.0, 0.0))
        assertTrue(RaidRewardPolicy.closeAsSuccess(DuelResult.LOST, 0.01, 0.0))
    }
}
