package com.zianblk.zianutilities.neoforge.raids;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zianblk.zianutilities.core.raids.weekly.WeeklySchedule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WeeklyStateTest {
    private static final WeeklySchedule.Rule RULE = new WeeklySchedule.Rule(ZoneId.of("Europe/Madrid"), java.time.DayOfWeek.MONDAY, java.time.LocalTime.of(4, 0), WeeklySchedule.PickOrder.SEQUENTIAL);

    private static String raid(String id, int attempts) {
        return "{\"id\":\"" + id + "\",\"mode\":\"DUEL\",\"limits\":{\"attemptsPerWeek\":" + attempts + "},\"boss\":{\"pokemon\":{\"species\":\"charizard\"},\"raid_tier\":\"TIER_FIVE\",\"raid_type\":\"FIRE\"}}";
    }

    private static WeeklyConfig config(String... raids) {
        return WeeklyConfig.parse("{\"formatVersion\":1,\"enabled\":true,\"rotation\":{\"zone\":\"Europe/Madrid\"},\"raids\":[" + String.join(",", raids) + "]}");
    }

    private static WeeklyState first(WeeklyConfig cfg, Instant now) {
        var week = WeeklySchedule.first(cfg.rule(), cfg.ids(), now, 5);
        return new WeeklyState(week, cfg.raid(week.activeId()).raw().deepCopy());
    }

    @Test void roundTripKeepsTheWeekTheSnapshotAndTheCounts(@TempDir Path dir) throws Exception {
        var cfg = config(raid("a", 3), raid("b", 3));
        var state = first(cfg, Instant.parse("2026-11-26T10:00:00Z"));
        UUID player = UUID.randomUUID();
        state.counts.addAttempt(player);
        state.counts.addAttempt(player);
        state.counts.addReward(player);
        Path file = dir.resolve("weekly-state.json");
        state.save(file);
        var back = WeeklyState.load(file);
        assertEquals(state.week, back.week);
        assertEquals(state.snapshot, back.snapshot);
        assertEquals(2, back.counts.of(player).attempts());
        assertEquals(1, back.counts.of(player).rewards());
        assertTrue(Files.readString(file).contains("\"formatVersion\": 1"));
    }

    @Test void noFileMeansNoStateAndNoTempFilesAreLeftBehind(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("weekly-state.json");
        assertNull(WeeklyState.load(file));
        first(config(raid("a", 3)), Instant.parse("2026-11-26T10:00:00Z")).save(file);
        try (var files = Files.list(dir)) { assertEquals(List.of("weekly-state.json"), files.map(p -> p.getFileName().toString()).toList()); }
    }

    @Test void theSnapshotStaysFrozenWhenTheConfigIsEdited(@TempDir Path dir) throws Exception {
        var original = config(raid("a", 3), raid("b", 3));
        var state = first(original, Instant.parse("2026-11-26T10:00:00Z"));
        Path file = dir.resolve("weekly-state.json");
        state.save(file);
        var edited = config(raid("a", 99));
        assertEquals(99, edited.raid("a").attemptsPerWeek());
        var restored = WeeklyState.load(file);
        var active = WeeklyConfig.parseRaid(restored.snapshot.deepCopy(), edited.attemptsPerWeek(), edited.rewardsPerWeek());
        assertEquals(3, active.attemptsPerWeek());
        var removed = config(raid("z", 1));
        assertEquals("a", WeeklyConfig.parseRaid(WeeklyState.load(file).snapshot.deepCopy(), 1, 1).id());
        assertNull(removed.raid("a"));
    }

    @Test void aNewWeekStartsWithEmptyCountsButAKeptWeekKeepsThem() {
        var cfg = config(raid("a", 3), raid("b", 3));
        var state = first(cfg, Instant.parse("2026-11-26T10:00:00Z"));
        UUID player = UUID.randomUUID();
        state.counts.addAttempt(player);
        var next = WeeklySchedule.rotateAuto(RULE, state.week, cfg.ids(), state.week.endsAt());
        var fresh = new WeeklyState(next, cfg.raid(next.activeId()).raw().deepCopy());
        assertEquals(0, fresh.counts.of(player).attempts());
        assertEquals(1, state.counts.of(player).attempts());
    }

    @Test void corruptOrFutureFilesThrowAndAreNeverOverwritten(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("weekly-state.json");
        var good = first(config(raid("a", 3)), Instant.parse("2026-11-26T10:00:00Z")).toJson();
        for (String bad : new String[] {
            "not json", "[]", "{}", good.replace("\"formatVersion\": 1", "\"formatVersion\": 2"),
            good.replace("\"formatVersion\": 1,", ""), good.replace("\"weekNumber\": 1", "\"weekNumber\": \"x\""),
            good.replace("\"snapshot\"", "\"snap\""), good.replaceFirst("\"endsAt\": \"[^\"]+\"", "\"endsAt\": \"2000-01-01T00:00:00Z\""),
            good.replace("\"players\": {}", "\"players\": {\"nope\": {\"attempts\": 1, \"rewards\": 0}}"),
        }) {
            Files.writeString(file, bad);
            assertThrows(IOException.class, () -> WeeklyState.load(file), bad);
            assertEquals(bad, Files.readString(file));
        }
        assertFalse(good.isEmpty());
    }
}
