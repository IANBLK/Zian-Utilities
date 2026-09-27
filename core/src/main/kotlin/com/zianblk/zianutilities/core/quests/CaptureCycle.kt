package com.zianblk.zianutilities.core.quests

import com.zianblk.zianutilities.core.generation.Generation
import java.time.Clock
import java.time.Duration
import java.util.UUID

data class CycleCapture(
    val pokemonId: UUID,
    val speciesId: String,
    val generation: Generation,
    val atEpochMs: Long,
) {
    init {
        require(speciesId.isNotBlank())
        require(atEpochMs > 0)
    }
}

/** Three distinct captured Pokemon within a six-hour, per-player window. No rewards. */
data class CaptureCycle(
    val cycleId: UUID,
    val playerId: UUID,
    val assignedAtEpochMs: Long,
    val captures: List<CycleCapture> = emptyList(),
) {
    init {
        require(assignedAtEpochMs > 0)
        require(captures.size <= GOAL)
        require(captures.map { it.pokemonId }.toSet().size == captures.size)
        require(captures.all { it.atEpochMs >= assignedAtEpochMs && it.atEpochMs < expiresAtEpochMs })
    }

    val expiresAtEpochMs: Long get() = Math.addExact(assignedAtEpochMs, DURATION_MS)
    val completed: Boolean get() = captures.size == GOAL

    companion object {
        const val GOAL = 3
        val DURATION_MS: Long = Duration.ofHours(6).toMillis()
        const val DEFINITION_ID = "capture_three_active"
        const val DEFINITION_VERSION = 1
    }
}

enum class CaptureCycleResult {
    NOT_ASSIGNED, PAUSED, IGNORED_GENERATION, DUPLICATE, ALREADY_COMPLETED, ADVANCED, COMPLETED
}

interface CaptureCycleStore {
    fun <T> withPlayerLock(playerId: UUID, action: () -> T): T
    fun load(playerId: UUID): CaptureCycle?
    fun save(cycle: CaptureCycle)
    /** Must persist the old cycle before replacing the current cycle. */
    fun rotate(previous: CaptureCycle, next: CaptureCycle)
}

class CaptureCycleService @JvmOverloads constructor(
    private val store: CaptureCycleStore,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun assign(playerId: UUID, enabled: Set<Generation>): CaptureCycle =
        store.withPlayerLock(playerId) {
            currentOrRotate(playerId, enabled)?.let { return@withPlayerLock it }
            require(enabled.isNotEmpty()) { "cannot assign a capture cycle without an enabled generation" }
            CaptureCycle(UUID.randomUUID(), playerId, clock.millis()).also(store::save)
        }

    /** Lazy renewal gives the player a full six hours after the next visit or capture. */
    fun inspect(playerId: UUID, enabled: Set<Generation>): CaptureCycle? =
        store.withPlayerLock(playerId) { currentOrRotate(playerId, enabled) }

    fun recordCapture(
        playerId: UUID,
        pokemonId: UUID,
        speciesId: String,
        resolved: Set<Generation>,
        enabled: Set<Generation>,
    ): CaptureCycleResult = store.withPlayerLock(playerId) {
        require(speciesId.isNotBlank())
        val cycle = currentOrRotate(playerId, enabled)
            ?: return@withPlayerLock CaptureCycleResult.NOT_ASSIGNED
        if (cycle.captures.any { it.pokemonId == pokemonId }) return@withPlayerLock CaptureCycleResult.DUPLICATE
        if (cycle.completed) return@withPlayerLock CaptureCycleResult.ALREADY_COMPLETED
        if (enabled.isEmpty()) return@withPlayerLock CaptureCycleResult.PAUSED
        val generation = resolved.intersect(enabled).minByOrNull { it.ordinal }
            ?: return@withPlayerLock CaptureCycleResult.IGNORED_GENERATION
        val updated = cycle.copy(captures = cycle.captures + CycleCapture(
            pokemonId, speciesId, generation, clock.millis(),
        ))
        store.save(updated)
        if (updated.completed) CaptureCycleResult.COMPLETED else CaptureCycleResult.ADVANCED
    }

    private fun currentOrRotate(playerId: UUID, enabled: Set<Generation>): CaptureCycle? {
        val current = store.load(playerId) ?: return null
        val now = clock.millis()
        if (now < current.expiresAtEpochMs || enabled.isEmpty()) return current
        val next = CaptureCycle(UUID.randomUUID(), playerId, now)
        store.rotate(current, next)
        return next
    }
}

