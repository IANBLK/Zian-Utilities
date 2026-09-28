package com.zianblk.zianutilities.neoforge.quests;

import com.zianblk.zianutilities.core.generation.Generation;
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

import java.util.ArrayList;
import java.util.List;

/** Dedicated client view; mutations and rewards are validated on the server. */
public final class ProgressionQuestNetwork {
    private static final Logger LOGGER = LoggerFactory.getLogger("ZianUtilities/ProgressionQuests");

    private ProgressionQuestNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("progression-quest-ui-1");
        registrar.playToClient(State.TYPE, State.CODEC, ProgressionQuestNetwork::onState);
        registrar.playToServer(Action.TYPE, Action.CODEC, ProgressionQuestNetwork::onAction);
    }

    public static void send(ServerPlayer player, boolean open) {
        send(player, open, (byte) 0);
    }

    public static void send(ServerPlayer player, boolean open, byte tab) {
        if (player == null || !NetworkRegistry.hasChannel(player.connection, State.TYPE.id())) return;
        try {
            var weekly = ProgressionQuestRuntime.weekly(player);
            var campaigns = ProgressionQuestRuntime.campaigns(player);
            PacketDistributor.sendToPlayer(player, new State(open, tab, GlobalQuestRuntime.rewardsEnabled(),
                weekly.endsAt(), weekly.testWindow(), weekly.accepted(), weekly.captures(), weekly.battles(),
                weekly.capturePaid(), weekly.battlePaid(),
                weekly.captureCurrency(), weekly.captureAmount(),
                weekly.battleCurrency(), weekly.battleAmount(), campaigns));
        } catch (Exception error) {
            LOGGER.error("[ZIAN-PROGRESSION-QUEST] action=visual_sync playerUuid={} result=error",
                player.getUUID(), error);
            player.sendSystemMessage(Component.literal("No se pudieron cargar las misiones semanales; revisa la consola."));
        }
    }

    private static void onState(State state, IPayloadContext context) {
        com.zianblk.zianutilities.neoforge.quests.client.ProgressionQuestScreen.receive(state);
    }

    private static void onAction(Action action, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !GlobalQuestRuntime.moduleEnabled()) return;
        try {
            switch (action.kind()) {
                case 0 -> send(player, true, (byte) 0);
                case 1 -> send(player, true, (byte) 1);
                case 2 -> {
                    ProgressionQuestRuntime.acceptWeekly(player);
                    send(player, false, (byte) 0);
                }
                case 3 -> {
                    if (action.generationIndex() < 0 || action.generationIndex() >= Generation.values().length) return;
                    ProgressionQuestRuntime.acceptCampaign(player, Generation.values()[action.generationIndex()]);
                    send(player, false, (byte) 1);
                }
                default -> { }
            }
        } catch (Exception error) {
            LOGGER.error("[ZIAN-PROGRESSION-QUEST] action={} playerUuid={} result=error",
                action.kind(), player.getUUID(), error);
            player.sendSystemMessage(Component.literal("No se pudo actualizar la misión; revisa la consola."));
        }
    }

    public static void request(byte kind, int generationIndex) {
        PacketDistributor.sendToServer(new Action(kind, generationIndex));
    }

    public record Action(byte kind, int generationIndex) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            ZianUtilitiesMod.MOD_ID, "progression_quest_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.of(
            (buffer, value) -> { buffer.writeByte(value.kind); buffer.writeVarInt(value.generationIndex); },
            buffer -> new Action(buffer.readByte(), buffer.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record State(boolean open, byte tab, boolean rewardsEnabled,
                        long weeklyEndsAt, boolean weeklyTest, boolean weeklyAccepted,
                        int captures, int battles, boolean capturePaid, boolean battlePaid,
                        String captureCurrency, long captureAmount,
                        String battleCurrency, long battleAmount,
                        List<ProgressionQuestRuntime.Campaign> campaigns) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            ZianUtilitiesMod.MOD_ID, "progression_quest_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeBoolean(value.open);
                buffer.writeByte(value.tab);
                buffer.writeBoolean(value.rewardsEnabled);
                buffer.writeLong(value.weeklyEndsAt);
                buffer.writeBoolean(value.weeklyTest);
                buffer.writeBoolean(value.weeklyAccepted);
                buffer.writeVarInt(value.captures);
                buffer.writeVarInt(value.battles);
                buffer.writeBoolean(value.capturePaid);
                buffer.writeBoolean(value.battlePaid);
                buffer.writeUtf(value.captureCurrency, 128);
                buffer.writeLong(value.captureAmount);
                buffer.writeUtf(value.battleCurrency, 128);
                buffer.writeLong(value.battleAmount);
                buffer.writeVarInt(value.campaigns.size());
                for (var campaign : value.campaigns) {
                    buffer.writeUtf(campaign.generation(), 16);
                    buffer.writeBoolean(campaign.available());
                    buffer.writeBoolean(campaign.accepted());
                    buffer.writeVarInt(campaign.chapter());
                    buffer.writeVarInt(campaign.capturedSpecies());
                    buffer.writeVarInt(campaign.goal());
                    buffer.writeBoolean(campaign.rewardPending());
                    buffer.writeUtf(campaign.currency(), 128);
                    buffer.writeLong(campaign.amount());
                }
            }, buffer -> {
                boolean open = buffer.readBoolean();
                byte tab = buffer.readByte();
                boolean rewardsEnabled = buffer.readBoolean();
                long ends = buffer.readLong();
                boolean weeklyTest = buffer.readBoolean();
                boolean accepted = buffer.readBoolean();
                int captures = buffer.readVarInt();
                int battles = buffer.readVarInt();
                boolean capturePaid = buffer.readBoolean();
                boolean battlePaid = buffer.readBoolean();
                String captureCurrency = buffer.readUtf(128);
                long captureAmount = buffer.readLong();
                String battleCurrency = buffer.readUtf(128);
                long battleAmount = buffer.readLong();
                int count = buffer.readVarInt();
                if (count < 0 || count > 9) throw new IllegalArgumentException("invalid campaign count");
                List<ProgressionQuestRuntime.Campaign> campaigns = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    campaigns.add(new ProgressionQuestRuntime.Campaign(buffer.readUtf(16),
                        buffer.readBoolean(), buffer.readBoolean(), buffer.readVarInt(),
                        buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(),
                        buffer.readUtf(128), buffer.readLong()));
                }
                return new State(open, tab, rewardsEnabled, ends, weeklyTest, accepted, captures, battles,
                    capturePaid, battlePaid, captureCurrency, captureAmount,
                    battleCurrency, battleAmount, List.copyOf(campaigns));
            });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
