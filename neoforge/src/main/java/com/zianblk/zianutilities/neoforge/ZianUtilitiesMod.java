package com.zianblk.zianutilities.neoforge;

import com.zianblk.zianutilities.neoforge.generation.FishingSpawnGuard;
import com.zianblk.zianutilities.neoforge.generation.FinalSpawnDiagnostics;
import com.zianblk.zianutilities.neoforge.generation.GenerationCommands;
import com.zianblk.zianutilities.neoforge.generation.GenerationPreselectionFilter;
import com.zianblk.zianutilities.neoforge.generation.NaturalSpawnGuard;
import com.zianblk.zianutilities.neoforge.generation.PokeSnackSpawnGuard;
import com.zianblk.zianutilities.neoforge.rewards.RewardCommands;
import com.zianblk.zianutilities.neoforge.quests.CaptureEventProbe;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(ZianUtilitiesMod.MOD_ID)
public final class ZianUtilitiesMod {
    public static final String MOD_ID = "zianutilities";

    public ZianUtilitiesMod() {
        NeoForge.EVENT_BUS.addListener(GenerationCommands::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(RewardCommands::onRegisterCommands);
        GenerationPreselectionFilter.install();
        NaturalSpawnGuard.install();
        FishingSpawnGuard.install();
        PokeSnackSpawnGuard.install();
        FinalSpawnDiagnostics.install();
        CaptureEventProbe.install();
    }
}

