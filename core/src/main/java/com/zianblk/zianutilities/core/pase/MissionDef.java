package com.zianblk.zianutilities.core.pase;

/** One mission of the daily set: what to do, how many times, and the base XP paid when it is completed. */
public record MissionDef(MissionType type, int target, int xp) {
  public MissionDef {
    if (target < 1 || xp < 0) throw new IllegalArgumentException("target >= 1 y xp >= 0");
  }
}
