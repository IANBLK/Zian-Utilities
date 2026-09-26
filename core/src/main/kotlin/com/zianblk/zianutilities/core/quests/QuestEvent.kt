package com.zianblk.zianutilities.core.quests

import com.zianblk.zianutilities.core.generation.Generation
import java.util.UUID

/** Adapters resolve Cobblemon species once and pass canonical events into quest core. */
sealed interface QuestEvent {
    val eventId: UUID
    val playerId: UUID
    val speciesId: String
    val generations: Set<Generation>

    data class PokemonCaptured(
        override val eventId: UUID,
        override val playerId: UUID,
        override val speciesId: String,
        override val generations: Set<Generation>,
    ) : QuestEvent {
        init {
            require(speciesId.isNotBlank()) { "speciesId must not be blank" }
        }
    }

    data class PokemonEvolved(
        override val eventId: UUID,
        override val playerId: UUID,
        override val speciesId: String,
        override val generations: Set<Generation>,
    ) : QuestEvent {
        init {
            require(speciesId.isNotBlank()) { "speciesId must not be blank" }
        }
    }
}

