package com.zianblk.zianutilities.core.breeding;

/** Single free-start timing policy; quotes never charge units with no useful reduction. */
public final class BreedingRules {
  private BreedingRules() {}

  public record Quote(long units, long reductionMillis, long nextReadyAtMillis) {}

  public static long readyAt(long start, long base, long minimum, long reduced) {
    if (start < 0 || base < minimum || minimum <= 0 || reduced < 0)
      throw new IllegalArgumentException("Invalid timing");
    return Math.addExact(start, Math.max(minimum, base - Math.min(base - minimum, reduced)));
  }

  public static Quote quote(
      long start, long base, long minimum, long reduced, long now, long perUnit, long units) {
    if (perUnit <= 0 || units <= 0 || now < 0)
      throw new IllegalArgumentException("Invalid acceleration");
    long before = readyAt(start, base, minimum, reduced),
        floor = Math.max(now, Math.addExact(start, minimum));
    long useful = Math.max(0, before - floor);
    if (useful == 0) throw new IllegalStateException("No useful reduction");
    long needed = useful / perUnit + (useful % perUnit == 0 ? 0 : 1);
    if (units > needed) throw new IllegalArgumentException("Excess units");
    long cut = units >= needed ? useful : Math.multiplyExact(units, perUnit);
    return new Quote(units, cut, before - cut);
  }
}
