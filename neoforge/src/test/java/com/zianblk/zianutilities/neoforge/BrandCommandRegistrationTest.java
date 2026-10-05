package com.zianblk.zianutilities.neoforge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.zianblk.zianutilities.neoforge.gacha.GachaCommands;
import com.zianblk.zianutilities.neoforge.generation.GenerationCommands;
import com.zianblk.zianutilities.neoforge.hub.ZianHubCommands;
import com.zianblk.zianutilities.neoforge.quests.*;
import com.zianblk.zianutilities.neoforge.rewards.RewardCommands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BrandCommandRegistrationTest {
    @Test
    void allModulesMergeUnderTheBrandRootWithoutKeepingTheOldRoot() {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        var event = new RegisterCommandsEvent(dispatcher, Commands.CommandSelection.ALL, null);
        GenerationCommands.onRegisterCommands(event);
        RewardCommands.onRegisterCommands(event);
        CaptureTrialCommands.onRegisterCommands(event);
        CaptureMissionCommands.onRegisterCommands(event);
        CaptureCycleCommands.onRegisterCommands(event);
        SharedCaptureMissionCommands.onRegisterCommands(event);
        GlobalQuestCommands.onRegisterCommands(event);
        ZianHubCommands.onRegisterCommands(event);
        GachaCommands.onRegisterCommands(event);

        assertNull(dispatcher.getRoot().getChild("zian"));
        assertEquals(1, dispatcher.getRoot().getChildren().size());
        var root = dispatcher.getRoot().getChild("ZianUtilities");
        assertNotNull(root);
        assertNotNull(root.getCommand(), "Opening the hub must remain available");
        assertTrue(root.getRequirement().test(null), "The shared root must stay public on Youer");
        for (String[] path : new String[][] {
            {"generation", "active"}, {"generation", "enable", "gen"},
            {"reward", "inspect"}, {"gacha", "review", "player"},
            {"gacha", "confirm-delivered", "player", "operationId"},
            {"quest", "trial", "start"}, {"quest", "capture", "status"},
            {"quest", "cycle"}, {"quest", "shared"}, {"quest", "test", "weekly", "rotate"}
        }) {
            CommandNode<CommandSourceStack> node = root;
            for (String name : path) {
                node = node.getChild(name);
                assertNotNull(node, "Missing merged command: " + String.join(" ", path));
            }
        }
    }
}
