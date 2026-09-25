package com.zianblk.zianutilities.neoforge.generation;

import com.cobblemon.mod.common.api.spawning.SpawnBucket;
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
            context -> List.of(new GenerationInfluence(context.getWorld().getServer()))
        );

        PokeSnackSpawnerFactory.INSTANCE.getInfluenceBuilders().add(
            context -> new GenerationInfluence(context.getWorld().getServer())
        );
    }

    private static final class GenerationInfluence implements SpawningInfluence {
        private final MinecraftServer server;
        private SpawnablePosition currentPosition;

        private GenerationInfluence(MinecraftServer server) {
            this.server = server;
        }

        @Override
        public void affectSpawnablePosition(SpawnablePosition spawnablePosition) {
            this.currentPosition = spawnablePosition;
        }

        @Override
        public void affectBucketWeights(Map<SpawnBucket, Float> bucketWeights) {
            SpawnablePosition position = currentPosition;
            if (position == null || bucketWeights.isEmpty()) {
                return;
            }

            for (Map.Entry<SpawnBucket, Float> entry : bucketWeights.entrySet()) {
                if (entry.getValue() <= 0.0F) {
                    continue;
                }

                boolean hasEligibleCandidate = position.getSpawner()
                    .getMatchingSpawns(entry.getKey(), position)
                    .stream()
                    .anyMatch(detail -> affectSpawnable(detail, position));

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
