package com.zianblk.zianutilities.neoforge.quests;

import com.zianblk.zianutilities.core.quests.GlobalQuestOffer;
import com.zianblk.zianutilities.core.quests.GlobalQuestProgress;
import com.zianblk.zianutilities.neoforge.ZianUtilitiesMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Required client display. The server remains the only source of quest and reward state. */
public final class GlobalQuestNetwork {
    private static final Logger LOGGER = LoggerFactory.getLogger("ZianUtilities/GlobalQuest");

    private GlobalQuestNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("global-quest-ui-2");
        registrar.playToClient(State.TYPE, State.CODEC, GlobalQuestNetwork::onState);
        registrar.playToServer(Action.TYPE, Action.CODEC, GlobalQuestNetwork::onAction);
    }

    public static boolean hasVisualClient(ServerPlayer player) {
        return NetworkRegistry.hasChannel(player.connection, State.TYPE.id())
            && NetworkRegistry.hasChannel(player.connection, Action.TYPE.id());
    }

    public static void send(ServerPlayer player, boolean open) {
        if (!hasVisualClient(player)) return;
        try {
            GlobalQuestOffer offer = GlobalQuestRuntime.offer(player.getServer());
            GlobalQuestProgress progress = GlobalQuestRuntime.service(player.getServer())
                .inspect(player.getUUID(), offer);
            PacketDistributor.sendToPlayer(player, new State(open, offer.getWindowStartEpochMs(),
                offer.getExpiresAtEpochMs(), offer.getTargetSpecies() == null ? "" : offer.getTargetSpecies(),
                offer.getGenerationIds(), progress != null, progress != null && progress.getCaptureComplete(),
                progress == null ? 0 : progress.getBattleIds().size(),
                offer.getRewardsEnabled() && GlobalQuestRuntime.rewardsEnabled(),
                offer.getCaptureCurrency(), offer.getCaptureAmount(),
                offer.getBattleCurrency(), offer.getBattleAmount()));
        } catch (Exception error) {
            LOGGER.error("[ZIAN-GLOBAL-QUEST] action=visual_sync playerUuid={} result=error", player.getUUID(), error);
            player.sendSystemMessage(Component.literal("No se pudo cargar la misión; revisa la consola."));
        }
    }

    private static void onState(State state, IPayloadContext context) {
        // This callback runs only for the clientbound play payload.
        com.zianblk.zianutilities.neoforge.quests.client.GlobalQuestScreen.receive(state);
    }

    private static void onAction(Action action, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
            || !GlobalQuestRuntime.moduleEnabled()) return;
        try {
            GlobalQuestOffer offer = GlobalQuestRuntime.offer(player.getServer());
            if (action.accept() && action.windowStartEpochMs() == offer.getWindowStartEpochMs()
                && action.targetSpecies().equals(offer.getTargetSpecies() == null ? "" : offer.getTargetSpecies())
                && action.generationIds().equals(offer.getGenerationIds())) {
                var service = GlobalQuestRuntime.service(player.getServer());
                if (service.inspect(player.getUUID(), offer) == null) {
                    service.accept(player.getUUID(), offer);
                    player.sendSystemMessage(Component.literal("Aceptaste los dos objetivos de este bloque."));
                }
            }
            send(player, false);
        } catch (Exception error) {
            LOGGER.error("[ZIAN-GLOBAL-QUEST] action=visual_accept playerUuid={} result=error", player.getUUID(), error);
            player.sendSystemMessage(Component.literal("No se pudo guardar la aceptación; revisa la consola."));
        }
    }

    public record Action(boolean accept, long windowStartEpochMs,
                         String targetSpecies, String generationIds) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            ZianUtilitiesMod.MOD_ID, "global_quest_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeBoolean(value.accept);
                buffer.writeLong(value.windowStartEpochMs);
                buffer.writeUtf(value.targetSpecies, 128);
                buffer.writeUtf(value.generationIds, 128);
            },
            buffer -> new Action(buffer.readBoolean(), buffer.readLong(),
                buffer.readUtf(128), buffer.readUtf(128)));

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record State(boolean open, long windowStartEpochMs, long expiresAtEpochMs,
                        String targetSpecies, String generationIds, boolean accepted,
                        boolean captureComplete, int battleWins, boolean rewardsEnabled,
                        String captureCurrency, long captureAmount,
                        String battleCurrency, long battleAmount) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            ZianUtilitiesMod.MOD_ID, "global_quest_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeBoolean(value.open);
                buffer.writeLong(value.windowStartEpochMs);
                buffer.writeLong(value.expiresAtEpochMs);
                buffer.writeUtf(value.targetSpecies, 128);
                buffer.writeUtf(value.generationIds, 128);
                buffer.writeBoolean(value.accepted);
                buffer.writeBoolean(value.captureComplete);
                buffer.writeVarInt(value.battleWins);
                buffer.writeBoolean(value.rewardsEnabled);
                buffer.writeUtf(value.captureCurrency, 128);
                buffer.writeLong(value.captureAmount);
                buffer.writeUtf(value.battleCurrency, 128);
                buffer.writeLong(value.battleAmount);
            },
            buffer -> new State(buffer.readBoolean(), buffer.readLong(), buffer.readLong(),
                buffer.readUtf(128), buffer.readUtf(128), buffer.readBoolean(), buffer.readBoolean(),
                buffer.readVarInt(), buffer.readBoolean(), buffer.readUtf(128), buffer.readLong(),
                buffer.readUtf(128), buffer.readLong()));

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void request(boolean accept, long windowStartEpochMs,
                               String targetSpecies, String generationIds) {
        PacketDistributor.sendToServer(new Action(accept, windowStartEpochMs, targetSpecies, generationIds));
    }
}

