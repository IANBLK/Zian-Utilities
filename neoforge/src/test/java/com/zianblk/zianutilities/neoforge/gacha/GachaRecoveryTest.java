package com.zianblk.zianutilities.neoforge.gacha;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GachaRecoveryTest {
    @Test
    void legacyDebitAndDeliveryUncertaintyNeverShareAResolution() {
        assertTrue(GachaRecovery.isDebitReview("RECOVERY_REQUIRED", "avecoins_debit_unconfirmed"));
        assertFalse(GachaRecovery.isDeliveryReview("RECOVERY_REQUIRED", "avecoins_debit_unconfirmed"));
        assertTrue(GachaRecovery.isDebitReview("RECOVERY_REQUIRED", "unexpected_debit_result"));
        assertFalse(GachaRecovery.isDeliveryReview("RECOVERY_REQUIRED", "unexpected_debit_result"));
        assertTrue(GachaRecovery.isDeliveryReview("RECOVERY_REQUIRED", ""));
        assertFalse(GachaRecovery.isDebitReview("RECOVERY_REQUIRED", ""));
        assertFalse(GachaRecovery.isDebitReview("RECOVERY_REQUIRED", "unknown_reason"));
        assertFalse(GachaRecovery.isDeliveryReview("RECOVERY_REQUIRED", "unknown_reason"));
        assertTrue(GachaRecovery.isBlocking("RECOVERY_REQUIRED"));
    }

    @Test
    void newPhasesAreSeparatedAndTerminalPhasesDoNotReopen() {
        assertTrue(GachaRecovery.isDebitReview("DEBIT_PENDING", ""));
        assertTrue(GachaRecovery.isDebitReview("DEBIT_UNCERTAIN", ""));
        assertTrue(GachaRecovery.isDeliveryReview("DELIVERING", ""));
        assertTrue(GachaRecovery.isDeliveryReview("DELIVERY_UNCERTAIN", ""));
        assertFalse(GachaRecovery.isDebitReview("READY", ""));
        assertFalse(GachaRecovery.isDeliveryReview("REJECTED", ""));
        assertFalse(GachaRecovery.isBlocking("DELIVERED"));
    }
}
