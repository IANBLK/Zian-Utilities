package com.zianblk.zianutilities.neoforge.hub;
import com.zianblk.zianutilities.neoforge.Tr;

import com.zianblk.zianutilities.neoforge.ZianUtilitiesMod;

import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class ZianHubCommands {
    private ZianHubCommands() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(ZianUtilitiesMod.COMMAND_ROOT)
            // Youer may rewrite a default root requirement into a Bukkit permission.
            // Keep this shared root public; protected child commands retain their checks.
            .requires(source -> true)
            .executes(context -> open(context.getSource()))
            .then(Commands.literal("menu").executes(context -> open(context.getSource()))));
    }

    private static int open(net.minecraft.commands.CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Tr.c("zianutilities.hub.cmd.abre_el_menu_desde_el", "Abre el menú desde el juego."));
            return 0;
        }
        if (!ZianHubNetwork.hasClient(player)) {
            source.sendFailure(Tr.c("zianutilities.gacha.cmd.instala_la_misma_version_de", "Instala la misma versión de Zian Utilities en tu cliente."));
            return 0;
        }
        ZianHubNetwork.open(player);
        return Command.SINGLE_SUCCESS;
    }
}
