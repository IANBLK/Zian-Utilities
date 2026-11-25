package com.zianblk.zianutilities.neoforge.quests;
import com.zianblk.zianutilities.neoforge.Tr;

import com.zianblk.zianutilities.neoforge.ZianUtilitiesMod;

import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.io.IOException;

public final class GlobalQuestCommands {
    private GlobalQuestCommands() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(ZianUtilitiesMod.COMMAND_ROOT)
            .requires(source -> true)
            .then(Commands.literal("quest")
                .requires(source -> GlobalQuestRuntime.moduleEnabled())
                .then(Commands.literal("test")
                    .requires(source -> source.hasPermission(2)
                        && Boolean.getBoolean(GlobalQuestRuntime.TEST_FLAG))
                    .then(Commands.literal("rotate")
                        .executes(context -> rotateForTest(context.getSource())))
                    .then(Commands.literal("pool")
                        .executes(context -> showPool(context.getSource())))
                    .then(Commands.literal("weekly")
                        .then(Commands.literal("rotate")
                            .executes(context -> rotateWeeklyForTest(context.getSource())))))
                .executes(context -> {
                    // Other Zian modules register the same parent literal; Brigadier merges its children.
                    if (!GlobalQuestRuntime.moduleEnabled()) {
                        context.getSource().sendFailure(Tr.c("zianutilities.quests.cmd.la_mision_global_esta_desactivada", "La misión global está desactivada."));
                        return 0;
                    }
                    if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                        context.getSource().sendFailure(Tr.c("zianutilities.quests.cmd.abre_la_mision_desde_el", "Abre la misión desde el juego."));
                        return 0;
                    }
                    if (!GlobalQuestNetwork.hasVisualClient(player)) {
                        context.getSource().sendFailure(Tr.c("zianutilities.gacha.cmd.instala_la_misma_version_de", "Instala la misma versión de Zian Utilities en tu cliente."));
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
            source.sendSuccess(() -> Tr.c("zianutilities.quests.cmd.objetivo_cambiado_acepta_de_nuevo", "Objetivo cambiado a %s. Acepta de nuevo. Esta rotación de prueba no paga recompensas; el horario real de reinicio no cambió.", String.valueOf(offer.getTargetSpecies())), false);
            return Command.SINGLE_SUCCESS;
        } catch (IllegalStateException error) {
            source.sendFailure(Tr.c("zianutilities.quests.cmd.no_se_puede_rotar_se", "No se puede rotar: se necesitan dos especies válidas para las generaciones activas."));
        } catch (IOException error) {
            source.sendFailure(Tr.c("zianutilities.quests.cmd.no_se_pudo_guardar_la", "No se pudo guardar la rotación; revisa la consola."));
        }
        return 0;
    }

    private static int showPool(net.minecraft.commands.CommandSourceStack source) {
        try {
            int count = GlobalQuestRuntime.eligibleSpeciesCount(source.getServer());
            source.sendSuccess(() -> Tr.c("zianutilities.quests.cmd.especies_elegibles_para_la_mision", "Especies elegibles para la misión de captura: %s. El sorteo usa las generaciones activas y apariciones naturales de Cobblemon.", String.valueOf(count)), false);
            return Command.SINGLE_SUCCESS;
        } catch (IOException error) {
            source.sendFailure(Tr.c("zianutilities.quests.cmd.no_se_pudo_consultar_el", "No se pudo consultar el grupo de especies; revisa la consola."));
            return 0;
        }
    }

    private static int rotateWeeklyForTest(net.minecraft.commands.CommandSourceStack source) {
        try {
            ProgressionQuestRuntime.rotateWeekForTest(source.getServer());
            for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
                ProgressionQuestNetwork.send(player, false);
            }
            source.sendSuccess(() -> Tr.c("zianutilities.quests.cmd.semana_de_prueba_iniciada_el", "Semana de prueba iniciada. El progreso semanal vuelve a cero; esta rotación no paga recompensas y el horario real no cambia."), false);
            return Command.SINGLE_SUCCESS;
        } catch (Exception error) {
            source.sendFailure(Tr.c("zianutilities.quests.cmd.no_se_pudo_iniciar_la", "No se pudo iniciar la semana de prueba; revisa la consola."));
            return 0;
        }
    }
}
