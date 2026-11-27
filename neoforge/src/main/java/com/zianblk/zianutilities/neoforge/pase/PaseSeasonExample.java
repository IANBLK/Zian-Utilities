package com.zianblk.zianutilities.neoforge.pase;

import com.zianblk.zianutilities.core.pase.LootTable;
import com.zianblk.zianutilities.core.pase.PaseReward;
import com.zianblk.zianutilities.core.pase.SeasonRewards;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The example season written to config/zianutilities-pase-temporada-1.json the first time. It is only a starting point for
 * the admin to edit: vanilla items on both tracks, AVECOINS every 10 levels, a few Pokemon at the big milestones (the paid
 * ones shiny or with perfect IVs) and the loot table of the extra levels. The example Pokemon stay at or under level 50 (levelCap).
 */
final class PaseSeasonExample {
  private static final String[][] FREE = {
      {"minecraft:bread", "16"}, {"minecraft:torch", "32"}, {"minecraft:cooked_beef", "12"}, {"minecraft:iron_ingot", "6"}, {"minecraft:coal", "24"},
      {"minecraft:arrow", "32"}, {"minecraft:experience_bottle", "6"}, {"minecraft:golden_carrot", "8"}, {"minecraft:cooked_salmon", "12"}, {"minecraft:glass", "32"}};
  private static final String[][] PREMIUM = {
      {"minecraft:diamond", "2"}, {"minecraft:golden_apple", "2"}, {"minecraft:ender_pearl", "4"}, {"minecraft:experience_bottle", "16"}, {"minecraft:emerald", "8"},
      {"minecraft:lapis_lazuli", "32"}, {"minecraft:name_tag", "2"}, {"minecraft:cooked_porkchop", "32"}, {"minecraft:iron_block", "2"}, {"minecraft:amethyst_shard", "16"}};

  private PaseSeasonExample() {}

  private static PaseReward pick(String[][] cycle, int level) {
    String[] e = cycle[(level - 1) % cycle.length];
    int count = Math.min(64, Integer.parseInt(e[1]) * (1 + level / 50));
    return PaseReward.item(e[0], count, null);
  }

  private static PaseReward pokemon(int level, int minPerfect, boolean shiny, PaseReward.Legendary legendary) {
    return PaseReward.pokemon(new PaseReward.PokemonSpec(null, List.of(), legendary, level, -1, minPerfect, false, shiny, false, false));
  }

  static SeasonRewards build(int levels) {
    Map<Integer, SeasonRewards.Level> map = new TreeMap<>();
    for (int level = 1; level <= levels; level++) {
      List<PaseReward> free = new ArrayList<>(), premium = new ArrayList<>();
      free.add(pick(FREE, level));
      premium.add(pick(PREMIUM, level));
      if (level % 10 == 0) {
        free.add(PaseReward.coins("avecoins:coppercoin", 32));
        premium.add(PaseReward.coins("avecoins:goldcoin", 4));
      }
      if (level % 25 == 0) {
        free.add(PaseReward.item("minecraft:diamond", level / 25 * 2, null));
        premium.add(PaseReward.item("minecraft:netherite_scrap", level / 25 * 2, null));
      }
      switch (level) {
        case 25 -> { free.add(pokemon(10, 0, false, PaseReward.Legendary.NONE)); premium.add(pokemon(20, 3, false, PaseReward.Legendary.NONE)); }
        case 50 -> { free.add(pokemon(20, 0, false, PaseReward.Legendary.NONE)); premium.add(pokemon(30, 4, false, PaseReward.Legendary.NONE)); }
        case 100 -> { free.add(pokemon(30, 0, false, PaseReward.Legendary.NONE)); premium.add(pokemon(40, 4, true, PaseReward.Legendary.NONE)); }
        case 150 -> { free.add(pokemon(35, 2, false, PaseReward.Legendary.NONE)); premium.add(pokemon(45, 5, true, PaseReward.Legendary.NONE)); }
        case 200 -> { free.add(pokemon(40, 3, false, PaseReward.Legendary.NONE)); premium.add(pokemon(50, 5, false, PaseReward.Legendary.ONLY)); }
        default -> { }
      }
      if (level % 50 == 0) premium.add(PaseReward.coins("avecoins:diamondcoin", level / 50));
      map.put(level, new SeasonRewards.Level(free, premium));
    }
    List<LootTable.Entry> loot = List.of(
        new LootTable.Entry(30, PaseReward.item("minecraft:diamond", 4, null)),
        new LootTable.Entry(30, PaseReward.item("minecraft:experience_bottle", 64, null)),
        new LootTable.Entry(20, PaseReward.item("minecraft:golden_apple", 4, null)),
        new LootTable.Entry(10, PaseReward.item("minecraft:netherite_ingot", 1, null)),
        new LootTable.Entry(25, PaseReward.coins("avecoins:goldcoin", 8)),
        new LootTable.Entry(15, PaseReward.coins("avecoins:diamondcoin", 3)),
        new LootTable.Entry(8, pokemon(40, 4, false, PaseReward.Legendary.NONE)),
        new LootTable.Entry(2, pokemon(40, 4, true, PaseReward.Legendary.NONE)));
    return new SeasonRewards(50, map, 5, loot);
  }

  static String json() { return PaseRewardJson.toJson(build(200)); }
}
