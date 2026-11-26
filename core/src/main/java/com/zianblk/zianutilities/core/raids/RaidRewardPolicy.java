package com.zianblk.zianutilities.core.raids;

/**
 * Decides how the duel closes the Raid Dens raid. Raid Dens applies requiredDamage itself in handleSuccess
 * (a proportion of the boss health), so a defeat that reached the threshold is closed as a success and the
 * rest as a failure. The comparison is inclusive: reaching exactly the threshold pays.
 */
public final class RaidRewardPolicy {
    private RaidRewardPolicy() {}

    public static boolean closeAsSuccess(DuelResult result, double dealtRatio, double requiredDamage) {
        return switch (result) {
            case WON -> true;
            case ABORTED -> false;
            case LOST, SURRENDERED, TIMEOUT -> dealtRatio > 0 && dealtRatio >= Math.max(requiredDamage, 0);
        };
    }
}
