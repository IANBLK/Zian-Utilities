package com.zianblk.zianutilities.core.raids

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DuelEngineTest {
    private fun params(health: Double = 100.0, cap: Double = 0.15, seconds: Int = 30) =
        DuelParams(seconds, health, 8.0, 50, 20, cap, 60, DuelParams.defaultPhases())

    private fun engine(p: DuelParams = params()) = DuelEngine(p).also { it.start() }

    @Test fun `starts only once and ends in WON when the boss health reaches zero`() {
        val e = engine(params(health = 100.0, cap = 1.0))
        assertThrows(IllegalStateException::class.java) { e.start() }
        e.hit(60.0); assertEquals(DuelEngine.State.ACTIVE, e.state())
        e.hit(60.0)
        assertEquals(DuelEngine.State.ENDED, e.state()); assertEquals(DuelResult.WON, e.result()); assertEquals(1.0, e.dealtRatio(), 1e-9)
    }

    @Test fun `one hit never takes more than the cap so there are no one shots`() {
        val e = engine(params(health = 100.0, cap = 0.15))
        assertEquals(0.15, e.hit(10_000.0), 1e-9)
        assertEquals(0.85, e.bossHpFraction(), 1e-9)
        assertEquals(0.0, e.hit(Double.NaN)); assertEquals(0.0, e.hit(-5.0))
    }

    @Test fun `telegraph comes first and the strike lands after the telegraph delay`() {
        val e = engine()
        val events = mutableListOf<Pair<Int, DuelEngine.Event>>()
        for (t in 1..120) e.tick().forEach { events += t to it }
        val first = events[0]; val second = events[1]
        assertEquals(40, first.first); assertTrue(first.second is DuelEngine.Telegraph)
        assertEquals(60, second.first); assertTrue(second.second is DuelEngine.Strike)
        assertEquals(8.0, (second.second as DuelEngine.Strike).damage(), 1e-9)
    }

    @Test fun `phases make the boss hit harder and faster as health drops`() {
        val e = engine(params(health = 100.0, cap = 1.0))
        assertEquals(1.0, e.phase().damageMult()); e.hit(40.0)
        assertEquals(1.15, e.phase().damageMult(), 1e-9); e.hit(40.0)
        assertEquals(1.3, e.phase().damageMult(), 1e-9)
    }

    @Test fun `time limit ends the duel as TIMEOUT with the damage dealt`() {
        val e = engine(params(seconds = 10))
        e.hit(20.0)
        repeat(200) { e.tick() }
        assertEquals(DuelResult.TIMEOUT, e.result()); assertEquals(0.15 + 0.0, e.dealtRatio(), 0.2)
    }

    @Test fun `a hit that leaves one health or less is cancelled and the duel is a defeat with withdrawal`() {
        val e = engine()
        assertFalse(e.incomingDamage(20.0, 8.0)); assertEquals(DuelEngine.State.ACTIVE, e.state())
        assertFalse(e.incomingDamage(10.0, 8.9))
        assertTrue(e.incomingDamage(10.0, 9.0))
        assertTrue(e.lethalCapped()); assertEquals(DuelResult.LOST, e.result())
    }

    @Test fun `any lethal source is treated the same, including damage above the health`() {
        val e = engine()
        assertTrue(e.incomingDamage(3.0, 500.0)); assertEquals(DuelResult.LOST, e.result())
    }

    @Test fun `damage after the duel ended is ignored`() {
        val e = engine(); e.abort()
        assertFalse(e.incomingDamage(1.0, 50.0)); assertEquals(DuelResult.ABORTED, e.result())
    }

    @Test fun `uninterceptable death closes the duel as aborted and surrender and abort end only once`() {
        val a = engine(); a.playerDied(); a.surrender(); assertEquals(DuelResult.ABORTED, a.result())
        val b = engine(); b.surrender(); assertEquals(DuelResult.SURRENDERED, b.result())
        val c = engine(); c.abort(); c.surrender(); assertEquals(DuelResult.ABORTED, c.result())
    }

    @Test fun `invalid parameters are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { DuelParams(5, 100.0, 8.0, 50, 20, 0.15, 60, DuelParams.defaultPhases()) }
        assertThrows(IllegalArgumentException::class.java) { DuelParams(30, 100.0, 8.0, 50, 20, 0.0, 60, DuelParams.defaultPhases()) }
        assertThrows(IllegalArgumentException::class.java) { DuelParams(30, 100.0, 8.0, 50, 20, 0.5, 60, listOf(DuelPhase(0.5, 1.0, 1.0, 3.0))) }
    }
}
