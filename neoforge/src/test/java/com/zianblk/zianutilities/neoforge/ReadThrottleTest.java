package com.zianblk.zianutilities.neoforge;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ReadThrottleTest {
    @Test void blocksBurstsPerPlayerAndReopensAfterTheInterval() {
        var throttle = new ReadThrottle(250);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        assertTrue(throttle.tryAcquire(a, 1_000));
        assertFalse(throttle.tryAcquire(a, 1_100));
        assertTrue(throttle.tryAcquire(b, 1_100));
        assertFalse(throttle.tryAcquire(a, 1_249));
        assertTrue(throttle.tryAcquire(a, 1_250));
    }

    @Test void aClockJumpBackwardsDoesNotLockThePlayerOut() {
        var throttle = new ReadThrottle(250);
        UUID a = UUID.randomUUID();
        assertTrue(throttle.tryAcquire(a, 10_000));
        assertTrue(throttle.tryAcquire(a, 5_000));
    }
}
