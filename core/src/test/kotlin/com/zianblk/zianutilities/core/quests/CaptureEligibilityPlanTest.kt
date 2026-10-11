package com.zianblk.zianutilities.core.quests

import com.zianblk.zianutilities.core.generation.Generation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CaptureEligibilityPlanTest {
    @Test
    fun `only enabled generations are candidates and disabled gen8 is never offered`() {
        val plan = CaptureEligibilityPlan(setOf(Generation.GEN_2, Generation.GEN_1))
        assertTrue(plan.assignable)
        assertEquals(listOf(Generation.GEN_1, Generation.GEN_2), plan.generationCandidates())
        assertTrue(plan.accepts(setOf(Generation.GEN_1)))
        assertFalse(plan.accepts(setOf(Generation.GEN_8)))
    }

    @Test
    fun `no enabled generation pauses capture assignment`() {
        val plan = CaptureEligibilityPlan(emptySet())
        assertFalse(plan.assignable)
        assertEquals(emptyList(), plan.generationCandidates())
        assertFalse(plan.accepts(setOf(Generation.GEN_7)))
    }
}

