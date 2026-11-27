package com.zianblk.zianutilities.neoforge.pase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zianblk.zianutilities.core.pase.MissionType;
import com.zianblk.zianutilities.core.pase.PaseRules;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PaseConfigTest {
    private static String edit(Consumer<JsonObject> change) {
        JsonObject o = JsonParser.parseString(PaseConfig.defaultJson()).getAsJsonObject();
        change.accept(o);
        return o.toString();
    }

    private static void rejects(Consumer<JsonObject> change, String part) {
        var e = assertThrows(IllegalArgumentException.class, () -> PaseConfig.parse(edit(change)));
        assertTrue(e.getMessage().contains(part), e.getMessage());
    }

    @Test void defaultsAreOffAndMatchTheDecidedNumbers() {
        PaseConfig c = PaseConfig.defaults();
        assertFalse(c.enabled());
        PaseRules r = c.rules();
        assertEquals(200, r.levels());
        assertEquals(800, r.xpPerLevel());
        assertEquals(4000, r.dailyCap());
        assertEquals(8000, r.boostCap());
        assertEquals(2, r.boostFactor());
        assertEquals(10, r.boostDays());
        assertEquals(1500, r.extraLevelXp());
        assertEquals(2, r.seasonMonths());
        assertFalse(r.streakEnabled());
        assertEquals(java.time.LocalTime.of(4, 0), r.resetTime());
        assertEquals(7, r.missions().size());
        assertEquals(1400, r.sourceCap("mission"));
        assertTrue(r.sourceCap("mission") <= (int) (0.35 * r.dailyCap()));
        assertEquals(PaseRules.defaults().missions(), r.missions());
    }

    @Test void theFileWrittenTheFirstTimeLoadsBack(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config/zianutilities-pase.json");
        PaseConfig c = PaseConfig.load(file);
        assertTrue(Files.exists(file));
        assertFalse(c.enabled());
        assertEquals(c.rules(), PaseConfig.load(file).rules());
    }

    @Test void unknownKeysBadTypesAndNewerVersionsFailClosed() {
        rejects(o -> o.addProperty("surprise", 1), "Clave desconocida");
        rejects(o -> o.getAsJsonObject("xp").addProperty("dailyCapp", 5), "Clave desconocida");
        rejects(o -> o.addProperty("formatVersion", 2), "formatVersion");
        rejects(o -> o.remove("formatVersion"), "formatVersion");
        rejects(o -> o.addProperty("enabled", "yes"), "true o false");
        rejects(o -> o.getAsJsonObject("xp").addProperty("xpPerLevel", "lots"), "entero");
        rejects(o -> o.getAsJsonObject("xp").addProperty("xpPerLevel", 0), "entre");
        rejects(o -> o.getAsJsonObject("xp").addProperty("boostCap", 1000), "boostCap");
        rejects(o -> o.getAsJsonObject("daily").addProperty("zone", "Mars/Base"), "zona");
        rejects(o -> o.getAsJsonObject("daily").addProperty("resetTime", "25:99"), "hora");
        rejects(o -> o.getAsJsonObject("season").addProperty("firstStart", "ayer"), "firstStart");
        assertThrows(IllegalArgumentException.class, () -> PaseConfig.parse("{not json"));
        assertThrows(IllegalArgumentException.class, () -> PaseConfig.parse("[]"));
    }

    @Test void missionsCannotPassThirtyFivePercentOfTheDailyCap() {
        rejects(o -> o.getAsJsonObject("missions").addProperty("dailyCap", 1401), "35 %");
        PaseConfig ok = PaseConfig.parse(edit(o -> o.getAsJsonObject("missions").addProperty("dailyCap", 1000)));
        assertEquals(1000, ok.rules().sourceCap("mission"));
    }

    @Test void aDisabledMissionTypeLeavesTheDraw() {
        PaseConfig c = PaseConfig.parse(edit(o -> o.getAsJsonObject("missions").getAsJsonObject("types").getAsJsonObject("fish").addProperty("enabled", false)));
        assertFalse(c.rules().missions().containsKey(MissionType.FISH));
        assertEquals(6, c.rules().missions().size());
    }

    @Test void sourcesCarryTheirOwnXpAndCap() {
        PaseConfig c = PaseConfig.parse(edit(o -> { o.getAsJsonObject("boss").addProperty("xp", 123); o.getAsJsonObject("raid").addProperty("enabled", false); }));
        assertEquals(123, c.source("boss").xp());
        assertFalse(c.source("raid").enabled());
        assertEquals(1500, c.source("weeklyRaid").dailyCap());
    }
}
