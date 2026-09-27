package com.zianblk.zianutilities.neoforge.gacha;

import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class GachaCommands {
    private GachaCommands() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("zian")
            .requires(source -> true)
            .then(Commands.literal("gacha").executes(context -> {
                if (!GachaRuntime.enabled()) {
                    context.getSource().sendFailure(Component.literal("Gachas desactivados."));
                    return 0;
                }
                if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                    context.getSource().sendFailure(Component.literal("Abre Gachas desde el juego."));
                    return 0;
                }
                if (!GachaNetwork.hasClient(player)) {
                    context.getSource().sendFailure(Component.literal(
                        "Instala la misma versión de Zian Utilities en tu cliente."));
                    return 0;
                }
                GachaNetwork.send(player, true);
                return Command.SINGLE_SUCCESS;
            })));
    }
}

