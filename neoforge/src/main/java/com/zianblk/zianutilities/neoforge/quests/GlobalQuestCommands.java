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
                    if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                        context.getSource().sendFailure(Component.literal("Abre la misión desde el juego."));
                        return 0;
                    }
                    GlobalQuestMenu.open(player);
                    return Command.SINGLE_SUCCESS;
                })));
    }
}

