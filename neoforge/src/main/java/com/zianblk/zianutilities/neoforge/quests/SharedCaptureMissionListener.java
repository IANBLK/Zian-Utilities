package com.zianblk.zianutilities.neoforge.quests;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.zianblk.zianutilities.core.generation.Generation;
import com.zianblk.zianutilities.core.quests.SharedCaptureResult;
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

/** Opt-in Cobblemon bridge for a mission shared by the whole server. */
public final class SharedCaptureMissionListener {
    private static final Logger LOGGER = LoggerFactory.getLogger("ZianUtilities/SharedCapture");
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static final CobblemonGenerationResolver RESOLVER = new CobblemonGenerationResolver(null);

    private SharedCaptureMissionListener() {}

    public static void install() {
        if (!Boolean.getBoolean(SharedCaptureMissionCommands.TEST_FLAG)
            || !INSTALLED.compareAndSet(false, true)) return;
        CobblemonEvents.POKEMON_CAPTURED.subscribe(
            Priority.NORMAL, (Consumer<PokemonCapturedEvent>) SharedCaptureMissionListener::onCaptured);
        LOGGER.info("[ZIAN-SHARED-QUEST] shared_capture=enabled rewards=disabled");
    }

    private static void onCaptured(PokemonCapturedEvent event) {
        ServerPlayer player = event.getPlayer();
        MinecraftServer server = player.getServer();
        if (server == null) return;
        UUID playerId = player.getUUID();
        UUID pokemonId = event.getPokemon().getUuid();
        String speciesId = event.getPokemon().getSpecies().getResourceIdentifier().toString();
        Set<Generation> generations = RESOLVER.resolve(event.getPokemon().getSpecies());
        server.execute(() -> {
            try {
                Set<Generation> enabled = new NeoForgeGenerationStateStore(server).load().getEnabled();
                var service = SharedCaptureMissionRuntime.service(server);
                SharedCaptureResult result = service.recordCapture(
                    playerId, pokemonId, speciesId, generations, enabled);
                if (result == SharedCaptureResult.ADVANCED || result == SharedCaptureResult.COMPLETED) {
                    var saved = service.inspect(playerId);
                    LOGGER.info("[ZIAN-SHARED-QUEST] event=capture playerUuid={} missionId={} progress={}/5 result={}",
                        playerId, saved.getMission().getMissionId(),
                        saved.getProgress().getCaptures().size(), result);
                }
            } catch (Exception error) {
                LOGGER.error("[ZIAN-SHARED-QUEST] event=capture playerUuid={} result=error", playerId, error);
            }
        });
    }
}

