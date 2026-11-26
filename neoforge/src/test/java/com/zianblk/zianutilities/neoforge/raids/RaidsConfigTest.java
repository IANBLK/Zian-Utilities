package com.zianblk.zianutilities.neoforge.raids;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RaidsConfigTest {
    @Test void defaultsAreOffAndStartAtTierFive() {
        var cfg = RaidsConfig.defaults();
        assertFalse(cfg.enabled());
        assertEquals(5, cfg.duelFromTier());
        assertTrue(cfg.respectGenerationFilter());
        assertTrue(cfg.safeRewardDelivery());
        assertEquals(240, cfg.paramsFor(5).timeLimitSeconds());
        assertEquals(7, cfg.tiers().size());
    }

    @Test void partialFileFillsMissingValuesFromDefaults() {
        var cfg = RaidsConfig.parse("{\"formatVersion\":1,\"enabled\":true,\"tiers\":{\"6\":{\"bossDamage\":12.5}}}");
        assertTrue(cfg.enabled());
        assertEquals(12.5, cfg.paramsFor(6).bossDamage());
        assertEquals(RaidsConfig.defaults().paramsFor(6).timeLimitSeconds(), cfg.paramsFor(6).timeLimitSeconds());
    }

    @Test void failsClosedOnAnythingItDoesNotUnderstand() {
        for (String bad : new String[] {
            "not json", "[]", "{}", "{\"formatVersion\":2}", "{\"formatVersion\":1,\"surprise\":true}",
            "{\"formatVersion\":1,\"enabled\":\"yes\"}", "{\"formatVersion\":1,\"duelFromTier\":9}",
            "{\"formatVersion\":1,\"rewardDelivery\":\"mail\"}", "{\"formatVersion\":1,\"tiers\":{\"x\":{}}}",
            "{\"formatVersion\":1,\"tiers\":{\"8\":{}}}", "{\"formatVersion\":1,\"tiers\":{\"5\":{\"timeLimitSeconds\":1}}}",
            "{\"formatVersion\":1,\"tiers\":{\"5\":{\"timeLimitSeconds\":\"a\"}}}", "{\"formatVersion\":1,\"tiers\":{\"5\":{\"nope\":1}}}",
            "{\"formatVersion\":1,\"tiers\":{\"5\":{\"phases\":[{\"hpBelow\":0.5,\"damageMult\":1,\"intervalMult\":1,\"radius\":3}]}}}",
        })
            assertThrows(IllegalArgumentException.class, () -> RaidsConfig.parse(bad), bad);
    }

    @Test void loadWritesDefaultsOnceAndNeverOverwritesABrokenFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config/zianutilities-raids.json");
        assertFalse(RaidsConfig.load(file).enabled());
        assertTrue(Files.exists(file));
        Files.writeString(file, "{\"formatVersion\":7}");
        assertThrows(IllegalArgumentException.class, () -> RaidsConfig.load(file));
        assertEquals("{\"formatVersion\":7}", Files.readString(file));
    }
}
