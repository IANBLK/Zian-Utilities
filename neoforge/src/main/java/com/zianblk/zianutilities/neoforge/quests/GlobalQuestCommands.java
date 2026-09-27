package com.zianblk.zianutilities.neoforge.quests;

import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.io.IOException;

public final class GlobalQuestCommands {
    private GlobalQuestCommands() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("zian")
            .requires(source -> true)
            .then(Commands.literal("quest")
                .requires(source -> Boolean.getBoolean(GlobalQuestRuntime.TEST_FLAG))
                .then(Commands.literal("test")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("rotate")
                        .executes(context -> rotateForTest(context.getSource())))
                    .then(Commands.literal("pool")
                        .executes(context -> showPool(context.getSource()))))
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
                    if (!GlobalQuestNetwork.hasVisualClient(player)) {
                        context.getSource().sendFailure(Component.literal(
                            "Instala la misma versión de Zian Utilities en tu cliente."));
                        return 0;
                    }
                    GlobalQuestNetwork.send(player, true);
                    return Command.SINGLE_SUCCESS;
                })));
    }

    private static int rotateForTest(net.minecraft.commands.CommandSourceStack source) {
        try {
            var offer = GlobalQuestRuntime.rotateForTest(source.getServer());
            for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
                GlobalQuestNetwork.send(player, false);
            }
            source.sendSuccess(() -> Component.literal("Objetivo cambiado a " + offer.getTargetSpecies()
                + ". Acepta de nuevo. Esta rotación de prueba no paga recompensas; "
                + "el horario real de reinicio no cambió."), false);
            return Command.SINGLE_SUCCESS;
        } catch (IllegalStateException error) {
            source.sendFailure(Component.literal("No se puede rotar: se necesitan dos especies válidas "
                + "para las generaciones activas."));
        } catch (IOException error) {
            source.sendFailure(Component.literal("No se pudo guardar la rotación; revisa la consola."));
        }
        return 0;
    }

    private static int showPool(net.minecraft.commands.CommandSourceStack source) {
        try {
            int count = GlobalQuestRuntime.eligibleSpeciesCount(source.getServer());
            source.sendSuccess(() -> Component.literal("Especies elegibles para la misión de captura: "
                + count + ". El sorteo usa las generaciones activas y apariciones naturales de Cobblemon."), false);
            return Command.SINGLE_SUCCESS;
        } catch (IOException error) {
            source.sendFailure(Component.literal("No se pudo consultar el grupo de especies; revisa la consola."));
            return 0;
        }
    }
}
