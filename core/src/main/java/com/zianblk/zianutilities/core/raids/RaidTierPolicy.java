package com.zianblk.zianutilities.core.raids;

/** Which Raid Dens tiers are a normal Pokemon battle and which are a duel. Tiers are 1..7. */
public final class RaidTierPolicy {
    public static final int MIN_TIER = 1, MAX_TIER = 7;

    private RaidTierPolicy() {}

    public static boolean validTier(int tier) { return tier >= MIN_TIER && tier <= MAX_TIER; }

    /** Out of range tiers are never a duel: unknown input falls back to Raid Dens as it is today. */
    public static boolean isDuel(int tier, int duelFromTier) {
        return validTier(tier) && validTier(duelFromTier) && tier >= duelFromTier;
    }

    public static int tierOfOrdinal(int ordinal) { return ordinal + 1; }
}
