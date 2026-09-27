package com.zianblk.zianutilities.neoforge.quests;

import com.mojang.brigadier.Command;
import com.zianblk.zianutilities.core.generation.Generation;
import com.zianblk.zianutilities.core.quests.CaptureCycle;
import com.zianblk.zianutilities.neoforge.generation.NeoForgeGenerationStateStore;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Set;

/** Operator assignment and player self-status for the opt-in six-hour trial. */
public final class CaptureCycleCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger("ZianUtilities/CaptureCycle");
    static final String TEST_FLAG = "zianutilities.captureCycleTestEnabled";

    private CaptureCycleCommands() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("zian").requires(source -> true)
                .then(Commands.literal("quest")
                    .then(Commands.literal("cycle")
                        .requires(source -> Boolean.getBoolean(TEST_FLAG))
                        .then(Commands.literal("start")
                            .requires(source -> source.hasPermission(3))
                            .executes(context -> start(context.getSource())))
                        .then(Commands.literal("assign")
                            .requires(source -> source.hasPermission(3))
                            .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> assign(
                                    context.getSource(), EntityArgument.getPlayer(context, "player")))))
                        .then(Commands.literal("status")
                            .executes(context -> status(context.getSource())))))
        );
    }

    private static int start(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Inicia tu ciclo desde el juego o asígnalo a un jugador."));
            return 0;
        }
        return assign(source, player);
    }

    private static int assign(CommandSourceStack source, ServerPlayer player) {
        try {
            Set<Generation> enabled = activeGenerations(source);
            var service = CaptureCycleRuntime.service(source.getServer());
            if (service.inspect(player.getUUID(), enabled) == null && enabled.isEmpty()) {
                source.sendFailure(Component.literal("No hay generaciones activas; no se asignó el ciclo."));
                return 0;
            }
            CaptureCycle cycle = service.assign(player.getUUID(), enabled);
            LOGGER.info("[ZIAN-QUEST-CYCLE] action=assign actor={} playerUuid={} cycleId={} progress={}",
                source.getTextName(), player.getUUID(), cycle.getCycleId(), cycle.getCaptures().size());
            if (source.getEntity() != player) {
                source.sendSuccess(() -> Component.literal("Ciclo de captura de "
                    + player.getGameProfile().getName() + ":"), false);
            }
            show(source, cycle, enabled);
            return Command.SINGLE_SUCCESS;
        } catch (Exception error) {
            LOGGER.error("[ZIAN-QUEST-CYCLE] action=assign result=error", error);
            source.sendFailure(Component.literal("No se pudo guardar el ciclo; no captures para probarlo."));
            return 0;
        }
    }

    private static int status(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Consulta tu ciclo desde el juego."));
            return 0;
        }
        try {
            Set<Generation> enabled = activeGenerations(source);
            CaptureCycle cycle = CaptureCycleRuntime.service(source.getServer())
                .inspect(player.getUUID(), enabled);
            if (cycle == null) {
                source.sendSuccess(() -> Component.literal("Ciclo captura: no asignado."), false);
            } else {
                show(source, cycle, enabled);
            }
            return Command.SINGLE_SUCCESS;
        } catch (Exception error) {
            LOGGER.error("[ZIAN-QUEST-CYCLE] action=status result=error", error);
            source.sendFailure(Component.literal("No se pudo leer el ciclo; no lo reinicies."));
            return 0;
        }
    }

    private static Set<Generation> activeGenerations(CommandSourceStack source) {
        return new NeoForgeGenerationStateStore(source.getServer()).load().getEnabled();
    }

    private static void show(CommandSourceStack source, CaptureCycle cycle, Set<Generation> enabled) {
        long remainingMs = Math.max(0, cycle.getExpiresAtEpochMs() - System.currentTimeMillis());
        long minutes = Duration.ofMillis(remainingMs).toMinutes();
        String state = enabled.isEmpty() ? "PAUSADO (sin generaciones activas)"
            : cycle.getCompleted() ? "COMPLETADO" : "ACTIVO";
        source.sendSuccess(() -> Component.literal("Ciclo captura " + cycle.getCycleId() + ": "
            + cycle.getCaptures().size() + "/" + CaptureCycle.GOAL + " " + state
            + "; quedan " + minutes + " min; sin recompensa."), false);
    }
}

