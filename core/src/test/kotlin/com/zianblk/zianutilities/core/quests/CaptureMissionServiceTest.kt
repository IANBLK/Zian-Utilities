package com.zianblk.zianutilities.core.quests

import com.zianblk.zianutilities.core.generation.Generation
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.io.TempDir
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CaptureMissionServiceTest {
    @TempDir lateinit var directory: Path
    private val player = UUID.randomUUID()
    private val clock = Clock.fixed(Instant.parse("2026-09-26T23:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `mission is accepted once and completion survives store recreation`() {
        val first = CaptureMissionService(FileCaptureMissionStore(directory), clock)
        assertFailsWith<IllegalArgumentException> { first.accept(player, emptySet()) }
        val assignment = first.accept(player, setOf(Generation.GEN_7))
        assertFalse(assignment.completed)
        assertEquals(assignment, first.accept(player, setOf(Generation.GEN_1)))
        assertEquals(
            CaptureMissionResult.COMPLETED,
            first.recordCapture(
                player, "cobblemon:komala",
                setOf(Generation.GEN_7), setOf(Generation.GEN_7),
            ),
        )

        val restarted = CaptureMissionService(FileCaptureMissionStore(directory), clock)
        val saved = assertNotNull(restarted.inspect(player))
        assertEquals(assignment.assignmentId, saved.assignmentId)
        assertEquals("cobblemon:komala", saved.capturedSpeciesId)
        assertEquals(Generation.GEN_7, saved.capturedGeneration)
        assertTrue(saved.completed)
        assertEquals(
            CaptureMissionResult.ALREADY_COMPLETED,
            restarted.recordCapture(
                player, "cobblemon:mudbray",
                setOf(Generation.GEN_7), setOf(Generation.GEN_7),
            ),
        )
        assertEquals(saved, restarted.inspect(player))
        assertEquals(null, restarted.inspect(UUID.randomUUID()))
    }

    @Test
    fun `capture follows current generations rather than a stale acceptance snapshot`() {
        val service = CaptureMissionService(FileCaptureMissionStore(directory), clock)
        service.accept(player, setOf(Generation.GEN_7))
        assertEquals(
            CaptureMissionResult.IGNORED_GENERATION,
            service.recordCapture(
                player, "cobblemon:rookidee",
                setOf(Generation.GEN_8), setOf(Generation.GEN_1, Generation.GEN_2),
            ),
        )
        assertEquals(
            CaptureMissionResult.IGNORED_GENERATION,
            service.recordCapture(
                player, "cobblemon:komala",
                setOf(Generation.GEN_7), setOf(Generation.GEN_1, Generation.GEN_2),
            ),
        )
        assertEquals(
            CaptureMissionResult.COMPLETED,
            service.recordCapture(
                player, "cobblemon:sentret",
                setOf(Generation.GEN_2), setOf(Generation.GEN_1, Generation.GEN_2),
            ),
        )
        assertEquals(Generation.GEN_2, assertNotNull(service.inspect(player)).capturedGeneration)
    }

    @Test
    fun `no active generation pauses mission without deleting assignment`() {
        val service = CaptureMissionService(FileCaptureMissionStore(directory), clock)
        val assignment = service.accept(player, setOf(Generation.GEN_7))
        assertEquals(
            CaptureMissionResult.PAUSED,
            service.recordCapture(
                player, "cobblemon:komala",
                setOf(Generation.GEN_7), emptySet(),
            ),
        )
        assertEquals(assignment, service.inspect(player))
        assertEquals(
            CaptureMissionResult.COMPLETED,
            service.recordCapture(
                player, "cobblemon:sentret",
                setOf(Generation.GEN_2), setOf(Generation.GEN_2),
            ),
        )
    }

    @Test
    fun `corrupt assignment fails closed and is not replaced`() {
        Files.createDirectories(directory)
        Files.writeString(
            directory.resolve("$player.properties"),
            "schemaVersion=99\nplayerId=$player\n",
            StandardCharsets.UTF_8,
        )
        val service = CaptureMissionService(FileCaptureMissionStore(directory), clock)
        assertFailsWith<IllegalArgumentException> {
            service.accept(player, setOf(Generation.GEN_7))
        }
    }

    @Test
    fun `failed completion save never reports success`() {
        val store = object : CaptureMissionStore {
            var value: CaptureMission? = null
            override fun <T> withPlayerLock(playerId: UUID, action: () -> T): T = action()
            override fun load(playerId: UUID): CaptureMission? = value
            override fun save(mission: CaptureMission) {
                if (mission.completed) throw IOException("disk full")
                value = mission
            }
        }
        val service = CaptureMissionService(store, clock)
        service.accept(player, setOf(Generation.GEN_7))
        assertFailsWith<IOException> {
            service.recordCapture(
                player, "cobblemon:komala",
                setOf(Generation.GEN_7), setOf(Generation.GEN_7),
            )
        }
        assertFalse(assertNotNull(service.inspect(player)).completed)
    }

    @Test
    fun `concurrent duplicate events complete only once`() {
        val service = CaptureMissionService(FileCaptureMissionStore(directory), clock)
        service.accept(player, setOf(Generation.GEN_7))
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val futures = listOf("cobblemon:komala", "cobblemon:mudbray").map { species ->
                pool.submit<CaptureMissionResult> {
                    start.await()
                    service.recordCapture(
                        player, species,
                        setOf(Generation.GEN_7), setOf(Generation.GEN_7),
                    )
                }
            }
            start.countDown()
            val results = futures.map { it.get(10, TimeUnit.SECONDS) }.toSet()
            assertEquals(
                setOf(CaptureMissionResult.COMPLETED, CaptureMissionResult.ALREADY_COMPLETED),
                results,
            )
            assertTrue(assertNotNull(service.inspect(player)).completed)
        } finally {
            pool.shutdownNow()
        }
    }
}

