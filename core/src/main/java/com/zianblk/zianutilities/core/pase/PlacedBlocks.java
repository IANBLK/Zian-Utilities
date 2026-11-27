package com.zianblk.zianutilities.core.pase;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Blocks a player (or a fluid) put in the world, per chunk, so breaking them never pays XP. Bounded: each chunk and the
 * whole level keep at most a fixed number of marks (4 bytes each); past that new placements are no longer marked, which
 * only means a few extra blocks could count, never a crash or a huge file.
 */
public final class PlacedBlocks {
  public static final int MAX_PER_CHUNK = 8192;
  public static final int MAX_TOTAL = 2_000_000;

  private static final class Marks {
    int[] a = new int[8];
    int n;
  }

  private final Map<Long, Marks> chunks = new HashMap<>();
  private int total;

  public static long chunkKey(int x, int z) { return ((long) (x >> 4) << 32) | ((z >> 4) & 0xFFFFFFFFL); }

  /** Position inside its chunk packed in an int; y may be from -2048 to 2047 in range. */
  static int local(int x, int y, int z) { return ((y + 2048) << 8) | ((z & 15) << 4) | (x & 15); }

  /** True if the mark was stored (false when already marked or a bound was hit). */
  public boolean mark(int x, int y, int z) {
    if (total >= MAX_TOTAL) return false;
    Marks m = chunks.computeIfAbsent(chunkKey(x, z), k -> new Marks());
    int v = local(x, y, z);
    int i = Arrays.binarySearch(m.a, 0, m.n, v);
    if (i >= 0 || m.n >= MAX_PER_CHUNK) return false;
    i = -i - 1;
    if (m.n == m.a.length) m.a = Arrays.copyOf(m.a, m.n * 2);
    System.arraycopy(m.a, i, m.a, i + 1, m.n - i);
    m.a[i] = v;
    m.n++;
    total++;
    return true;
  }

  /** Removes the mark and says whether there was one: the block was placed by someone, so it does not count. */
  public boolean consume(int x, int y, int z) {
    Marks m = chunks.get(chunkKey(x, z));
    if (m == null) return false;
    int i = Arrays.binarySearch(m.a, 0, m.n, local(x, y, z));
    if (i < 0) return false;
    System.arraycopy(m.a, i + 1, m.a, i, m.n - i - 1);
    m.n--;
    total--;
    if (m.n == 0) chunks.remove(chunkKey(x, z));
    return true;
  }

  public boolean isMarked(int x, int y, int z) {
    Marks m = chunks.get(chunkKey(x, z));
    return m != null && Arrays.binarySearch(m.a, 0, m.n, local(x, y, z)) >= 0;
  }

  public int size() { return total; }

  public Set<Long> chunkKeys() { return Set.copyOf(chunks.keySet()); }

  public int[] marksOf(long chunk) {
    Marks m = chunks.get(chunk);
    return m == null ? new int[0] : Arrays.copyOf(m.a, m.n);
  }

  /** Loads a chunk's marks from storage (already sorted or not; duplicates and overflow are dropped). */
  public void load(long chunk, int[] values) {
    int[] sorted = Arrays.stream(values).distinct().sorted().toArray();
    int room = Math.min(MAX_PER_CHUNK, MAX_TOTAL - total);
    if (room <= 0 || sorted.length == 0) return;
    Marks m = new Marks();
    m.n = Math.min(room, sorted.length);
    m.a = Arrays.copyOf(sorted, Math.max(8, m.n));
    Marks old = chunks.put(chunk, m);
    total += m.n - (old == null ? 0 : old.n);
  }
}
