package com.zianblk.zianutilities.neoforge.raids;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RaidRewardGateTest {
    private static RaidRewardGate.Probe probe(boolean denied, boolean live, boolean entry, boolean full) {
        return new RaidRewardGate.Probe() {
            public boolean deniedByGeneration() { return denied; }
            public boolean alreadyInStorage() { return live; }
            public boolean hasEntry() { return entry; }
            public boolean pcFull() { return full; }
        };
    }

    @Test void cleanRewardIsAllowed() {
        var outcome = RaidRewardGate.decide(probe(false, false, false, false));
        assertEquals(RaidRewardGate.Outcome.ALLOW, outcome);
        assertFalse(RaidRewardGate.cancels(outcome));
    }

    @Test void fullPcCancelsAndNeverAsksForAnEntry() {
        var outcome = RaidRewardGate.decide(probe(false, false, false, true));
        assertEquals(RaidRewardGate.Outcome.PC_FULL, outcome);
        assertTrue(RaidRewardGate.cancels(outcome));
    }

    @Test void aPokemonAlreadyInStorageOrWithAnEntryCancelsInAnyState() {
        assertEquals(RaidRewardGate.Outcome.DUPLICATE, RaidRewardGate.decide(probe(false, true, false, false)));
        assertEquals(RaidRewardGate.Outcome.DUPLICATE, RaidRewardGate.decide(probe(false, false, true, false)));
        assertEquals(RaidRewardGate.Outcome.DUPLICATE, RaidRewardGate.decide(probe(false, true, true, true)));
    }

    @Test void deniedGenerationCancels() {
        assertEquals(RaidRewardGate.Outcome.DENIED_GENERATION, RaidRewardGate.decide(probe(true, false, false, false)));
    }

    @Test void anyFailureWhileDecidingCancels() {
        var boom = new RaidRewardGate.Probe() {
            public boolean deniedByGeneration() { return false; }
            public boolean alreadyInStorage() throws Exception { throw new Exception("storage unavailable"); }
            public boolean hasEntry() { return false; }
            public boolean pcFull() { return false; }
        };
        var outcome = RaidRewardGate.decide(boom);
        assertEquals(RaidRewardGate.Outcome.ERROR, outcome);
        assertTrue(RaidRewardGate.cancels(outcome));
        var linkage = new RaidRewardGate.Probe() {
            public boolean deniedByGeneration() { throw new LinkageError("generation mod missing"); }
            public boolean alreadyInStorage() { return false; }
            public boolean hasEntry() { return false; }
            public boolean pcFull() { return false; }
        };
        assertTrue(RaidRewardGate.cancels(RaidRewardGate.decide(linkage)));
    }
}
