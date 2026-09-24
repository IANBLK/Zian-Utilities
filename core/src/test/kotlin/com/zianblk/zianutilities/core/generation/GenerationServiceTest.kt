package com.zianblk.zianutilities.core.generation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GenerationServiceTest {
    @Test
    fun `enable persists changed state`() {
        val store = InMemoryStore(GenerationState.none())
        val service = GenerationService(store)

        assertEquals(
            GenerationMutationResult.CHANGED,
            service.enable(Generation.GEN_3),
        )
        assertTrue(store.state.isEnabled(Generation.GEN_3))
        assertEquals(1, store.saveCount)
    }

    @Test
    fun `enable already enabled is idempotent and does not save`() {
        val store = InMemoryStore(
            GenerationState.none().enable(Generation.GEN_3)
        )
        val service = GenerationService(store)

        assertEquals(
            GenerationMutationResult.ALREADY_ENABLED,
            service.enable(Generation.GEN_3),
        )
        assertEquals(0, store.saveCount)
    }

    @Test
    fun `disable persists changed state`() {
        val store = InMemoryStore(
            GenerationState.none().enable(Generation.GEN_8)
        )
        val service = GenerationService(store)

        assertEquals(
            GenerationMutationResult.CHANGED,
            service.disable(Generation.GEN_8),
        )
        assertFalse(store.state.isEnabled(Generation.GEN_8))
        assertEquals(1, store.saveCount)
    }

    @Test
    fun `disable already disabled is idempotent and does not save`() {
        val store = InMemoryStore(GenerationState.none())
        val service = GenerationService(store)

        assertEquals(
            GenerationMutationResult.ALREADY_DISABLED,
            service.disable(Generation.GEN_8),
        )
        assertEquals(0, store.saveCount)
    }

    @Test
    fun `snapshot always reflects store state`() {
        val store = InMemoryStore(GenerationState.none())
        val service = GenerationService(store)

        store.state = GenerationState.all()

        assertEquals(GenerationState.all(), service.snapshot())
    }

    private class InMemoryStore(
        initial: GenerationState,
    ) : GenerationStateStore {
        var state: GenerationState = initial
        var saveCount: Int = 0

        override fun load(): GenerationState = state

        override fun save(state: GenerationState) {
            this.state = state
            saveCount++
        }
    }
}
