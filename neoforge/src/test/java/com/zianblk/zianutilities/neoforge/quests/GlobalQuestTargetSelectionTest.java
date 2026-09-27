package com.zianblk.zianutilities.neoforge.quests;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}
