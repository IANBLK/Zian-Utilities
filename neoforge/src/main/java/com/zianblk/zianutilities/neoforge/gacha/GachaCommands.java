package com.zianblk.zianutilities.neoforge.gacha;
import com.zianblk.zianutilities.neoforge.Tr;

import com.zianblk.zianutilities.neoforge.ZianUtilitiesMod;

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
        var gacha = Commands.literal("gacha").requires(s->com.zianblk.zianutilities.neoforge.UtilitiesPermissions.allows(s,"gacha.use",false)).executes(GachaCommands::open);
        gacha.then(Commands.literal("review")
            .requires(source -> com.zianblk.zianutilities.neoforge.UtilitiesPermissions.allows(source,"admin.gacha",true))
            .then(Commands.argument("player", EntityArgument.player()).executes(GachaCommands::review)));
        gacha.then(Commands.literal("confirm-delivered")
            .requires(source -> com.zianblk.zianutilities.neoforge.UtilitiesPermissions.allows(source,"admin.gacha",true))
            .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("operationId", StringArgumentType.word()).suggests(GachaCommands::suggestOperations)
                    .executes(GachaCommands::confirmDelivered))));
        gacha.then(Commands.literal("resolve-debit")
            .requires(source -> com.zianblk.zianutilities.neoforge.UtilitiesPermissions.allows(source,"admin.gacha",true))
            .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("operationId", StringArgumentType.word()).suggests(GachaCommands::suggestOperations)
                    .then(Commands.literal("charged")
                        .then(Commands.argument("evidence", StringArgumentType.greedyString())
                            .executes(context -> resolveDebit(context, true))))
                    .then(Commands.literal("not-charged")
                        .then(Commands.argument("evidence", StringArgumentType.greedyString())
                            .executes(context -> resolveDebit(context, false)))))));
        gacha.then(Commands.literal("confirm-compensated")
            .requires(source -> com.zianblk.zianutilities.neoforge.UtilitiesPermissions.allows(source,"admin.gacha",true))
            .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("operationId", StringArgumentType.word()).suggests(GachaCommands::suggestOperations)
                    .then(Commands.argument("evidence", StringArgumentType.greedyString())
                        .executes(GachaCommands::confirmCompensated)))));
        event.getDispatcher().register(Commands.literal(ZianUtilitiesMod.COMMAND_ROOT).requires(source -> true).then(gacha));
    }

    private static int open(CommandContext<CommandSourceStack> context) {
        if (!GachaRuntime.enabled()) {
            context.getSource().sendFailure(Tr.c("zianutilities.gacha.cmd.gachas_desactivados", "Gachas desactivados."));
            return 0;
        }
        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
            context.getSource().sendFailure(Tr.c("zianutilities.gacha.cmd.abre_gachas_desde_el_juego", "Abre Gachas desde el juego."));
            return 0;
        }
        if (!GachaNetwork.hasClient(player)) {
            context.getSource().sendFailure(Tr.c("zianutilities.gacha.cmd.instala_la_misma_version_de", "Instala la misma versión de Zian Utilities en tu cliente."));
            return 0;
        }
        GachaNetwork.send(player, true);
        return Command.SINGLE_SUCCESS;
    }

    private static int review(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(context, "player");
            var pending = GachaRuntime.review(target);
            if (pending.isEmpty()) context.getSource().sendSuccess(() -> Tr.c("zianutilities.gacha.cmd.no_hay_tiradas_pendientes_para", "No hay tiradas pendientes para %s", String.valueOf(target.getGameProfile().getName())), false);
            for (var operation : pending) context.getSource().sendSuccess(() -> Tr.c("zianutilities.gacha.cmd.premio_motivo", "%s | %s | %s | %s %s | premio: %s x%s | motivo: %s", String.valueOf(com.zianblk.zianutilities.neoforge.references.References.label(target.server,target.getUUID(),operation.id())), String.valueOf(operation.phase()), String.valueOf(operation.poolName()), String.valueOf(operation.cost()), String.valueOf(operation.ticket()), operation.prize().getHoverName().getString(), String.valueOf(operation.prize().getCount()), String.valueOf(operation.reason())), false);
            return Command.SINGLE_SUCCESS;
        } catch (Exception error) {
            context.getSource().sendFailure(Tr.err(error));
            return 0;
        }
    }

    private static int confirmDelivered(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(context, "player");
            UUID id = operationId(context);
            GachaRuntime.confirmDelivered(target, id, admin(context));
            context.getSource().sendSuccess(() -> Tr.c("zianutilities.gacha.cmd.entrega_verificada_la_tirada_quedo", "Entrega verificada. La tirada %s quedó cerrada.", String.valueOf(id)), true);
            return Command.SINGLE_SUCCESS;
        } catch (Exception error) {
            context.getSource().sendFailure(Tr.err(error));
            return 0;
        }
    }

    private static int resolveDebit(CommandContext<CommandSourceStack> context, boolean charged) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(context, "player");
            UUID id = operationId(context);
            GachaRuntime.resolveDebit(target, id, admin(context), charged,
                StringArgumentType.getString(context, "evidence"));
            context.getSource().sendSuccess(() -> Tr.c("zianutilities.gacha.cmd.debit_resolved", "Cobro %s en tirada %s.", charged ? Tr.c("zianutilities.gacha.cmd.debit_charged", "confirmado; premio listo para reclamar") : Tr.c("zianutilities.gacha.cmd.debit_discarded", "descartado"), String.valueOf(id)), true);
            return Command.SINGLE_SUCCESS;
        } catch (Exception error) {
            context.getSource().sendFailure(Tr.err(error));
            return 0;
        }
    }

    private static int confirmCompensated(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(context, "player");
            UUID id = operationId(context);
            GachaRuntime.confirmCompensated(target, id, admin(context),
                StringArgumentType.getString(context, "evidence"));
            context.getSource().sendSuccess(() -> Tr.c("zianutilities.gacha.cmd.compensacion_confirmada_la_tirada_quedo", "Compensación confirmada. La tirada %s quedó cerrada.", String.valueOf(id)), true);
            return Command.SINGLE_SUCCESS;
        } catch (Exception error) {
            context.getSource().sendFailure(Tr.err(error));
            return 0;
        }
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestOperations(CommandContext<CommandSourceStack> c,com.mojang.brigadier.suggestion.SuggestionsBuilder b){try{var target=EntityArgument.getPlayer(c,"player");var refs=new java.util.ArrayList<String>();for(var operation:GachaRuntime.review(target))refs.add(com.zianblk.zianutilities.neoforge.references.References.label(target.server,target.getUUID(),operation.id()));return net.minecraft.commands.SharedSuggestionProvider.suggest(refs,b);}catch(Exception unavailable){return b.buildFuture();}}
    private static UUID operationId(CommandContext<CommandSourceStack> context) {
        try{var target=EntityArgument.getPlayer(context,"player");return com.zianblk.zianutilities.neoforge.references.References.resolve(context.getSource().getServer(),target.getUUID(),StringArgumentType.getString(context,"operationId"));}catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){throw new IllegalArgumentException("Jugador no disponible",e);}
    }

    private static UUID admin(CommandContext<CommandSourceStack> context) {
        return context.getSource().getEntity() instanceof ServerPlayer operator
            ? operator.getUUID() : new UUID(0L, 0L);
    }
}
