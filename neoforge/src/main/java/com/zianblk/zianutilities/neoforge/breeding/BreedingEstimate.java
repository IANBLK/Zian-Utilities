package com.zianblk.zianutilities.neoforge.breeding;

import java.util.*;

/** Statistical expectation only; never generates, reads or changes a reserved offspring. */
public final class BreedingEstimate {
  public record Power(int stat, int iv) {
    public Power {
      if (stat < 0 || stat >= 6 || iv < 0 || iv > 31) throw new IllegalArgumentException();
    }
  }

  public static List<Integer> ivs(
      List<Integer> a, List<Integer> b, boolean knot, List<Power> powers) {
    if (a.size() != 6 || b.size() != 6 || powers.size() > 2) throw new IllegalArgumentException();
    int count = knot ? 5 : 3;
    var result = new ArrayList<Integer>();
    for (int i = 0; i < 6; i++) {
      if (a.get(i) < 0 || a.get(i) > 31 || b.get(i) < 0 || b.get(i) > 31)
        throw new IllegalArgumentException();
      double mean = (a.get(i) + b.get(i)) / 2.0;
      double expected;
      if (powers.isEmpty()) expected = count / 6.0 * mean + (1 - count / 6.0) * 15.5;
      else {
        int matches = 0, sum = 0;
        for (var power : powers)
          if (power.stat() == i) {
            matches++;
            sum += power.iv();
          }
        double forced = matches / (double) powers.size();
        double other = (count - 1) / 5.0;
        expected =
            sum / (double) powers.size() + (1 - forced) * (other * mean + (1 - other) * 15.5);
      }
      result.add((int) Math.round(expected));
    }
    return List.copyOf(result);
  }

  /** Level one, zero EV and neutral nature; actual nature/IV rolls can differ. */
  public static List<Integer> stats(List<Integer> bases, List<Integer> ivs) {
    if (bases.size() != 6 || ivs.size() != 6) throw new IllegalArgumentException();
    var result = new ArrayList<Integer>();
    for (int i = 0; i < 6; i++)
      result.add(
          i == 0 && bases.get(i) == 1
              ? 1
              : (2 * bases.get(i) + ivs.get(i)) / 100 + (i == 0 ? 11 : 5));
    return List.copyOf(result);
  }
}
