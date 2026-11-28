package com.zianblk.zianutilities.core.pase;

import java.util.List;
import java.util.Map;

/** The rewards of one season: per level, two tracks, and the loot table of the extra levels (paid track only). */
public record SeasonRewards(int levelCap, Map<Integer, Level> levels, int extraEvery, List<LootTable.Entry> loot) {
  public record Level(List<PaseReward> free, List<PaseReward> premium) {
    public Level {
      free = List.copyOf(free);
      premium = List.copyOf(premium);
    }
  }

  public SeasonRewards {
    if (levelCap < 1 || levelCap > 100) throw PaseDataException.range("levelCap", 1, 100);
    if (extraEvery < 1) throw PaseDataException.range("every", 1, 1_000_000);
    levels = Map.copyOf(levels);
    loot = List.copyOf(loot);
  }

  public Level level(int n) { return levels.get(n); }
}
