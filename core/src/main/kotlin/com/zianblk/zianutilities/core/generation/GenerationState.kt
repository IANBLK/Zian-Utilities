package com.zianblk.zianutilities.core.generation

class GenerationState(enabled: Set<Generation>) {
    private val snapshot: Set<Generation> = enabled.toSet()

    val enabled: Set<Generation>
        get() = snapshot

    fun isEnabled(generation: Generation): Boolean = generation in snapshot

    fun enable(generation: Generation): GenerationState =
        if (generation in snapshot) this else GenerationState(snapshot + generation)

    fun disable(generation: Generation): GenerationState =
        if (generation !in snapshot) this else GenerationState(snapshot - generation)

    fun enableAll(): GenerationState = all()

    fun disableAll(): GenerationState = none()

    override fun equals(other: Any?): Boolean =
        other is GenerationState && snapshot == other.snapshot

    override fun hashCode(): Int = snapshot.hashCode()

    override fun toString(): String =
        "GenerationState(enabled=" + snapshot.sortedBy { it.ordinal } + ")"

    companion object {
        fun none(): GenerationState = GenerationState(emptySet())

        fun all(): GenerationState = GenerationState(Generation.entries.toSet())
    }
}
