package com.zianblk.zianutilities.neoforge.raids;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * A raid duel is not a leader fight for Zian-RCT. RCT only counts an IndependentTrainerMob whose trainer id is in its
 * leader chain (rassvet_leader_*), and a duel creates no RCT battle at all. This keeps the ids Utilities uses for
 * raids from ever looking like a leader id; the runtime also cancels every Cobblemon battle start while dueling.
 */
class RaidIdsNeverLeaderTest {
    private static final Pattern LEADER = Pattern.compile("(?i).*rassvet_leader.*");

    @Test void raidIdentifiersAreNotLeaderIds() {
        assertFalse(LEADER.matcher(RaidsMixinPlugin.RAID_DENS).matches());
        assertFalse(LEADER.matcher(RaidsModule.RAID_CHALLENGE).matches());
        for (int i = 0; i < 100; i++) assertFalse(LEADER.matcher(UUID.randomUUID().toString()).matches());
    }
}
