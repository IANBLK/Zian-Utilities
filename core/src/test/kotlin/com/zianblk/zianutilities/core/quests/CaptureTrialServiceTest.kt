package com.zianblk.zianutilities.core.quests

import com.zianblk.zianutilities.core.generation.Generation
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import org.junit.jupiter.api.io.TempDir
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CaptureTrialServiceTest {
    @TempDir lateinit var directory: Path
    private val player = UUID.randomUUID()

    @Test
    fun `completion persists and duplicate captures cannot create more progress`() {
        val first = CaptureTrialService(FileCaptureTrialStore(directory))
        val trial = first.start(player, setOf(Generation.GEN_7))
        assertFalse(trial.completed)
        assertEquals(
            CaptureTrialResult.COMPLETED,
            first.recordCapture(
                player, "cobblemon:yungoos",
                setOf(Generation.GEN_7), setOf(Generation.GEN_7),
            ),
        )

        val afterRestart = CaptureTrialService(FileCaptureTrialStore(directory))
        val saved = assertNotNull(afterRestart.inspect(player))
        assertEquals(trial.assignmentId, saved.assignmentId)
        assertEquals("cobblemon:yungoos", saved.capturedSpeciesId)
        assertTrue(saved.completed)
        assertEquals(
            CaptureTrialResult.ALREADY_COMPLETED,
            afterRestart.recordCapture(
                player, "cobblemon:popplio",
                setOf(Generation.GEN_7), setOf(Generation.GEN_7),
            ),
        )
        assertEquals(saved, afterRestart.start(player, setOf(Generation.GEN_2)))
    }

    @Test
    fun `unfinished trial follows currently enabled generations without changing assignment ID`() {
        val service = CaptureTrialService(FileCaptureTrialStore(directory))
        assertEquals(
            CaptureTrialResult.NOT_STARTED,
            service.recordCapture(
                player, "cobblemon:yungoos",
                setOf(Generation.GEN_7), setOf(Generation.GEN_7),
            ),
        )
        val original = service.start(player, setOf(Generation.GEN_7))
        val retargeted = assertNotNull(
            service.synchronize(player, setOf(Generation.GEN_1, Generation.GEN_2))
        )
        assertEquals(original.assignmentId, retargeted.assignmentId)
        assertEquals(setOf(Generation.GEN_1, Generation.GEN_2), retargeted.targetGenerations)
        assertEquals(
            CaptureTrialResult.IGNORED_GENERATION,
            service.recordCapture(
                player, "cobblemon:yungoos",
                setOf(Generation.GEN_7), setOf(Generation.GEN_2),
            ),
        )
        assertEquals(
            CaptureTrialResult.IGNORED_GENERATION,
            service.recordCapture(
                player, "cobblemon:rookidee",
                setOf(Generation.GEN_8), setOf(Generation.GEN_2),
            ),
        )
        assertFalse(assertNotNull(service.inspect(player)).completed)
        assertEquals(
            CaptureTrialResult.COMPLETED,
            service.recordCapture(
                player, "cobblemon:sentret",
                setOf(Generation.GEN_2), setOf(Generation.GEN_2),
            ),
        )
        val finished = assertNotNull(service.synchronize(player, setOf(Generation.GEN_8)))
        assertEquals(setOf(Generation.GEN_2), finished.targetGenerations)
        assertEquals("cobblemon:sentret", finished.capturedSpeciesId)
    }

    @Test
    fun `trial pauses with no active generations and resumes when one is enabled`() {
        val service = CaptureTrialService(FileCaptureTrialStore(directory))
        val original = service.start(player, setOf(Generation.GEN_7))
        assertEquals(original, service.synchronize(player, emptySet()))
        assertEquals(
            CaptureTrialResult.IGNORED_GENERATION,
            service.recordCapture(
                player, "cobblemon:yungoos",
                setOf(Generation.GEN_7), emptySet(),
            ),
        )
        val resumed = assertNotNull(service.synchronize(player, setOf(Generation.GEN_1)))
        assertEquals(original.assignmentId, resumed.assignmentId)
        assertEquals(setOf(Generation.GEN_1), resumed.targetGenerations)
        assertFalse(resumed.completed)
    }

    @Test
    fun `corrupt stored trial fails closed rather than starting a replacement`() {
        Files.createDirectories(directory)
        Files.writeString(
            directory.resolve("$player.properties"),
            "version=99\nplayerId=$player\n",
            StandardCharsets.UTF_8,
        )
        val service = CaptureTrialService(FileCaptureTrialStore(directory))
        assertFailsWith<IllegalArgumentException> {
            service.start(player, setOf(Generation.GEN_7))
        }
    }
}

