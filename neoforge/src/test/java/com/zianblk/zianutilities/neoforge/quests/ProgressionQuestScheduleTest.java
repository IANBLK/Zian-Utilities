package com.zianblk.zianutilities.neoforge.quests;

import org.junit.jupiter.api.Test;

import java.time.Instant;

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
}
