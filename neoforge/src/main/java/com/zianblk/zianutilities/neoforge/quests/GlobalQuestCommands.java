package com.zianblk.zianutilities.neoforge.quests;

import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class GlobalQuestCommands {
    private GlobalQuestCommands() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("zian")
            .then(Commands.literal("quest")
                .requires(source -> Boolean.getBoolean(GlobalQuestRuntime.TEST_FLAG))
                .executes(context -> {
                    // Other Zian modules register the same parent literal; Brigadier merges its children.
                    if (!Boolean.getBoolean(GlobalQuestRuntime.TEST_FLAG)) {
                        context.getSource().sendFailure(Component.literal("La misión global de prueba está desactivada."));
                        return 0;
                    }
                    if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                        context.getSource().sendFailure(Component.literal("Abre la misión desde el juego."));
                        return 0;
                    }
                    if (GlobalQuestNetwork.hasVisualClient(player)) {
                        GlobalQuestNetwork.send(player, true);
                    } else {
                        GlobalQuestMenu.open(player);
                    }
                    return Command.SINGLE_SUCCESS;
                })));
    }
}
