package com.zianblk.zianutilities.neoforge.generation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class NaturalSpawnGuardTest {
    @Test
    void unknownSpawnerIsNotTreatedAsNatural() {
        assertFalse(NaturalSpawnGuard.isControlledNaturalSpawner(new Object()));
        assertFalse(NaturalSpawnGuard.isControlledNaturalSpawner(null));
    }
}
