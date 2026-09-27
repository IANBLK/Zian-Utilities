package com.zianblk.zianutilities.neoforge.gacha;

import java.util.List;
import java.util.random.RandomGenerator;

/** A single weighted outcome; weights must be positive. */
final class GachaDraw {
    private GachaDraw() {}

    static int choose(List<Integer> weights, RandomGenerator random) {
        if (weights.isEmpty() || weights.stream().anyMatch(w -> w == null || w <= 0))
            throw new IllegalArgumentException("Every prize needs a positive weight");
        long total = weights.stream().mapToLong(Integer::longValue).sum();
        long draw = random.nextLong(total);
        for (int i = 0; i < weights.size(); i++) {
            draw -= weights.get(i);
            if (draw < 0) return i;
        }
        throw new IllegalStateException("Weighted draw overflow");
    }
}
