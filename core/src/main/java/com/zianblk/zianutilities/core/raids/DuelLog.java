package com.zianblk.zianutilities.core.raids;

import com.zianblk.zianutilities.core.breeding.BreedingJournal;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Write-ahead log of duels and reward delivery phases, on the hash-chained journal Breeding already uses.
 * Lines are "TYPE|id|a|b|c...". A STARTED without ENDED at startup was cut by a crash.
 */
public final class DuelLog implements AutoCloseable {
    public record Open(UUID duelId, UUID player, int tier, String raidId) {}

    private final BreedingJournal journal;
    private final Map<UUID, Open> open = new LinkedHashMap<>();

    public DuelLog(Path path) throws IOException {
        this.journal = BreedingJournal.open(path, this::replay);
    }

    private void replay(String line) {
        String[] f = line.split("\\|", -1);
        if (f.length < 2) throw new IllegalArgumentException("Bad duel log line");
        UUID id = UUID.fromString(f[1]);
        switch (f[0]) {
            case "STARTED" -> open.put(id, new Open(id, UUID.fromString(f[2]), Integer.parseInt(f[3]), f[4]));
            case "ENDED" -> open.remove(id);
            default -> { }
        }
    }

    /** Duels begun and never ended: the server died in the middle of them. */
    public Map<UUID, Open> unfinished() { return Map.copyOf(open); }

    public void started(UUID duelId, UUID player, int tier, String raidId, String boss, long deadline) throws IOException {
        append("STARTED", duelId, player, tier, clean(raidId), clean(boss), deadline);
        open.put(duelId, new Open(duelId, player, tier, raidId));
    }

    public void ended(UUID duelId, DuelResult result, double ratio) throws IOException {
        append("ENDED", duelId, result, ratio);
        open.remove(duelId);
    }

    /** Free-form audit line (REWARD_QUEUED, REWARD_DELIVERED, ...). */
    public void note(String type, UUID id, Object... details) throws IOException { append(type, id, details); }

    private void append(String type, UUID id, Object... details) throws IOException {
        StringBuilder b = new StringBuilder(type).append('|').append(id);
        for (Object d : details) b.append('|').append(clean(String.valueOf(d)));
        journal.append(b.toString());
    }

    private static String clean(String s) { return s.replace('|', '/').replace('\n', ' ').replace('\r', ' '); }

    public boolean locked() { return journal.locked(); }

    @Override public void close() throws IOException { journal.close(); }
}
