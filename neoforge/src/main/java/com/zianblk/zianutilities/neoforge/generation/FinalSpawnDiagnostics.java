package com.zianblk.zianutilities.neoforge.generation;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.entity.SpawnEvent;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class FinalSpawnDiagnostics {
    private static final Logger LOGGER =
        LoggerFactory.getLogger("ZianUtilities/FinalSpawnDiagnostics");

    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static final boolean RUNTIME_TEST_LOG =
        Boolean.getBoolean("zianutilities.runtimeTestFinalBarrier");
    private static final SpawnSourceClassifier CLASSIFIER =
        new SpawnSourceClassifier();

    private FinalSpawnDiagnostics() {
    }

    public static void install() {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }

        CobblemonEvents.ENTITY_SPAWN.subscribe(
            Priority.LOWEST,
            (Consumer<SpawnEvent<?>>) FinalSpawnDiagnostics::onEntitySpawn
        );
    }

    private static void onEntitySpawn(SpawnEvent<?> rawEvent) {
        if (!(rawEvent.getEntity() instanceof PokemonEntity pokemon)) {
            return;
        }

        @SuppressWarnings("unchecked")
        SpawnEvent<PokemonEntity> event =
            (SpawnEvent<PokemonEntity>) rawEvent;

        SpawnSourceClassifier.ControlledSource source =
            CLASSIFIER.classify(event);

        if (!RUNTIME_TEST_LOG && !LOGGER.isDebugEnabled()) {
            return;
        }

        String species = pokemon.getPokemon()
            .getSpecies()
            .getResourceIdentifier()
            .toString();

        String sourceName = source == null ? "UNCONTROLLED" : source.name();
        String causeClass = event.getSpawnablePosition().getCause()
            .getClass().getName();
        String spawnerClass = event.getSpawnablePosition().getSpawner()
            .getClass().getName();

        if (RUNTIME_TEST_LOG) {
            LOGGER.info(
                "[ZIAN-RUNTIME] stage=FINAL source={} canceled={} species={} causeClass={} spawnerClass={}",
                sourceName,
                event.isCanceled(),
                species,
                causeClass,
                spawnerClass
            );
        } else {
            LOGGER.debug(
                "[ZIAN-SPAWN] stage=FINAL source={} canceled={} species={} causeClass={} spawnerClass={}",
                sourceName,
                event.isCanceled(),
                species,
                causeClass,
                spawnerClass
            );
        }
    }
}
