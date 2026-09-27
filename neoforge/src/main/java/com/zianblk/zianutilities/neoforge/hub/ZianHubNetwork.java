package com.zianblk.zianutilities.neoforge.hub;

import com.zianblk.zianutilities.neoforge.ZianUtilitiesMod;
import com.zianblk.zianutilities.neoforge.quests.GlobalQuestNetwork;
import com.zianblk.zianutilities.neoforge.gacha.GachaNetwork;
import com.zianblk.zianutilities.neoforge.gacha.GachaRuntime;
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

/** The hub is navigation only; all quest and gacha mutations remain server-owned. */
public final class ZianHubNetwork {
    private static final String QUEST_FLAG = "zianutilities.globalQuestTestEnabled";

    private ZianHubNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("zian-hub-2");
        registrar.playToClient(State.TYPE, State.CODEC, ZianHubNetwork::onState);
        registrar.playToServer(Navigate.TYPE, Navigate.CODEC, ZianHubNetwork::onNavigate);
    }

    public static boolean hasClient(ServerPlayer player) {
        return NetworkRegistry.hasChannel(player.connection, State.TYPE.id())
            && NetworkRegistry.hasChannel(player.connection, Navigate.TYPE.id());
    }

    public static void open(ServerPlayer player) {
        if (!hasClient(player)) {
            player.sendSystemMessage(Component.literal(
                "Instala la misma versión de Zian Utilities en tu cliente."));
            return;
        }
        PacketDistributor.sendToPlayer(player, new State(Boolean.getBoolean(QUEST_FLAG),
            Boolean.getBoolean(GachaRuntime.TEST_FLAG)));
    }

    private static void onState(State state, IPayloadContext context) {
        com.zianblk.zianutilities.neoforge.hub.client.ZianHubScreen.receive(state);
    }

    private static void onNavigate(Navigate request, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        switch (request.destination()) {
            case 0 -> open(player);
            case 1 -> {
                if (!Boolean.getBoolean(QUEST_FLAG)) {
                    open(player);
                    return;
                }
                GlobalQuestNetwork.send(player, true);
            }
            case 2 -> {
                if (Boolean.getBoolean(GachaRuntime.TEST_FLAG)) GachaNetwork.send(player, true);
            }
            default -> { }
        }
    }

    public record State(boolean questsEnabled, boolean gachasEnabled) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            ZianUtilitiesMod.MOD_ID, "hub_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeBoolean(value.questsEnabled);
                buffer.writeBoolean(value.gachasEnabled);
            },
            buffer -> new State(buffer.readBoolean(), buffer.readBoolean()));

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Navigate(byte destination) implements CustomPacketPayload {
        public static final Type<Navigate> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            ZianUtilitiesMod.MOD_ID, "hub_navigate"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Navigate> CODEC = StreamCodec.of(
            (buffer, value) -> buffer.writeByte(value.destination),
            buffer -> new Navigate(buffer.readByte()));

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void requestHub() { PacketDistributor.sendToServer(new Navigate((byte) 0)); }

    public static void requestQuests() { PacketDistributor.sendToServer(new Navigate((byte) 1)); }
    public static void requestGachas() { PacketDistributor.sendToServer(new Navigate((byte) 2)); }
}
