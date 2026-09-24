package com.zianblk.zianutilities.core.generation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GenerationStateTest {
    @Test
    fun `state copies the input set and remains immutable by snapshot`() {
        val source = mutableSetOf(Generation.GEN_1)
        val state = GenerationState(source)

        source += Generation.GEN_2

        assertEquals(setOf(Generation.GEN_1), state.enabled)
    }

    @Test
    fun `enable and disable return new snapshots only when needed`() {
        val empty = GenerationState.none()
        val gen1 = empty.enable(Generation.GEN_1)

        assertFalse(empty.isEnabled(Generation.GEN_1))
        assertTrue(gen1.isEnabled(Generation.GEN_1))
        assertSame(gen1, gen1.enable(Generation.GEN_1))
        assertSame(empty, empty.disable(Generation.GEN_1))
        assertEquals(GenerationState.none(), gen1.disable(Generation.GEN_1))
    }

    @Test
    fun `all contains exactly nine canonical generations`() {
        assertEquals(9, GenerationState.all().enabled.size)
        assertEquals(Generation.entries.toSet(), GenerationState.all().enabled)
    }
}
