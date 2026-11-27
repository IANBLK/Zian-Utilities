package com.zianblk.zianutilities.neoforge.raids;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.zianblk.zianutilities.core.raids.weekly.WeeklySchedule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WeeklyConfigTest {
    private static String raid(String id, String extra) {
        return "{\"id\":\"" + id + "\",\"mode\":\"DUEL\",\"boss\":{\"pokemon\":{\"species\":\"charizard\",\"level\":90},\"raid_tier\":\"TIER_FIVE\",\"raid_type\":\"FIRE\"}" + extra + "}";
    }

    private static String file(String top, String raids) {
        return "{\"formatVersion\":1," + top + "\"raids\":[" + raids + "]}";
    }

    @Test void theWrittenDefaultFileIsValidAndOff() {
        var cfg = WeeklyConfig.parse(WeeklyConfig.defaultJson());
        assertFalse(cfg.enabled());
        assertEquals(1, cfg.raids().size());
        assertEquals("semana-charizard", cfg.raids().get(0).id());
        assertEquals(5, cfg.raids().get(0).tier());
        assertEquals("charizard", cfg.raids().get(0).species());
        assertEquals(DayOfWeek.MONDAY, cfg.day());
        assertEquals(LocalTime.of(4, 0), cfg.time());
        assertEquals(WeeklySchedule.PickOrder.SEQUENTIAL, cfg.order());
    }

    @Test void emptyZoneMeansTheServerZoneAndAnExplicitZoneIsHonoured() {
        assertEquals(ZoneId.systemDefault(), WeeklyConfig.parse(file("", raid("a", ""))).zone());
        var cfg = WeeklyConfig.parse(file("\"rotation\":{\"zone\":\"America/Mexico_City\",\"dayOfWeek\":\"FRIDAY\",\"time\":\"18:30\",\"pickOrder\":\"RANDOM_NO_REPEAT\"},", raid("a", "")));
        assertEquals(ZoneId.of("America/Mexico_City"), cfg.zone());
        assertEquals(DayOfWeek.FRIDAY, cfg.day());
        assertEquals(WeeklySchedule.PickOrder.RANDOM_NO_REPEAT, cfg.order());
    }

    @Test void theBossKeepsTheRaidDensKeysUntouched() {
        var cfg = WeeklyConfig.parse(file("", raid("a", "").replace("\"raid_type\":\"FIRE\"", "\"raid_type\":\"FIRE\",\"scale\":1.5,\"health_multi\":25,\"loot_table\":\"x:y\"")));
        var boss = cfg.raids().get(0).boss();
        assertEquals(1.5, boss.get("scale").getAsDouble());
        assertEquals(25, boss.get("health_multi").getAsInt());
        assertEquals("x:y", boss.get("loot_table").getAsString());
    }

    @Test void limitsFallBackToTheGlobalOnesAndAreWrittenIntoTheSnapshot() {
        var cfg = WeeklyConfig.parse(file("\"limits\":{\"attemptsPerWeek\":4,\"rewardsPerWeek\":2},", raid("a", "") + "," + raid("b", ",\"limits\":{\"attemptsPerWeek\":9}")));
        assertEquals(4, cfg.raid("a").attemptsPerWeek());
        assertEquals(9, cfg.raid("b").attemptsPerWeek());
        assertEquals(2, cfg.raid("b").rewardsPerWeek());
        var frozen = cfg.raid("b").raw();
        assertEquals(9, frozen.getAsJsonObject("limits").get("attemptsPerWeek").getAsInt());
        var back = WeeklyConfig.parseRaid(frozen.deepCopy(), 1, 1);
        assertEquals(9, back.attemptsPerWeek());
        assertEquals(2, back.rewardsPerWeek());
    }

    @Test void duelNumbersAreTheOnesOfTheDuelAndHaveNoCooldown() {
        var cfg = WeeklyConfig.parse(file("", raid("a", ",\"duel\":{\"timeLimitSeconds\":100,\"bossDamage\":9.5}")));
        var duel = cfg.raid("a").duel();
        assertEquals(100, duel.timeLimitSeconds());
        assertEquals(9.5, duel.bossDamage());
        assertEquals(0, duel.cooldownMinutes());
        assertEquals(50, duel.attackIntervalTicks());
    }

    @Test void battleModeHasNoDuelParams() {
        var cfg = WeeklyConfig.parse(file("", raid("a", "").replace("\"DUEL\"", "\"BATTLE\"")));
        assertEquals(WeeklyConfig.Mode.BATTLE, cfg.raid("a").mode());
        assertNull(cfg.raid("a").duel());
        assertThrows(IllegalArgumentException.class, () -> WeeklyConfig.parse(file("", raid("a", ",\"duel\":{}").replace("\"DUEL\"", "\"BATTLE\""))));
    }

    @Test void failsClosedOnAnythingItDoesNotUnderstand() {
        String ok = raid("a", "");
        for (String bad : new String[] {
            "not json", "[]", "{}", "{\"formatVersion\":2,\"raids\":[]}", "{\"formatVersion\":1,\"surprise\":1}",
            file("\"enabled\":\"yes\",", ok), file("\"enabled\":true,", ""),
            file("\"rotation\":{\"dayOfWeek\":\"FUNDAY\"},", ok), file("\"rotation\":{\"time\":\"25:99\"},", ok),
            file("\"rotation\":{\"zone\":\"Mars/Olympus\"},", ok), file("\"rotation\":{\"pickOrder\":\"CHAOS\"},", ok),
            file("\"entrance\":{\"dimension\":\"Over World\"},", ok), file("\"entrance\":{\"yaw\":720},", ok),
            file("\"limits\":{\"attemptsPerWeek\":-1},", ok), file("\"queue\":{\"turnSeconds\":10},", ok), file("\"queue\":{\"maxTurnMinutes\":0},", ok),
            file("", raid("a", "").replace("\"level\":90", "\"ivs\":{\"hp\":31}")), file("", raid("a", "").replace("\"raid_type\":\"FIRE\"", "\"raid_type\":\"FIRE\",\"adventure\":[]")),
            file("", raid("a", "").replace("\"raid_type\":\"FIRE\"", "\"raid_type\":\"FIRE\",\"boss\":{\"ivs\":1}")),
            file("", ok + "," + ok), file("", raid("Bad Id", "")), file("", raid("a", "").replace("TIER_FIVE", "TIER_NINE")),
            file("", raid("a", "").replace("\"species\":\"charizard\",", "")), file("", raid("a", "").replace("\"raid_type\":\"FIRE\"", "\"x\":1")),
            file("", raid("a", ",\"duel\":{\"timeLimitSeconds\":1}")), file("", raid("a", ",\"duel\":{\"cooldownMinutes\":5}")),
            file("", raid("a", ",\"mode2\":1")), file("", raid("a", "").replace("\"DUEL\"", "\"RACE\"")),
            file("", raid("a", "").replace("\"level\":90", "\"level\":900")),
            file("", raid("a", "").replace("\"raid_type\":\"FIRE\"", "\"raid_type\":\"FIRE\",\"required_damage\":3")),
        })
            assertThrows(IllegalArgumentException.class, () -> WeeklyConfig.parse(bad), bad);
    }

    @Test void aMissingFileIsCreatedOffAndABrokenFileThrowsWithoutBeingOverwritten(@TempDir Path dir) throws Exception {
        Path path = dir.resolve("config/zianutilities-weekly-raids.json");
        var created = WeeklyConfig.load(path);
        assertFalse(created.enabled());
        assertTrue(Files.exists(path));
        Files.writeString(path, "{ broken");
        assertThrows(IllegalArgumentException.class, () -> WeeklyConfig.load(path));
        assertEquals("{ broken", Files.readString(path));
    }

    @Test void parsesTheSnapshotOfAnEditedRaid() {
        var cfg = WeeklyConfig.parse(file("", raid("a", "")));
        var snapshot = JsonParser.parseString(cfg.raid("a").raw().toString()).getAsJsonObject();
        assertNotNull(WeeklyConfig.parseRaid(snapshot, 3, 1));
    }
}
