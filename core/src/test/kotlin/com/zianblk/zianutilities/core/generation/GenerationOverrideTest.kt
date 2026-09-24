package com.zianblk.zianutilities.core.generation

import kotlin.test.Test
import kotlin.test.assertFailsWith

class GenerationOverrideTest {
    @Test
    fun `blank species id is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            GenerationOverride("   ", Generation.GEN_1)
        }
    }
}
