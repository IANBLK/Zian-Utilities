package com.zianblk.zianutilities.neoforge.quests;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.zianblk.zianutilities.core.generation.Generation;
import com.zianblk.zianutilities.core.quests.CaptureCycleResult;
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

/** Independent event bridge, active only for the new opt-in cycle test. */
public final class CaptureCycleListener {
    private static final Logger LOGGER = LoggerFactory.getLogger("ZianUtilities/CaptureCycle");
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static final CobblemonGenerationResolver RESOLVER = new CobblemonGenerationResolver(null);

    private CaptureCycleListener() {}

    public static void install() {
        if (!Boolean.getBoolean(CaptureCycleCommands.TEST_FLAG)
            || !INSTALLED.compareAndSet(false, true)) return;
        CobblemonEvents.POKEMON_CAPTURED.subscribe(
            Priority.NORMAL, (Consumer<PokemonCapturedEvent>) CaptureCycleListener::onCaptured);
        LOGGER.info("[ZIAN-QUEST-CYCLE] capture_cycle=enabled rewards=disabled");
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
                var service = CaptureCycleRuntime.service(server);
                CaptureCycleResult result = service.recordCapture(
                    playerId, pokemonId, speciesId, generations, enabled);
                if (result == CaptureCycleResult.ADVANCED || result == CaptureCycleResult.COMPLETED) {
                    var saved = service.inspect(playerId, enabled);
                    LOGGER.info("[ZIAN-QUEST-CYCLE] event=capture playerUuid={} cycleId={} pokemonId={} progress={}/3 result={}",
                        playerId, saved.getCycleId(), pokemonId, saved.getCaptures().size(), result);
                }
            } catch (Exception error) {
                LOGGER.error("[ZIAN-QUEST-CYCLE] event=capture playerUuid={} result=error", playerId, error);
            }
        });
    }
}

