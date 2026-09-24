package com.zianblk.zianutilities.core.generation

class GenerationService(
    private val store: GenerationStateStore,
) {
    fun snapshot(): GenerationState = store.load()

    fun isEnabled(generation: Generation): Boolean =
        snapshot().isEnabled(generation)

    fun enable(generation: Generation): GenerationMutationResult {
        val current = snapshot()
        if (current.isEnabled(generation)) {
            return GenerationMutationResult.ALREADY_ENABLED
        }

        store.save(current.enable(generation))
        return GenerationMutationResult.CHANGED
    }

    fun disable(generation: Generation): GenerationMutationResult {
        val current = snapshot()
        if (!current.isEnabled(generation)) {
            return GenerationMutationResult.ALREADY_DISABLED
        }

        store.save(current.disable(generation))
        return GenerationMutationResult.CHANGED
    }
}
