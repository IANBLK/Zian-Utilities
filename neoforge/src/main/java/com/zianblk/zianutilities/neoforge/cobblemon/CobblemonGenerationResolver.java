package com.zianblk.zianutilities.neoforge.cobblemon;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Species;
import com.zianblk.zianutilities.core.generation.Generation;
import com.zianblk.zianutilities.core.generation.GenerationOverride;
import com.zianblk.zianutilities.core.generation.GenerationOverrideStore;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class CobblemonGenerationResolver {
    private final GenerationOverrideStore overrideStore;
    private final Map<String, Set<Generation>> cache = new ConcurrentHashMap<>();

    public CobblemonGenerationResolver(GenerationOverrideStore overrideStore) {
        this.overrideStore = overrideStore;
    }

    public Set<Generation> resolve(Species species) {
        if (species == null) {
            return Set.of();
        }

        String speciesId = species.getResourceIdentifier().toString();
        Generation override = findOverride(speciesId);

        if (override != null) {
            return Set.of(override);
        }

        return cache.computeIfAbsent(
            speciesId,
            ignored -> CobblemonGenerationMapping.resolveLabels(species.getLabels())
        );
    }

    public Set<Generation> resolve(String speciesId) {
        if (speciesId == null || speciesId.isBlank()) {
            return Set.of();
        }

        ResourceLocation identifier = normalizeSpeciesIdentifier(speciesId);
        if (identifier == null) {
            return Set.of();
        }

        Species species = PokemonSpecies.INSTANCE.getByIdentifier(identifier);
        return species == null ? Set.of() : resolve(species);
    }

    static ResourceLocation normalizeSpeciesIdentifier(String speciesId) {
        if (speciesId == null || speciesId.isBlank()) {
            return null;
        }

        return speciesId.indexOf(':') >= 0
            ? ResourceLocation.tryParse(speciesId)
            : ResourceLocation.tryParse("cobblemon:" + speciesId);
    }

    public void clearCache() {
        cache.clear();
    }

    public int cachedSpeciesCount() {
        return cache.size();
    }

    private Generation findOverride(String speciesId) {
        if (overrideStore == null) {
            return null;
        }

        for (GenerationOverride override : overrideStore.all()) {
            if (speciesId.equals(override.getSpeciesId())) {
                return override.getGeneration();
            }
        }

        return null;
    }
}
