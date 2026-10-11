package com.zianblk.zianutilities.neoforge.gacha;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class GachaDrawTest {
    @Test
    void everyDrawChoosesOneConfiguredPrizeAndApproximateWeights() {
        Random random = new Random(24);
        int[] hits = new int[3];
        for (int i = 0; i < 100_000; i++) hits[GachaDraw.choose(List.of(1, 3, 6), random)]++;
        assertEquals(100_000, hits[0] + hits[1] + hits[2]);
        assertTrue(hits[0] > 9_000 && hits[0] < 11_000);
        assertTrue(hits[1] > 29_000 && hits[1] < 31_000);
        assertTrue(hits[2] > 59_000 && hits[2] < 61_000);
    }

    @Test
    void emptyAndInvalidPoolsCannotDraw() {
        assertThrows(IllegalArgumentException.class,
            () -> GachaDraw.choose(List.of(), new Random(1)));
        assertThrows(IllegalArgumentException.class,
            () -> GachaDraw.choose(List.of(1, 0), new Random(1)));
    }
}
