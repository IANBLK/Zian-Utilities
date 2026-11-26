package com.zianblk.zianutilities.core.raids;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure rules of a one-player boss duel, advanced one server tick at a time. It never touches the world:
 * the runtime turns Telegraph/Strike events into particles and damage and feeds hits back in.
 */
public final class DuelEngine {
    public enum State { PREPARING, ACTIVE, ENDED }

    public sealed interface Event permits Telegraph, Strike {}
    /** The boss announces a ground attack; it lands after delayTicks. */
    public record Telegraph(int attackNo, int delayTicks, double radius) implements Event {}
    public record Strike(int attackNo, double damage, double radius) implements Event {}

    private static final int FIRST_ATTACK_DELAY = 40;
    private final DuelParams params;
    private State state = State.PREPARING;
    private DuelResult result;
    private double hpFraction = 1.0, dealt;
    private int tick, attackNo, nextTelegraph = FIRST_ATTACK_DELAY, strikeAt = -1;
    private final int limitTicks;
    private boolean lethalCapped;

    public DuelEngine(DuelParams params) {
        this.params = params;
        this.limitTicks = Math.multiplyExact(params.timeLimitSeconds(), 20);
    }

    public void start() {
        if (state != State.PREPARING) throw new IllegalStateException("already started");
        state = State.ACTIVE;
    }

    public State state() { return state; }
    public DuelResult result() { return result; }
    public double bossHpFraction() { return hpFraction; }
    /** Fraction of the boss health taken, the same proportion Raid Dens compares with requiredDamage. */
    public double dealtRatio() { return dealt; }
    public int ticksLeft() { return Math.max(0, limitTicks - tick); }
    public boolean lethalCapped() { return lethalCapped; }

    public DuelPhase phase() {
        DuelPhase current = params.phases().getFirst();
        for (DuelPhase p : params.phases()) if (hpFraction <= p.hpBelow()) current = p;
        return current;
    }

    public List<Event> tick() {
        List<Event> out = new ArrayList<>(2);
        if (state != State.ACTIVE) return out;
        tick++;
        if (tick >= limitTicks) { end(DuelResult.TIMEOUT); return out; }
        DuelPhase phase = phase();
        if (strikeAt == tick) {
            out.add(new Strike(attackNo, params.bossDamage() * phase.damageMult(), phase.radius()));
            strikeAt = -1;
            nextTelegraph = tick + Math.max(params.telegraphTicks() + 5, (int) Math.round(params.attackIntervalTicks() * phase.intervalMult()));
        } else if (strikeAt < 0 && tick >= nextTelegraph) {
            attackNo++;
            strikeAt = tick + params.telegraphTicks();
            out.add(new Telegraph(attackNo, params.telegraphTicks(), phase.radius()));
        }
        return out;
    }

    /** A player hit. Returns the boss health fraction removed; one hit never takes more than the configured cap. */
    public double hit(double rawDamage) {
        if (state != State.ACTIVE || !(rawDamage > 0) || !Double.isFinite(rawDamage)) return 0;
        double applied = Math.min(Math.min(rawDamage / params.bossHealth(), params.maxDamagePerHitFraction()), hpFraction);
        hpFraction -= applied;
        dealt += applied;
        if (hpFraction <= 1e-9) { hpFraction = 0; dealt = 1.0; end(DuelResult.WON); }
        return applied;
    }

    /** Health at or below this after a hit means the hit is cancelled and the duel is lost (half a heart). */
    public static final double WITHDRAW_AT = 1.0;

    /**
     * Any damage about to land on the dueling player, from the boss or from anything else. Nobody dies inside a
     * duel so no item or totem is ever spent: a hit that would leave the player at 1.0 health or less is not
     * applied, the duel ends as a defeat and the runtime withdraws the player alive. Returns true when the hit
     * must be cancelled.
     */
    /** The one rule for "this hit would leave the player at WITHDRAW_AT health or less", also used by the post-duel grace window. */
    public static boolean isLethal(double playerHealthPlusAbsorption, double damage) {
        return playerHealthPlusAbsorption - damage <= WITHDRAW_AT;
    }

    public boolean incomingDamage(double playerHealthPlusAbsorption, double damage) {
        if (state != State.ACTIVE) return false;
        if (isLethal(playerHealthPlusAbsorption, damage)) { lethalCapped = true; end(DuelResult.LOST); return true; }
        return false;
    }

    public void surrender() { end(DuelResult.SURRENDERED); }
    /** Death that could not be intercepted (/kill, genericKill): vanilla death, the duel is closed as aborted. */
    public void playerDied() { end(DuelResult.ABORTED); }
    public void abort() { end(DuelResult.ABORTED); }

    private void end(DuelResult how) {
        if (state == State.ENDED) return;
        state = State.ENDED;
        result = how;
    }
}
