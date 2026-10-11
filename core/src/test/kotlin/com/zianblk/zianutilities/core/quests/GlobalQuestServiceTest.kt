package com.zianblk.zianutilities.core.quests

import com.zianblk.zianutilities.core.generation.Generation
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.jupiter.api.io.TempDir

class GlobalQuestServiceTest {
    @TempDir lateinit var directory: Path
    private val start = Instant.parse("2026-09-26T05:00:00Z")
    private val player = UUID.randomUUID()
    private val other = UUID.randomUUID()
    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
    }

    private fun offer(clock: MutableClock, species: String = "cobblemon:yungoos") = GlobalQuestOffer(
        SharedCaptureSchedule.at(clock.millis()).windowStartEpochMs, species, "gen7",
        "avecoins:coppercoin", 1, "avecoins:coppercoin", 1, false,
    )

    @Test fun `acceptance is private and both objectives survive restart without duplicates`() {
        val clock = MutableClock(start)
        val definition = offer(clock)
        val service = GlobalQuestService(FileGlobalQuestProgressStore(directory), clock)
        assertNull(service.inspect(player, definition))
        assertEquals(GlobalQuestEventResult.NOT_ACCEPTED, service.wildVictory(player, definition, UUID.randomUUID()))
        assertEquals(GlobalQuestEventResult.NOT_ACCEPTED, service.capture(
            player, definition, UUID.randomUUID(), "cobblemon:yungoos",
            setOf(Generation.GEN_7), setOf(Generation.GEN_7)))
        val accepted = service.accept(player, definition)
        assertEquals(accepted, service.accept(player, definition))
        assertNull(service.inspect(other, definition))
        assertEquals(GlobalQuestEventResult.WRONG_SPECIES, service.capture(
            player, definition, UUID.randomUUID(), "cobblemon:komala",
            setOf(Generation.GEN_7), setOf(Generation.GEN_7)))
        assertEquals(GlobalQuestEventResult.COMPLETE, service.capture(
            player, definition, UUID.randomUUID(), "cobblemon:yungoos",
            setOf(Generation.GEN_7), setOf(Generation.GEN_7)))
        val battle = UUID.randomUUID()
        assertEquals(GlobalQuestEventResult.ADVANCED, service.wildVictory(player, definition, battle))
        assertEquals(GlobalQuestEventResult.DUPLICATE, service.wildVictory(player, definition, battle))
        repeat(3) { assertEquals(GlobalQuestEventResult.ADVANCED,
            service.wildVictory(player, definition, UUID.randomUUID())) }
        assertEquals(GlobalQuestEventResult.COMPLETE, service.wildVictory(player, definition, UUID.randomUUID()))
        val restored = GlobalQuestService(FileGlobalQuestProgressStore(directory), clock)
        val state = assertNotNull(restored.inspect(player, definition))
        assertEquals(true, state.captureComplete)
        assertEquals(5, state.battleIds.size)
        assertEquals(GlobalQuestEventResult.DUPLICATE,
            restored.wildVictory(player, definition, UUID.randomUUID()))
        assertNull(restored.inspect(other, definition))
    }

    @Test fun `new three hour window archives prior progress and never carries captures forward`() {
        val clock = MutableClock(start)
        val store = FileGlobalQuestProgressStore(directory)
        val service = GlobalQuestService(store, clock)
        val first = offer(clock)
        service.accept(player, first)
        clock.now = start.plusSeconds(3 * 3600)
        val next = offer(clock, "cobblemon:komala")
        assertNull(service.inspect(player, next))
        assertNotNull(store.loadPrevious(player))
        assertEquals(GlobalQuestEventResult.NOT_ACCEPTED,
            service.wildVictory(player, next, UUID.randomUUID()))
        assertEquals(0, service.accept(player, next).battleIds.size)
        assertEquals(next.id, store.load(player)?.offer?.id)
        assertEquals(true, Files.exists(directory.resolve("previous").resolve("$player.properties")))
    }

    @Test fun `generation change invalidates prior acceptance within a window`() {
        val clock = MutableClock(start)
        val store = FileGlobalQuestProgressStore(directory)
        val service = GlobalQuestService(store, clock)
        val first = offer(clock)
        service.accept(player, first)
        val changed = first.copy(targetSpecies = "cobblemon:caterpie", generationIds = "gen1")
        assertNull(service.inspect(player, changed))
        assertEquals(GlobalQuestEventResult.NOT_ACCEPTED, service.capture(
            player, changed, UUID.randomUUID(), "cobblemon:caterpie",
            setOf(Generation.GEN_1), setOf(Generation.GEN_1)))
        assertEquals(changed.id, service.accept(player, changed).offer.id)
    }
}

