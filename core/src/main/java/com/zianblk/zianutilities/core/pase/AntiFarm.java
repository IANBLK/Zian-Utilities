package com.zianblk.zianutilities.core.pase;

import java.util.Set;

/** The farming rules that need no Minecraft types: which mobs count, and which spawn types are let through. */
public final class AntiFarm {
  /** Spawn types (by MobSpawnType name) whose mobs may count. Spawners, spawn eggs, commands, dispensers and the rest do not. */
  private static final Set<String> COUNTING = Set.of("NATURAL", "CHUNK_GENERATION", "STRUCTURE", "REINFORCEMENT", "EVENT", "PATROL", "JOCKEY");

  private AntiFarm() {}

  public static boolean spawnTypeCounts(String spawnType) { return COUNTING.contains(spawnType); }

  /** A kill counts when the mob did not come from a spawner or the like, has no name, is not leashed, and a real player did it. */
  public static boolean mobCounts(boolean flaggedAtSpawn, boolean named, boolean leashed, boolean realPlayerKill) {
    return realPlayerKill && !flaggedAtSpawn && !named && !leashed;
  }
}
