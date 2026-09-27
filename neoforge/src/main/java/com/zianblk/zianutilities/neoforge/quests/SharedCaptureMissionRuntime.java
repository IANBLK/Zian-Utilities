package com.zianblk.zianutilities.neoforge.quests;

import com.zianblk.zianutilities.core.quests.FileSharedCaptureProgressStore;
import com.zianblk.zianutilities.core.quests.SharedCaptureMissionService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;

public final class SharedCaptureMissionRuntime {
    private static MinecraftServer activeServer;
    private static SharedCaptureMissionService activeService;

    private SharedCaptureMissionRuntime() {}

    public static synchronized SharedCaptureMissionService service(MinecraftServer server) {
        if (activeServer != server) {
            Path directory = server.getWorldPath(LevelResource.ROOT)
                .resolve("data").resolve("zianutilities")
                .resolve("quest_assignments").resolve("shared_capture_v1");
            activeService = new SharedCaptureMissionService(
                new FileSharedCaptureProgressStore(directory));
            activeServer = server;
        }
        return activeService;
    }
}

