package com.zianblk.zianutilities.neoforge.quests;

import com.zianblk.zianutilities.core.quests.GlobalQuestOffer;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalQuestTargetSelectionTest {
    private static final List<String> GEN5 = List.of("cobblemon:lillipup", "cobblemon:patrat",
        "cobblemon:pidove", "cobblemon:roggenrola", "cobblemon:venipede", "cobblemon:woobat");

    @Test
    void randomTargetVariesWithoutRepeatingPreviousSpecies() {
        Random random = new Random(42);
        var observed = new HashSet<String>();
        for (int i = 0; i < 100; i++) {
            String target = GlobalQuestRuntime.selectTarget(GEN5, "cobblemon:patrat", random);
            assertNotEquals("cobblemon:patrat", target);
            observed.add(target);
        }
        assertEquals(5, observed.size());
    }

    @Test
    void oneAvailableSpeciesMayRepeatAndEmptyPoolPausesCapture() {
        assertEquals("cobblemon:patrat", GlobalQuestRuntime.selectTarget(
            List.of("cobblemon:patrat"), "cobblemon:patrat", new Random(1)));
        assertNull(GlobalQuestRuntime.selectTarget(List.of(), null, new Random(1)));
    }

    @Test
    void testRotationChangesTargetWithoutMovingRealWindowOrAllowingPayment() {
        GlobalQuestOffer current = new GlobalQuestOffer(1790485200000L,
            "cobblemon:patrat", "gen5", "avecoins:coppercoin", 1,
            "avecoins:coppercoin", 1, true);
        GlobalQuestOffer rotated = GlobalQuestRuntime.testRotationOffer(current, GEN5);
        assertNotEquals(current.getTargetSpecies(), rotated.getTargetSpecies());
        assertTrue(GEN5.contains(rotated.getTargetSpecies()));
        assertEquals(current.getWindowStartEpochMs(), rotated.getWindowStartEpochMs());
        assertFalse(rotated.getRewardsEnabled());
        assertThrows(IllegalStateException.class,
            () -> GlobalQuestRuntime.testRotationOffer(current, List.of("cobblemon:patrat")));
    }

    @Test
    void legacyDefaultPoolsExpandButCustomPoolsStayUntouched() {
        Properties config = new Properties();
        config.setProperty("species.gen5", "cobblemon:patrat,cobblemon:lillipup");
        config.setProperty("species.gen7", "cobblemon:mudbray,cobblemon:komala");
        GlobalQuestRuntime.upgradeLegacyPools(config);
        assertTrue(config.getProperty("species.gen5").split(",").length > 2);
        assertEquals("cobblemon:mudbray,cobblemon:komala", config.getProperty("species.gen7"));
    }
}
