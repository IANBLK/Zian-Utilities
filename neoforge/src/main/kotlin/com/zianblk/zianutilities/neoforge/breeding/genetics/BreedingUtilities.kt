package com.zianblk.zianutilities.neoforge.breeding.genetics

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.abilities.Abilities
import com.cobblemon.mod.common.api.moves.BenchedMove
import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.pokeball.PokeBalls
import com.cobblemon.mod.common.api.pokemon.Natures
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies.getByName
import com.cobblemon.mod.common.api.pokemon.egg.EggGroup
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.api.properties.CustomPokemonProperty
import com.cobblemon.mod.common.pokemon.*
import net.minecraft.world.item.Item
import java.util.*
import kotlin.random.Random
import kotlin.random.nextUInt

/** Adapted from Cobbreeding 2.4.0: MIT, Copyright 2023 Ludichat and Fuzuki. */
object BreedingUtilities {
    private val randomScope = ThreadLocal<Random>()
    private val rng: Random get() = randomScope.get() ?: Random.Default
    @JvmStatic fun generate(parents: List<Pokemon>, random: Random, allowed: java.util.function.Predicate<FormData>): PokemonProperties? {
        randomScope.set(random)
        try { return chooseEgg(getPossibleEggs(parents).filter { allowed.test(it.key) }) }
        finally { randomScope.remove() }
    }

    /**
     * Converts IVs to an integer array.
     * @return IVs as int array.
     */
    @JvmStatic
    fun IVs.toIntArray(): IntArray = setOf(
        Stats.HP,
        Stats.ATTACK,
        Stats.DEFENCE,
        Stats.SPECIAL_ATTACK,
        Stats.SPECIAL_DEFENCE,
        Stats.SPEED
    ).map { this[it] ?: 0 }.toIntArray()

    /**
     * Converts integer array to IVs.
     * @return int array as IVs.
     */
    @JvmStatic
    fun IntArray.toIVs(): IVs = IVs().apply {
        setOf(
            Stats.HP,
            Stats.ATTACK,
            Stats.DEFENCE,
            Stats.SPECIAL_ATTACK,
            Stats.SPECIAL_DEFENCE,
            Stats.SPEED
        ).zip(this@toIVs.toList()).forEach { (stat, iv) -> this[stat] = iv }
    }

    /**
     * Convert set of moves to int array
     * @return moves as int array.
     */
    @JvmStatic
    fun Set<MoveTemplate>.toIdArray(): IntArray = this.map { it.num }.toIntArray()

    /**
     * Convert set of moves to int array
     * @return moves as int array.
     */
    @JvmStatic
    fun IntArray.toMoves(): Set<MoveTemplate> = this.map { Moves.getByNumericalId(it)!! }.toSet()

    /**
     * Gets a random Pokémon from a pair of Pokémon.
     * @return Random Pokémon from the pair.
     */
    private fun Pair<Pokemon, Pokemon>.random(random: Random): Pokemon = if (rng.nextInt(2) == 0) first else second

    /**
     * Choose an egg from the possible eggs in the pasture.
     * @return Integer array of the Pokédex number and the computed IVs for the egg.
     */
    @JvmStatic
    fun chooseEgg(eggs: Collection<Map.Entry<FormData, List<Pair<Pokemon, Pokemon>>>>): PokemonProperties? {
        return if (eggs.isNotEmpty()) {
            val entry = eggs.random(rng)
            val properties = PokemonProperties()

            val form = entry.key

            val couples = entry.value
            val parents = couples.random(rng)

            if (parents.first.form.eggGroups.contains(EggGroup.DITTO) && parents.second.form.eggGroups.contains(EggGroup.DITTO))
            {
                properties.species = "random"
            }
            else
            {
                val eggMoves = calcEggMoves(parents, form)
                val ability = calcAbility(form, parents)

                properties.species = form.species.showdownId()
                properties.form = form.formOnlyShowdownId()
                properties.moves = eggMoves
                properties.ability = ability
            }

            properties.ivs = calcStats(parents)
            properties.nature = calcNature(parents).name.toString()
            properties.pokeball = calcBall(parents)
            properties.shiny = calcShiny(parents)
            calcFeatures(parents, properties)
            properties.isAlpha = calcAlpha(parents)

            properties
        } else {
            null
        }
    }

    /**
     * Choose an egg from the possible eggs in the pasture.
     * @return Integer array of the Pokédex number and the computed IVs for the egg.
     */
    @JvmStatic
    fun chooseEgg(pokemon: List<Pokemon?>): PokemonProperties? {
        val eggs = getPossibleEggs(pokemon.filterNotNull())
        return chooseEgg(eggs)
    }

    /**
     * @return Collection of all currently possible eggs in the pasture, mapping species to possible parents.
     */
    @JvmStatic
    fun getPossibleEggs(pokemon: List<Pokemon>): Collection<Map.Entry<FormData, List<Pair<Pokemon, Pokemon>>>> {
        val eggs = HashMap<FormData, MutableList<Pair<Pokemon, Pokemon>>>()

        for (i in pokemon.indices) {
            val pokemon1 = pokemon[i]
            val eggGroups1 = pokemon1.form.eggGroups

            // Skipping the Pokémon if it's been set unbreedable
            if (pokemon1.persistentData.getBoolean("neutered"))
                continue

            for (j in i + 1 until pokemon.size) {
                val pokemon2 = pokemon[j]
                val eggGroups2 = pokemon2.form.eggGroups

                // Skipping the Pokémon if it's been set unbreedable
                if (pokemon2.persistentData.getBoolean("neutered"))
                    continue

                // Undiscovered egg group checks
                if (!eggGroups1.contains(EggGroup.UNDISCOVERED) && !eggGroups2.contains(EggGroup.UNDISCOVERED)) {
                    // Ditto checks
                    if (eggGroups1.contains(EggGroup.DITTO)) {
                        dittoBreed(pokemon2)?.let {
                            if (eggs.containsKey(it)) eggs[it]?.add(Pair(pokemon2, pokemon1))
                            else eggs[it] = mutableListOf(Pair(pokemon2, pokemon1))
                        }
                    } else if (eggGroups2.contains(EggGroup.DITTO)) {
                        dittoBreed(pokemon1)?.let {
                            if (eggs.containsKey(it)) eggs[it]?.add(Pair(pokemon1, pokemon2))
                            else eggs[it] = mutableListOf(Pair(pokemon1, pokemon2))
                        }
                    }
                    // Normal checks
                    else if (eggGroups1.any(eggGroups2::contains)) {
                        if (pokemon1.gender == Gender.FEMALE && pokemon2.gender == Gender.MALE) {
                            getBaby(pokemon1.form).let {
                                if (eggs.containsKey(it)) eggs[it]?.add(Pair(pokemon2, pokemon1))
                                else eggs[it] = mutableListOf(Pair(pokemon2, pokemon1))
                            }
                        } else if (pokemon2.gender == Gender.FEMALE && pokemon1.gender == Gender.MALE) {
                            getBaby(pokemon2.form).let {
                                if (eggs.containsKey(it)) eggs[it]?.add(Pair(pokemon1, pokemon2))
                                else eggs[it] = mutableListOf(Pair(pokemon1, pokemon2))
                            }
                        }
                    }
                }
            }
        }

        // Nidoran family and Volbeat/Illumise produce both male and female eggs despite being different Pokémon
        // If the egg list contains a female Nidoran, we must add male Nidoran to it, and the other way around
        @Suppress("SpellCheckingInspection")
        if (eggs.containsKey(getByName("nidoranf")!!.standardForm)) {
            eggs[getByName("nidoranm")!!.standardForm] = eggs[getByName("nidoranf")!!.standardForm]!!
        } else if (eggs.containsKey(getByName("nidoranm")!!.standardForm)) {
            // Avoiding adding female Nidoran if we just added male Nidoran due to the previous condition
            eggs[getByName("nidoranf")!!.standardForm] = eggs[getByName("nidoranm")!!.standardForm]!!
        }

        // Doing the same thing for Volbeat and Illumise
        if (eggs.containsKey(getByName("illumise")!!.standardForm)) {
            eggs[getByName("volbeat")!!.standardForm] = eggs[getByName("illumise")!!.standardForm]!!
        } else if (eggs.containsKey(getByName("volbeat")!!.standardForm)) {
            eggs[getByName("illumise")!!.standardForm] = eggs[getByName("volbeat")!!.standardForm]!!
        }

        return eggs.mapValues { (_, v) -> v.toList() }.entries
    }

    private fun dittoBreed(other: Pokemon): FormData? {
        // Ditto + Manapy = Phione
        if (other.species == getByName("manaphy"))
            return getByName("phione")!!.standardForm

        // The other pokémon is a Ditto
        if (other.form.eggGroups.contains(EggGroup.DITTO))
        {
            // Two Ditto are allowed to breed
            if (GeneticsSettings.dittoAndDittoRandomEgg)
            {
                // It doesn't matter what is returned here
                // The actual Pokémon will be calculated when the egg hatches
                return PokemonSpecies.random().standardForm
            }
            return null
        }

        return getBaby(other.form)
    }

    fun getBaby(pokemon: FormData): FormData {
        // Changed how this works until Cobblemon fixes getting the right form of pre-evolutions
        var species = pokemon.species
         while (species.preEvolution != null)
         {
             species = species.preEvolution!!.species
         }

        // regional-exclusive evolution edge case
        if (pokemon.species.name == "Sirfetch’d" ||
            pokemon.species.name == "Cursola" ||
            pokemon.species.name == "Obstagoon" ||
            pokemon.species.name == "Runerigus" ||
            pokemon.species.name == "Clodsire" ||
            pokemon.species.name == "Overqwil" ||
            pokemon.species.name == "Sneasler")
            return species.forms[1]
        // special condition for Basculegion
        if (pokemon.species.name == "Perrserker" ||
            pokemon.species.name == "Basculegion")
            return species.forms[2]

        return species.forms.find { it.formOnlyShowdownId().contains(pokemon.formOnlyShowdownId()) } ?:
               species.standardForm

        //var baby = pokemon.form

        //while (baby.preEvolution != null) {
        //    baby = baby.preEvolution!!.form
        //}

        //return baby
    }

    private fun calcStats(parents: Pair<Pokemon, Pokemon>): IVs {
        val finalStats = IVs().apply { listOf(Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED).forEach { this[it] = rng.nextInt(32) } }
        val (father, mother) = parents
        val ivs = mutableSetOf(
            Stats.HP,
            Stats.ATTACK,
            Stats.DEFENCE,
            Stats.SPECIAL_ATTACK,
            Stats.SPECIAL_DEFENCE,
            Stats.SPEED
        )

        // First transferred IV
        // Check parents for power items
        val ivsToChooseFrom = mapOf(
            Pair(father, powerItemToIV(father.heldItem().item)),
            Pair(mother, powerItemToIV(mother.heldItem().item))
        ).filterNot { it.value == null }

        var iv: Stats
        var parent: Pokemon
        if (ivsToChooseFrom.isEmpty()) {
            // No power item
            iv = ivs.random(rng)
            parent = parents.random(rng)
        } else {
            val (pokemon, stat) = ivsToChooseFrom.toList().random(rng)
            iv = stat!!
            parent = pokemon
        }

        parent.ivs[iv]?.let { finalStats[iv] = it }
        ivs.remove(iv)

        var ivCount =
            if (father.heldItem().item == CobblemonItems.DESTINY_KNOT || mother.heldItem().item == CobblemonItems.DESTINY_KNOT) 4
            else 2
        while (ivCount != 0) {
            iv = ivs.random(rng)
            parent = parents.random(rng)
            parent.ivs[iv]?.let { finalStats[iv] = it }

            ivs.remove(iv)

            ivCount--
        }

        return finalStats
    }

    private fun powerItemToIV(item: Item): Stats? = when (item) {
        CobblemonItems.POWER_WEIGHT -> Stats.HP
        CobblemonItems.POWER_BRACER -> Stats.ATTACK
        CobblemonItems.POWER_BELT -> Stats.DEFENCE
        CobblemonItems.POWER_LENS -> Stats.SPECIAL_ATTACK
        CobblemonItems.POWER_BAND -> Stats.SPECIAL_DEFENCE
        CobblemonItems.POWER_ANKLET -> Stats.SPEED
        // If the parent isn't holding an item, return null to use in logic
        else -> null
    }

    private fun calcNature(parents: Pair<Pokemon, Pokemon>): Nature {
        val (father, mother) = parents
        val parentsItems = mapOf(
            Pair(father, father.heldItem().item),
            Pair(mother, mother.heldItem().item)
        ).filterNot { it.value != CobblemonItems.EVERSTONE }

        if (parentsItems.isEmpty())
            return Natures.all().random(rng)
        return parentsItems.toList().random(rng).first.nature
    }

    private fun calcEggMoves(parents: Pair<Pokemon, Pokemon>, child: FormData): List<String> {
        val possibleEggMoves = child.moves.eggMoves
        val eggMoves = LinkedList<String>()

        for (parent in parents.toList()) {
            val allAccessibleMoves = parent.allAccessibleMoves.toMutableSet()
            for (move in parent.moveSet.getMoves())
                allAccessibleMoves.add(move.template)
            for (accessibleMove in allAccessibleMoves) {
                if (possibleEggMoves.contains(accessibleMove))
                    eggMoves.add(accessibleMove.name)
            }
        }

        @Suppress("SpellCheckingInspection")
        if (child.species.name == "Pichu" && parents.toList().any { p -> p.heldItem().item == CobblemonItems.LIGHT_BALL })
            eggMoves.add("volttackle")

        return eggMoves
    }

    private fun calcAbility(form: FormData, parents: Pair<Pokemon, Pokemon>): String {
        val (father, mother) = parents

        // Get parent to pass ability down (either mother or non-ditto parent).
        val ancestor =
            if (mother.species == getByName("ditto")) father
            else mother
        val oldAbility = ancestor.ability

        // Keep old ability if it was forced, otherwise find ability.
        // If forced abilities are disabled in the config, then ignore the forced ability and do as if it wasn't forced.
        if (oldAbility.forced && GeneticsSettings.forcedAbilitiesEnabled)
            return oldAbility.name

        // Get priority and index. If not defined, look them up.
        val (priority, index) = if (oldAbility.index >= 0) Pair(oldAbility.priority, oldAbility.index)
        else {
            // If the indices can't be found here for some reason, default to the 1st common ability.
            val entry = ancestor.form.abilities.mapping.entries.firstOrNull { (_, abilities) ->
                abilities.map { it.template }.contains(oldAbility.template)
            }
            entry?.run {
                Pair(key, value.map { it.template }.indexOf(oldAbility.template))
            } ?: Pair(Priority.LOWEST, 0)
        }

        // Get proper ability and other abilities.
        // If the index can't be found, it's likely an evolved parent having kept index 1 despite only having one ability option.
        // If hidden abilities are disabled by config, then filter them out of the remaining abilities.
        val inheritedAbility = form.abilities.mapping[priority]?.getOrNull(index)?.template ?: // The same ability slot
                               form.abilities.mapping[priority]?.firstOrNull()?.template ?:  // If it can't be found, the first slot of the same priority
                               form.abilities.mapping[Priority.LOWEST]?.firstOrNull()?.template ?:  // If it can't be found, the first slot of the lowest priority
                               Abilities.DUMMY  // Basically the worst case scenario
        val remainingAbilities =
            if (GeneticsSettings.hiddenAbilitiesEnabled)
                form.abilities.mapping.values
                    .flatten()
                    .map { it.template }
                    .filterNot { it == inheritedAbility }
            else
                form.abilities.mapping.values
                    .flatten()
                    .filterNot { it.priority == Priority.LOW }
                    .map { it.template }
                    .filterNot { it == inheritedAbility }

        val chance = when (priority) {
            // Keep hidden ability: 60%
            Priority.LOW -> 6u
            // Keep other ability: 80%
            else -> 8u
        }

        // Ferroseed has the same A and HA so without this, it would always get its HA regardless of inheritance
        if (form.species.name.lowercase() == "ferroseed")
        {
            val random = rng.nextUInt(10u)
            if (random < chance && priority != Priority.LOW || random >= chance && priority == Priority.LOW)
            {
                return ""
            }
        }

        // Select if the inherited ability should be kept or choose another one.
        val template = if (remainingAbilities.isEmpty() || rng.nextUInt(10u) < chance) inheritedAbility
        else remainingAbilities.random(rng)

        // Create the ability as not forced.
        return template.name
    }

    private fun calcBall(parents: Pair<Pokemon, Pokemon>) : String
    {
        var ball = parents.second.caughtBall // Mother ball by default
        // If both parents are of the same species, the child inherit from either one at random
        if (parents.first.species.name == parents.second.species.name)
        ball =  parents.random(rng).caughtBall
        // Not inheriting ball from Ditto
        if (parents.second.species.name == "Ditto")
            ball =  parents.first.caughtBall

        // Cherish Ball, Master Ball, and Strange Ball (unimplemented) count as regular pokeball
        if (ball == PokeBalls.CHERISH_BALL || ball == PokeBalls.MASTER_BALL)
            ball = PokeBalls.POKE_BALL
        return ball.name.toString()
    }

    private fun calcShiny(parents: Pair<Pokemon, Pokemon>): Boolean?
    {
        val shinyMethods = GeneticsSettings.shinyMethod
        var shinyOdds = Cobblemon.config.shinyRate

        try
        {
            if ("always" in shinyMethods)
            {
                shinyOdds /= shinyMethods["always"]!!
            }
            if ("crystal" in shinyMethods)
            {
                if (parents.first.shiny)
                    shinyOdds /= shinyMethods["crystal"]!!
                if (parents.second.shiny)
                    shinyOdds /= shinyMethods["crystal"]!!
            }
            if ("masuda" in shinyMethods)
            {
                val owner1 = if (parents.first.originalTrainer != null) parents.first.originalTrainer else parents.first.getOwnerPlayer()?.stringUUID
                val owner2 = if (parents.second.originalTrainer != null) parents.second.originalTrainer else parents.second.getOwnerPlayer()?.stringUUID
                if (owner1 != owner2)
                    shinyOdds /= shinyMethods["masuda"]!!
            }
        }
        catch (_: ArithmeticException) { return false }

        return if (shinyOdds < 1) true else rng.nextInt(0, shinyOdds.toInt()) == 0
    }

    private fun calcFeatures(parents: Pair<Pokemon, Pokemon>, properties: PokemonProperties)
    {
        val mother = if (parents.second.species == getByName("ditto")) parents.first else parents.second
        val features = mother.features  // inheriting aspects from the mother

        for (feature in features)
        {
            // Allowing only certain aspects to pass
            if (feature.name in GeneticsSettings.inheritedFeatures)
            {
                if (feature is CustomPokemonProperty)
                {
                    properties.customProperties.add(feature)
                }
            }
        }
    }

    private fun calcAlpha(parents: Pair<Pokemon, Pokemon>): Boolean?
    {
        if(GeneticsSettings.inheritAlpha)
        {
            val mother = if (parents.second.species == getByName("ditto")) parents.first else parents.second
            if (mother.isAlpha)
            {
                return true
            }
        }
        return null
    }
}
