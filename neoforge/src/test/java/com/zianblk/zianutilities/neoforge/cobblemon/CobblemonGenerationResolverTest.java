package com.zianblk.zianutilities.neoforge.cobblemon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CobblemonGenerationResolverTest {
    @Test
    void unqualifiedSpeciesDefaultsToCobblemonNamespace() {
        assertEquals(
            "cobblemon:magikarp",
            CobblemonGenerationResolver
                .normalizeSpeciesIdentifier("magikarp")
                .toString()
        );
    }

    @Test
    void qualifiedSpeciesKeepsItsNamespace() {
        assertEquals(
            "example:addonmon",
            CobblemonGenerationResolver
                .normalizeSpeciesIdentifier("example:addonmon")
                .toString()
        );
    }

    @Test
    void blankSpeciesIsRejected() {
        assertNull(CobblemonGenerationResolver.normalizeSpeciesIdentifier(""));
        assertNull(CobblemonGenerationResolver.normalizeSpeciesIdentifier("   "));
        assertNull(CobblemonGenerationResolver.normalizeSpeciesIdentifier(null));
    }
}
