package com.zianblk.zianutilities.neoforge.quests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProgressionQuestScheduleTest {
    @Test
    void weeklyWindowUsesMondayMidnightInEcuadorAndNotPlayerLogin() {
        long beforeMonday = Instant.parse("2026-09-28T04:59:59Z").toEpochMilli();
        long monday = Instant.parse("2026-09-28T05:00:00Z").toEpochMilli();
        assertEquals(Instant.parse("2026-09-21T05:00:00Z").toEpochMilli(),
            ProgressionQuestRuntime.weekStart(beforeMonday));
        assertEquals(monday, ProgressionQuestRuntime.weekStart(monday));
        assertEquals(monday, ProgressionQuestRuntime.weekStart(
            Instant.parse("2026-10-04T23:59:59Z").toEpochMilli()));
    }

    @Test
    void campaignCollectsHalfTheNaturalSpeciesAcrossCumulativeStages() {
        assertEquals(73, ProgressionQuestRuntime.halfGoal(146));
        assertEquals(74, ProgressionQuestRuntime.halfGoal(147));
        assertEquals(3, ProgressionQuestRuntime.chapterGoal(0, 73));
        assertEquals(8, ProgressionQuestRuntime.chapterGoal(1, 73));
        assertEquals(73, ProgressionQuestRuntime.chapterGoal(2, 73));
        assertEquals(0, ProgressionQuestRuntime.chapterGoal(3, 73));
    }

    @Test
    void legacyChaptersRetainTheirEarnedCaptureCredit() {
        assertEquals(0, ProgressionQuestRuntime.legacyCompletedCount(0));
        assertEquals(3, ProgressionQuestRuntime.legacyCompletedCount(1));
        assertEquals(11, ProgressionQuestRuntime.legacyCompletedCount(2));
        assertEquals(26, ProgressionQuestRuntime.legacyCompletedCount(3));
    }

    @Test
    void migrationPreservesCurrentSpeciesAndNeverRepaysCompletedFinalStage(@TempDir Path temp)
        throws IOException {
        Properties secondStage = new Properties();
        secondStage.setProperty("acceptedAt", "1");
        secondStage.setProperty("chapter", "1");
        secondStage.setProperty("species", "cobblemon:pidgey,cobblemon:rattata");
        Path path = temp.resolve("stage2.properties");
        ProgressionQuestRuntime.migrateCampaign(path, secondStage, 146);
        assertEquals("73", secondStage.getProperty("goal.total"));
        assertEquals("3", secondStage.getProperty("legacy.count"));
        assertEquals("cobblemon:pidgey,cobblemon:rattata", secondStage.getProperty("species"));
        assertEquals("2", secondStage.getProperty("schema"));
        assertEquals(true, Files.exists(path));

        Properties finished = new Properties();
        finished.setProperty("acceptedAt", "1");
        finished.setProperty("chapter", "3");
        ProgressionQuestRuntime.migrateCampaign(temp.resolve("finished.properties"), finished, 146);
        assertEquals("2", finished.getProperty("chapter"));
        assertEquals("26", finished.getProperty("legacy.count"));
        assertEquals("true", finished.getProperty("legacy.finalPaid"));
    }
}
