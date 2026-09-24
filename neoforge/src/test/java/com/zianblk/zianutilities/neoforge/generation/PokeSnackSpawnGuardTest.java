package com.zianblk.zianutilities.neoforge.generation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class PokeSnackSpawnGuardTest {
    @Test
    void nullActionIsNotClassifiedAsPokeSnackPokemonAction() {
        assertFalse(PokeSnackSpawnGuard.isControlledPokeSnackAction(null));
        assertNull(PokeSnackSpawnGuard.plannedSpeciesId(null));
    }
}
