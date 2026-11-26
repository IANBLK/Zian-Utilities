package com.zianblk.zianutilities.core.raids.weekly;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The weekly rotation, calculated from the stored start and the clock, never from an in-memory timer. A week runs
 * from one (day, time) boundary in the configured zone to the next one, counted in local calendar days, so a
 * daylight saving change makes that one week 167 or 169 hours long. After a long shutdown the raid rotates ONCE.
 */
public final class WeeklySchedule {
    public enum PickOrder { SEQUENTIAL, RANDOM_NO_REPEAT }

    public record Rule(ZoneId zone, DayOfWeek day, LocalTime time, PickOrder order) {}

    /** The active week. weekNumber grows by one with every change of raid; endsAt is stored, so editing the rule never moves it. */
    public record Week(long weekNumber, String activeId, Instant startedAt, Instant endsAt, int listIndex, long seed, String reason) {}

    private WeeklySchedule() {}

    private static ZonedDateTime boundaryOn(LocalDate date, Rule rule) {
        return ZonedDateTime.of(date, rule.time(), rule.zone());
    }

    private static LocalDate dateAtOrBefore(Instant now, Rule rule) {
        LocalDate date = now.atZone(rule.zone()).toLocalDate().with(TemporalAdjusters.previousOrSame(rule.day()));
        while (boundaryOn(date, rule).toInstant().isAfter(now)) date = date.minusWeeks(1);
        return date;
    }

    /** Latest boundary that is not after the instant. */
    public static Instant boundaryAtOrBefore(Instant now, Rule rule) {
        return boundaryOn(dateAtOrBefore(now, rule), rule).toInstant();
    }

    /** First boundary strictly after the instant. */
    public static Instant boundaryAfter(Instant now, Rule rule) {
        LocalDate date = dateAtOrBefore(now, rule);
        Instant candidate;
        do {
            date = date.plusWeeks(1);
            candidate = boundaryOn(date, rule).toInstant();
        } while (!candidate.isAfter(now));
        return candidate;
    }

    public static boolean due(Week week, Instant now) { return !now.isBefore(week.endsAt()); }

    /** First week ever: it started at the last boundary, as if the rotation had always run. */
    public static Week first(Rule rule, List<String> ids, Instant now, long seed) {
        require(ids);
        int index = rule.order() == PickOrder.RANDOM_NO_REPEAT ? new Random(seed).nextInt(ids.size()) : 0;
        return new Week(1, ids.get(index), boundaryAtOrBefore(now, rule), boundaryAfter(now, rule), index, seed, "START");
    }

    /** The single rotation after the end of a week, however long ago it ended. */
    public static Week rotateAuto(Rule rule, Week previous, List<String> ids, Instant now) {
        Instant start = boundaryAtOrBefore(now, rule);
        if (start.isBefore(previous.endsAt())) start = previous.endsAt();
        return next(rule, previous, ids, start, boundaryAfter(now, rule), "AUTO");
    }

    /** Admin rotation: starts now and ends at the next scheduled boundary. */
    public static Week rotateForced(Rule rule, Week previous, List<String> ids, Instant now) {
        return next(rule, previous, ids, now, boundaryAfter(now, rule), "FORCED");
    }

    /** Admin pin of one raid: a new week entry that ends at the next scheduled boundary. */
    public static Week fix(Rule rule, Week previous, List<String> ids, String id, Instant now) {
        int index = ids.indexOf(id);
        if (index < 0) throw new IllegalArgumentException("unknown raid id " + id);
        return new Week(previous.weekNumber() + 1, id, now, boundaryAfter(now, rule), index, previous.seed(), "MANUAL_SET");
    }

    private static Week next(Rule rule, Week previous, List<String> ids, Instant start, Instant end, String reason) {
        require(ids);
        long number = previous.weekNumber() + 1;
        int current = ids.indexOf(previous.activeId());
        if (current < 0) current = Math.min(previous.listIndex(), ids.size() - 1);
        int index;
        if (rule.order() == PickOrder.SEQUENTIAL) {
            index = (current + 1) % ids.size();
        } else if (ids.size() == 1) {
            index = 0;
        } else {
            List<Integer> others = new ArrayList<>();
            for (int i = 0; i < ids.size(); i++) if (!ids.get(i).equals(previous.activeId())) others.add(i);
            index = others.get(new Random(previous.seed() * 31 + number).nextInt(others.size()));
        }
        return new Week(number, ids.get(index), start, end, index, previous.seed(), reason);
    }

    private static void require(List<String> ids) {
        if (ids == null || ids.isEmpty()) throw new IllegalArgumentException("the raid list is empty");
    }
}
