package com.zianblk.zianutilities.core.config

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

class ConfigHistoryTest {
    @TempDir lateinit var dir: Path

    private class Ticking : Clock() {
        var t: Instant = Instant.parse("2026-01-01T00:00:00Z")
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(z: ZoneId?): Clock = this
        override fun instant(): Instant { t = t.plusMillis(1); return t }
    }

    private fun history(keep: Int = 10) = ConfigHistory(dir.resolve("pools.nbt"), dir.resolve("bk"), dir.resolve("log/changes.jsonl"), keep, Ticking())
    private fun backups() = Files.list(dir.resolve("bk")).use { it.toList() }

    @Test fun `rotation keeps exactly ten and drops the oldest`() {
        val h = history(); val f = dir.resolve("pools.nbt")
        for (i in 1..15) { Files.writeString(f, "v$i"); h.backup() }
        val names = backups().map { it.fileName.toString() }.sorted()
        assertEquals(10, names.size)
        assertEquals("v6", Files.readString(dir.resolve("bk").resolve(names.first())))
        assertEquals("v15", Files.readString(dir.resolve("bk").resolve(names.last())))
    }

    @Test fun `backup without file does nothing`() {
        history().backup()
        assertFalse(Files.exists(dir.resolve("bk")))
    }

    @Test fun `same millisecond gets a suffix and keeps order`() {
        val fixed = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)
        val f = dir.resolve("pools.nbt")
        val h = ConfigHistory(f, dir.resolve("bk"), dir.resolve("c.jsonl"), 10, fixed)
        for (i in 1..3) { Files.writeString(f, "v$i"); h.backup() }
        assertEquals(3, backups().size)
        Files.writeString(f, "now"); assertTrue(h.undoLast()); assertEquals("v3", Files.readString(f))
    }

    @Test fun `undo restores and deletes used copy, twice goes two changes back`() {
        val h = history(); val f = dir.resolve("pools.nbt")
        Files.writeString(f, "a"); h.backup(); Files.writeString(f, "b"); h.backup(); Files.writeString(f, "c")
        assertTrue(h.undoLast()); assertEquals("b", Files.readString(f)); assertEquals(1, backups().size)
        assertTrue(h.undoLast()); assertEquals("a", Files.readString(f)); assertEquals(0, backups().size)
        assertFalse(h.undoLast())
    }

    @Test fun `undo without copies returns false and leaves file`() {
        val f = dir.resolve("pools.nbt"); Files.writeString(f, "x")
        assertFalse(history().undoLast()); assertEquals("x", Files.readString(f))
    }

    @Test fun `record writes escaped json lines`() {
        val h = history(); val id = UUID.randomUUID()
        h.record(id, "Ev\"il\nName\\", "gacha", "a\tb\u0001")
        h.record(id, "Ok", "gacha", "dos")
        val lines = Files.readAllLines(dir.resolve("log/changes.jsonl"))
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("\"actorName\":\"Ev\\\"il\\nName\\\\\""))
        assertTrue(lines[0].contains("\"summary\":\"a\\tb\\u0001\""))
        assertTrue(lines[0].startsWith("{\"at\":\"2026-01-01T00:00:00.001Z\",\"actor\":\"$id\""))
    }

    @Test fun `no temp files remain and keep is validated`() {
        val h = history(); val f = dir.resolve("pools.nbt")
        Files.writeString(f, "a"); h.backup(); h.undoLast()
        assertTrue(Files.list(dir.resolve("bk")).use { it.toList() }.none { it.toString().endsWith(".tmp") })
        assertThrows(IllegalArgumentException::class.java) { history(0) }
        assertThrows(IllegalArgumentException::class.java) { history(101) }
    }
}
