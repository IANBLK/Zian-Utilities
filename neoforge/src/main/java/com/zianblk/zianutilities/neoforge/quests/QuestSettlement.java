package com.zianblk.zianutilities.neoforge.quests;

import com.zianblk.zianutilities.core.rewards.ClaimStatus;
import java.util.Properties;

/** Disabled rewards finish without payment, preserving the historical no-backpay policy. */
final class QuestSettlement {
    private QuestSettlement() {}

    static boolean skipped(Properties state, String objective) {
        return state.getProperty(objective + ".settlement", "").startsWith("SKIPPED_");
    }

    static boolean record(Properties state, String objective, ClaimStatus status, boolean testWindow) {
        if (status == ClaimStatus.CLAIMED) {
            state.setProperty(objective + ".paid", "true");
            state.setProperty(objective + ".settlement", "PAID");
            return true;
        }
        if (status == null) {
            state.setProperty(objective + ".paid", "false");
            state.setProperty(objective + ".settlement",
                testWindow ? "SKIPPED_TEST_WINDOW" : "SKIPPED_REWARDS_DISABLED");
            return true;
        }
        return false;
    }
}
