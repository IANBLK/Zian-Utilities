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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class SharedCaptureMissionServiceTest {
    @TempDir lateinit var directory: Path
    private val player = UUID.randomUUID()
    private val second = UUID.randomUUID()
    private val gen7 = setOf(Generation.GEN_7)
    // 05:00 UTC is 00:00 in Ecuador: five captures; 08:00 UTC is distinct species.
    private val start = Instant.parse("2026-09-26T05:00:00Z")

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
    }

    @Test
    fun `one definition is shared and acceptance and progress remain private`() {
        val clock = MutableClock(start)
        val service = SharedCaptureMissionService(FileSharedCaptureProgressStore(directory), clock)
        val firstOffer = service.inspect(player)
        val secondOffer = service.inspect(second)
        assertEquals(firstOffer.mission, secondOffer.mission)
        assertEquals(SharedCaptureObjective.FIVE_CAPTURES, firstOffer.mission.objective)
        assertNull(firstOffer.progress)
        assertEquals(SharedCaptureResult.NOT_ACCEPTED, service.recordCapture(
            player, UUID.randomUUID(), "cobblemon:komala", gen7, gen7))

        val accepted = assertNotNull(service.accept(player).progress)
        assertEquals(accepted, service.accept(player).progress)
        assertNull(service.inspect(second).progress)
        repeat(4) {
            assertEquals(SharedCaptureResult.ADVANCED, service.recordCapture(
                player, UUID.randomUUID(), "cobblemon:komala", gen7, gen7))
        }
        assertEquals(SharedCaptureResult.COMPLETED, service.recordCapture(
            player, UUID.randomUUID(), "cobblemon:komala", gen7, gen7))
        assertEquals(SharedCaptureResult.ALREADY_COMPLETED, service.recordCapture(
            player, UUID.randomUUID(), "cobblemon:yungoos", gen7, gen7))
        assertEquals(5, service.inspect(player).progress?.captures?.size)
        assertNull(service.inspect(second).progress)
        assertTrue(Files.exists(directory.resolve("$player.properties")))
        assertTrue(!Files.exists(directory.resolve("$second.properties")))
    }

    @Test
    fun `next global mission requires a fresh acceptance and distinct species`() {
        val clock = MutableClock(start)
        val service = SharedCaptureMissionService(FileSharedCaptureProgressStore(directory), clock)
        val first = service.accept(player).mission
        val pokemon = UUID.randomUUID()
        assertEquals(SharedCaptureResult.ADVANCED, service.recordCapture(
            player, pokemon, "cobblemon:komala", gen7, gen7))
        assertEquals(SharedCaptureResult.DUPLICATE_POKEMON, service.recordCapture(
            player, pokemon, "cobblemon:komala", gen7, gen7))
        clock.now = start.plusSeconds(3 * 3600)
        val nextOffer = service.inspect(player)
        assertNotEquals(first.missionId, nextOffer.mission.missionId)
        assertEquals(SharedCaptureObjective.FIVE_DISTINCT_SPECIES, nextOffer.mission.objective)
        assertNull(nextOffer.progress)
        assertTrue(Files.exists(directory.resolve("previous").resolve("$player.properties")))
        assertEquals(SharedCaptureResult.NOT_ACCEPTED, service.recordCapture(
            player, UUID.randomUUID(), "cobblemon:yungoos", gen7, gen7))
        service.accept(player)
        assertEquals(SharedCaptureResult.ADVANCED, service.recordCapture(
            player, UUID.randomUUID(), "cobblemon:komala", gen7, gen7))
        assertEquals(SharedCaptureResult.DUPLICATE_SPECIES, service.recordCapture(
            player, UUID.randomUUID(), "cobblemon:komala", gen7, gen7))
        for (species in listOf("yungoos", "minior", "mudbray")) {
            assertEquals(SharedCaptureResult.ADVANCED, service.recordCapture(
                player, UUID.randomUUID(), "cobblemon:$species", gen7, gen7))
        }
        assertEquals(SharedCaptureResult.COMPLETED, service.recordCapture(
            player, UUID.randomUUID(), "cobblemon:oranguru", gen7, gen7))
    }

    @Test
    fun `generation changes pause or reject captures without changing the global mission`() {
        val clock = MutableClock(start)
        val service = SharedCaptureMissionService(FileSharedCaptureProgressStore(directory), clock)
        val missionId = service.accept(player).mission.missionId
        assertEquals(SharedCaptureResult.PAUSED, service.recordCapture(
            player, UUID.randomUUID(), "cobblemon:komala", gen7, emptySet()))
        assertEquals(SharedCaptureResult.IGNORED_GENERATION, service.recordCapture(
            player, UUID.randomUUID(), "cobblemon:rookidee", setOf(Generation.GEN_8), gen7))
        assertEquals(SharedCaptureResult.ADVANCED, service.recordCapture(
            player, UUID.randomUUID(), "cobblemon:sentret",
            setOf(Generation.GEN_2), setOf(Generation.GEN_1, Generation.GEN_2)))
        assertEquals(missionId, service.inspect(player).mission.missionId)
        assertEquals(1, service.inspect(player).progress?.captures?.size)
    }

    @Test
    fun `restart and offline gap never replay or extend an expired acceptance`() {
        val clock = MutableClock(start.plusSeconds(2 * 3600))
        val service = SharedCaptureMissionService(FileSharedCaptureProgressStore(directory), clock)
        val accepted = assertNotNull(service.accept(player).progress)
        assertEquals(start.toEpochMilli(), accepted.windowStartEpochMs)
        val restarted = SharedCaptureMissionService(FileSharedCaptureProgressStore(directory), clock)
        assertEquals(accepted, restarted.inspect(player).progress)
        clock.now = start.plusSeconds(10 * 3600)
        val offer = restarted.inspect(player)
        assertEquals(start.plusSeconds(9 * 3600).toEpochMilli(), offer.mission.windowStartEpochMs)
        assertNull(offer.progress)
        assertEquals(SharedCaptureResult.NOT_ACCEPTED, restarted.recordCapture(
            player, UUID.randomUUID(), "cobblemon:komala", gen7, gen7))
        assertEquals(accepted.missionId,
            readMissionId(directory.resolve("previous").resolve("$player.properties")))
    }

    @Test
    fun `storage remains bounded across many windows and players`() {
        val clock = MutableClock(start)
        val service = SharedCaptureMissionService(FileSharedCaptureProgressStore(directory), clock)
        val players = (1..40).map { UUID.randomUUID() }
        players.forEach(service::accept)
        repeat(20) { period ->
            clock.now = start.plusSeconds((period + 1L) * 3 * 3600)
            players.forEach(service::accept)
        }
        Files.list(directory).use { files ->
            assertEquals(players.size.toLong(), files.filter { it.fileName.toString().endsWith(".properties") }.count())
        }
        Files.list(directory.resolve("previous")).use { files ->
            assertEquals(players.size.toLong(), files.filter { it.fileName.toString().endsWith(".properties") }.count())
        }
    }

    @Test
    fun `corrupt state and failed save never invent successful progress`() {
        Files.createDirectories(directory)
        Files.writeString(directory.resolve("$player.properties"), "schemaVersion=99\nplayerId=$player\n")
        val service = SharedCaptureMissionService(FileSharedCaptureProgressStore(directory), MutableClock(start))
        assertFailsWith<IllegalArgumentException> { service.inspect(player) }

        val store = object : SharedCaptureProgressStore {
            var current: SharedCaptureProgress? = null
            override fun <T> withPlayerLock(playerId: UUID, action: () -> T): T = action()
            override fun load(playerId: UUID): SharedCaptureProgress? = current
            override fun save(progress: SharedCaptureProgress) {
                if (progress.captures.isNotEmpty()) throw IOException("disk full")
                current = progress
            }
            override fun archiveAndClear(progress: SharedCaptureProgress) { current = null }
        }
        val failing = SharedCaptureMissionService(store, MutableClock(start))
        failing.accept(player)
        assertFailsWith<IOException> { failing.recordCapture(
            player, UUID.randomUUID(), "cobblemon:komala", gen7, gen7) }
        assertEquals(0, failing.inspect(player).progress?.captures?.size)
    }

    private fun readMissionId(path: Path): UUID {
        val p = java.util.Properties()
        Files.newBufferedReader(path).use(p::load)
        return UUID.fromString(p.getProperty("missionId"))
    }
}

