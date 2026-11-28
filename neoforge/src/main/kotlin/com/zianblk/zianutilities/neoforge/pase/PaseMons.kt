package com.zianblk.zianutilities.neoforge.pase

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.pokemon.Pokemon
import java.util.UUID
import kotlin.random.Random

/** Builds the Pokemon of a reward with a fixed id, so delivering it again after a crash can never create a second one. */
object PaseMons {
    private val STATS = listOf(Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED)

    /** ivs is -1 (random) or 0-31 for every stat; minPerfect stats chosen by the seed become 31. */
    @JvmStatic
    fun build(species: String, level: Int, ivs: Int, minPerfect: Int, alpha: Boolean, shiny: Boolean, id: UUID, seed: Long, trainer: UUID): Pokemon {
        val properties = PokemonProperties.parse("$species level=$level" + if (shiny) " shiny=yes" else " shiny=no")
        properties.isAlpha = null
        val pokemon = properties.create()
        properties.isAlpha = if (alpha) true else null
        properties.apply(pokemon)
        if (ivs >= 0) STATS.forEach { pokemon.ivs[it] = ivs }
        else if (minPerfect > 0) STATS.shuffled(Random(seed)).take(minPerfect).forEach { pokemon.ivs[it] = 31 }
        pokemon.uuid = id
        pokemon.setOriginalTrainer(trainer)
        return pokemon
    }
}
