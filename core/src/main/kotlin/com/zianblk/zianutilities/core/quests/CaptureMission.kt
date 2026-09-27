package com.zianblk.zianutilities.core.quests

import com.zianblk.zianutilities.core.generation.Generation
import java.time.Clock
import java.util.UUID

/** One persistent, explicitly accepted capture mission with no reward path. */
data class CaptureMission(
    val assignmentId: UUID,
    val playerId: UUID,
    val acceptedAtEpochMs: Long,
    val capturedSpeciesId: String? = null,
    val capturedGeneration: Generation? = null,
    val completedAtEpochMs: Long? = null,
) {
    init {
        require(acceptedAtEpochMs > 0)
        val completed = capturedSpeciesId != null
        require(completed == (capturedGeneration != null && completedAtEpochMs != null))
        require(capturedSpeciesId == null || capturedSpeciesId.isNotBlank())
        require(completedAtEpochMs == null || completedAtEpochMs >= acceptedAtEpochMs)
    }

    val completed: Boolean get() = capturedSpeciesId != null

    fun complete(speciesId: String, generation: Generation, atEpochMs: Long): CaptureMission =
        copy(
            capturedSpeciesId = speciesId,
            capturedGeneration = generation,
            completedAtEpochMs = atEpochMs,
        )

    companion object {
        const val DEFINITION_ID = "capture_any_active"
        const val DEFINITION_VERSION = 1
    }
}

enum class CaptureMissionResult {
    NOT_ASSIGNED,
    PAUSED,
    IGNORED_GENERATION,
    ALREADY_COMPLETED,
    COMPLETED,
}

interface CaptureMissionStore {
    fun <T> withPlayerLock(playerId: UUID, action: () -> T): T
    fun load(playerId: UUID): CaptureMission?
    fun save(mission: CaptureMission)
}

class CaptureMissionService @JvmOverloads constructor(
    private val store: CaptureMissionStore,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun accept(playerId: UUID, currentlyEnabled: Set<Generation>): CaptureMission =
        store.withPlayerLock(playerId) {
            store.load(playerId)?.let { return@withPlayerLock it }
            require(CaptureEligibilityPlan(currentlyEnabled).assignable) {
                "cannot assign a capture mission without an enabled generation"
            }
            CaptureMission(
                UUID.randomUUID(), playerId, clock.millis()
            ).also(store::save)
        }

    fun inspect(playerId: UUID): CaptureMission? =
        store.withPlayerLock(playerId) { store.load(playerId) }

    fun recordCapture(
        playerId: UUID,
        speciesId: String,
        resolved: Set<Generation>,
        currentlyEnabled: Set<Generation>,
    ): CaptureMissionResult = store.withPlayerLock(playerId) {
        require(speciesId.isNotBlank()) { "speciesId must not be blank" }
        val mission = store.load(playerId) ?: return@withPlayerLock CaptureMissionResult.NOT_ASSIGNED
        if (mission.completed) return@withPlayerLock CaptureMissionResult.ALREADY_COMPLETED
        val plan = CaptureEligibilityPlan(currentlyEnabled)
        if (!plan.assignable) return@withPlayerLock CaptureMissionResult.PAUSED
        val matched = resolved.intersect(plan.enabledGenerations)
            .minByOrNull { it.ordinal }
            ?: return@withPlayerLock CaptureMissionResult.IGNORED_GENERATION
        store.save(mission.complete(speciesId, matched, clock.millis()))
        CaptureMissionResult.COMPLETED
    }
}

