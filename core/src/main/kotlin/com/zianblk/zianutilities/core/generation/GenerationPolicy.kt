package com.zianblk.zianutilities.core.generation

object GenerationPolicy {
    const val REASON_OVERRIDE_GENERATION_DISABLED = "override_generation_disabled"
    const val REASON_GENERATION_DISABLED = "generation_disabled"
    const val REASON_UNKNOWN_SPECIES = "unknown_species"

    fun decide(
        resolvedGenerations: Set<Generation>,
        enabled: Set<Generation>,
        override: Generation?,
        unknownPolicy: UnknownSpeciesPolicy,
    ): SpawnDecision {
        if (override != null) {
            return if (override in enabled) {
                SpawnDecision.Allow
            } else {
                SpawnDecision.Deny(REASON_OVERRIDE_GENERATION_DISABLED)
            }
        }

        if (resolvedGenerations.isNotEmpty()) {
            return if (resolvedGenerations.any { it in enabled }) {
                SpawnDecision.Allow
            } else {
                SpawnDecision.Deny(REASON_GENERATION_DISABLED)
            }
        }

        return when (unknownPolicy) {
            UnknownSpeciesPolicy.ALLOW -> SpawnDecision.Allow
            UnknownSpeciesPolicy.DENY -> SpawnDecision.Deny(REASON_UNKNOWN_SPECIES)
        }
    }

    fun decide(
        resolvedGenerations: Set<Generation>,
        state: GenerationState,
        override: Generation?,
        unknownPolicy: UnknownSpeciesPolicy,
    ): SpawnDecision = decide(
        resolvedGenerations = resolvedGenerations,
        enabled = state.enabled,
        override = override,
        unknownPolicy = unknownPolicy,
    )
}
