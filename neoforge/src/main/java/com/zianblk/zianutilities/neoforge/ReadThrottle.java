package com.zianblk.zianutilities.neoforge;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Límite de frecuencia por jugador para peticiones de solo lectura que llegan del cliente. */
public final class ReadThrottle {
    private final long intervalMs;
    private final Map<UUID, Long> last = new ConcurrentHashMap<>();

    public ReadThrottle(long intervalMs) { this.intervalMs = intervalMs; }

    public boolean tryAcquire(UUID player) { return tryAcquire(player, System.currentTimeMillis()); }

    public boolean tryAcquire(UUID player, long nowMs) {
        if (last.size() > 1024) last.values().removeIf(t -> Math.abs(nowMs - t) >= intervalMs);
        boolean[] ok = {false};
        // abs(): si el reloj retrocede no se bloquea al jugador, solo se cuenta el salto como tiempo pasado.
        last.compute(player, (id, prev) -> {
            ok[0] = prev == null || Math.abs(nowMs - prev) >= intervalMs;
            return ok[0] ? nowMs : prev;
        });
        return ok[0];
    }
}
