package com.zianblk.zianutilities.neoforge.cobblemon;

import com.zianblk.zianutilities.core.generation.Generation;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public final class CobblemonGenerationMapping {
    private CobblemonGenerationMapping() {
    }

    public static String normalizeLabel(String raw) {
        if (raw == null) {
            return null;
        }

        return switch (raw) {
            case "gen7b" -> "gen7";
            case "gen8a" -> "gen8";
            default -> raw;
        };
    }

    public static Set<Generation> resolveLabels(Iterable<String> labels) {
        if (labels == null) {
            return Collections.emptySet();
        }

        EnumSet<Generation> resolved = EnumSet.noneOf(Generation.class);

        for (String label : labels) {
            String normalized = normalizeLabel(label);
            if (normalized == null) {
                continue;
            }

            Generation generation = Generation.Companion.fromCanonicalId(normalized);
            if (generation != null) {
                resolved.add(generation);
            }
        }

        return Collections.unmodifiableSet(resolved);
    }

    public static Set<Generation> resolveWithOverride(
        Iterable<String> labels,
        Generation override
    ) {
        if (override != null) {
            return Collections.singleton(override);
        }

        return resolveLabels(labels);
    }
}
