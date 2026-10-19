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
        com.zianblk.zianutilities.neoforge.breeding.BreedingCommands.register(event);

        for(String parent:java.util.List.of("crianza","breeding-admin"))for(var node:dispatcher.getRoot().getChild("ZianUtilities").getChild(parent).getChildren()){
            var id=node.getChild("id");if(id instanceof com.mojang.brigadier.tree.ArgumentCommandNode<?,?> argument)assertNotNull(argument.getCustomSuggestions(),"UUID autocomplete missing for "+node.getName());
        }
        assertNull(dispatcher.getRoot().getChild("zian"));
        assertEquals(2, dispatcher.getRoot().getChildren().size());
        assertNotNull(dispatcher.getRoot().getChild("crianza"));
        var root = dispatcher.getRoot().getChild("ZianUtilities");
        assertNotNull(root);
        assertNotNull(root.getCommand(), "Opening the hub must remain available");
        assertTrue(root.getRequirement().test(null), "The shared root must stay public on Youer");
        for (String[] path : new String[][] {
            {"crianza", "start", "first", "second"}, {"breeding-admin", "complete", "id"}, {"breeding-admin", "resolve-debit", "id", "charged", "evidence"},
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
