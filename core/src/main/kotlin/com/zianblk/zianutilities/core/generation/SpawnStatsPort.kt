package com.zianblk.zianutilities.core.generation

interface SpawnStatsPort {
    fun recordDecision(
        source: String,
        allowed: Boolean,
        speciesId: String?,
    )
}
