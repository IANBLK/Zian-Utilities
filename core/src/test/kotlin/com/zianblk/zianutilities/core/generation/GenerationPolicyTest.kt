package com.zianblk.zianutilities.core.generation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GenerationPolicyTest {
    @Test
    fun `enabled generation allows resolved species`() {
        val result = GenerationPolicy.decide(
            resolvedGenerations = setOf(Generation.GEN_1),
            enabled = setOf(Generation.GEN_1),
            override = null,
            unknownPolicy = UnknownSpeciesPolicy.DENY,
        )

        assertEquals(SpawnDecision.Allow, result)
    }

    @Test
    fun `disabled generation denies resolved species`() {
        val result = GenerationPolicy.decide(
            resolvedGenerations = setOf(Generation.GEN_2),
            enabled = setOf(Generation.GEN_1),
            override = null,
            unknownPolicy = UnknownSpeciesPolicy.ALLOW,
        )

        assertEquals(
            SpawnDecision.Deny(GenerationPolicy.REASON_GENERATION_DISABLED),
            result,
        )
    }

    @Test
    fun `species with multiple resolved generations is allowed when any is enabled`() {
        val result = GenerationPolicy.decide(
            resolvedGenerations = setOf(Generation.GEN_1, Generation.GEN_9),
            enabled = setOf(Generation.GEN_9),
            override = null,
            unknownPolicy = UnknownSpeciesPolicy.DENY,
        )

        assertEquals(SpawnDecision.Allow, result)
    }

    @Test
    fun `override has priority over resolved generations and can allow`() {
        val result = GenerationPolicy.decide(
            resolvedGenerations = setOf(Generation.GEN_2),
            enabled = setOf(Generation.GEN_1),
            override = Generation.GEN_1,
            unknownPolicy = UnknownSpeciesPolicy.DENY,
        )

        assertEquals(SpawnDecision.Allow, result)
    }

    @Test
    fun `override has priority over resolved generations and can deny`() {
        val result = GenerationPolicy.decide(
            resolvedGenerations = setOf(Generation.GEN_1),
            enabled = setOf(Generation.GEN_1),
            override = Generation.GEN_2,
            unknownPolicy = UnknownSpeciesPolicy.ALLOW,
        )

        assertEquals(
            SpawnDecision.Deny(GenerationPolicy.REASON_OVERRIDE_GENERATION_DISABLED),
            result,
        )
    }

    @Test
    fun `unknown species obeys ALLOW policy`() {
        val result = GenerationPolicy.decide(
            resolvedGenerations = emptySet(),
            enabled = emptySet(),
            override = null,
            unknownPolicy = UnknownSpeciesPolicy.ALLOW,
        )

        assertEquals(SpawnDecision.Allow, result)
    }

    @Test
    fun `unknown species obeys DENY policy`() {
        val result = GenerationPolicy.decide(
            resolvedGenerations = emptySet(),
            enabled = emptySet(),
            override = null,
            unknownPolicy = UnknownSpeciesPolicy.DENY,
        )

        assertEquals(
            SpawnDecision.Deny(GenerationPolicy.REASON_UNKNOWN_SPECIES),
            result,
        )
    }

    @Test
    fun `unknown DENY still denies when all nine generations are enabled`() {
        val result = GenerationPolicy.decide(
            resolvedGenerations = emptySet(),
            state = GenerationState.all(),
            override = null,
            unknownPolicy = UnknownSpeciesPolicy.DENY,
        )

        assertEquals(
            SpawnDecision.Deny(GenerationPolicy.REASON_UNKNOWN_SPECIES),
            result,
        )
    }

    @Test
    fun `unknown ALLOW still allows when all nine generations are enabled`() {
        val result = GenerationPolicy.decide(
            resolvedGenerations = emptySet(),
            state = GenerationState.all(),
            override = null,
            unknownPolicy = UnknownSpeciesPolicy.ALLOW,
        )

        assertEquals(SpawnDecision.Allow, result)
    }

    @Test
    fun `deny decision carries a stable nonblank reason`() {
        val result = GenerationPolicy.decide(
            resolvedGenerations = setOf(Generation.GEN_3),
            enabled = emptySet(),
            override = null,
            unknownPolicy = UnknownSpeciesPolicy.DENY,
        )

        val denied = assertIs<SpawnDecision.Deny>(result)
        assertEquals(GenerationPolicy.REASON_GENERATION_DISABLED, denied.reason)
    }
}
