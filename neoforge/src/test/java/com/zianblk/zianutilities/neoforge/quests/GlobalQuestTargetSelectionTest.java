package com.zianblk.zianutilities.neoforge.quests;

import com.zianblk.zianutilities.core.quests.GlobalQuestOffer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GlobalQuestTargetSelectionTest {
    private static final List<String> GEN5 = List.of("cobblemon:lillipup", "cobblemon:patrat");

    @Test
    void newWindowUsesDifferentSpeciesWhenPoolHasAnAlternative() {
        long firstWindow = 1790485200000L;
        long nextWindow = firstWindow + 10_800_000L;
        String first = GlobalQuestRuntime.selectTarget(GEN5, firstWindow, "gen5", null);
        assertEquals("cobblemon:patrat", first);
        assertEquals("cobblemon:lillipup",
            GlobalQuestRuntime.selectTarget(GEN5, nextWindow, "gen5", first));
    }

    @Test
    void oneAvailableSpeciesMayRepeatAndEmptyPoolPausesCapture() {
        assertEquals("cobblemon:patrat", GlobalQuestRuntime.selectTarget(
            List.of("cobblemon:patrat"), 1790496000000L, "gen5", "cobblemon:patrat"));
        assertNull(GlobalQuestRuntime.selectTarget(List.of(), 1790496000000L, "", null));
    }

    @Test
    void testRotationChangesTargetWithoutMovingRealWindowOrAllowingPayment() {
        GlobalQuestOffer current = new GlobalQuestOffer(1790485200000L,
            "cobblemon:patrat", "gen5", "avecoins:coppercoin", 1,
            "avecoins:coppercoin", 1, true);
        GlobalQuestOffer rotated = GlobalQuestRuntime.testRotationOffer(current, GEN5);
        assertEquals("cobblemon:lillipup", rotated.getTargetSpecies());
        assertEquals(current.getWindowStartEpochMs(), rotated.getWindowStartEpochMs());
        assertFalse(rotated.getRewardsEnabled());
        assertThrows(IllegalStateException.class,
            () -> GlobalQuestRuntime.testRotationOffer(current, List.of("cobblemon:patrat")));
    }
}
