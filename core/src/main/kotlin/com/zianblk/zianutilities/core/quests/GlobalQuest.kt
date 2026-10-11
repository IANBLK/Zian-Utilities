package com.zianblk.zianutilities.core.quests

import com.zianblk.zianutilities.core.generation.Generation
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.util.UUID

/** A single offer is calculated for the server, while only accepted progress is stored per player. */
data class GlobalQuestOffer(
    val windowStartEpochMs: Long,
    val targetSpecies: String?,
    val generationIds: String,
    val captureCurrency: String,
    val captureAmount: Long,
    val battleCurrency: String,
    val battleAmount: Long,
    val rewardsEnabled: Boolean,
) {
    val expiresAtEpochMs: Long get() = windowStartEpochMs + SharedCaptureMission.DURATION_MS
    val id: UUID get() = UUID.nameUUIDFromBytes(
        "zianutilities:global_quest_v1:$windowStartEpochMs:$generationIds:$targetSpecies"
            .toByteArray(StandardCharsets.UTF_8)
    )
    init {
        require(windowStartEpochMs == SharedCaptureSchedule.at(windowStartEpochMs).windowStartEpochMs)
        require(captureCurrency.isNotBlank() && battleCurrency.isNotBlank())
        require(captureAmount > 0 && battleAmount > 0)
    }
}

data class GlobalQuestProgress(
    val offer: GlobalQuestOffer,
    val playerId: UUID,
    val acceptedAtEpochMs: Long,
    val capturedPokemonId: UUID? = null,
    val battleIds: List<UUID> = emptyList(),
) {
    init {
        require(acceptedAtEpochMs in offer.windowStartEpochMs until offer.expiresAtEpochMs)
        require(battleIds.size <= 5 && battleIds.distinct().size == battleIds.size)
    }
    val captureComplete: Boolean get() = capturedPokemonId != null
    val battleComplete: Boolean get() = battleIds.size == 5
}

interface GlobalQuestProgressStore {
    fun <T> withPlayerLock(playerId: UUID, action: () -> T): T
    fun load(playerId: UUID): GlobalQuestProgress?
    fun save(progress: GlobalQuestProgress)
    fun archiveAndClear(progress: GlobalQuestProgress)
    fun loadPrevious(playerId: UUID): GlobalQuestProgress?
}

enum class GlobalQuestEventResult { NOT_ACCEPTED, EXPIRED, PAUSED, WRONG_SPECIES, DUPLICATE, COMPLETE, ADVANCED }

class GlobalQuestService @JvmOverloads constructor(
    private val store: GlobalQuestProgressStore,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun inspect(playerId: UUID, offer: GlobalQuestOffer): GlobalQuestProgress? =
        store.withPlayerLock(playerId) { current(playerId, offer) }

    fun accept(playerId: UUID, offer: GlobalQuestOffer): GlobalQuestProgress =
        store.withPlayerLock(playerId) {
            current(playerId, offer) ?: GlobalQuestProgress(offer, playerId, clock.millis()).also(store::save)
        }

    fun capture(
        playerId: UUID, offer: GlobalQuestOffer, pokemonId: UUID,
        speciesId: String, resolved: Set<Generation>, enabled: Set<Generation>,
    ): GlobalQuestEventResult = store.withPlayerLock(playerId) {
        val progress = current(playerId, offer) ?: return@withPlayerLock GlobalQuestEventResult.NOT_ACCEPTED
        if (progress.captureComplete) return@withPlayerLock GlobalQuestEventResult.DUPLICATE
        if (offer.targetSpecies == null || enabled.isEmpty()) return@withPlayerLock GlobalQuestEventResult.PAUSED
        if (speciesId != offer.targetSpecies || resolved.intersect(enabled).isEmpty()) {
            return@withPlayerLock GlobalQuestEventResult.WRONG_SPECIES
        }
        store.save(progress.copy(capturedPokemonId = pokemonId))
        GlobalQuestEventResult.COMPLETE
    }

    fun wildVictory(playerId: UUID, offer: GlobalQuestOffer, battleId: UUID): GlobalQuestEventResult =
        store.withPlayerLock(playerId) {
            val progress = current(playerId, offer) ?: return@withPlayerLock GlobalQuestEventResult.NOT_ACCEPTED
            if (battleId in progress.battleIds || progress.battleComplete) {
                return@withPlayerLock GlobalQuestEventResult.DUPLICATE
            }
            val next = progress.copy(battleIds = progress.battleIds + battleId)
            store.save(next)
            if (next.battleComplete) GlobalQuestEventResult.COMPLETE else GlobalQuestEventResult.ADVANCED
        }

    private fun current(playerId: UUID, offer: GlobalQuestOffer): GlobalQuestProgress? {
        val now = clock.millis()
        if (now !in offer.windowStartEpochMs until offer.expiresAtEpochMs) return null
        val saved = store.load(playerId) ?: return null
        if (saved.offer.id == offer.id) return saved
        require(saved.offer.windowStartEpochMs <= offer.windowStartEpochMs) { "server clock moved backwards" }
        store.archiveAndClear(saved)
        return null
    }
}

