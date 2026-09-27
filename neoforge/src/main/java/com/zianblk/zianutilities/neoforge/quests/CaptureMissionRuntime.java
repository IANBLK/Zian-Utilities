package com.zianblk.zianutilities.neoforge.quests;

import com.zianblk.zianutilities.core.quests.CaptureMissionService;
import com.zianblk.zianutilities.core.quests.FileCaptureMissionStore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;

/** One immediately persisted mission store for the active Youer server process. */
public final class CaptureMissionRuntime {
    private static MinecraftServer activeServer;
    private static CaptureMissionService activeService;

    private CaptureMissionRuntime() {
    }

    public static synchronized CaptureMissionService service(MinecraftServer server) {
        if (activeServer != server) {
            Path directory = server.getWorldPath(LevelResource.ROOT)
                .resolve("data").resolve("zianutilities")
                .resolve("quest_assignments").resolve("capture_any_active_v1");
            activeService = new CaptureMissionService(new FileCaptureMissionStore(directory));
            activeServer = server;
        }
        return activeService;
    }
}

