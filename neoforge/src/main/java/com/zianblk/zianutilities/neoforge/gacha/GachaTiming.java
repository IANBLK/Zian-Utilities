package com.zianblk.zianutilities.neoforge.gacha;

/** Shared timing keeps the visual reel and server delivery delay in sync. */
public final class GachaTiming {
    public static final long SPIN_MILLIS = 3400;
    public static final long REEL_MILLIS = 5300;
    public static final long DELIVERY_MILLIS = REEL_MILLIS + 1000;
    private GachaTiming() {}
}
