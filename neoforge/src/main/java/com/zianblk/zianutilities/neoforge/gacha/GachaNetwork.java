package com.zianblk.zianutilities.neoforge.gacha;

import com.zianblk.zianutilities.neoforge.ZianUtilitiesMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class GachaNetwork {
    private static final Logger LOG = LoggerFactory.getLogger("ZianUtilities/Gacha");
    private GachaNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("gacha-ui-1");
        registrar.playToClient(State.TYPE, State.CODEC, GachaNetwork::onState);
        registrar.playToServer(Action.TYPE, Action.CODEC, GachaNetwork::onAction);
    }

    public static boolean hasClient(ServerPlayer player) {
        return NetworkRegistry.hasChannel(player.connection, State.TYPE.id())
            && NetworkRegistry.hasChannel(player.connection, Action.TYPE.id());
    }

    public static void send(ServerPlayer player, boolean open) {
        if (!hasClient(player)) return;
        try {
            GachaRuntime.View view = GachaRuntime.view(player);
            List<PoolView> pools = view.pools().stream().map(pool ->
                new PoolView(pool.id(), pool.name(), pool.ticket(), pool.cost(), pool.enabled(),
                    pool.prizes().stream().map(p -> new PrizeView(p.item(), p.weight())).toList())).toList();
            List<PendingView> pending = view.pending().stream()
                .filter(p -> p.phase().equals("READY"))
                .map(p -> new PendingView(p.id().toString(), p.poolName(), p.item())).toList();
            PacketDistributor.sendToPlayer(player,
                new State(open, view.admin(), view.paymentEnabled(), pools, pending));
        } catch (Exception error) {
            LOG.error("[ZIAN-GACHA] action=sync playerUuid={} result=error", player.getUUID(), error);
            player.sendSystemMessage(Component.literal("No se pudo cargar Gachas; revisa la consola."));
        }
    }

    private static void onState(State state, IPayloadContext context) {
        com.zianblk.zianutilities.neoforge.gacha.client.GachaScreen.receive(state);
    }

    private static void onAction(Action action, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
            || !GachaRuntime.enabled()) return;
        try {
            switch (action.kind()) {
                case 0 -> { }
                case 1 -> GachaRuntime.create(player);
                case 2, 3, 4, 5, 6, 7, 8 -> GachaRuntime.edit(
                    player, action.poolId(), action.kind(), action.index(), action.text());
                case 9 -> GachaRuntime.roll(player, action.poolId());
                case 10 -> GachaRuntime.claim(player, UUID.fromString(action.text()));
                case 11 -> GachaRuntime.delete(player, action.poolId(), action.text());
                default -> throw new IllegalStateException("Acción inválida");
            }
            send(player, false);
        } catch (Exception error) {
            if (error instanceof IllegalStateException
                && "No tienes tickets suficientes".equals(error.getMessage())) {
                LOG.info("[ZIAN-GACHA] action={} playerUuid={} result=insufficient_tickets",
                    action.kind(), player.getUUID());
            } else {
                LOG.warn("[ZIAN-GACHA] action={} playerUuid={} result=error",
                    action.kind(), player.getUUID(), error);
            }
            player.sendSystemMessage(Component.literal(error instanceof IllegalStateException
                ? error.getMessage() : "No se pudo guardar la operación de gacha; revisa la consola."));
            send(player, false);
        }
    }

    public record PrizeView(ItemStack item, int weight) {}
    public record PoolView(int id, String name, String ticket, int cost, boolean enabled,
                           List<PrizeView> prizes) {}
    public record PendingView(String id, String pool, ItemStack item) {}

    public record State(boolean open, boolean admin, boolean paymentEnabled,
                        List<PoolView> pools, List<PendingView> pending) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            ZianUtilitiesMod.MOD_ID, "gacha_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeBoolean(value.open);
                buffer.writeBoolean(value.admin);
                buffer.writeBoolean(value.paymentEnabled);
                buffer.writeVarInt(value.pools.size());
                for (PoolView pool : value.pools) {
                    buffer.writeVarInt(pool.id);
                    buffer.writeUtf(pool.name, 32);
                    buffer.writeUtf(pool.ticket, 64);
                    buffer.writeVarInt(pool.cost);
                    buffer.writeBoolean(pool.enabled);
                    buffer.writeVarInt(pool.prizes.size());
                    for (PrizeView prize : pool.prizes) {
                        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, prize.item);
                        buffer.writeVarInt(prize.weight);
                    }
                }
                buffer.writeVarInt(value.pending.size());
                for (PendingView prize : value.pending) {
                    buffer.writeUtf(prize.id, 36);
                    buffer.writeUtf(prize.pool, 32);
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, prize.item);
                }
            },
            buffer -> {
                boolean open = buffer.readBoolean();
                boolean admin = buffer.readBoolean();
                boolean payment = buffer.readBoolean();
                int poolCount = bounded(buffer.readVarInt(), 8);
                List<PoolView> pools = new ArrayList<>();
                for (int i = 0; i < poolCount; i++) {
                    int id = buffer.readVarInt();
                    String name = buffer.readUtf(32);
                    String ticket = buffer.readUtf(64);
                    int cost = buffer.readVarInt();
                    boolean enabled = buffer.readBoolean();
                    int prizeCount = bounded(buffer.readVarInt(), 12);
                    List<PrizeView> prizes = new ArrayList<>();
                    for (int j = 0; j < prizeCount; j++) {
                        prizes.add(new PrizeView(ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                            buffer.readVarInt()));
                    }
                    pools.add(new PoolView(id, name, ticket, cost, enabled, prizes));
                }
                int pendingCount = bounded(buffer.readVarInt(), 50);
                List<PendingView> pending = new ArrayList<>();
                for (int i = 0; i < pendingCount; i++)
                    pending.add(new PendingView(buffer.readUtf(36), buffer.readUtf(32),
                        ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer)));
                return new State(open, admin, payment, pools, pending);
            });
        private static int bounded(int count, int max) {
            if (count < 0 || count > max) throw new IllegalArgumentException("Invalid gacha list size");
            return count;
        }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Action(byte kind, int poolId, int index, String text) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            ZianUtilitiesMod.MOD_ID, "gacha_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeByte(value.kind);
                buffer.writeVarInt(value.poolId);
                buffer.writeVarInt(value.index);
                buffer.writeUtf(value.text, 64);
            },
            buffer -> new Action(buffer.readByte(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readUtf(64)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void request(int kind, int poolId, int index, String text) {
        PacketDistributor.sendToServer(new Action((byte) kind, poolId, index, text));
    }
}


