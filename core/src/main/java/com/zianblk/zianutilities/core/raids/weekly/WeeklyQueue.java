package com.zianblk.zianutilities.core.raids.weekly;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Waiting line of the weekly raid, which one player plays at a time. A free raid with nobody waiting lets the
 * challenger in at once. Otherwise they queue; when the turn comes they are "called" and have turnMillis to start.
 * Pure: time comes in as a parameter and every change returns events for the runtime to announce.
 */
public final class WeeklyQueue {
    public sealed interface Event permits Called, TimedOut, Dropped, Moved {}
    public record Called(UUID player) implements Event {}
    public record TimedOut(UUID player) implements Event {}
    public record Dropped(UUID player) implements Event {}
    public record Moved(UUID player, int position) implements Event {}

    public enum Entry { GRANTED, QUEUED, ALREADY_PLAYING }

    public record Result(Entry entry, int position) {}

    private long turnMillis;
    private final Set<UUID> waiting = new LinkedHashSet<>();
    private UUID playing, called;
    private long calledUntil;

    public WeeklyQueue(long turnMillis) {
        if (turnMillis < 1000) throw new IllegalArgumentException("turnMillis must be at least 1000");
        this.turnMillis = turnMillis;
    }

    /** A reload may change how long a called player has; the player being called keeps the old deadline. */
    public void setTurnMillis(long turnMillis) {
        if (turnMillis < 1000) throw new IllegalArgumentException("turnMillis must be at least 1000");
        this.turnMillis = turnMillis;
    }

    public UUID playing() { return playing; }
    public UUID called() { return called; }
    public long calledUntil() { return calledUntil; }
    public List<UUID> waiting() { return List.copyOf(waiting); }

    /** 0 when the player is the one being called, 1.. in the line, -1 when not queued. */
    public int position(UUID player) {
        if (player.equals(called)) return 0;
        int i = 1;
        for (UUID w : waiting) { if (w.equals(player)) return i; i++; }
        return -1;
    }

    /** The player pressed the challenge. GRANTED reserves the raid for them until release(). */
    public Result request(UUID player, long now) {
        if (player.equals(playing)) return new Result(Entry.ALREADY_PLAYING, 0);
        if (player.equals(called)) {
            called = null;
            playing = player;
            return new Result(Entry.GRANTED, 0);
        }
        if (playing == null && called == null && waiting.isEmpty()) {
            playing = player;
            return new Result(Entry.GRANTED, 0);
        }
        waiting.add(player);
        return new Result(Entry.QUEUED, position(player));
    }

    /** The player who held the raid is done (or never started). Returns who is called next. */
    public List<Event> release(UUID player, long now) {
        List<Event> out = new ArrayList<>();
        if (!player.equals(playing)) return out;
        playing = null;
        callNext(now, out);
        return out;
    }

    /** The player leaves the line (command, disconnect). A called player leaving passes the turn on. */
    public List<Event> leave(UUID player, long now) {
        List<Event> out = new ArrayList<>();
        boolean removed = waiting.remove(player);
        if (player.equals(called)) {
            called = null;
            out.add(new Dropped(player));
            if (playing == null) callNext(now, out);
        } else if (removed) {
            out.add(new Dropped(player));
            moved(out);
        }
        return out;
    }

    /** Empties the line. The player who is playing keeps the raid. */
    public List<Event> clear() {
        List<Event> out = new ArrayList<>();
        for (UUID w : waiting) out.add(new Dropped(w));
        if (called != null) out.add(new Dropped(called));
        waiting.clear();
        called = null;
        return out;
    }

    /** Time passes: a called player who did not come in is skipped, and offline players are dropped. */
    public List<Event> tick(long now, Predicate<UUID> online) {
        List<Event> out = new ArrayList<>();
        boolean changed = false;
        for (UUID w : List.copyOf(waiting)) {
            if (online.test(w)) continue;
            waiting.remove(w);
            out.add(new Dropped(w));
            changed = true;
        }
        if (called != null && (!online.test(called) || now >= calledUntil)) {
            out.add(online.test(called) ? new TimedOut(called) : new Dropped(called));
            called = null;
        }
        if (playing == null && called == null) callNext(now, out);
        else if (changed) moved(out);
        return out;
    }

    private void callNext(long now, List<Event> out) {
        if (waiting.isEmpty()) return;
        UUID next = waiting.iterator().next();
        waiting.remove(next);
        called = next;
        calledUntil = now + turnMillis;
        out.add(new Called(next));
        moved(out);
    }

    private void moved(List<Event> out) {
        int i = 1;
        for (UUID w : waiting) out.add(new Moved(w, i++));
    }
}
