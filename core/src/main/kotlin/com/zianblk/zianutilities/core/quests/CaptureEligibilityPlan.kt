package com.zianblk.zianutilities.core.quests

import com.zianblk.zianutilities.core.generation.Generation

/**
 * A capture-any objective follows the server's enabled generations.
 * Specific species/type objectives are deliberately not offered here: they
 * need spawn-availability validation before assignment.
 */
class CaptureEligibilityPlan(enabled: Set<Generation>) {
    val enabledGenerations: Set<Generation> = enabled.toSet()
    val assignable: Boolean get() = enabledGenerations.isNotEmpty()

    fun accepts(resolved: Set<Generation>): Boolean =
        resolved.any(enabledGenerations::contains)

    fun generationCandidates(): List<Generation> =
        enabledGenerations.sortedBy { it.ordinal }
}

