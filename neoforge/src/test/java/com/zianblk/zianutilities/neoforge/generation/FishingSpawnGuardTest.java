package com.zianblk.zianutilities.neoforge.generation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class FishingSpawnGuardTest {
    @Test
    void nullActionIsNotClassifiedAsFishingPokemonAction() {
        assertFalse(FishingSpawnGuard.isControlledFishingAction(null));
        assertNull(FishingSpawnGuard.plannedSpeciesId(null));
    }
}
