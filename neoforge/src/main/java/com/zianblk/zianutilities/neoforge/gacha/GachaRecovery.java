package com.zianblk.zianutilities.neoforge.gacha;

/** Classifies old and new journal phases without guessing the outcome of an external mutation. */
final class GachaRecovery {
    private GachaRecovery() {}

    static boolean isBlocking(String phase) {
        return switch (phase) {
            case "DEBIT_PENDING", "DEBIT_UNCERTAIN", "DELIVERING", "DELIVERY_UNCERTAIN", "RECOVERY_REQUIRED" -> true;
            default -> false;
        };
    }

    static boolean isDebitReview(String phase, String reason) {
        return phase.equals("DEBIT_PENDING") || phase.equals("DEBIT_UNCERTAIN")
            || phase.equals("RECOVERY_REQUIRED") && (reason.equals("avecoins_debit_unconfirmed")
                || reason.equals("unexpected_debit_result"));
    }

    static boolean isDeliveryReview(String phase, String reason) {
        return phase.equals("DELIVERING") || phase.equals("DELIVERY_UNCERTAIN")
            || phase.equals("RECOVERY_REQUIRED") && reason.isBlank();
    }
}
