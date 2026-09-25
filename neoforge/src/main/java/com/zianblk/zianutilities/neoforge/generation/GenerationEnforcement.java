package com.zianblk.zianutilities.neoforge.generation;

import com.zianblk.zianutilities.core.generation.Generation;
import com.zianblk.zianutilities.core.generation.GenerationPolicy;
import com.zianblk.zianutilities.core.generation.GenerationState;
import com.zianblk.zianutilities.core.generation.SpawnDecision;
import com.zianblk.zianutilities.core.generation.UnknownSpeciesPolicy;

import java.util.Set;

/**
 * Shared Generation Control enforcement policy for controlled Cobblemon spawn paths.
 *
 * <p>Keeping the policy call here prevents natural, fishing, Poke Snack and
 * preselection guards from silently diverging if enforcement options change.</p>
 */
final class GenerationEnforcement {
    private GenerationEnforcement() {
    }

    static SpawnDecision decide(Set<Generation> resolved, GenerationState state) {
        return GenerationPolicy.INSTANCE.decide(
            resolved,
            state,
            null,
            UnknownSpeciesPolicy.DENY
        );
    }
}
