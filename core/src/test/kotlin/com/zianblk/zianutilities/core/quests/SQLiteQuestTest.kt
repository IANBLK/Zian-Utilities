package com.zianblk.zianutilities.core.quests
import com.zianblk.zianutilities.core.storage.*
import com.zianblk.zianutilities.core.generation.Generation
import java.nio.file.*
import java.util.UUID
import java.time.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.io.TempDir
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse

class SQLiteQuestTest {
    @TempDir lateinit var world:Path
    @AfterEach fun close(){StorageFiles.closeAll()}
    @Test fun `current and previous quest retain progress and duplicate protection in SQLite`() {
        val root=world.resolve("data/zianutilities/global_quest_v1/players")
        val time=Instant.parse("2026-09-26T05:00:00Z");val clock=Clock.fixed(time,ZoneOffset.UTC)
        val offer=GlobalQuestOffer(SharedCaptureSchedule.at(clock.millis()).windowStartEpochMs,"cobblemon:bulbasaur","gen1","avecoins:coppercoin",1,"avecoins:coppercoin",1,false)
        val player=UUID.randomUUID();val battle=UUID.randomUUID();val service=GlobalQuestService(FileGlobalQuestProgressStore(root),clock)
        service.accept(player,offer);service.capture(player,offer,UUID.randomUUID(),"cobblemon:bulbasaur",setOf(Generation.GEN_1),setOf(Generation.GEN_1))
        assertEquals(GlobalQuestEventResult.ADVANCED,service.wildVictory(player,offer,battle))
        val expected=service.inspect(player,offer)
        StorageMaintenance.migrateOffline(world);Files.move(world.resolve("data/zianutilities"),world.resolve("originals"))
        val migrated=GlobalQuestService(FileGlobalQuestProgressStore(root),clock)
        assertEquals(expected,migrated.inspect(player,offer));assertEquals(GlobalQuestEventResult.DUPLICATE,migrated.wildVictory(player,offer,battle))
        StorageFiles.closeAll()
        val store=FileGlobalQuestProgressStore(root);assertEquals(expected,store.load(player))
        store.archiveAndClear(requireNotNull(expected));StorageFiles.closeAll()
        assertEquals(expected,FileGlobalQuestProgressStore(root).loadPrevious(player))
        assertNull(FileGlobalQuestProgressStore(root).load(player));assertFalse(Files.exists(root))
    }
}
