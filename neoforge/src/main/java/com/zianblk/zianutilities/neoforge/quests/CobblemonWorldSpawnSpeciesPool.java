package com.zianblk.zianutilities.neoforge.quests;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.spawning.CobblemonSpawnPools;
import com.cobblemon.mod.common.api.spawning.detail.PokemonHerdSpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail;
import com.cobblemon.mod.common.pokemon.Species;
import com.zianblk.zianutilities.core.generation.Generation;
import com.zianblk.zianutilities.neoforge.cobblemon.CobblemonGenerationResolver;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Species that Cobblemon can spawn naturally in at least one server biome. */
final class CobblemonWorldSpawnSpeciesPool {
    private CobblemonWorldSpawnSpeciesPool() {}

    static List<String> eligible(Set<Generation> enabled, CobblemonGenerationResolver resolver) {
        if (enabled.isEmpty()) return List.of();
        Set<String> result = new TreeSet<>();
        for (var detail : CobblemonSpawnPools.INSTANCE.getWORLD_SPAWN_POOL().getDetails()) {
            if (detail.getValidBiomes().isEmpty()) continue;
            if (detail instanceof PokemonSpawnDetail pokemon) {
                add(pokemon.getPokemon().getSpecies(), enabled, resolver, result);
            } else if (detail instanceof PokemonHerdSpawnDetail herd) {
                for (var member : herd.getHerdablePokemon()) {
                    add(member.getPokemon().getSpecies(), enabled, resolver, result);
                }
            }
        }
        return List.copyOf(result);
    }

    private static void add(String name, Set<Generation> enabled,
                            CobblemonGenerationResolver resolver, Set<String> result) {
        if (name == null || name.isBlank() || name.equalsIgnoreCase("random")) return;
        ResourceLocation id = ResourceLocation.tryParse(name.contains(":") ? name : "cobblemon:" + name);
        if (id == null) return;
        Species species = PokemonSpecies.INSTANCE.getByIdentifier(id);
        if (species == null || !species.getImplemented()) return;
        if (resolver.resolve(species).stream().noneMatch(enabled::contains)) return;
        result.add(species.getResourceIdentifier().toString());
    }
}
