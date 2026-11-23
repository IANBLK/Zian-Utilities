package com.zianblk.zianutilities.core.equipment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WeaponBalanceTest {
    @Test fun `valores de balance fijados por Ian`() {
        assertEquals(4, WeaponBalance.POISON_SECONDS)
        assertEquals(0, WeaponBalance.POISON_AMPLIFIER)
        assertEquals(2, WeaponBalance.REAPER_HEAL)
        assertEquals(30, WeaponBalance.ACTIVE_COOLDOWN_SECONDS)
        assertEquals(4, WeaponBalance.FROST_RADIUS)
        assertEquals(4, WeaponBalance.FROST_SECONDS)
        assertEquals(1, WeaponBalance.FROST_AMPLIFIER)
        assertEquals(3, WeaponBalance.MOLTEN_RADIUS)
        assertEquals(4, WeaponBalance.MOLTEN_FIRE_SECONDS)
    }
}
