package com.zianblk.zianutilities.neoforge.gacha;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-thread queue; journaled READY prizes remain recoverable if this queue is lost. */
final class GachaDeliveryQueue {
    record Delivery(UUID player, UUID operation) {}
    private final Map<Delivery, Long> waiting = new LinkedHashMap<>();

    void schedule(UUID player, UUID operation, long nowNanos) {
        waiting.putIfAbsent(new Delivery(player, operation),
            nowNanos + GachaTiming.DELIVERY_MILLIS * 1_000_000L);
    }

    List<Delivery> due(long nowNanos) {
        List<Delivery> result = new ArrayList<>();
        var iterator = waiting.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (nowNanos - entry.getValue() < 0) continue;
            result.add(entry.getKey());
            iterator.remove(); // Never automatically replay a delivery that might have applied.
        }
        return result;
    }

    void removePlayer(UUID player) { waiting.keySet().removeIf(key -> key.player().equals(player)); }
    void clear() { waiting.clear(); }
}
