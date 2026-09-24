package com.zianblk.zianutilities.core.generation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GenerationTest {
    @Test
    fun `canonical ids resolve exactly`() {
        Generation.entries.forEach { generation ->
            assertEquals(generation, Generation.fromCanonicalId(generation.id))
        }
    }

    @Test
    fun `cobblemon aliases are not resolved in core`() {
        assertNull(Generation.fromCanonicalId("gen7b"))
        assertNull(Generation.fromCanonicalId("gen8a"))
    }

    @Test
    fun `canonical parsing is case sensitive and does not trim silently`() {
        assertNull(Generation.fromCanonicalId("GEN1"))
        assertNull(Generation.fromCanonicalId(" gen1"))
        assertNull(Generation.fromCanonicalId("gen1 "))
    }
}
