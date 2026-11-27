package com.zianblk.zianutilities.neoforge.raids;

import com.necro.raid.dens.common.data.raid.RaidBoss;
import com.zianblk.zianutilities.core.generation.SpawnDecision;
import com.zianblk.zianutilities.neoforge.cobblemon.CobblemonGenerationResolver;
import com.zianblk.zianutilities.neoforge.generation.GenerationEnforcement;
import com.zianblk.zianutilities.neoforge.generation.NeoForgeGenerationStateStore;

/** Raid bosses and rewards obey the Generation Control filter, like breeding does. */
final class RaidGenerationFilter {
  private static final CobblemonGenerationResolver RESOLVER = new CobblemonGenerationResolver(null);

  private RaidGenerationFilter() {}

  static boolean enabled() { return RaidsModule.config != null && RaidsModule.config.respectGenerationFilter() && RaidsModule.server != null; }

  static boolean deniedSpecies(String speciesId) {
    if (!enabled() || speciesId == null) return false;
    return GenerationEnforcement.decide(RESOLVER.resolve(speciesId), new NeoForgeGenerationStateStore(RaidsModule.server).load())
        instanceof SpawnDecision.Deny;
  }

  /** The weekly raid always obeys the filter, whatever the duel config says. */
  static boolean deniedWeekly(RaidBoss boss) {
    return boss != null && deniedWeeklySpecies(boss.getBossProperties().getSpecies());
  }

  static boolean deniedWeeklySpecies(String speciesId) {
    if (speciesId == null || RaidsModule.server == null) return false;
    return GenerationEnforcement.decide(RESOLVER.resolve(speciesId), new NeoForgeGenerationStateStore(RaidsModule.server).load())
        instanceof SpawnDecision.Deny;
  }

  static boolean denied(RaidBoss boss) {
    return boss != null && deniedSpecies(boss.getBossProperties().getSpecies());
  }
}
