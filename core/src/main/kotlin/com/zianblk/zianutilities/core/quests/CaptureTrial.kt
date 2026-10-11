package com.zianblk.zianutilities.core.quests

import com.zianblk.zianutilities.core.generation.Generation
import java.util.UUID

/** One explicitly started, one-capture server trial; it never grants a reward. */
data class CaptureTrial(
    val assignmentId: UUID,
    val playerId: UUID,
    val targetGenerations: Set<Generation>,
    val capturedSpeciesId: String? = null,
) {
    init {
        require(targetGenerations.isNotEmpty()) { "trial needs at least one generation" }
        require(capturedSpeciesId == null || capturedSpeciesId.isNotBlank())
    }

    val completed: Boolean get() = capturedSpeciesId != null
}

enum class CaptureTrialResult {
    NOT_STARTED,
    IGNORED_GENERATION,
    ALREADY_COMPLETED,
    COMPLETED,
}

interface CaptureTrialStore {
    fun <T> withPlayerLock(playerId: UUID, action: () -> T): T
    fun load(playerId: UUID): CaptureTrial?
    fun save(trial: CaptureTrial)
}

class CaptureTrialService(private val store: CaptureTrialStore) {
    fun start(playerId: UUID, targets: Set<Generation>): CaptureTrial =
        store.withPlayerLock(playerId) {
            val existing = store.load(playerId)
            if (existing != null) return@withPlayerLock retargetIfActive(existing, targets)
            CaptureTrial(
                UUID.randomUUID(), playerId, targets.toSet()
            ).also(store::save)
        }

    fun inspect(playerId: UUID): CaptureTrial? =
        store.withPlayerLock(playerId) { store.load(playerId) }

    /** Refreshes an unfinished capture-any assignment without changing its identity. */
    fun synchronize(playerId: UUID, currentlyEnabled: Set<Generation>): CaptureTrial? =
        store.withPlayerLock(playerId) {
            store.load(playerId)?.let { retargetIfActive(it, currentlyEnabled) }
        }

    fun recordCapture(
        playerId: UUID,
        speciesId: String,
        resolved: Set<Generation>,
        currentlyEnabled: Set<Generation>,
    ): CaptureTrialResult = store.withPlayerLock(playerId) {
        require(speciesId.isNotBlank()) { "speciesId must not be blank" }
        val trial = store.load(playerId) ?: return@withPlayerLock CaptureTrialResult.NOT_STARTED
        if (trial.completed) return@withPlayerLock CaptureTrialResult.ALREADY_COMPLETED
        val plan = CaptureEligibilityPlan(currentlyEnabled)
        if (!plan.assignable) {
            return@withPlayerLock CaptureTrialResult.IGNORED_GENERATION
        }
        val current = retargetIfActive(trial, plan.enabledGenerations)
        if (!plan.accepts(resolved)) return@withPlayerLock CaptureTrialResult.IGNORED_GENERATION
        store.save(current.copy(capturedSpeciesId = speciesId))
        CaptureTrialResult.COMPLETED
    }

    private fun retargetIfActive(trial: CaptureTrial, enabled: Set<Generation>): CaptureTrial {
        if (trial.completed || enabled.isEmpty() || trial.targetGenerations == enabled) return trial
        return trial.copy(targetGenerations = enabled.toSet()).also(store::save)
    }
}

