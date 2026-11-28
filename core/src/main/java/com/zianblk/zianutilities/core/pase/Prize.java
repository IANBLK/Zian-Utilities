package com.zianblk.zianutilities.core.pase;

/**
 * One reward owed to one player, with its delivery journal. The id is stable ({@code season|track|level|key}, the key being the
 * reward id written in the season file), so the same reward never creates two prizes however the file is edited later. {@code reward} is a snapshot: editing the season file later does not change what is owed.
 */
public final class Prize {
  /** PENDING: owed. DELIVERING: written before the reward is given (a crash here means it may have been given). DELIVERED: done. UNCERTAIN: an admin must look. */
  public enum State { PENDING, DELIVERING, DELIVERED, UNCERTAIN }

  /** EXTRA is the paid-only roll of the extra levels; its level is the extra level number. */
  public enum Track { FREE, PREMIUM, EXTRA }

  public final String id, seasonId;
  public final Track track;
  public final int level;
  /** The reward id of the season file; old journals used the position in the list ("0", "1"), and the extra roll is "0". */
  public final String key;
  public final PaseReward reward;
  public State state = State.PENDING;
  /** Why it is still pending or uncertain (a code such as INVENTORY_FULL), empty otherwise. */
  public String reason = "";
  /** What the delivery decided and must repeat after a restart (the species chosen for a random Pokemon). */
  public String detail = "";
  public int attempts;
  public long updated;

  public Prize(String seasonId, Track track, int level, String key, PaseReward reward) {
    this.seasonId = seasonId;
    this.track = track;
    this.level = level;
    this.key = key;
    this.reward = reward;
    this.id = idOf(seasonId, track, level, key);
  }

  public static String idOf(String seasonId, Track track, int level, String key) {
    return levelKey(seasonId, track, level) + "|" + key;
  }

  /** season|track|level: what the legacy marker of a player refers to. */
  public static String levelKey(String seasonId, Track track, int level) {
    return seasonId + "|" + track.name().toLowerCase(java.util.Locale.ROOT) + "|" + level;
  }

  public boolean premiumOnly() { return track != Track.FREE; }
}
