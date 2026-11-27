package com.zianblk.zianutilities.core.pase;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Map;

/**
 * Every number the engine uses. Caps are in base XP (before the x2): the day total, and one per source. The x2 of the
 * last days is applied after the caps, and {@code boostCap} bounds the final XP of a boosted day.
 */
public record PaseRules(
    int levels, int xpPerLevel, int dailyCap, int boostCap, int boostFactor, int boostDays,
    int extraLevelXp, int maxExtraLevels, LocalTime resetTime, ZoneId zone, int seasonMonths,
    boolean streakEnabled, Map<String, Integer> sourceCaps, int missionsPerDay, Map<MissionType, MissionDef> missions
) {
  /** Missions may bring at most this share of the daily cap. */
  public static final double MISSION_SHARE = 0.35;
  public static final String MISSION = "mission";

  public PaseRules {
    if (levels < 1 || xpPerLevel < 1 || dailyCap < 1 || boostFactor < 1 || boostDays < 0 || extraLevelXp < 1 || maxExtraLevels < 0 || seasonMonths < 1)
      throw new IllegalArgumentException("valores fuera de rango");
    if (boostCap < dailyCap) throw new IllegalArgumentException("boostCap no puede ser menor que dailyCap");
    if (missionsPerDay < 0) throw new IllegalArgumentException("missionsPerDay negativo");
    sourceCaps = Map.copyOf(sourceCaps);
    missions = Map.copyOf(missions);
  }

  public static PaseRules defaults() {
    Map<MissionType, MissionDef> m = new EnumMap<>(MissionType.class);
    m.put(MissionType.BREAK_BLOCKS, new MissionDef(MissionType.BREAK_BLOCKS, 150, 350));
    m.put(MissionType.HARVEST_CROPS, new MissionDef(MissionType.HARVEST_CROPS, 80, 300));
    m.put(MissionType.KILL_MOBS, new MissionDef(MissionType.KILL_MOBS, 25, 350));
    m.put(MissionType.BREED_ANIMALS, new MissionDef(MissionType.BREED_ANIMALS, 3, 250));
    m.put(MissionType.TRADE, new MissionDef(MissionType.TRADE, 3, 300));
    m.put(MissionType.EXPLORE, new MissionDef(MissionType.EXPLORE, 2, 400));
    m.put(MissionType.FISH, new MissionDef(MissionType.FISH, 10, 300));
    return new PaseRules(200, 800, 4000, 8000, 2, 10, 1500, 100, LocalTime.of(4, 0), ZoneId.systemDefault(), 2, false,
        Map.of(MISSION, 1400, "boss", 1600, "raid", 1500, "weeklyRaid", 1500), 4, m);
  }

  public long totalXp() { return (long) levels * xpPerLevel; }

  /** Cap of a source in base XP; missions are further limited to their share of the daily cap. */
  public int sourceCap(String source) {
    int cap = sourceCaps.getOrDefault(source, dailyCap);
    return MISSION.equals(source) ? Math.min(cap, (int) Math.floor(MISSION_SHARE * dailyCap)) : cap;
  }

  /** The last {@code boostDays} days of the season, when XP is multiplied and the cap rises. */
  public boolean inBoostWindow(Season season, Instant now) {
    return boostDays > 0 && !now.isBefore(season.end().minus(java.time.Duration.ofDays(boostDays))) && now.isBefore(season.end());
  }

  public int dayFinalCap(boolean boost) { return boost ? boostCap : dailyCap; }
}
