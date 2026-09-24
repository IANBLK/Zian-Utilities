package com.zianblk.zianutilities.core.generation

data class GenerationOverride(
    val speciesId: String,
    val generation: Generation,
) {
    init {
        require(speciesId.isNotBlank()) { "speciesId must not be blank" }
    }
}
