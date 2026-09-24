package com.zianblk.zianutilities.neoforge.cobblemon;

import com.zianblk.zianutilities.core.generation.Generation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CobblemonGenerationMappingTest {
    @Test
    void canonicalGenerationLabelsResolve() {
        for (Generation generation : Generation.values()) {
            assertEquals(
                Set.of(generation),
                CobblemonGenerationMapping.resolveLabels(List.of(generation.getId()))
            );
        }
    }

    @Test
    void gen7bMapsToGeneration7() {
        assertEquals(
            Set.of(Generation.GEN_7),
            CobblemonGenerationMapping.resolveLabels(List.of("gen7b"))
        );
    }

    @Test
    void gen8aMapsToGeneration8() {
        assertEquals(
            Set.of(Generation.GEN_8),
            CobblemonGenerationMapping.resolveLabels(List.of("gen8a"))
        );
    }

    @Test
    void unknownLabelsAreIgnored() {
        assertEquals(
            Set.of(),
            CobblemonGenerationMapping.resolveLabels(List.of("future_generation", "regional"))
        );
    }

    @Test
    void mixedLabelsKeepAllRecognizedGenerations() {
        assertEquals(
            Set.of(Generation.GEN_2, Generation.GEN_7, Generation.GEN_9),
            CobblemonGenerationMapping.resolveLabels(
                List.of("gen2", "gen7b", "gen9", "unknown")
            )
        );
    }

    @Test
    void manualOverrideWinsOverLabels() {
        assertEquals(
            Set.of(Generation.GEN_4),
            CobblemonGenerationMapping.resolveWithOverride(
                List.of("gen1", "gen9"),
                Generation.GEN_4
            )
        );
    }

    @Test
    void normalizeLabelLeavesUnknownValuesUntouched() {
        assertEquals("gen7", CobblemonGenerationMapping.normalizeLabel("gen7b"));
        assertEquals("gen8", CobblemonGenerationMapping.normalizeLabel("gen8a"));
        assertEquals("other", CobblemonGenerationMapping.normalizeLabel("other"));
        assertNull(CobblemonGenerationMapping.normalizeLabel(null));
    }
}
