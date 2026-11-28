package com.zianblk.zianutilities.core.pase;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;

/** The weighted roll of the extra levels. The same seed always gives the same entry, so claiming twice cannot change the result. */
public final class LootTable {
  public record Entry(int weight, PaseReward reward) {
    public Entry {
      if (weight < 1 || weight > 1_000_000) throw PaseDataException.range("weight", 1, 1_000_000);
    }
  }

  private LootTable() {}

  /** null when the table is empty. */
  public static PaseReward draw(List<Entry> entries, long seed) {
    long total = 0;
    for (Entry e : entries) total += e.weight();
    if (total <= 0) return null;
    long roll = new SplittableRandom(seed).nextLong(total);
    for (Entry e : entries) {
      roll -= e.weight();
      if (roll < 0) return e.reward();
    }
    throw new IllegalStateException("el sorteo no cayo en ninguna entrada");
  }

  /** Seed per player, season and extra level: stable across restarts and servers. */
  public static long seed(UUID player, String seasonId, int extraLevel) {
    try {
      byte[] h = MessageDigest.getInstance("SHA-256").digest(("pase-extra|" + player + "|" + seasonId + "|" + extraLevel).getBytes(StandardCharsets.UTF_8));
      long v = 0;
      for (int i = 0; i < 8; i++) v = (v << 8) | (h[i] & 0xFF);
      return v;
    } catch (NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }
}
