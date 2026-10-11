package com.zianblk.zianutilities.neoforge.quests;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.zianblk.zianutilities.core.generation.Generation;
import com.zianblk.zianutilities.core.quests.CaptureTrialResult;
import com.zianblk.zianutilities.neoforge.cobblemon.CobblemonGenerationResolver;
import com.zianblk.zianutilities.neoforge.generation.NeoForgeGenerationStateStore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Records one qualifying capture only after the player explicitly starts a trial. */
public final class CaptureTrialListener {
    private static final Logger LOGGER = LoggerFactory.getLogger("ZianUtilities/CaptureTrial");
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static final CobblemonGenerationResolver RESOLVER =
        new CobblemonGenerationResolver(null);

    private CaptureTrialListener() {
    }

    public static void install() {
        if (!Boolean.getBoolean(CaptureTrialCommands.TEST_FLAG)
            || !INSTALLED.compareAndSet(false, true)) {
            return;
        }
        CobblemonEvents.POKEMON_CAPTURED.subscribe(
            Priority.NORMAL,
            (Consumer<PokemonCapturedEvent>) CaptureTrialListener::onCaptured
        );
        LOGGER.info("[ZIAN-QUEST-TRIAL] capture=enabled rewards=disabled");
    }

    private static void onCaptured(PokemonCapturedEvent event) {
        ServerPlayer player = event.getPlayer();
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        UUID playerId = player.getUUID();
        String speciesId = event.getPokemon().getSpecies().getResourceIdentifier().toString();
        Set<Generation> generations = RESOLVER.resolve(event.getPokemon().getSpecies());
        server.execute(() -> {
            try {
                Set<Generation> enabled = new NeoForgeGenerationStateStore(server)
                    .load().getEnabled();
                CaptureTrialResult result = CaptureTrialRuntime.service(server)
                    .recordCapture(playerId, speciesId, generations, enabled);
                if (result == CaptureTrialResult.COMPLETED) {
                    LOGGER.info(
                        "[ZIAN-QUEST-TRIAL] event=capture playerUuid={} species={} result=COMPLETED",
                        playerId, speciesId
                    );
                }
            } catch (Exception error) {
                LOGGER.error(
                    "[ZIAN-QUEST-TRIAL] event=capture playerUuid={} result=error",
                    playerId, error
                );
            }
        });
    }
}

