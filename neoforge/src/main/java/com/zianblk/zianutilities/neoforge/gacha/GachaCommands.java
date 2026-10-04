package com.zianblk.zianutilities.neoforge.gacha;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.UUID;

public final class GachaCommands {
    private GachaCommands() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        var gacha = Commands.literal("gacha").executes(GachaCommands::open);
        gacha.then(Commands.literal("review")
            .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(Commands.argument("player", EntityArgument.player()).executes(GachaCommands::review)));
        gacha.then(Commands.literal("confirm-delivered")
            .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("operationId", StringArgumentType.word())
                    .executes(GachaCommands::confirmDelivered))));
        gacha.then(Commands.literal("resolve-debit")
            .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("operationId", StringArgumentType.word())
                    .then(Commands.literal("charged")
                        .then(Commands.argument("evidence", StringArgumentType.greedyString())
                            .executes(context -> resolveDebit(context, true))))
                    .then(Commands.literal("not-charged")
                        .then(Commands.argument("evidence", StringArgumentType.greedyString())
                            .executes(context -> resolveDebit(context, false)))))));
        gacha.then(Commands.literal("confirm-compensated")
            .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("operationId", StringArgumentType.word())
                    .then(Commands.argument("evidence", StringArgumentType.greedyString())
                        .executes(GachaCommands::confirmCompensated)))));
        event.getDispatcher().register(Commands.literal("zian").requires(source -> true).then(gacha));
    }

    private static int open(CommandContext<CommandSourceStack> context) {
        if (!GachaRuntime.enabled()) {
            context.getSource().sendFailure(Component.literal("Gachas desactivados."));
            return 0;
        }
        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
            context.getSource().sendFailure(Component.literal("Abre Gachas desde el juego."));
            return 0;
        }
        if (!GachaNetwork.hasClient(player)) {
            context.getSource().sendFailure(Component.literal("Instala la misma versión de Zian Utilities en tu cliente."));
            return 0;
        }
        GachaNetwork.send(player, true);
        return Command.SINGLE_SUCCESS;
    }

    private static int review(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(context, "player");
            var pending = GachaRuntime.review(target);
            if (pending.isEmpty()) context.getSource().sendSuccess(() -> Component.literal(
                "No hay tiradas pendientes para " + target.getGameProfile().getName()), false);
            for (var operation : pending) context.getSource().sendSuccess(() -> Component.literal(
                operation.id() + " | " + operation.phase() + " | " + operation.poolName()
                    + " | " + operation.cost() + " " + operation.ticket()
                    + " | premio: " + operation.prize().getHoverName().getString()
                    + " x" + operation.prize().getCount() + " | motivo: " + operation.reason()), false);
            return Command.SINGLE_SUCCESS;
        } catch (Exception error) {
            context.getSource().sendFailure(Component.literal(error.getMessage()));
            return 0;
        }
    }

    private static int confirmDelivered(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(context, "player");
            UUID id = operationId(context);
            GachaRuntime.confirmDelivered(target, id, admin(context));
            context.getSource().sendSuccess(() -> Component.literal(
                "Entrega verificada. La tirada " + id + " quedó cerrada."), true);
            return Command.SINGLE_SUCCESS;
        } catch (Exception error) {
            context.getSource().sendFailure(Component.literal(error.getMessage()));
            return 0;
        }
    }

    private static int resolveDebit(CommandContext<CommandSourceStack> context, boolean charged) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(context, "player");
            UUID id = operationId(context);
            GachaRuntime.resolveDebit(target, id, admin(context), charged,
                StringArgumentType.getString(context, "evidence"));
            context.getSource().sendSuccess(() -> Component.literal(
                "Cobro " + (charged ? "confirmado; premio listo para reclamar" : "descartado")
                    + " en tirada " + id + "."), true);
            return Command.SINGLE_SUCCESS;
        } catch (Exception error) {
            context.getSource().sendFailure(Component.literal(error.getMessage()));
            return 0;
        }
    }

    private static int confirmCompensated(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(context, "player");
            UUID id = operationId(context);
            GachaRuntime.confirmCompensated(target, id, admin(context),
                StringArgumentType.getString(context, "evidence"));
            context.getSource().sendSuccess(() -> Component.literal(
                "Compensación confirmada. La tirada " + id + " quedó cerrada."), true);
            return Command.SINGLE_SUCCESS;
        } catch (Exception error) {
            context.getSource().sendFailure(Component.literal(error.getMessage()));
            return 0;
        }
    }

    private static UUID operationId(CommandContext<CommandSourceStack> context) {
        return UUID.fromString(StringArgumentType.getString(context, "operationId"));
    }

    private static UUID admin(CommandContext<CommandSourceStack> context) {
        return context.getSource().getEntity() instanceof ServerPlayer operator
            ? operator.getUUID() : new UUID(0L, 0L);
    }
}
