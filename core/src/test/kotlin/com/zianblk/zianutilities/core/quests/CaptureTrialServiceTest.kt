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
    fun `only a started trial with a target that is still enabled can complete`() {
        val service = CaptureTrialService(FileCaptureTrialStore(directory))
        assertEquals(
            CaptureTrialResult.NOT_STARTED,
            service.recordCapture(
                player, "cobblemon:yungoos",
                setOf(Generation.GEN_7), setOf(Generation.GEN_7),
            ),
        )
        service.start(player, setOf(Generation.GEN_7))
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
                player, "cobblemon:sentret",
                setOf(Generation.GEN_2), setOf(Generation.GEN_2),
            ),
        )
        assertFalse(assertNotNull(service.inspect(player)).completed)
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

