package com.zianblk.zianutilities.core.generation

interface GenerationStateStore {
    fun load(): GenerationState

    fun save(state: GenerationState)
}
