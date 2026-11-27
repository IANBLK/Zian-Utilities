package com.zianblk.zianutilities.core.pase;

import java.util.Set;

/** The farming rules that need no Minecraft types: which mobs count, and which spawn types are let through. */
public final class AntiFarm {
  /** Spawn types (by MobSpawnType name) whose mobs get the natural mark. Everything else (spawners, trial spawners, eggs, commands, dispensers, breeding, conversion, reinforcements...) pays nothing. */
  private static final Set<String> NATURAL = Set.of("NATURAL", "CHUNK_GENERATION", "STRUCTURE", "PATROL", "JOCKEY");

  private AntiFarm() {}

  public static boolean spawnTypeCounts(String spawnType) { return NATURAL.contains(spawnType); }

  /** A kill counts only if the mob carries the natural mark, has no name, is not leashed, and a real player did it. */
  public static boolean mobCounts(boolean markedNatural, boolean named, boolean leashed, boolean realPlayerKill) {
    return realPlayerKill && markedNatural && !named && !leashed;
  }
}
