package com.zianblk.zianutilities.neoforge.quests;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.zianblk.zianutilities.core.generation.Generation;
import com.zianblk.zianutilities.neoforge.cobblemon.CobblemonGenerationResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Opt-in, read-only Cobblemon capture probe for the Youer test server. */
public final class CaptureEventProbe {
    private static final Logger LOGGER = LoggerFactory.getLogger("ZianUtilities/CaptureEventProbe");
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static final String TEST_FLAG = "zianutilities.questCaptureProbeEnabled";
    private static final CobblemonGenerationResolver RESOLVER =
        new CobblemonGenerationResolver(null);

    private CaptureEventProbe() {
    }

    public static void install() {
        if (!Boolean.getBoolean(TEST_FLAG) || !INSTALLED.compareAndSet(false, true)) {
            return;
        }

        CobblemonEvents.POKEMON_CAPTURED.subscribe(
            Priority.NORMAL,
            (Consumer<PokemonCapturedEvent>) CaptureEventProbe::onCaptured
        );
        LOGGER.info("[ZIAN-QUEST-PROBE] capture=enabled rewards=disabled");
    }

    private static void onCaptured(PokemonCapturedEvent event) {
        String speciesId = event.getPokemon().getSpecies().getResourceIdentifier().toString();
        Set<Generation> generations = RESOLVER.resolve(event.getPokemon().getSpecies());
        String generationIds = generations.isEmpty()
            ? "unknown"
            : generations.stream()
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .map(Generation::getId)
                .collect(Collectors.joining(","));

        LOGGER.info(
            "[ZIAN-QUEST-PROBE] event=capture playerUuid={} species={} generations={}",
            event.getPlayer().getUUID(), speciesId, generationIds
        );
    }
}

