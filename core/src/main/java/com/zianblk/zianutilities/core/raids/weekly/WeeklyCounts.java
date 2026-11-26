package com.zianblk.zianutilities.core.raids.weekly;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Attempts and rewards of each player in the active week. A limit of 0 means no limit. */
public final class WeeklyCounts {
    public record Count(int attempts, int rewards) {}

    private final Map<UUID, Count> counts = new LinkedHashMap<>();

    public Count of(UUID player) { return counts.getOrDefault(player, new Count(0, 0)); }

    public boolean attemptsLeft(UUID player, int limit) { return limit <= 0 || of(player).attempts() < limit; }

    public boolean rewardsLeft(UUID player, int limit) { return limit <= 0 || of(player).rewards() < limit; }

    public void addAttempt(UUID player) { Count c = of(player); counts.put(player, new Count(c.attempts() + 1, c.rewards())); }

    public void addReward(UUID player) { Count c = of(player); counts.put(player, new Count(c.attempts(), c.rewards() + 1)); }

    public void set(UUID player, int attempts, int rewards) {
        if (attempts < 0 || rewards < 0) throw new IllegalArgumentException("negative count");
        counts.put(player, new Count(attempts, rewards));
    }

    public boolean reset(UUID player) { return counts.remove(player) != null; }

    public void clear() { counts.clear(); }

    public Map<UUID, Count> all() { return Map.copyOf(counts); }
}
