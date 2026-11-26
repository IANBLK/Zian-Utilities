package com.zianblk.zianutilities.core.raids

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Random

class RaidGenerationPickTest {
    @Test fun `a denied boss is re-rolled among the allowed ones`() {
        val picked = RaidGenerationPick.reroll(listOf("gen1", "gen9", "gen2"), { it != "gen1" && it != "gen9" }, Random(1))
        assertEquals("gen2", picked.get())
    }

    @Test fun `every candidate denied gives nothing so the join is cancelled`() {
        assertTrue(RaidGenerationPick.reroll(listOf("a", "b"), { false }, Random(1)).isEmpty)
        assertTrue(RaidGenerationPick.reroll(emptyList<String>(), { true }, Random(1)).isEmpty)
    }
}
