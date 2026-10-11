package com.zianblk.zianutilities.neoforge.breeding;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class BreedingEstimateTest {
  @Test
  void knotRaisesMeanWithoutPromisingFivePerfectValuesFromOnePerfectParent() {
    var high = Collections.nCopies(6, 31);
    var low = Collections.nCopies(6, 0);
    assertEquals(Collections.nCopies(6, 16), BreedingEstimate.ivs(high, low, true, List.of()));
    assertEquals(Collections.nCopies(6, 28), BreedingEstimate.ivs(high, high, true, List.of()));
    assertEquals(Collections.nCopies(6, 23), BreedingEstimate.ivs(high, high, false, List.of()));
  }

  @Test
  void onePowerGuaranteesOnlyItsCarriersIvAndTwoPowersDoNotGuaranteeBoth() {
    var high = Collections.nCopies(6, 31);
    var low = Collections.nCopies(6, 0);
    assertEquals(
        31,
        BreedingEstimate.ivs(high, low, true, List.of(new BreedingEstimate.Power(1, 31))).get(1));
    assertEquals(
        23,
        BreedingEstimate.ivs(
                high,
                low,
                true,
                List.of(new BreedingEstimate.Power(1, 31), new BreedingEstimate.Power(2, 31)))
            .get(1));
    assertEquals(
        16,
        BreedingEstimate.ivs(
                high,
                low,
                true,
                List.of(new BreedingEstimate.Power(1, 31), new BreedingEstimate.Power(1, 0)))
            .get(1));
  }

  @Test
  void estimatesLevelOneRatherThanCopyingParentsCurrentStats() {
    assertEquals(
        List.of(12, 6, 6, 6, 6, 6),
        BreedingEstimate.stats(Collections.nCopies(6, 50), Collections.nCopies(6, 31)));
    assertEquals(
        1,
        BreedingEstimate.stats(List.of(1, 50, 50, 50, 50, 50), Collections.nCopies(6, 31)).get(0));
  }
}
