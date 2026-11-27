package com.zianblk.zianutilities.core.raids.weekly

import com.zianblk.zianutilities.core.raids.weekly.WeeklySchedule.PickOrder
import com.zianblk.zianutilities.core.raids.weekly.WeeklySchedule.Rule
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class WeeklyScheduleTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val ids = listOf("a", "b", "c")

    private fun rule(zone: ZoneId = madrid, day: DayOfWeek = DayOfWeek.MONDAY, time: String = "04:00", order: PickOrder = PickOrder.SEQUENTIAL) =
        Rule(zone, day, LocalTime.parse(time), order)

    private fun at(text: String, zone: ZoneId = madrid): Instant = ZonedDateTime.parse("$text[${zone.id}]").toInstant()

    @Test fun `boundary is the last monday at 04 00 local`() {
        val r = rule()
        assertEquals(at("2026-11-23T04:00:00+01:00"), WeeklySchedule.boundaryAtOrBefore(at("2026-11-26T10:00:00+01:00"), r))
        assertEquals(at("2026-11-30T04:00:00+01:00"), WeeklySchedule.boundaryAfter(at("2026-11-26T10:00:00+01:00"), r))
    }

    @Test fun `exactly on the boundary belongs to the new week`() {
        val r = rule()
        val edge = at("2026-11-23T04:00:00+01:00")
        assertEquals(edge, WeeklySchedule.boundaryAtOrBefore(edge, r))
        assertEquals(at("2026-11-30T04:00:00+01:00"), WeeklySchedule.boundaryAfter(edge, r))
        assertEquals(at("2026-11-23T04:00:00+01:00").minusMillis(1).let { WeeklySchedule.boundaryAtOrBefore(it, r) }, at("2026-11-16T04:00:00+01:00"))
    }

    @Test fun `every day of the week and several zones`() {
        for (zone in listOf("Europe/Madrid", "UTC", "America/Mexico_City", "Asia/Kolkata", "Pacific/Auckland")) {
            val z = ZoneId.of(zone)
            for (day in DayOfWeek.values()) {
                val r = rule(z, day, "23:30")
                for (hours in 0L..400L step 7) {
                    val now = Instant.parse("2026-11-20T00:00:00Z").plusSeconds(hours * 3600)
                    val start = WeeklySchedule.boundaryAtOrBefore(now, r)
                    val end = WeeklySchedule.boundaryAfter(now, r)
                    assertFalse(start.isAfter(now), "$zone $day $now")
                    assertTrue(end.isAfter(now), "$zone $day $now")
                    val local = start.atZone(z)
                    assertEquals(day, local.dayOfWeek); assertEquals(LocalTime.of(23, 30), local.toLocalTime())
                    assertEquals(start.atZone(z).toLocalDate().plusWeeks(1), end.atZone(z).toLocalDate())
                }
            }
        }
    }

    @Test fun `the zone changes the instant of the same wall clock`() {
        val now = at("2026-11-26T12:00:00+00:00", ZoneId.of("UTC"))
        val utc = WeeklySchedule.boundaryAtOrBefore(now, rule(ZoneId.of("UTC")))
        val mexico = WeeklySchedule.boundaryAtOrBefore(now, rule(ZoneId.of("America/Mexico_City")))
        assertEquals(Instant.parse("2026-11-23T04:00:00Z"), utc)
        assertEquals(Instant.parse("2026-11-23T10:00:00Z"), mexico)
    }

    @Test fun `first week starts at the last boundary`() {
        val w = WeeklySchedule.first(rule(), ids, at("2026-11-26T10:00:00+01:00"), 7)
        assertEquals("a", w.activeId); assertEquals(1, w.weekNumber)
        assertEquals(at("2026-11-23T04:00:00+01:00"), w.startedAt); assertEquals(at("2026-11-30T04:00:00+01:00"), w.endsAt)
    }

    @Test fun `one millisecond before the end nothing changes and at the end it rotates once`() {
        val r = rule()
        val w = WeeklySchedule.first(r, ids, at("2026-11-26T10:00:00+01:00"), 7)
        assertFalse(WeeklySchedule.due(w, w.endsAt.minusMillis(1)))
        assertTrue(WeeklySchedule.due(w, w.endsAt))
        val next = WeeklySchedule.rotateAuto(r, w, ids, w.endsAt)
        assertEquals("b", next.activeId); assertEquals(2, next.weekNumber)
        assertEquals(w.endsAt, next.startedAt); assertEquals(at("2026-12-07T04:00:00+01:00"), next.endsAt)
    }

    @Test fun `a long shutdown rotates a single time and lands on the current week`() {
        val r = rule()
        val w = WeeklySchedule.first(r, ids, at("2026-11-26T10:00:00+01:00"), 7)
        val now = at("2026-12-20T09:00:00+01:00")
        val next = WeeklySchedule.rotateAuto(r, w, ids, now)
        assertEquals(2, next.weekNumber); assertEquals("b", next.activeId)
        assertEquals(at("2026-12-14T04:00:00+01:00"), next.startedAt); assertEquals(at("2026-12-21T04:00:00+01:00"), next.endsAt)
        assertFalse(WeeklySchedule.due(next, now))
    }

    @Test fun `sequential order loops and survives a list that lost the active raid`() {
        val r = rule()
        var w = WeeklySchedule.first(r, ids, at("2026-11-26T10:00:00+01:00"), 7)
        val seen = mutableListOf(w.activeId)
        repeat(4) { w = WeeklySchedule.rotateForced(r, w, ids, w.endsAt); seen += w.activeId }
        assertEquals(listOf("a", "b", "c", "a", "b"), seen)
        val shrunk = listOf("x", "y")
        val after = WeeklySchedule.rotateForced(r, w, shrunk, w.endsAt)
        assertTrue(after.activeId in shrunk)
    }

    @Test fun `random order never repeats the previous raid and is reproducible from the saved seed`() {
        val r = rule(order = PickOrder.RANDOM_NO_REPEAT)
        var w = WeeklySchedule.first(r, ids, at("2026-11-26T10:00:00+01:00"), 99)
        val again = WeeklySchedule.first(r, ids, at("2026-11-26T10:00:00+01:00"), 99)
        assertEquals(w, again)
        repeat(60) {
            val next = WeeklySchedule.rotateAuto(r, w, ids, w.endsAt)
            assertNotEquals(w.activeId, next.activeId)
            assertEquals(next, WeeklySchedule.rotateAuto(r, w, ids, w.endsAt))
            w = next
        }
        val single = WeeklySchedule.rotateAuto(r, WeeklySchedule.first(r, listOf("solo"), at("2026-11-26T10:00:00+01:00"), 1), listOf("solo"), at("2027-01-01T00:00:00+01:00"))
        assertEquals("solo", single.activeId)
    }

    @Test fun `forced rotation and fix end at the next scheduled boundary`() {
        val r = rule()
        val w = WeeklySchedule.first(r, ids, at("2026-11-24T10:00:00+01:00"), 7)
        val now = at("2026-11-25T12:00:00+01:00")
        val forced = WeeklySchedule.rotateForced(r, w, ids, now)
        assertEquals(now, forced.startedAt); assertEquals(w.endsAt, forced.endsAt); assertEquals("FORCED", forced.reason)
        val fixed = WeeklySchedule.fix(r, forced, ids, "c", now)
        assertEquals("c", fixed.activeId); assertEquals(3, fixed.weekNumber); assertEquals("MANUAL_SET", fixed.reason)
        assertThrows(IllegalArgumentException::class.java) { WeeklySchedule.fix(r, forced, ids, "zzz", now) }
    }

    @Test fun `changing the rule does not move the stored end of the active week`() {
        val w = WeeklySchedule.first(rule(), ids, at("2026-11-26T10:00:00+01:00"), 7)
        val other = rule(ZoneId.of("UTC"), DayOfWeek.FRIDAY, "12:00")
        assertFalse(WeeklySchedule.due(w, at("2026-11-29T10:00:00+01:00")))
        val next = WeeklySchedule.rotateAuto(other, w, ids, w.endsAt)
        assertEquals(w.endsAt, next.startedAt)
        assertTrue(next.endsAt.isAfter(w.endsAt))
    }

    @Test fun `spring forward week is 167 hours and autumn week is 169 hours`() {
        val r = rule(day = DayOfWeek.MONDAY, time = "04:00")
        val spring = WeeklySchedule.boundaryAtOrBefore(at("2026-03-25T10:00:00+01:00"), r)
        val springEnd = WeeklySchedule.boundaryAfter(spring, r)
        assertEquals(167, Duration.between(spring, springEnd).toHours())
        val autumn = WeeklySchedule.boundaryAtOrBefore(at("2026-10-21T10:00:00+02:00"), r)
        assertEquals(169, Duration.between(autumn, WeeklySchedule.boundaryAfter(autumn, r)).toHours())
        assertEquals(LocalTime.of(4, 0), springEnd.atZone(madrid).toLocalTime())
    }

    @Test fun `a boundary inside the spring gap or the autumn overlap still resolves`() {
        val gap = rule(day = DayOfWeek.SUNDAY, time = "02:30")
        val start = WeeklySchedule.boundaryAtOrBefore(at("2026-03-29T12:00:00+02:00"), gap)
        assertEquals(LocalTime.of(3, 30), start.atZone(madrid).toLocalTime())
        assertTrue(WeeklySchedule.boundaryAfter(start, gap).isAfter(start))
        val overlap = rule(day = DayOfWeek.SUNDAY, time = "02:30")
        val a = WeeklySchedule.boundaryAtOrBefore(at("2026-10-25T12:00:00+01:00"), overlap)
        assertTrue(WeeklySchedule.boundaryAfter(a, overlap).isAfter(a))
    }

    @Test fun `empty list is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { WeeklySchedule.first(rule(), emptyList(), Instant.EPOCH, 1) }
    }
}
