package com.zianblk.zianutilities.neoforge.generation;

import com.cobblemon.mod.common.api.spawning.BestSpawner;
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail;
import com.cobblemon.mod.common.api.spawning.influence.SpawningInfluence;
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition;
import com.cobblemon.mod.common.api.spawning.spawner.FishingSpawnerFactory;
import com.cobblemon.mod.common.api.spawning.spawner.PokeSnackSpawnerFactory;
import com.zianblk.zianutilities.core.generation.Generation;
import com.zianblk.zianutilities.core.generation.GenerationState;
import com.zianblk.zianutilities.core.generation.SpawnDecision;
import com.zianblk.zianutilities.neoforge.cobblemon.CobblemonGenerationResolver;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Filters disabled generations before Cobblemon performs its weighted encounter
 * selection for fishing and Poke Snacks.
 *
 * <p>The PRE guards remain installed as a final enforcement barrier. This
 * influence is only the early candidate filter, so it never spawns entities,
 * rerolls encounters, or replaces Cobblemon spawn pools.</p>
 */
public final class GenerationPreselectionFilter {
    private static final Logger LOGGER =
        LoggerFactory.getLogger("ZianUtilities/GenerationPreselectionFilter");
    private static final boolean RUNTIME_TEST_LOG =
        Boolean.getBoolean("zianutilities.runtimeTestPreselection");
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static final CobblemonGenerationResolver RESOLVER =
        new CobblemonGenerationResolver(null);

    private GenerationPreselectionFilter() {
    }

    public static void install() {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }

        FishingSpawnerFactory.INSTANCE.getPositionInfluenceBuilders().add(
            context -> List.of(new GenerationInfluence(context.getWorld().getServer(), true))
        );

        PokeSnackSpawnerFactory.INSTANCE.getInfluenceBuilders().add(
            context -> new GenerationInfluence(context.getWorld().getServer(), true)
        );
    }

    private static final class GenerationInfluence implements SpawningInfluence {
        private final MinecraftServer server;
        private final boolean renormalizeBuckets;

        private GenerationInfluence(MinecraftServer server, boolean renormalizeBuckets) {
            this.server = server;
            this.renormalizeBuckets = renormalizeBuckets;
        }

        @Override
        public void affectBucketWeights(Map<String, Float> bucketWeights) {
            if (!renormalizeBuckets || bucketWeights.isEmpty()) {
                return;
            }

            GenerationState state = new NeoForgeGenerationStateStore(server).load();
            for (Map.Entry<String, Float> entry : bucketWeights.entrySet()) {
                if (entry.getValue() <= 0.0F) {
                    continue;
                }

                boolean hasEligibleCandidate = BestSpawner.INSTANCE.getFishingSpawner()
                    .getSpawnPool()
                    .getDetails()
                    .stream()
                    .filter(detail -> entry.getKey().equals(detail.getBucket()))
                    .filter(PokemonSpawnDetail.class::isInstance)
                    .map(PokemonSpawnDetail.class::cast)
                    .map(detail -> detail.getPokemon().getSpecies())
                    .filter(speciesId -> speciesId != null && !speciesId.isBlank())
                    .map(RESOLVER::resolve)
                    .anyMatch(resolved -> GenerationEnforcement.decide(resolved, state) instanceof SpawnDecision.Allow);

                if (!hasEligibleCandidate) {
                    entry.setValue(0.0F);
                }
            }

            float total = bucketWeights.values().stream()
                .filter(weight -> weight > 0.0F)
                .reduce(0.0F, Float::sum);

            if (total > 0.0F) {
                bucketWeights.replaceAll((bucket, weight) ->
                    weight <= 0.0F ? 0.0F : (weight / total) * 100.0F
                );
            }
        }

        @Override
        public boolean affectSpawnable(SpawnDetail detail, SpawnablePosition spawnablePosition) {
            if (!(detail instanceof PokemonSpawnDetail pokemonDetail)) {
                return true;
            }

            String speciesId = pokemonDetail.getPokemon().getSpecies();
            if (speciesId == null || speciesId.isBlank()) {
                // Unknown Pokemon details follow the same fail-closed policy used
                // by the final fishing/snack guards.
                if (RUNTIME_TEST_LOG) {
                    LOGGER.info("[ZIAN-RUNTIME] stage=PRESELECTION decision=DENY reason=missing_species");
                }
                return false;
            }

            Set<Generation> resolved = RESOLVER.resolve(speciesId);

            GenerationState state = new NeoForgeGenerationStateStore(server).load();

            SpawnDecision decision = GenerationEnforcement.decide(resolved, state);

            boolean allowed = decision instanceof SpawnDecision.Allow;
            if (RUNTIME_TEST_LOG) {
                LOGGER.info(
                    "[ZIAN-RUNTIME] stage=PRESELECTION decision={} species={} generations={} active={}",
                    allowed ? "ALLOW" : "DENY",
                    speciesId,
                    resolved,
                    state.getEnabled()
                );
            }
            return allowed;
        }
    }
}
