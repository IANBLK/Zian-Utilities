package com.zianblk.zianutilities.core.pase

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.UUID

class PaseEngineTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val a = UUID.randomUUID()

    /** Mutable clock for the tests. */
    private class Fake(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
    }

    private class Mem : PaseEngine.Store {
        val map = HashMap<UUID, PlayerPase>()
        override fun get(id: UUID): PlayerPase = map.computeIfAbsent(id) { PlayerPase(it) }
    }

    private fun at(text: String): Instant = ZonedDateTime.parse("$text[Europe/Madrid]").toInstant()

    private fun custom(
        sources: Map<String, Int> = mapOf(), maxExtra: Int = 100, perDay: Int = 4,
        missions: Map<MissionType, MissionDef> = PaseRules.defaults().missions(),
    ): PaseRules {
        val it = PaseRules.defaults()
        return PaseRules(it.levels(), it.xpPerLevel(), it.dailyCap(), it.boostCap(), it.boostFactor(), it.boostDays(), it.extraLevelXp(), maxExtra,
            it.resetTime(), madrid, 2, false, sources, perDay, missions)
    }

    private fun engine(
        clock: Fake, rules: PaseRules = custom(), start: String = "2026-12-01T00:00:00+01:00", days: Long = 60,
        previous: Boolean = false, mem: Mem = Mem(),
    ): PaseEngine {
        val s = at(start)
        val cur = Season("temporada-2", s, s.plus(Duration.ofDays(days)))
        val prev = if (previous) Season("temporada-1", s.minus(Duration.ofDays(60)), s) else null
        return PaseEngine(rules, SeasonBook(cur, prev), mem, clock)
    }

    @Test fun `daily cap trims the grant and the next game day starts again`() {
        val c = Fake(at("2026-12-02T10:00:00+01:00")); val e = engine(c)
        assertEquals(3000, e.grant(a, "x", 3000).applied())
        val r = e.grant(a, "x", 2000)
        assertEquals(1000, r.applied()); assertEquals(GrantResult.Limit.DAY, r.limit())
        assertEquals(0, e.grant(a, "x", 10).applied())
        c.now = at("2026-12-03T10:00:00+01:00")
        assertEquals(4000, e.grant(a, "x", 9000).applied())
    }

    @Test fun `a source cap limits that source and leaves the rest of the day for the others`() {
        val c = Fake(at("2026-12-02T10:00:00+01:00")); val e = engine(c, custom(mapOf("boss" to 1600)))
        val r = e.grant(a, "boss", 2000)
        assertEquals(1600, r.applied()); assertEquals(GrantResult.Limit.SOURCE, r.limit())
        assertEquals(0, e.grant(a, "boss", 1).applied())
        assertEquals(2400, e.grant(a, "other", 5000).applied())
    }

    @Test fun `missions never bring more than 35 percent of the daily cap even if configured higher`() {
        val c = Fake(at("2026-12-02T10:00:00+01:00")); val e = engine(c, custom(mapOf("mission" to 4000)))
        assertEquals(1400, e.grant(a, "mission", 4000).applied())
        assertEquals(0, e.grant(a, "mission", 1).applied())
    }

    @Test fun `the daily reset happens at the configured hour in the configured zone`() {
        val c = Fake(at("2026-12-02T03:59:00+01:00")); val e = engine(c)
        e.grant(a, "x", 4000)
        c.now = at("2026-12-02T04:01:00+01:00")
        assertEquals(4000, e.grant(a, "x", 4000).applied())
        c.now = at("2026-12-03T03:59:30+01:00")
        assertEquals(0, e.grant(a, "x", 100).applied())
        c.now = at("2026-12-03T04:00:00+01:00")
        assertEquals(100, e.grant(a, "x", 100).applied())
    }

    @Test fun `a clock that goes back does not hand out a second day`() {
        val c = Fake(at("2026-12-05T10:00:00+01:00")); val e = engine(c)
        e.grant(a, "x", 4000)
        c.now = at("2026-12-04T10:00:00+01:00")
        assertEquals(0, e.grant(a, "x", 100).applied())
    }

    @Test fun `x2 only inside the last ten days and the cap rises with it`() {
        val c = Fake(at("2026-12-02T10:00:00+01:00")); val e = engine(c)
        assertEquals(100, e.grant(a, "x", 100).applied())
        // The season ends 2027-01-30 00:00 (+01), 60 days after 12-01; the window opens ten days earlier, on 2027-01-20 00:00.
        c.now = at("2027-01-19T23:59:00+01:00")
        assertEquals(100, e.grant(a, "x", 100).applied())
        c.now = at("2027-01-20T10:00:00+01:00")
        assertEquals(200, e.grant(a, "x", 100).applied())
        assertEquals(8000 - 200, e.view(a).capLeft())
        e.grant(a, "x", 9000)
        assertEquals(0, e.view(a).capLeft())
    }

    @Test fun `a boosted day reaches exactly the boost cap and not more`() {
        val c = Fake(at("2027-01-25T10:00:00+01:00")); val e = engine(c)
        assertEquals(8000, e.grant(a, "x", 100000).applied())
        assertEquals(0, e.grant(a, "x", 1).applied())
    }

    @Test fun `after the season ends the next one starts without doubling`() {
        val c = Fake(at("2027-01-31T10:00:00+01:00")); val e = engine(c)
        assertEquals(100, e.grant(a, "x", 100).applied())
        assertFalse(e.view(a).boost())
    }

    @Test fun `previous season is filled first and the overflow goes to the current one`() {
        val c = Fake(at("2026-12-02T10:00:00+01:00")); val mem = Mem()
        val e = engine(c, previous = true, mem = mem)
        mem.get(a).progress("temporada-1").xp = 159_000
        val r = e.grant(a, "x", 3000)
        assertEquals(3000, r.applied())
        assertEquals(160_000, mem.get(a).xp("temporada-1"))
        assertEquals(2_000, mem.get(a).xp("temporada-2"))
        assertEquals("temporada-1", r.levels().first().seasonId())
        assertEquals("temporada-2", r.levels().last().seasonId())
        assertEquals(listOf(199, 200), r.levels().filter { it.seasonId() == "temporada-1" }.map { it.level() })
    }

    @Test fun `extra levels need the pass done, cost 1500, are never doubled and only the current season gets them`() {
        val c = Fake(at("2027-01-25T10:00:00+01:00")); val mem = Mem()
        val e = engine(c, previous = true, mem = mem)
        mem.get(a).progress("temporada-1").xp = 160_000
        mem.get(a).progress("temporada-2").xp = 160_000 - 800
        // 8000 final XP on a boosted day: 800 finish the pass, 7200 final = 3600 base = 2 extra levels of 1500 and 600 of the third.
        val r = e.grant(a, "x", 100000)
        assertEquals(160_000, mem.get(a).xp("temporada-2"))
        assertEquals(3_600, mem.get(a).progress("temporada-2").extraXp)
        assertEquals(0, mem.get(a).progress("temporada-1").extraXp)
        assertEquals(listOf(1, 2), r.levels().filter { it.extra() }.map { it.level() })
        assertTrue(r.levels().none { it.seasonId() == "temporada-1" && it.extra() })
    }

    @Test fun `the extra levels stop at the configured maximum`() {
        val c = Fake(at("2026-12-02T10:00:00+01:00")); val mem = Mem()
        val e = engine(c, custom(maxExtra = 2), mem = mem)
        mem.get(a).progress("temporada-2").xp = 160_000
        e.grant(a, "x", 4000)
        assertEquals(3000, mem.get(a).progress("temporada-2").extraXp)
        c.now = at("2026-12-03T10:00:00+01:00")
        assertEquals(0, e.grant(a, "x", 100).applied())
    }

    @Test fun `crossing levels creates one event per level and survives several at once`() {
        val c = Fake(at("2026-12-02T10:00:00+01:00")); val e = engine(c)
        val r = e.grant(a, "x", 2500)
        assertEquals(listOf(1, 2, 3), r.levels().map { it.level() })
        assertEquals(3, e.pendingEvents(a).size)
        assertEquals(1, e.grant(a, "x", 800).levels().size)
    }

    @Test fun `admin xp ignores caps and doubling and admin remove takes extra first`() {
        val c = Fake(at("2027-01-25T10:00:00+01:00")); val mem = Mem(); val e = engine(c, mem = mem)
        assertEquals(50_000, e.adminGrant(a, 50_000).applied())
        assertEquals(0, mem.get(a).baseToday)
        mem.get(a).progress("temporada-2").extraXp = 700
        assertEquals(1500, e.adminRemove(a, 1500))
        assertEquals(50_000 - 800, mem.get(a).xp("temporada-2"))
        assertEquals(0, mem.get(a).progress("temporada-2").extraXp)
        assertEquals(3, e.adminGiveLevels(a, 3).levels().size)
    }

    @Test fun `a season rolls to the next and only one stays behind`() {
        val c = Fake(at("2027-01-31T10:00:00+01:00")); val e = engine(c, previous = true)
        e.grant(a, "x", 100)
        assertEquals("temporada-3", e.seasons().current().id())
        assertEquals("temporada-2", e.seasons().previous()?.id())
        assertTrue(e.seasons().dirty)
        val n = e.newSeason()
        assertEquals("temporada-4", n.id())
        assertEquals("temporada-3", e.seasons().previous()?.id())
    }

    @Test fun `the missions are the same set for every player on a given day and different days differ`() {
        val r = custom()
        val d1 = java.time.LocalDate.of(2026, 12, 2)
        assertEquals(MissionBoard.forDay(d1, r), MissionBoard.forDay(d1, r))
        assertEquals(4, MissionBoard.forDay(d1, r).size)
        assertTrue((0..30).map { MissionBoard.forDay(d1.plusDays(it.toLong()), r).map { m -> m.type() } }.toSet().size > 3)
    }

    private fun todayTypes(e: PaseEngine, id: UUID) = e.view(id).missions().map { it.type() }

    @Test fun `a mission pays once when completed and progress is lost the next day`() {
        val c = Fake(at("2026-12-02T10:00:00+01:00")); val e = engine(c)
        val t = todayTypes(e, a).first()
        val def = e.rules().missions()[t]!!
        var last: PaseEngine.MissionResult? = null
        repeat(def.target() + 5) { e.progress(a, t, 1, null)?.let { last = it } }
        assertTrue(last!!.completed())
        assertEquals(def.xp().toLong(), last!!.grant().applied())
        assertNull(e.progress(a, t, 1, null))
        assertTrue(e.view(a).missions().first { it.type() == t }.done())
        c.now = at("2026-12-03T10:00:00+01:00")
        val line = e.view(a).missions().firstOrNull { it.type() == t }
        if (line != null) { assertEquals(0, line.progress()); assertFalse(line.done()) }
    }

    @Test fun `a mission that is not on the board of the day gives nothing`() {
        val c = Fake(at("2026-12-02T10:00:00+01:00")); val e = engine(c)
        val off = MissionType.entries.first { it !in todayTypes(e, a) }
        assertNull(e.progress(a, off, 100, null))
    }

    @Test fun `distinct keys count once a day (trades with the same villager and level)`() {
        val c = Fake(at("2026-12-02T10:00:00+01:00"))
        val e = engine(c, custom(perDay = 7))
        assertEquals(1, e.progress(a, MissionType.TRADE, 1, "v1:1")!!.progress())
        assertNull(e.progress(a, MissionType.TRADE, 1, "v1:1"))
        assertEquals(2, e.progress(a, MissionType.TRADE, 1, "v1:2")!!.progress())
        assertEquals(3, e.progress(a, MissionType.TRADE, 1, "v2:1")!!.progress())
        c.now = at("2026-12-03T10:00:00+01:00")
        assertEquals(1, e.progress(a, MissionType.TRADE, 1, "v1:1")!!.progress())
    }

    @Test fun `each biome or structure counts once per season`() {
        val c = Fake(at("2026-12-02T10:00:00+01:00"))
        val ms = PaseRules.defaults().missions().mapValues { if (it.key == MissionType.EXPLORE) MissionDef(MissionType.EXPLORE, 5, 400) else it.value }
        val e = engine(c, custom(perDay = 7, missions = ms))
        assertEquals(1, e.explore(a, "biome:plains")!!.progress())
        assertNull(e.explore(a, "biome:plains"))
        c.now = at("2026-12-03T10:00:00+01:00")
        assertNull(e.explore(a, "biome:plains"))
        assertEquals(1, e.explore(a, "structure:village")!!.progress())
    }

    @Test fun `a locked player is left alone`() {
        val c = Fake(at("2026-12-02T10:00:00+01:00")); val mem = Mem(); val e = engine(c, mem = mem)
        mem.get(a).locked = true
        assertEquals(GrantResult.Limit.LOCKED, e.grant(a, "x", 100).limit())
        assertNull(e.view(a))
    }

    @Test fun `premium flag is stored per season`() {
        val c = Fake(at("2026-12-02T10:00:00+01:00")); val e = engine(c)
        assertTrue(e.setPremium(a, "temporada-2", true))
        assertFalse(e.setPremium(a, "temporada-2", true))
        assertTrue(e.view(a).premium())
        assertTrue(e.setPremium(a, "temporada-2", false))
        assertFalse(e.view(a).premium())
    }

    // ---------------------------------------------------------------- simulator equivalence

    /** Same deterministic profiles as PaseSim (60 days, x2 in the last 10, cap 4000/8000, 800 per level), played day by day. */
    private fun finishDay(xpPerDay: (Int) -> Int): Int {
        val c = Fake(at("2026-12-01T10:00:00+01:00")); val mem = Mem()
        val s = at("2026-12-01T00:00:00+01:00")
        val e = PaseEngine(custom(), SeasonBook(Season("temporada-1", s, s.plus(Duration.ofDays(60))), null), mem, c)
        for (d in 1..60) {
            c.now = s.plus(Duration.ofDays((d - 1).toLong())).plus(Duration.ofHours(10))
            val want = xpPerDay(d)
            if (want > 0) e.grant(a, "x", want)
            if (mem.get(a).xp("temporada-1") >= 160_000) return d
        }
        return -1
    }

    @Test fun `matches PaseSim - dedicated finishes on day 40 and constant on day 45`() {
        assertEquals(40, finishDay { 4000 })
        assertEquals(45, finishDay { 3600 })
    }

    @Test fun `matches PaseSim - a fixed five days a week profile gets the doubling help`() {
        // Independent recomputation of the PaseSim rule: base = min(cap, xp); final = base * 2 in the last 10 days; five days of seven.
        var xp = 0L
        var expected = -1
        for (d in 1..60) {
            if ((d - 1) % 7 >= 5) continue
            val base = minOf(4000, 3600)
            xp += if (d > 50) minOf(base * 2, 8000) else base
            if (xp >= 160_000 && expected < 0) expected = d
        }
        assertEquals(expected, finishDay { d -> if ((d - 1) % 7 < 5) 3600 else 0 })
        assertEquals(-1, finishDay { d -> if ((d - 1) % 7 < 3) 3000 else 0 })
    }

    // ---------------------------------------------------------------- antifarm and afk

    @Test fun `a block placed by a player is marked once, consumed when broken and then counts again`() {
        val p = PlacedBlocks()
        assertTrue(p.mark(10, 64, -5))
        assertFalse(p.mark(10, 64, -5))
        assertTrue(p.isMarked(10, 64, -5))
        assertFalse(p.isMarked(11, 64, -5))
        assertFalse(p.isMarked(10, 65, -5))
        assertTrue(p.consume(10, 64, -5))
        assertFalse(p.consume(10, 64, -5))
        assertEquals(0, p.size())
        p.mark(-1, -60, -1); p.mark(0, -60, 0)
        assertEquals(2, p.size())
        assertTrue(p.isMarked(-1, -60, -1))
        assertFalse(p.isMarked(-1, -60, 0))
    }

    @Test fun `marks are bounded per chunk and survive a save and load`() {
        val p = PlacedBlocks()
        var stored = 0
        for (y in 0 until 100) for (x in 0 until 16) for (z in 0 until 16) if (p.mark(x, y, z)) stored++
        assertEquals(PlacedBlocks.MAX_PER_CHUNK, stored)
        val q = PlacedBlocks()
        for (k in p.chunkKeys()) q.load(k, p.marksOf(k))
        assertEquals(stored, q.size())
        assertTrue(q.isMarked(0, 0, 0))
    }

    @Test fun `mobs from spawners, eggs and commands do not count and named, leashed or machine kills do not either`() {
        for (type in listOf("SPAWNER", "TRIAL_SPAWNER", "SPAWN_ITEM_USE", "COMMAND", "DISPENSER", "MOB_SUMMONED", "BUCKET"))
            assertFalse(AntiFarm.spawnTypeCounts(type), type)
        for (type in listOf("NATURAL", "CHUNK_GENERATION", "STRUCTURE", "REINFORCEMENT")) assertTrue(AntiFarm.spawnTypeCounts(type), type)
        assertTrue(AntiFarm.mobCounts(false, false, false, true))
        assertFalse(AntiFarm.mobCounts(true, false, false, true))
        assertFalse(AntiFarm.mobCounts(false, true, false, true))
        assertFalse(AntiFarm.mobCounts(false, false, true, true))
        assertFalse(AntiFarm.mobCounts(false, false, false, false))
    }

    @Test fun `a player who does not move or turn is afk after the time and moving or turning resets it`() {
        val d = AfkDetector(); val id = UUID.randomUUID()
        assertFalse(d.afk(id, 100_000, 60_000))
        d.sample(id, 0.0, 64.0, 0.0, 10f, 0f, 0)
        d.sample(id, 0.1, 64.0, 0.0, 11f, 0f, 30_000)
        assertFalse(d.afk(id, 30_000, 60_000))
        assertTrue(d.afk(id, 61_000, 60_000))
        d.sample(id, 0.0, 64.0, 0.0, 30f, 0f, 62_000)
        assertFalse(d.afk(id, 63_000, 60_000))
        d.sample(id, 5.0, 64.0, 0.0, 30f, 0f, 70_000)
        assertFalse(d.afk(id, 120_000, 60_000))
        assertTrue(d.afk(id, 130_000, 60_000))
        d.forget(id)
        assertFalse(d.afk(id, 999_999, 60_000))
    }
}
