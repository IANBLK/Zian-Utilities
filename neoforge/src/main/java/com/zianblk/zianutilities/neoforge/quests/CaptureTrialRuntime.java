package com.zianblk.zianutilities.neoforge.quests;

import com.zianblk.zianutilities.core.quests.CaptureTrialService;
import com.zianblk.zianutilities.core.quests.FileCaptureTrialStore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;

/** Shares one immediately persisted trial store across commands and capture events. */
public final class CaptureTrialRuntime {
    private static MinecraftServer activeServer;
    private static CaptureTrialService activeService;

    private CaptureTrialRuntime() {
    }

    public static synchronized CaptureTrialService service(MinecraftServer server) {
        if (activeServer != server) {
            Path directory = server.getWorldPath(LevelResource.ROOT)
                .resolve("data").resolve("zianutilities").resolve("capture_trials");
            activeService = new CaptureTrialService(new FileCaptureTrialStore(directory));
            activeServer = server;
        }
        return activeService;
    }
}

