package com.zianblk.zianutilities.core.raids.weekly

import com.zianblk.zianutilities.core.raids.weekly.WeeklyQueue.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class WeeklyQueueTest {
    private val a = UUID.randomUUID()
    private val b = UUID.randomUUID()
    private val c = UUID.randomUUID()
    private val everyone: (UUID) -> Boolean = { true }

    @Test fun `a free raid lets the first challenger in and queues the rest in order`() {
        val q = WeeklyQueue(30_000)
        assertEquals(Result(Entry.GRANTED, 0), q.request(a, 0))
        assertEquals(Result(Entry.ALREADY_PLAYING, 0), q.request(a, 1))
        assertEquals(Result(Entry.QUEUED, 1), q.request(b, 2))
        assertEquals(Result(Entry.QUEUED, 2), q.request(c, 3))
        assertEquals(Result(Entry.QUEUED, 2), q.request(c, 4))
        assertEquals(1, q.position(b)); assertEquals(-1, q.position(a))
    }

    @Test fun `when the player finishes the next one is called and then plays`() {
        val q = WeeklyQueue(30_000)
        q.request(a, 0); q.request(b, 0); q.request(c, 0)
        assertTrue(q.release(a, 1_000).isEmpty())
        assertTrue(q.tick(1_000, everyone, false).isEmpty()); assertNull(q.called())
        val events = q.tick(1_000, everyone, true)
        assertTrue(events.contains(Called(b))); assertTrue(events.contains(Moved(c, 1)))
        assertEquals(0, q.position(b)); assertEquals(31_000, q.calledUntil())
        assertEquals(Result(Entry.QUEUED, 1), q.request(c, 2_000))
        assertEquals(Result(Entry.GRANTED, 0), q.request(b, 2_000))
        assertEquals(b, q.playing())
    }

    @Test fun `a player who does not come in time is skipped and the next one is called`() {
        val q = WeeklyQueue(30_000)
        q.request(a, 0); q.request(b, 0); q.request(c, 0)
        q.release(a, 0); q.tick(0, everyone, true)
        assertTrue(q.tick(29_999, everyone, true).isEmpty())
        val events = q.tick(30_000, everyone, true)
        assertTrue(events.contains(TimedOut(b))); assertTrue(events.contains(Called(c)))
        assertEquals(c, q.called()); assertEquals(-1, q.position(b))
        assertEquals(60_000, q.calledUntil())
    }

    @Test fun `disconnected players leave the line and a disconnected called player passes the turn`() {
        val q = WeeklyQueue(30_000)
        q.request(a, 0); q.request(b, 0); q.request(c, 0)
        val offlineB: (UUID) -> Boolean = { it != b }
        val events = q.tick(10, offlineB, true)
        assertTrue(events.contains(Dropped(b))); assertEquals(1, q.position(c))
        q.release(a, 20); q.tick(20, everyone, true)
        assertEquals(c, q.called())
        val dropped = q.tick(30, { it != c }, true)
        assertTrue(dropped.contains(Dropped(c))); assertNull(q.called()); assertNull(q.playing())
    }

    @Test fun `leaving by command moves the others up`() {
        val q = WeeklyQueue(30_000)
        q.request(a, 0); q.request(b, 0); q.request(c, 0)
        val events = q.leave(b, 5)
        assertTrue(events.contains(Dropped(b))); assertTrue(events.contains(Moved(c, 1)))
        assertTrue(q.leave(b, 6).isEmpty())
    }

    @Test fun `a called player who leaves hands the turn over at once`() {
        val q = WeeklyQueue(30_000)
        q.request(a, 0); q.request(b, 0); q.request(c, 0)
        q.release(a, 0); q.tick(0, everyone, true)
        assertEquals(b, q.called())
        val events = q.leave(b, 1)
        assertTrue(events.contains(Dropped(b))); assertNull(q.called())
        assertTrue(q.tick(2, everyone, true).contains(Called(c))); assertEquals(c, q.called())
    }

    @Test fun `a free raid with someone being called still queues a newcomer`() {
        val q = WeeklyQueue(30_000)
        q.request(a, 0); q.request(b, 0)
        q.release(a, 0); q.tick(0, everyone, true)
        assertEquals(Result(Entry.QUEUED, 1), q.request(c, 1))
    }

    @Test fun `clear empties the line but keeps the player who is playing`() {
        val q = WeeklyQueue(30_000)
        q.request(a, 0); q.request(b, 0); q.request(c, 0)
        val events = q.clear()
        assertEquals(2, events.size); assertEquals(a, q.playing()); assertTrue(q.waiting().isEmpty())
    }

    @Test fun `a turn never starts counting while the boss is not ready`() {
        val q = WeeklyQueue(30_000)
        q.request(a, 0); q.request(b, 0)
        q.release(a, 0)
        assertTrue(q.tick(500_000, everyone, false).isEmpty()); assertNull(q.called())
        assertTrue(q.tick(500_000, everyone, true).contains(Called(b)))
        assertEquals(530_000, q.calledUntil())
    }

    @Test fun `release by someone who is not playing changes nothing`() {
        val q = WeeklyQueue(30_000)
        q.request(a, 0)
        assertTrue(q.release(b, 1).isEmpty()); assertEquals(a, q.playing())
        assertThrows(IllegalArgumentException::class.java) { WeeklyQueue(10) }
    }
}
