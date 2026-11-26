package com.zianblk.zianutilities.core.raids;

import java.util.Comparator;
import java.util.List;

/** Scripted boss numbers for one tier. Everything is validated here so the engine never sees bad values. */
public record DuelParams(
    int timeLimitSeconds, double bossHealth, double bossDamage, int attackIntervalTicks, int telegraphTicks,
    double maxDamagePerHitFraction, int cooldownMinutes, List<DuelPhase> phases
) {
    public DuelParams {
        if (timeLimitSeconds < 10 || timeLimitSeconds > 3600) throw new IllegalArgumentException("timeLimitSeconds must be 10..3600");
        if (!(bossHealth >= 1 && bossHealth <= 1_000_000)) throw new IllegalArgumentException("bossHealth must be 1..1000000");
        if (!(bossDamage >= 0.5 && bossDamage <= 1000)) throw new IllegalArgumentException("bossDamage must be 0.5..1000");
        if (attackIntervalTicks < 10 || attackIntervalTicks > 1200) throw new IllegalArgumentException("attackIntervalTicks must be 10..1200");
        if (telegraphTicks < 5 || telegraphTicks > 200) throw new IllegalArgumentException("telegraphTicks must be 5..200");
        if (!(maxDamagePerHitFraction > 0 && maxDamagePerHitFraction <= 1)) throw new IllegalArgumentException("maxDamagePerHitFraction must be in (0,1]");
        if (cooldownMinutes < 0 || cooldownMinutes > 525_600) throw new IllegalArgumentException("cooldownMinutes must be 0..525600");
        if (phases == null || phases.isEmpty() || phases.size() > 8) throw new IllegalArgumentException("phases must have 1..8 entries");
        phases = phases.stream().sorted(Comparator.comparingDouble(DuelPhase::hpBelow).reversed()).toList();
        if (phases.getFirst().hpBelow() < 1.0) throw new IllegalArgumentException("the first phase must start at hpBelow 1.0");
    }

    /** The weekly raid has its own attempt limits, so the per-tier cooldown does not apply to it. */
    public DuelParams withoutCooldown() {
        return new DuelParams(timeLimitSeconds, bossHealth, bossDamage, attackIntervalTicks, telegraphTicks, maxDamagePerHitFraction, 0, phases);
    }

    public static List<DuelPhase> defaultPhases() {
        return List.of(new DuelPhase(1.0, 1.0, 1.0, 3.5), new DuelPhase(0.66, 1.15, 0.8, 4.0), new DuelPhase(0.33, 1.3, 0.6, 4.5));
    }

    public static DuelParams defaults(int tier) {
        int extra = Math.max(0, tier - 5);
        return new DuelParams(240 + 60 * extra, 600 + 300 * extra, 6.0 + 2.0 * extra, 50, 20, 0.15, 60, defaultPhases());
    }
}
