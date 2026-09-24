package com.zianblk.zianutilities.neoforge.generation;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.entity.SpawnEvent;
import com.cobblemon.mod.common.api.spawning.spawner.PlayerSpawner;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.zianblk.zianutilities.core.generation.Generation;
import com.zianblk.zianutilities.core.generation.GenerationPolicy;
import com.zianblk.zianutilities.core.generation.GenerationState;
import com.zianblk.zianutilities.core.generation.SpawnDecision;
import com.zianblk.zianutilities.core.generation.UnknownSpeciesPolicy;
import com.zianblk.zianutilities.neoforge.cobblemon.CobblemonGenerationResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class NaturalSpawnGuard {
    private static final Logger LOGGER =
        LoggerFactory.getLogger("ZianUtilities/NaturalSpawnGuard");

    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static final boolean RUNTIME_TEST_LOG =
        Boolean.getBoolean("zianutilities.runtimeTestNatural");

    private static final CobblemonGenerationResolver RESOLVER =
        new CobblemonGenerationResolver(null);

    private NaturalSpawnGuard() {
    }

    public static void install() {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }

        CobblemonEvents.ENTITY_SPAWN.subscribe(
            Priority.HIGHEST,
            (Consumer<SpawnEvent<?>>) NaturalSpawnGuard::onEntitySpawn
        );
    }

    static boolean isControlledNaturalSpawner(Object spawner) {
        return spawner instanceof PlayerSpawner;
    }

    private static void onEntitySpawn(SpawnEvent<?> event) {
        if (event.isCanceled()) {
            return;
        }

        if (!(event.getEntity() instanceof PokemonEntity pokemonEntity)) {
            return;
        }

        if (!isControlledNaturalSpawner(event.getSpawner())) {
            return;
        }

        Set<Generation> resolved =
            RESOLVER.resolve(pokemonEntity.getPokemon().getSpecies());

        GenerationState state =
            new NeoForgeGenerationStateStore(
                pokemonEntity.getServer()
            ).load();

        SpawnDecision decision = GenerationPolicy.INSTANCE.decide(
            resolved,
            state,
            null,
            UnknownSpeciesPolicy.DENY
        );

        String speciesId = pokemonEntity.getPokemon()
            .getSpecies()
            .getResourceIdentifier()
            .toString();

        if (decision instanceof SpawnDecision.Deny deny) {
            if (RUNTIME_TEST_LOG) {
                LOGGER.info(
                    "[ZIAN-RUNTIME] source=NATURAL decision=DENY species={} generations={} active={} reason={}",
                    speciesId,
                    resolved,
                    state.getEnabled(),
                    deny.getReason()
                );
            } else if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                    "[ZIAN-SPAWN] source=NATURAL species={} generations={} reason={}",
                    speciesId,
                    resolved,
                    deny.getReason()
                );
            }

            event.cancel();
            return;
        }

        if (RUNTIME_TEST_LOG) {
            LOGGER.info(
                "[ZIAN-RUNTIME] source=NATURAL decision=ALLOW species={} generations={} active={}",
                speciesId,
                resolved,
                state.getEnabled()
            );
        }
    }

    static CobblemonGenerationResolver resolverForTests() {
        return RESOLVER;
    }
}
