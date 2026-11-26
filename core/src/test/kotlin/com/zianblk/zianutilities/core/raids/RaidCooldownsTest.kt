package com.zianblk.zianutilities.core.raids

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class RaidCooldownsTest {
    @TempDir lateinit var dir: Path
    private val a = UUID.randomUUID()
    private val b = UUID.randomUUID()

    @Test fun `cooldown is per player and per tier`() {
        val c = RaidCooldowns.load(dir.resolve("c.properties"))
        c.start(a, 5, 10_000)
        assertEquals(4_000, c.remainingMillis(a, 5, 6_000))
        assertEquals(0, c.remainingMillis(a, 6, 6_000)); assertEquals(0, c.remainingMillis(b, 5, 6_000))
        assertEquals(0, c.remainingMillis(a, 5, 10_000))
    }

    @Test fun `persists across a reload and reset releases it`() {
        val file = dir.resolve("c.properties")
        RaidCooldowns.load(file).also { it.start(a, 5, 9_000); it.start(a, 6, 9_000) }
        val again = RaidCooldowns.load(file)
        assertEquals(1_000, again.remainingMillis(a, 5, 8_000))
        assertTrue(again.reset(a, 5)); assertFalse(again.reset(a, 5))
        assertEquals(0, RaidCooldowns.load(file).remainingMillis(a, 5, 0)); assertEquals(1_000, RaidCooldowns.load(file).remainingMillis(a, 6, 8_000))
        assertTrue(again.reset(a, 0)); assertEquals(0, RaidCooldowns.load(file).remainingMillis(a, 6, 0))
    }

    @Test fun `a corrupt or future file fails and is left untouched`() {
        val file = dir.resolve("c.properties")
        Files.writeString(file, "formatVersion=2\n$a.5=1\n")
        assertThrows(IOException::class.java) { RaidCooldowns.load(file) }
        assertEquals("formatVersion=2\n$a.5=1\n", Files.readString(file))
        Files.writeString(file, "formatVersion=1\nnot-a-uuid.5=1\n")
        assertThrows(IllegalArgumentException::class.java) { RaidCooldowns.load(file) }
        Files.writeString(file, "$a.5=1\n")
        assertThrows(IOException::class.java) { RaidCooldowns.load(file) }
    }

    @Test fun `the duel log reports duels that never ended and forgets finished ones`() {
        val path = dir.resolve("duels.wal")
        val open = UUID.randomUUID(); val done = UUID.randomUUID(); val bossId = UUID.randomUUID()
        DuelLog(path).use {
            it.started(open, a, 5, "raid-1", "boss", 1L, bossId, true, 1.0, 2.0, 3.0); it.started(done, b, 6, "raid-2", "boss", 1L, UUID.randomUUID(), false, 0.0, 0.0, 0.0)
            it.ended(done, DuelResult.WON, 1.0)
        }
        DuelLog(path).use {
            assertEquals(setOf(open), it.unfinished().keys); assertEquals(a, it.unfinished()[open]!!.player)
            assertEquals(bossId, it.unfinished()[open]!!.bossEntity); assertTrue(it.unfinished()[open]!!.bossWasInvulnerable); assertEquals(3.0, it.unfinished()[open]!!.bossZ)
            it.ended(open, DuelResult.ABORTED, 0.0)
        }
        DuelLog(path).use { assertTrue(it.unfinished().isEmpty()) }
    }

    @Test fun `pending boss restores persist until removed and a bad file is never overwritten`() {
        val file = dir.resolve("bosses.properties"); val boss = UUID.randomUUID()
        PendingBossRestores.load(file).put(boss, PendingBossRestores.Entry(true, 1.5, 64.0, -3.25, 90f))
        val again = PendingBossRestores.load(file)
        assertEquals(PendingBossRestores.Entry(true, 1.5, 64.0, -3.25, 90f), again.get(boss))
        assertTrue(again.remove(boss)); assertFalse(again.remove(boss))
        assertTrue(PendingBossRestores.load(file).all().isEmpty())
        Files.writeString(file, "formatVersion=9\n")
        assertThrows(IOException::class.java) { PendingBossRestores.load(file) }
        assertEquals("formatVersion=9\n", Files.readString(file))
    }
}
