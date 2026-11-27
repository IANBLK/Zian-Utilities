package com.zianblk.zianutilities.core.pase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Everything stored about one player. Plain mutable data; the engine is the only one that changes it (and the storage codec). */
public final class PlayerPase {
  /** XP of one season: normal XP up to levels*xpPerLevel, then extra XP (only the current season earns it). */
  public static final class Progress {
    public long xp;
    public long extraXp;
  }

  public final UUID id;
  public final Map<String, Progress> seasons = new LinkedHashMap<>();
  /** Season ids with the paid track bought. P1 only keeps the flag; P2 delivers rewards and Tebex sets it. */
  public final Set<String> premium = new LinkedHashSet<>();
  public final List<LevelEvent> events = new ArrayList<>();
  /** Biomes and structures already counted, per season. */
  public final Map<String, Set<String>> explored = new LinkedHashMap<>();

  /** The game day (see the daily reset) the counters below belong to; any other day wipes them. */
  public String day = "";
  public long baseToday;
  public long finalToday;
  public final Map<String, Integer> perSource = new HashMap<>();
  public final Map<String, Integer> missionProgress = new HashMap<>();
  public final Set<String> missionDone = new HashSet<>();
  /** Today's distinct trades, "villager:level". */
  public final Set<String> trades = new HashSet<>();

  /** Set when the stored file could not be read: the engine then leaves the player alone and the file is never overwritten. */
  public boolean locked;
  public boolean dirty;

  public PlayerPase(UUID id) { this.id = id; }

  public Progress progress(String seasonId) { return seasons.computeIfAbsent(seasonId, k -> new Progress()); }

  public long xp(String seasonId) {
    Progress p = seasons.get(seasonId);
    return p == null ? 0 : p.xp;
  }

  public void clearDaily() {
    baseToday = 0;
    finalToday = 0;
    perSource.clear();
    missionProgress.clear();
    missionDone.clear();
    trades.clear();
  }
}
