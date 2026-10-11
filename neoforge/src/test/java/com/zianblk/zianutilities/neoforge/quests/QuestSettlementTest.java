package com.zianblk.zianutilities.neoforge.quests;

import com.zianblk.zianutilities.core.rewards.ClaimStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

class QuestSettlementTest {
    @TempDir Path directory;

    @Test void disabledPaymentSurvivesRestartAsSkippedInsteadOfPaid() throws Exception {
        Properties state = new Properties();
        assertTrue(QuestSettlement.record(state, "capture", null, false));
        Path path = directory.resolve("quest.properties");
        try (var output = Files.newOutputStream(path)) { state.store(output, "test"); }
        Properties reloaded = new Properties();
        try (var input = Files.newInputStream(path)) { reloaded.load(input); }
        assertTrue(QuestSettlement.skipped(reloaded, "capture"));
        assertEquals("false", reloaded.getProperty("capture.paid"));
        assertEquals("SKIPPED_REWARDS_DISABLED", reloaded.getProperty("capture.settlement"));
    }

    @Test void onlyConfirmedPaymentIsMarkedPaid() {
        for (ClaimStatus status : new ClaimStatus[]{ClaimStatus.PENDING, ClaimStatus.REJECTED, ClaimStatus.RECOVERY_REQUIRED}) {
            Properties state = new Properties();
            assertFalse(QuestSettlement.record(state, "battle", status, false));
            assertNull(state.getProperty("battle.paid"));
        }
        Properties paid = new Properties();
        assertTrue(QuestSettlement.record(paid, "battle", ClaimStatus.CLAIMED, false));
        assertEquals("true", paid.getProperty("battle.paid"));
        assertFalse(QuestSettlement.skipped(paid, "battle"));
    }

    @Test void testWindowsAndCampaignChaptersHaveDistinctRecords() {
        Properties state = new Properties();
        QuestSettlement.record(state, "capture", null, true);
        QuestSettlement.record(state, "chapter.0", null, false);
        QuestSettlement.record(state, "chapter.1", ClaimStatus.CLAIMED, false);
        assertEquals("SKIPPED_TEST_WINDOW", state.getProperty("capture.settlement"));
        assertTrue(QuestSettlement.skipped(state, "chapter.0"));
        assertFalse(QuestSettlement.skipped(state, "chapter.1"));
    }
}
