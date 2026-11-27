package com.zianblk.zianutilities.core.pase;

import java.util.List;

/** What a grant did. {@code applied} is final XP (after the x2); {@code limit} says what trimmed it, if anything. */
public record GrantResult(long applied, Limit limit, List<LevelEvent> levels, List<String> caps) {
  /** {@code caps} lists the caps reached by this grant that the player has not been told about today: "day" or a source name. */
  public GrantResult(long applied, Limit limit, List<LevelEvent> levels) { this(applied, limit, levels, List.of()); }

  public enum Limit { NONE, DAY, SOURCE, LOCKED }

  public static final GrantResult NOTHING = new GrantResult(0, Limit.NONE, List.of());

  public boolean any() { return applied > 0 || !levels.isEmpty(); }
}
