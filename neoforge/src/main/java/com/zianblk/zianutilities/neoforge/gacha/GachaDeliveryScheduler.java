package com.zianblk.zianutilities.neoforge.gacha;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.LoggerFactory;
import java.util.UUID;

final class GachaDeliveryScheduler {
    private static final GachaDeliveryQueue QUEUE = new GachaDeliveryQueue();
    private static MinecraftServer server;
    private GachaDeliveryScheduler() {}

    static void install() {
        NeoForge.EVENT_BUS.addListener(GachaDeliveryScheduler::onTick);
        NeoForge.EVENT_BUS.addListener(GachaDeliveryScheduler::onLogout);
        NeoForge.EVENT_BUS.addListener(GachaDeliveryScheduler::onStop);
    }

    static void schedule(ServerPlayer player, UUID operation) {
        if (server != player.getServer()) { QUEUE.clear(); server = player.getServer(); }
        QUEUE.schedule(player.getUUID(), operation, System.nanoTime());
    }

    private static void onTick(ServerTickEvent.Post event) {
        if (event.getServer() != server) return;
        for (var delivery : QUEUE.due(System.nanoTime())) {
            ServerPlayer player = server.getPlayerList().getPlayer(delivery.player());
            if (player == null) continue;
            try {
                GachaRuntime.deliverScheduled(player, delivery.operation());
            } catch (Exception error) {
                LoggerFactory.getLogger("ZianUtilities/Gacha").error(
                    "[ZIAN-GACHA] action=scheduled_delivery playerUuid={} operation={} result=review_required",
                    delivery.player(), delivery.operation(), error);
                player.sendSystemMessage(Component.literal("No se pudo confirmar la entrega; revisa Gachas o consulta al administrador."));
            }
            GachaNetwork.send(player, false);
        }
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        QUEUE.removePlayer(event.getEntity().getUUID());
    }

    private static void onStop(ServerStoppedEvent event) {
        if (event.getServer() == server) { QUEUE.clear(); server = null; }
    }
}
