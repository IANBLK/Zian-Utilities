package com.zianblk.zianutilities.core.quests

import com.zianblk.zianutilities.core.generation.Generation
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class CaptureCycleServiceTest {
    @TempDir lateinit var directory: Path
    private val player = UUID.randomUUID()
    private val gen7 = setOf(Generation.GEN_7)
    private val start = Instant.parse("2026-09-26T00:00:00Z")

    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = instant
    }

    @Test
    fun `three distinct captures complete once and persist after restart`() {
        val clock = MutableClock(start)
        val service = CaptureCycleService(FileCaptureCycleStore(directory), clock)
        assertFailsWith<IllegalArgumentException> { service.assign(player, emptySet()) }
        val first = service.assign(player, gen7)
        assertEquals(first, service.assign(player, gen7))
        val pokemon = UUID.randomUUID()
        assertEquals(CaptureCycleResult.ADVANCED, service.recordCapture(
            player, pokemon, "cobblemon:komala", gen7, gen7))
        assertEquals(CaptureCycleResult.DUPLICATE, service.recordCapture(
            player, pokemon, "cobblemon:komala", gen7, gen7))
        assertEquals(CaptureCycleResult.IGNORED_GENERATION, service.recordCapture(
            player, UUID.randomUUID(), "cobblemon:rookidee", setOf(Generation.GEN_8), gen7))
        assertEquals(CaptureCycleResult.ADVANCED, service.recordCapture(
            player, UUID.randomUUID(), "cobblemon:yungoos", gen7, gen7))
        assertEquals(CaptureCycleResult.COMPLETED, service.recordCapture(
            player, UUID.randomUUID(), "cobblemon:minior", gen7, gen7))
        assertEquals(CaptureCycleResult.ALREADY_COMPLETED, service.recordCapture(
            player, UUID.randomUUID(), "cobblemon:mudbray", gen7, gen7))
        val restarted = CaptureCycleService(FileCaptureCycleStore(directory), clock)
        val saved = assertNotNull(restarted.inspect(player, gen7))
        assertEquals(first.cycleId, saved.cycleId)
        assertEquals(3, saved.captures.size)
        assertTrue(saved.completed)
    }

    @Test
    fun `six hour boundary archives prior cycle and first capture advances new one`() {
        val clock = MutableClock(start)
        val service = CaptureCycleService(FileCaptureCycleStore(directory), clock)
        val first = service.assign(player, gen7)
        clock.instant = start.plusSeconds(6 * 3600 - 1)
        assertEquals(first.cycleId, service.inspect(player, gen7)?.cycleId)
        clock.instant = start.plusSeconds(6 * 3600)
        assertEquals(CaptureCycleResult.ADVANCED, service.recordCapture(
            player, UUID.randomUUID(), "cobblemon:komala", gen7, gen7))
        val next = assertNotNull(service.inspect(player, gen7))
        assertNotEquals(first.cycleId, next.cycleId)
        assertEquals(1, next.captures.size)
        assertEquals(clock.millis(), next.assignedAtEpochMs)
        assertTrue(Files.exists(directory.resolve("history").resolve(player.toString())
            .resolve("${first.cycleId}.properties")))
    }

    @Test
    fun `players have independent clocks and generation changes remain eligible`() {
        val clock = MutableClock(start)
        val service = CaptureCycleService(FileCaptureCycleStore(directory), clock)
        val secondPlayer = UUID.randomUUID()
        val first = service.assign(player, gen7)
        clock.instant = start.plusSeconds(3600)
        val second = service.assign(secondPlayer, gen7)
        assertEquals(CaptureCycleResult.PAUSED, service.recordCapture(
            secondPlayer, UUID.randomUUID(), "cobblemon:komala", gen7, emptySet()))
        assertEquals(CaptureCycleResult.ADVANCED, service.recordCapture(
            secondPlayer, UUID.randomUUID(), "cobblemon:sentret",
            setOf(Generation.GEN_2), setOf(Generation.GEN_1, Generation.GEN_2)))
        clock.instant = start.plusSeconds(6 * 3600)
        assertNotEquals(first.cycleId, service.inspect(player, gen7)?.cycleId)
        assertEquals(second.cycleId, service.inspect(secondPlayer, gen7)?.cycleId)
        assertEquals(1, service.inspect(secondPlayer, gen7)?.captures?.size)
    }

    @Test
    fun `corrupt cycle and failed save fail closed`() {
        Files.createDirectories(directory)
        Files.writeString(directory.resolve("$player.properties"), "schemaVersion=99\nplayerId=$player\n")
        val service = CaptureCycleService(FileCaptureCycleStore(directory), MutableClock(start))
        assertFailsWith<IllegalArgumentException> { service.assign(player, gen7) }
        Files.delete(directory.resolve("$player.properties"))

        val store = object : CaptureCycleStore {
            var value: CaptureCycle? = null
            override fun <T> withPlayerLock(playerId: UUID, action: () -> T): T = action()
            override fun load(playerId: UUID): CaptureCycle? = value
            override fun save(cycle: CaptureCycle) {
                if (cycle.captures.isNotEmpty()) throw IOException("disk full")
                value = cycle
            }
            override fun rotate(previous: CaptureCycle, next: CaptureCycle) { value = next }
        }
        val failing = CaptureCycleService(store, MutableClock(start))
        failing.assign(player, gen7)
        assertFailsWith<IOException> { failing.recordCapture(
            player, UUID.randomUUID(), "cobblemon:komala", gen7, gen7) }
        assertEquals(0, failing.inspect(player, gen7)?.captures?.size)
    }
}

