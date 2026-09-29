package com.zianblk.zianutilities.neoforge.gacha;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.UUID;

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
            }).then(Commands.literal("review")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(context -> {
                        try {
                            ServerPlayer target = EntityArgument.getPlayer(context, "player");
                            var pending = GachaRuntime.review(target);
                            if (pending.isEmpty()) {
                                context.getSource().sendSuccess(() -> Component.literal(
                                    "No hay tiradas pendientes para " + target.getGameProfile().getName()), false);
                            }
                            for (var operation : pending) {
                                context.getSource().sendSuccess(() -> Component.literal(
                                    operation.id() + " | " + operation.phase() + " | "
                                        + operation.poolName() + " | " + operation.cost() + " "
                                        + operation.ticket() + " | premio: "
                                        + operation.prize().getHoverName().getString() + " x"
                                        + operation.prize().getCount()), false);
                            }
                            return Command.SINGLE_SUCCESS;
                        } catch (Exception error) {
                            context.getSource().sendFailure(Component.literal(error.getMessage()));
                            return 0;
                        }
                    })))
            .then(Commands.literal("confirm-delivered")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("operationId", StringArgumentType.word())
                        .executes(context -> {
                            try {
                                ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                UUID id = UUID.fromString(StringArgumentType.getString(context, "operationId"));
                                UUID admin = context.getSource().getEntity() instanceof ServerPlayer operator
                                    ? operator.getUUID() : new UUID(0L, 0L);
                                GachaRuntime.confirmDelivered(target, id, admin);
                                context.getSource().sendSuccess(() -> Component.literal(
                                    "Entrega confirmada. La tirada " + id + " quedó cerrada."), true);
                                return Command.SINGLE_SUCCESS;
                            } catch (Exception error) {
                                context.getSource().sendFailure(Component.literal(error.getMessage()));
                                return 0;
                            }
                        }))))));
    }
}
