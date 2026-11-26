package com.zianblk.zianutilities.core.raids;

/** A fight phase, active while the boss health fraction is at or below hpBelow (the lowest matching one wins). */
public record DuelPhase(double hpBelow, double damageMult, double intervalMult, double radius) {
    public DuelPhase {
        if (!(hpBelow > 0 && hpBelow <= 1)) throw new IllegalArgumentException("hpBelow must be in (0,1]");
        if (!(damageMult > 0 && damageMult <= 10)) throw new IllegalArgumentException("damageMult must be in (0,10]");
        if (!(intervalMult >= 0.2 && intervalMult <= 5)) throw new IllegalArgumentException("intervalMult must be in [0.2,5]");
        if (!(radius >= 1 && radius <= 16)) throw new IllegalArgumentException("radius must be in [1,16]");
    }
}
