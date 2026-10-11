package com.zianblk.zianutilities.core.quests

import com.zianblk.zianutilities.core.generation.Generation
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

enum class SharedCaptureObjective(val description: String) {
    FIVE_CAPTURES("Captura 5 Pokémon"),
    FIVE_DISTINCT_SPECIES("Captura 5 especies distintas"),
}

/** One deterministic mission definition shared by every player in a three-hour window. */
data class SharedCaptureMission(
    val missionId: UUID,
    val windowStartEpochMs: Long,
    val objective: SharedCaptureObjective,
) {
    val expiresAtEpochMs: Long get() = Math.addExact(windowStartEpochMs, DURATION_MS)

    companion object {
        const val GOAL = 5
        val DURATION_MS: Long = Duration.ofHours(3).toMillis()
        const val DEFINITION_VERSION = 1
    }
}

object SharedCaptureSchedule {
    private val zone = ZoneId.of("America/Guayaquil")

    fun at(epochMs: Long): SharedCaptureMission {
        val local = Instant.ofEpochMilli(epochMs).atZone(zone)
        val slot = local.hour / 3
        val start = local.withHour(slot * 3).withMinute(0).withSecond(0).withNano(0)
            .toInstant().toEpochMilli()
        val objective = if (slot % 2 == 0) SharedCaptureObjective.FIVE_CAPTURES
            else SharedCaptureObjective.FIVE_DISTINCT_SPECIES
        val key = "zianutilities:shared_capture_v1:$start:${objective.name}"
        return SharedCaptureMission(
            UUID.nameUUIDFromBytes(key.toByteArray(StandardCharsets.UTF_8)),
            start,
            objective,
        )
    }
}

/** The only per-player state: acceptance and up to five successful captures. */
data class SharedCaptureProgress(
    val missionId: UUID,
    val playerId: UUID,
    val windowStartEpochMs: Long,
    val acceptedAtEpochMs: Long,
    val captures: List<CycleCapture> = emptyList(),
) {
    init {
        val definition = SharedCaptureSchedule.at(windowStartEpochMs)
        require(definition.windowStartEpochMs == windowStartEpochMs)
        require(missionId == definition.missionId)
        require(acceptedAtEpochMs >= windowStartEpochMs && acceptedAtEpochMs < definition.expiresAtEpochMs)
        require(captures.size <= SharedCaptureMission.GOAL)
        require(captures.map { it.pokemonId }.toSet().size == captures.size)
        require(captures.all { it.atEpochMs >= acceptedAtEpochMs && it.atEpochMs < definition.expiresAtEpochMs })
        if (definition.objective == SharedCaptureObjective.FIVE_DISTINCT_SPECIES) {
            require(captures.map { it.speciesId }.toSet().size == captures.size)
        }
    }

    val completed: Boolean get() = captures.size == SharedCaptureMission.GOAL
}

data class SharedCaptureStatus(
    val mission: SharedCaptureMission,
    val progress: SharedCaptureProgress?,
)

enum class SharedCaptureResult {
    NOT_ACCEPTED, PAUSED, IGNORED_GENERATION, DUPLICATE_POKEMON, DUPLICATE_SPECIES,
    ALREADY_COMPLETED, ADVANCED, COMPLETED,
}

interface SharedCaptureProgressStore {
    fun <T> withPlayerLock(playerId: UUID, action: () -> T): T
    fun load(playerId: UUID): SharedCaptureProgress?
    fun save(progress: SharedCaptureProgress)
    /** Persist latest accepted mission before removing the stale current file. */
    fun archiveAndClear(progress: SharedCaptureProgress)
}

class SharedCaptureMissionService @JvmOverloads constructor(
    private val store: SharedCaptureProgressStore,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun offer(): SharedCaptureMission = SharedCaptureSchedule.at(clock.millis())

    fun accept(playerId: UUID): SharedCaptureStatus = store.withPlayerLock(playerId) {
        val mission = offer()
        val existing = current(playerId, mission)
        if (existing != null) return@withPlayerLock SharedCaptureStatus(mission, existing)
        val accepted = SharedCaptureProgress(
            mission.missionId, playerId, mission.windowStartEpochMs, clock.millis(),
        )
        store.save(accepted)
        SharedCaptureStatus(mission, accepted)
    }

    fun inspect(playerId: UUID): SharedCaptureStatus = store.withPlayerLock(playerId) {
        val mission = offer()
        SharedCaptureStatus(mission, current(playerId, mission))
    }

    fun recordCapture(
        playerId: UUID,
        pokemonId: UUID,
        speciesId: String,
        resolved: Set<Generation>,
        enabled: Set<Generation>,
    ): SharedCaptureResult = store.withPlayerLock(playerId) {
        require(speciesId.isNotBlank())
        val mission = offer()
        val progress = current(playerId, mission)
            ?: return@withPlayerLock SharedCaptureResult.NOT_ACCEPTED
        if (progress.captures.any { it.pokemonId == pokemonId }) {
            return@withPlayerLock SharedCaptureResult.DUPLICATE_POKEMON
        }
        if (progress.completed) return@withPlayerLock SharedCaptureResult.ALREADY_COMPLETED
        if (enabled.isEmpty()) return@withPlayerLock SharedCaptureResult.PAUSED
        val generation = resolved.intersect(enabled).minByOrNull { it.ordinal }
            ?: return@withPlayerLock SharedCaptureResult.IGNORED_GENERATION
        if (mission.objective == SharedCaptureObjective.FIVE_DISTINCT_SPECIES &&
            progress.captures.any { it.speciesId == speciesId }
        ) return@withPlayerLock SharedCaptureResult.DUPLICATE_SPECIES
        val next = progress.copy(captures = progress.captures + CycleCapture(
            pokemonId, speciesId, generation, clock.millis(),
        ))
        store.save(next)
        if (next.completed) SharedCaptureResult.COMPLETED else SharedCaptureResult.ADVANCED
    }

    private fun current(playerId: UUID, mission: SharedCaptureMission): SharedCaptureProgress? {
        val stored = store.load(playerId) ?: return null
        if (stored.missionId == mission.missionId) return stored
        require(stored.windowStartEpochMs < mission.windowStartEpochMs) {
            "stored mission is newer than the server clock"
        }
        store.archiveAndClear(stored)
        return null
    }
}

