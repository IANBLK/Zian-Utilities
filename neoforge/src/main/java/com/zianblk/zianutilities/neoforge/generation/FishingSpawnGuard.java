package com.zianblk.zianutilities.neoforge.generation;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.fishing.BobberSpawnPokemonEvent;
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnAction;
import com.cobblemon.mod.common.api.spawning.detail.SpawnAction;
import com.zianblk.zianutilities.core.generation.Generation;
import com.zianblk.zianutilities.core.generation.GenerationState;
import com.zianblk.zianutilities.core.generation.SpawnDecision;
import com.zianblk.zianutilities.neoforge.cobblemon.CobblemonGenerationResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class FishingSpawnGuard {
    private static final Logger LOGGER =
        LoggerFactory.getLogger("ZianUtilities/FishingSpawnGuard");

    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static final boolean RUNTIME_TEST_LOG =
        Boolean.getBoolean("zianutilities.runtimeTestFishing");

    private static final CobblemonGenerationResolver RESOLVER =
        new CobblemonGenerationResolver(null);

    private FishingSpawnGuard() {
    }

    public static void install() {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }

        CobblemonEvents.BOBBER_SPAWN_POKEMON_PRE.subscribe(
            Priority.HIGHEST,
            (Consumer<BobberSpawnPokemonEvent.Pre>) FishingSpawnGuard::onFishingPre
        );
    }

    private static void onFishingPre(BobberSpawnPokemonEvent.Pre event) {
        if (event.isCanceled()) {
            return;
        }

        String speciesId = plannedSpeciesId(event.getSpawnAction());
        if (speciesId == null) {
            if (RUNTIME_TEST_LOG) {
                LOGGER.info(
                    "[ZIAN-RUNTIME] source=FISHING decision=SKIP reason=unclassified_spawn_action actionClass={}",
                    event.getSpawnAction().getClass().getName()
                );
            }
            return;
        }

        Set<Generation> resolved = RESOLVER.resolve(speciesId);
        GenerationState state =
            new NeoForgeGenerationStateStore(event.getBobber().getServer()).load();

        SpawnDecision decision = GenerationEnforcement.decide(resolved, state);

        if (decision instanceof SpawnDecision.Deny deny) {
            if (RUNTIME_TEST_LOG) {
                LOGGER.info(
                    "[ZIAN-RUNTIME] source=FISHING decision=DENY species={} generations={} active={} reason={}",
                    speciesId,
                    resolved,
                    state.getEnabled(),
                    deny.getReason()
                );
            } else if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                    "[ZIAN-SPAWN] source=FISHING species={} generations={} reason={}",
                    speciesId,
                    resolved,
                    deny.getReason()
                );
            }

            // Cobblemon 1.8.1 returns immediately from PokeRodFishingBobberEntity#retrieve
            // when BOBBER_SPAWN_POKEMON_PRE is cancelled. That early return happens before
            // the normal bobber discard at the end of retrieve(), leaving the rod visually
            // stuck in a cast state. Close the denied attempt explicitly.
            event.cancel();
            event.getBobber().setCast(false);
            event.getBobber().discard();

            if (RUNTIME_TEST_LOG) {
                LOGGER.info(
                    "[ZIAN-RUNTIME] source=FISHING cleanup=BOBBER_DISCARDED species={} bobber={}",
                    speciesId,
                    event.getBobber().getUUID()
                );
            }
            return;
        }

        if (RUNTIME_TEST_LOG) {
            LOGGER.info(
                "[ZIAN-RUNTIME] source=FISHING decision=ALLOW species={} generations={} active={}",
                speciesId,
                resolved,
                state.getEnabled()
            );
        }
    }

    static String plannedSpeciesId(SpawnAction<?> action) {
        if (!(action instanceof PokemonSpawnAction pokemonAction)) {
            return null;
        }

        String species = pokemonAction.getProps().getSpecies();
        if (species == null || species.isBlank()) {
            return null;
        }

        return species;
    }

    static boolean isControlledFishingAction(SpawnAction<?> action) {
        return action instanceof PokemonSpawnAction
            && plannedSpeciesId(action) != null;
    }
}
