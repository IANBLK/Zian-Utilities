package com.zianblk.zianutilities.neoforge;

import com.zianblk.zianutilities.neoforge.generation.FishingSpawnGuard;
import com.zianblk.zianutilities.neoforge.generation.FinalSpawnDiagnostics;
import com.zianblk.zianutilities.neoforge.generation.GenerationCommands;
import com.zianblk.zianutilities.neoforge.generation.GenerationPreselectionFilter;
import com.zianblk.zianutilities.neoforge.generation.NaturalSpawnGuard;
import com.zianblk.zianutilities.neoforge.generation.PokeSnackSpawnGuard;
import com.zianblk.zianutilities.neoforge.hub.ZianHubCommands;
import com.zianblk.zianutilities.neoforge.hub.ZianHubNetwork;
import com.zianblk.zianutilities.neoforge.gacha.GachaNetwork;
import com.zianblk.zianutilities.neoforge.gacha.GachaCommands;
import com.zianblk.zianutilities.neoforge.rewards.RewardCommands;
import com.zianblk.zianutilities.neoforge.quests.CaptureEventProbe;
import com.zianblk.zianutilities.neoforge.quests.CaptureTrialCommands;
import com.zianblk.zianutilities.neoforge.quests.CaptureTrialListener;
import com.zianblk.zianutilities.neoforge.quests.CaptureMissionCommands;
import com.zianblk.zianutilities.neoforge.quests.CaptureMissionListener;
import com.zianblk.zianutilities.neoforge.quests.CaptureCycleCommands;
import com.zianblk.zianutilities.neoforge.quests.CaptureCycleListener;
import com.zianblk.zianutilities.neoforge.quests.SharedCaptureMissionCommands;
import com.zianblk.zianutilities.neoforge.quests.SharedCaptureMissionListener;
import com.zianblk.zianutilities.neoforge.quests.GlobalQuestCommands;
import com.zianblk.zianutilities.neoforge.quests.GlobalQuestListener;
import com.zianblk.zianutilities.neoforge.quests.GlobalQuestNetwork;
import com.zianblk.zianutilities.neoforge.quests.ProgressionQuestNetwork;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(ZianUtilitiesMod.MOD_ID)
public final class ZianUtilitiesMod {
    public static final String MOD_ID = "zianutilities";

    public ZianUtilitiesMod(IEventBus modBus) {
        modBus.addListener(GlobalQuestNetwork::register);
        modBus.addListener(ProgressionQuestNetwork::register);
        modBus.addListener(ZianHubNetwork::register);
        modBus.addListener(GachaNetwork::register);
        NeoForge.EVENT_BUS.addListener(GenerationCommands::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(RewardCommands::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(CaptureTrialCommands::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(CaptureMissionCommands::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(CaptureCycleCommands::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(SharedCaptureMissionCommands::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(GlobalQuestCommands::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(ZianHubCommands::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(GachaCommands::onRegisterCommands);
        GenerationPreselectionFilter.install();
        NaturalSpawnGuard.install();
        FishingSpawnGuard.install();
        PokeSnackSpawnGuard.install();
        FinalSpawnDiagnostics.install();
        CaptureEventProbe.install();
        CaptureTrialListener.install();
        CaptureMissionListener.install();
        CaptureCycleListener.install();
        SharedCaptureMissionListener.install();
        GlobalQuestListener.install();
    }
}
