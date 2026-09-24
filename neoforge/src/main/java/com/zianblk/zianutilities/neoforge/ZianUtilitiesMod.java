package com.zianblk.zianutilities.neoforge;

import com.zianblk.zianutilities.neoforge.generation.GenerationCommands;
import com.zianblk.zianutilities.neoforge.generation.NaturalSpawnGuard;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(ZianUtilitiesMod.MOD_ID)
public final class ZianUtilitiesMod {
    public static final String MOD_ID = "zianutilities";

    public ZianUtilitiesMod() {
        NeoForge.EVENT_BUS.addListener(GenerationCommands::onRegisterCommands);
        NaturalSpawnGuard.install();
    }
}
