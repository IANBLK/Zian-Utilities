package com.zianblk.zianutilities.neoforge.quests;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.zianblk.zianutilities.core.quests.GlobalQuestEventResult;
import com.zianblk.zianutilities.core.quests.GlobalQuestOffer;
import com.zianblk.zianutilities.core.quests.GlobalQuestProgress;
import com.zianblk.zianutilities.core.rewards.ClaimStatus;
import com.zianblk.zianutilities.neoforge.cobblemon.CobblemonGenerationResolver;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class GlobalQuestListener {
    private static final Logger LOGGER = LoggerFactory.getLogger("ZianUtilities/GlobalQuest");
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static final CobblemonGenerationResolver RESOLVER = new CobblemonGenerationResolver(null);

    private GlobalQuestListener() {}

    public static void install() {
        if (!Boolean.getBoolean(GlobalQuestRuntime.TEST_FLAG) || !INSTALLED.compareAndSet(false, true)) return;
        CobblemonEvents.POKEMON_CAPTURED.subscribe(
            Priority.NORMAL, (Consumer<PokemonCapturedEvent>) GlobalQuestListener::onCaptured);
        CobblemonEvents.BATTLE_VICTORY.subscribe(
            Priority.NORMAL, (Consumer<BattleVictoryEvent>) GlobalQuestListener::onVictory);
        LOGGER.info("[ZIAN-GLOBAL-QUEST] enabled; battle victories and captures observed; reward flag={}",
            Boolean.getBoolean(GlobalQuestRuntime.REWARD_FLAG));
    }

    private static void onCaptured(PokemonCapturedEvent event) {
        ServerPlayer player = event.getPlayer();
        MinecraftServer server = player.getServer();
        if (server == null) return;
        UUID pokemonId = event.getPokemon().getUuid();
        String species = event.getPokemon().getSpecies().getResourceIdentifier().toString();
        var generations = RESOLVER.resolve(event.getPokemon().getSpecies());
        server.execute(() -> {
            try {
                GlobalQuestOffer offer = GlobalQuestRuntime.offer(server);
                GlobalQuestEventResult result = GlobalQuestRuntime.service(server).capture(
                    player.getUUID(), offer, pokemonId, species, generations, GlobalQuestRuntime.enabled(server));
                if (result == GlobalQuestEventResult.COMPLETE) {
                    GlobalQuestProgress progress = GlobalQuestRuntime.service(server).inspect(player.getUUID(), offer);
                    ClaimStatus claim = GlobalQuestRuntime.pay(progress, "capture");
                    player.sendSystemMessage(Component.literal("Objetivo de captura completado. Premio: "
                        + (claim == null ? "desactivado" : claim)));
                    refresh(player);
                }
            } catch (Exception error) {
                LOGGER.error("[ZIAN-GLOBAL-QUEST] event=capture playerUuid={} result=error", player.getUUID(), error);
            }
        });
    }

    private static void onVictory(BattleVictoryEvent event) {
        if (!event.getBattle().isPvW() || event.getWasWildCapture()
            || event.getLosers().stream().anyMatch(actor -> actor.getType() != ActorType.WILD)) return;
        UUID battleId = event.getBattle().getBattleId();
        for (var actor : event.getWinners()) {
            if (!(actor instanceof PlayerBattleActor winner)) continue;
            ServerPlayer player = winner.getEntity();
            if (player == null || player.getServer() == null) continue;
            MinecraftServer server = player.getServer();
            server.execute(() -> {
                try {
                    GlobalQuestOffer offer = GlobalQuestRuntime.offer(server);
                    GlobalQuestEventResult result = GlobalQuestRuntime.service(server)
                        .wildVictory(player.getUUID(), offer, battleId);
                    if (result == GlobalQuestEventResult.ADVANCED || result == GlobalQuestEventResult.COMPLETE) {
                        GlobalQuestProgress progress = GlobalQuestRuntime.service(server).inspect(player.getUUID(), offer);
                        ClaimStatus claim = result == GlobalQuestEventResult.COMPLETE
                            ? GlobalQuestRuntime.pay(progress, "battle") : null;
                        player.sendSystemMessage(Component.literal("Batallas salvajes ganadas: "
                            + progress.getBattleIds().size() + "/5"
                            + (result == GlobalQuestEventResult.COMPLETE ? "; premio: "
                            + (claim == null ? "desactivado" : claim) : "")));
                        refresh(player);
                    }
                } catch (Exception error) {
                    LOGGER.error("[ZIAN-GLOBAL-QUEST] event=battle playerUuid={} battleId={} result=error",
                        player.getUUID(), battleId, error);
                }
            });
        }
    }

    private static void refresh(ServerPlayer player) {
        if (player.containerMenu instanceof GlobalQuestMenu menu) menu.refresh();
        GlobalQuestNetwork.send(player, false);
    }
}
