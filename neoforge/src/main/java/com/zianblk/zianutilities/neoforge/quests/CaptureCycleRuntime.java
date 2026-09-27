package com.zianblk.zianutilities.neoforge.quests;

import com.zianblk.zianutilities.core.quests.CaptureCycleService;
import com.zianblk.zianutilities.core.quests.FileCaptureCycleStore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;

public final class CaptureCycleRuntime {
    private static MinecraftServer activeServer;
    private static CaptureCycleService activeService;

    private CaptureCycleRuntime() {}

    public static synchronized CaptureCycleService service(MinecraftServer server) {
        if (activeServer != server) {
            Path directory = server.getWorldPath(LevelResource.ROOT)
                .resolve("data").resolve("zianutilities")
                .resolve("quest_assignments").resolve("capture_three_global_v1");
            activeService = new CaptureCycleService(new FileCaptureCycleStore(directory));
            activeServer = server;
        }
        return activeService;
    }
}

