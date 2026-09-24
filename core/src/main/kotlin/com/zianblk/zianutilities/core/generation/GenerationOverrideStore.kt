package com.zianblk.zianutilities.core.generation

interface GenerationOverrideStore {
    fun all(): List<GenerationOverride>

    fun find(speciesId: String): GenerationOverride? =
        all().firstOrNull { it.speciesId == speciesId }
}
