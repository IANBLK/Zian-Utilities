package com.zianblk.zianutilities.neoforge;

import com.zianblk.zianutilities.neoforge.generation.FishingSpawnGuard;
import com.zianblk.zianutilities.neoforge.generation.FinalSpawnDiagnostics;
import com.zianblk.zianutilities.neoforge.generation.GenerationCommands;
import com.zianblk.zianutilities.neoforge.generation.GenerationPreselectionFilter;
import com.zianblk.zianutilities.neoforge.generation.NaturalSpawnGuard;
import com.zianblk.zianutilities.neoforge.generation.PokeSnackSpawnGuard;
import com.zianblk.zianutilities.neoforge.rewards.RewardCommands;
import com.zianblk.zianutilities.neoforge.quests.CaptureEventProbe;
import com.zianblk.zianutilities.neoforge.quests.CaptureTrialCommands;
import com.zianblk.zianutilities.neoforge.quests.CaptureTrialListener;
import com.zianblk.zianutilities.neoforge.quests.CaptureMissionCommands;
import com.zianblk.zianutilities.neoforge.quests.CaptureMissionListener;
import com.zianblk.zianutilities.neoforge.quests.CaptureCycleCommands;
import com.zianblk.zianutilities.neoforge.quests.CaptureCycleListener;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(ZianUtilitiesMod.MOD_ID)
public final class ZianUtilitiesMod {
    public static final String MOD_ID = "zianutilities";

    public ZianUtilitiesMod() {
        NeoForge.EVENT_BUS.addListener(GenerationCommands::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(RewardCommands::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(CaptureTrialCommands::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(CaptureMissionCommands::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(CaptureCycleCommands::onRegisterCommands);
        GenerationPreselectionFilter.install();
        NaturalSpawnGuard.install();
        FishingSpawnGuard.install();
        PokeSnackSpawnGuard.install();
        FinalSpawnDiagnostics.install();
        CaptureEventProbe.install();
        CaptureTrialListener.install();
        CaptureMissionListener.install();
        CaptureCycleListener.install();
    }
}

