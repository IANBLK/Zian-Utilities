package com.zianblk.zianutilities.neoforge.quests;

import com.mojang.brigadier.Command;
import com.zianblk.zianutilities.core.generation.Generation;
import com.zianblk.zianutilities.core.quests.CaptureTrial;
import com.zianblk.zianutilities.neoforge.generation.NeoForgeGenerationStateStore;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

/** Explicit operator-only trial. No normal quest or reward command is enabled. */
public final class CaptureTrialCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger("ZianUtilities/CaptureTrial");
    static final String TEST_FLAG = "zianutilities.questTrialEnabled";

    private CaptureTrialCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("zian")
                .then(Commands.literal("quest")
                    .then(Commands.literal("plan")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> plan(context.getSource())))
                    .then(Commands.literal("trial")
                        .requires(source -> source.hasPermission(3) && Boolean.getBoolean(TEST_FLAG))
                        .then(Commands.literal("start")
                            .executes(context -> start(context.getSource())))
                        .then(Commands.literal("status")
                            .executes(context -> status(context.getSource())))
                    )
                )
        );
    }

    private static int plan(CommandSourceStack source) {
        try {
            Set<Generation> enabled = new NeoForgeGenerationStateStore(source.getServer())
                .load().getEnabled();
            if (enabled.isEmpty()) {
                source.sendSuccess(
                    () -> Component.literal("Vista previa de captura: PAUSADA; no hay generaciones activas."),
                    false
                );
                return Command.SINGLE_SUCCESS;
            }
            String generations = generationIds(enabled);
            source.sendSuccess(
                () -> Component.literal(
                    "Vista previa (sin asignar): captura 1 Pokémon de cualquier generación activa ("
                        + generations + "). Objetivos por tipo: desactivados."
                ),
                false
            );
            return Command.SINGLE_SUCCESS;
        } catch (Exception error) {
            LOGGER.error("[ZIAN-QUEST-PLAN] result=error", error);
            source.sendFailure(Component.literal("No se pudo leer el plan de captura."));
            return 0;
        }
    }

    private static int start(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("El ensayo debe iniciarlo un jugador operador."));
            return 0;
        }
        try {
            Set<Generation> enabled = new NeoForgeGenerationStateStore(source.getServer())
                .load().getEnabled();
            if (enabled.isEmpty()) {
                source.sendFailure(Component.literal("Activa una generación antes de iniciar el ensayo."));
                return 0;
            }
            CaptureTrial trial = CaptureTrialRuntime.service(source.getServer())
                .start(player.getUUID(), enabled);
            LOGGER.info(
                "[ZIAN-QUEST-TRIAL] action=start playerUuid={} assignmentId={} completed={}",
                player.getUUID(), trial.getAssignmentId(), trial.getCapturedSpeciesId() != null
            );
            showTrial(source, trial);
            return Command.SINGLE_SUCCESS;
        } catch (Exception error) {
            LOGGER.error("[ZIAN-QUEST-TRIAL] action=start result=error", error);
            source.sendFailure(Component.literal("No se pudo guardar el ensayo; no captures para probarlo."));
            return 0;
        }
    }

    private static int status(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Consulta el ensayo como jugador operador."));
            return 0;
        }
        try {
            Set<Generation> enabled = new NeoForgeGenerationStateStore(source.getServer())
                .load().getEnabled();
            CaptureTrial trial = CaptureTrialRuntime.service(source.getServer())
                .synchronize(player.getUUID(), enabled);
            if (trial == null) {
                source.sendSuccess(() -> Component.literal("Ensayo de captura: no iniciado."), false);
            } else {
                showTrial(source, trial, enabled);
            }
            return Command.SINGLE_SUCCESS;
        } catch (Exception error) {
            LOGGER.error("[ZIAN-QUEST-TRIAL] action=status result=error", error);
            source.sendFailure(Component.literal("No se pudo leer el ensayo; no lo reinicies."));
            return 0;
        }
    }

    private static String generationIds(Set<Generation> generations) {
        return generations.stream()
            .sorted(Comparator.comparingInt(Enum::ordinal))
            .map(Generation::getId)
            .collect(Collectors.joining(","));
    }

    private static void showTrial(CommandSourceStack source, CaptureTrial trial) {
        Set<Generation> enabled = new NeoForgeGenerationStateStore(source.getServer())
            .load().getEnabled();
        showTrial(source, trial, enabled);
    }

    private static void showTrial(
        CommandSourceStack source,
        CaptureTrial trial,
        Set<Generation> currentlyEnabled
    ) {
        String generations = trial.getCapturedSpeciesId() == null && currentlyEnabled.isEmpty()
            ? "ninguna"
            : generationIds(trial.getTargetGenerations());
        String progress = trial.getCapturedSpeciesId() != null
            ? "1/1 COMPLETADO"
            : currentlyEnabled.isEmpty() ? "0/1 PAUSADO" : "0/1 ACTIVO";
        String species = trial.getCapturedSpeciesId() == null
            ? "" : " especie=" + trial.getCapturedSpeciesId();
        source.sendSuccess(
            () -> Component.literal("Ensayo captura " + trial.getAssignmentId()
                + ": " + progress + " generaciones=" + generations + species
                + "; sin recompensa."),
            false
        );
    }
}

