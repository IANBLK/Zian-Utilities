package com.zianblk.zianutilities.neoforge.pase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zianblk.zianutilities.core.pase.LevelEvent;
import com.zianblk.zianutilities.core.pase.PlayerPase;
import com.zianblk.zianutilities.core.pase.Season;
import com.zianblk.zianutilities.core.pase.SeasonBook;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

class PaseStateTest {
    private final UUID id = UUID.randomUUID();

    private PlayerPase sample() {
        PlayerPase p = new PlayerPase(id);
        p.progress("temporada-1").xp = 123_456;
        p.progress("temporada-2").xp = 160_000;
        p.progress("temporada-2").extraXp = 2_000;
        p.premium.add("temporada-2");
        p.events.add(new LevelEvent("temporada-2", 3, false, 111L));
        p.events.add(new LevelEvent("temporada-2", 1, true, 222L));
        p.explored.put("temporada-2", new java.util.LinkedHashSet<>(java.util.List.of("biome:minecraft:plains", "structure:minecraft:village_plains")));
        p.day = "2026-12-02";
        p.baseToday = 3000;
        p.finalToday = 6000;
        p.perSource.put("mission", 1000);
        p.missionProgress.put("FISH", 4);
        p.missionDone.add("TRADE");
        p.trades.add("TRADE:abc:2");
        return p;
    }

    @Test void aPlayerSurvivesAWriteAndARead() {
        PlayerPase back = PaseStateJson.readPlayer(PaseStateJson.writePlayer(sample()), id);
        assertEquals(sample().seasons.keySet(), back.seasons.keySet());
        assertEquals(123_456, back.xp("temporada-1"));
        assertEquals(2_000, back.progress("temporada-2").extraXp);
        assertEquals(sample().premium, back.premium);
        assertEquals(sample().events, back.events);
        assertEquals(sample().explored, back.explored);
        assertEquals("2026-12-02", back.day);
        assertEquals(3000, back.baseToday);
        assertEquals(6000, back.finalToday);
        assertEquals(sample().perSource, back.perSource);
        assertEquals(sample().missionProgress, back.missionProgress);
        assertEquals(sample().missionDone, back.missionDone);
        assertEquals(sample().trades, back.trades);
    }

    @Test void aFutureOrMissingFormatVersionIsRefusedAndNeverHalfRead() {
        String future = PaseStateJson.writePlayer(sample()).replace("\"formatVersion\": 1", "\"formatVersion\": 2");
        var e = assertThrows(IllegalArgumentException.class, () -> PaseStateJson.readPlayer(future, id));
        assertTrue(e.getMessage().contains("más nueva"), e.getMessage());
        String missing = PaseStateJson.writePlayer(sample()).replace("\"formatVersion\": 1,", "");
        assertThrows(IllegalArgumentException.class, () -> PaseStateJson.readPlayer(missing, id));
        String zero = PaseStateJson.writePlayer(sample()).replace("\"formatVersion\": 1", "\"formatVersion\": 0");
        assertThrows(IllegalArgumentException.class, () -> PaseStateJson.readPlayer(zero, id));
        assertThrows(IllegalArgumentException.class, () -> PaseStateJson.readSeasons("{\"formatVersion\":9,\"current\":{}}"));
    }

    @Test void aVersionOneFileWithoutTheFieldsAddedLaterStillLoadsWithDefaults() {
        PlayerPase p = PaseStateJson.readPlayer("{\"formatVersion\":1,\"id\":\"" + id + "\",\"seasons\":{\"temporada-1\":{\"xp\":800}}}", id);
        assertEquals(800, p.xp("temporada-1"));
        assertTrue(p.premium.isEmpty() && p.events.isEmpty() && p.explored.isEmpty());
        assertEquals("", p.day);
        assertFalse(p.locked);
    }

    @Test void aFileOfAnotherPlayerOrWithNegativeXpIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> PaseStateJson.readPlayer(PaseStateJson.writePlayer(sample()), UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> PaseStateJson.readPlayer("{\"formatVersion\":1,\"id\":\"" + id + "\",\"seasons\":{\"t\":{\"xp\":-5}}}", id));
    }

    @Test void seasonsRoundTripWithAndWithoutAPreviousOne() {
        Season cur = new Season("temporada-2", Instant.parse("2026-12-01T00:00:00Z"), Instant.parse("2027-02-01T00:00:00Z"));
        Season prev = new Season("temporada-1", Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-12-01T00:00:00Z"));
        SeasonBook both = PaseStateJson.readSeasons(PaseStateJson.writeSeasons(new SeasonBook(cur, prev)));
        assertEquals(cur, both.current());
        assertEquals(prev, both.previous());
        assertEquals(null, PaseStateJson.readSeasons(PaseStateJson.writeSeasons(new SeasonBook(cur, null))).previous());
    }

    @Test void theStoreWritesAtomicallyAndLeavesNoTemporaryFiles(@TempDir Path dir) throws Exception {
        var store = new PaseStore(dir, LoggerFactory.getLogger("test"));
        PlayerPase p = store.get(id);
        p.progress("temporada-1").xp = 900;
        p.dirty = true;
        assertTrue(store.flush());
        assertFalse(p.dirty);
        try (var files = Files.list(dir.resolve("players"))) {
            assertEquals(java.util.List.of(id + ".json"), files.map(f -> f.getFileName().toString()).toList());
        }
        store.release(id);
        assertEquals(900, new PaseStore(dir, LoggerFactory.getLogger("test")).get(id).xp("temporada-1"));
    }

    @Test void anUnreadablePlayerFileLocksThePlayerAndIsNeverOverwritten(@TempDir Path dir) throws Exception {
        var store = new PaseStore(dir, LoggerFactory.getLogger("test"));
        Path file = store.playerFile(id);
        Files.createDirectories(file.getParent());
        String future = PaseStateJson.writePlayer(sample()).replace("\"formatVersion\": 1", "\"formatVersion\": 5");
        Files.writeString(file, future);
        PlayerPase p = store.get(id);
        assertTrue(p.locked);
        p.dirty = true;
        store.flush();
        store.release(id);
        assertEquals(future, Files.readString(file));
        Files.writeString(file, "{ broken");
        var again = new PaseStore(dir, LoggerFactory.getLogger("test"));
        assertTrue(again.get(id).locked);
        again.flush();
        assertEquals("{ broken", Files.readString(file));
    }

    @Test void anUnreadableSeasonsFileThrowsInsteadOfStartingFresh(@TempDir Path dir) throws Exception {
        var store = new PaseStore(dir, LoggerFactory.getLogger("test"));
        assertEquals(null, store.loadSeasons());
        Files.writeString(store.seasonsFile(), "{\"formatVersion\":3,\"current\":{}}");
        assertThrows(java.io.IOException.class, store::loadSeasons);
    }
}
