package com.zianblk.zianutilities.core.raids.weekly

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class WeeklyCountsTest {
    private val a = UUID.randomUUID()
    private val b = UUID.randomUUID()

    @Test fun `attempt number N plus one is refused and other players are not affected`() {
        val c = WeeklyCounts()
        repeat(3) { assertTrue(c.attemptsLeft(a, 3)); c.addAttempt(a) }
        assertFalse(c.attemptsLeft(a, 3)); assertTrue(c.attemptsLeft(b, 3))
        assertEquals(3, c.of(a).attempts)
    }

    @Test fun `rewards have their own limit and zero means unlimited`() {
        val c = WeeklyCounts()
        assertTrue(c.rewardsLeft(a, 1)); c.addReward(a); assertFalse(c.rewardsLeft(a, 1))
        repeat(50) { c.addAttempt(a); c.addReward(a) }
        assertTrue(c.attemptsLeft(a, 0)); assertTrue(c.rewardsLeft(a, 0))
    }

    @Test fun `reset frees a single player and clear a new week`() {
        val c = WeeklyCounts()
        c.addAttempt(a); c.addAttempt(b)
        assertTrue(c.reset(a)); assertFalse(c.reset(a))
        assertEquals(0, c.of(a).attempts); assertEquals(1, c.of(b).attempts)
        c.clear(); assertTrue(c.all().isEmpty())
        assertThrows(IllegalArgumentException::class.java) { c.set(a, -1, 0) }
    }
}
