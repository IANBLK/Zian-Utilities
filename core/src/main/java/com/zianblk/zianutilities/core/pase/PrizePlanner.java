package com.zianblk.zianutilities.core.pase;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Turns progress into owed prizes. It looks at the XP, not at the level events, so a premium bought late, an old save and a
 * lost event all give the same result, and running it again never adds a prize twice (the id is the key).
 */
public final class PrizePlanner {
  private PrizePlanner() {}

  /**
   * Adds the missing prizes of the given seasons. A season whose rewards are not available ({@code rewardsOf} returns null)
   * is skipped and picked up the next time. Returns how many prizes were added.
   */
  public static int sync(PlayerPase p, PaseRules rules, List<String> seasonIds, Function<String, SeasonRewards> rewardsOf, long now) {
    int added = 0;
    for (String season : seasonIds) {
      SeasonRewards rewards = rewardsOf.apply(season);
      PlayerPase.Progress pr = p.seasons.get(season);
      if (rewards == null || pr == null) continue;
      boolean premium = p.premium.contains(season);
      long top = Math.min(rules.levels(), pr.xp / rules.xpPerLevel());
      for (int level = 1; level <= top; level++) {
        SeasonRewards.Level entry = rewards.level(level);
        if (entry == null) continue;
        added += add(p, season, Prize.Track.FREE, level, entry.free(), now);
        if (premium) added += add(p, season, Prize.Track.PREMIUM, level, entry.premium(), now);
      }
      if (!premium) continue;
      long extra = pr.extraXp / rules.extraLevelXp();
      for (int level = rewards.extraEvery(); level <= extra; level += rewards.extraEvery()) {
        if (p.prizes.containsKey(Prize.idOf(season, Prize.Track.EXTRA, level, "0"))) continue;
        PaseReward drawn = LootTable.draw(rewards.loot(), LootTable.seed(p.id, season, level));
        if (drawn != null) added += add(p, season, Prize.Track.EXTRA, level, List.of(drawn.withId("0")), now);
      }
    }
    if (added > 0) p.dirty = true;
    return added;
  }

  private static int add(PlayerPase p, String season, Prize.Track track, int level, List<PaseReward> rewards, long now) {
    int added = 0;
    if (p.legacyLevels.contains(Prize.levelKey(season, track, level))) return 0;
    for (PaseReward reward : rewards) {
      Prize z = new Prize(season, track, level, reward.id() == null ? "0" : reward.id(), reward);
      if (p.prizes.putIfAbsent(z.id, z) == null) {
        z.updated = now;
        added++;
      }
    }
    return added;
  }

  /** A refund: the paid prizes of that season that were not given yet are dropped. Given or uncertain ones stay for the admin. Returns how many. */
  public static int revokePremium(PlayerPase p, String seasonId) {
    List<String> drop = new ArrayList<>();
    for (Prize z : p.prizes.values()) if (z.seasonId.equals(seasonId) && z.premiumOnly() && z.state == Prize.State.PENDING) drop.add(z.id);
    drop.forEach(p.prizes::remove);
    // The paid levels are not "already planned" any more: a premium bought again must create their prizes.
    if (p.legacyLevels.removeIf(k -> k.startsWith(seasonId + "|premium|"))) p.dirty = true;
    if (!drop.isEmpty()) p.dirty = true;
    return drop.size();
  }

  /** Delivered prizes of seasons that can no longer produce prizes are forgotten; pending ones are never dropped. */
  public static void prune(PlayerPase p, List<String> keepSeasons) {
    if (p.prizes.values().removeIf(z -> z.state == Prize.State.DELIVERED && !keepSeasons.contains(z.seasonId))) p.dirty = true;
  }

  public static long pending(PlayerPase p) {
    return p.prizes.values().stream().filter(z -> z.state == Prize.State.PENDING).count();
  }
}
