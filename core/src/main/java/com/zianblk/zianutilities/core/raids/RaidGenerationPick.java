package com.zianblk.zianutilities.core.raids;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Predicate;

/** Re-draws a raid boss among candidates that pass the generation filter; empty when every one is denied. */
public final class RaidGenerationPick {
    private RaidGenerationPick() {}

    public static <T> Optional<T> reroll(List<T> candidates, Predicate<T> allowed, Random random) {
        List<T> ok = candidates.stream().filter(allowed).toList();
        return ok.isEmpty() ? Optional.empty() : Optional.of(ok.get(random.nextInt(ok.size())));
    }
}
