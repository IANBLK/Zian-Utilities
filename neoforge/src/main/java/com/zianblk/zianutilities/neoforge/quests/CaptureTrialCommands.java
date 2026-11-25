package com.zianblk.zianutilities.neoforge.quests;
import com.zianblk.zianutilities.neoforge.Tr;

import com.zianblk.zianutilities.neoforge.ZianUtilitiesMod;

import com.mojang.brigadier.Command;
import com.zianblk.zianutilities.core.generation.Generation;
import com.zianblk.zianutilities.core.quests.CaptureEligibilityPlan;
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
            Commands.literal(ZianUtilitiesMod.COMMAND_ROOT)
                .requires(source -> true)
                .then(Commands.literal("quest")
                    .then(Commands.literal("plan")
                        .requires(source -> com.zianblk.zianutilities.neoforge.UtilitiesPermissions.allows(source,"admin.quests",true))
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
            CaptureEligibilityPlan capturePlan = new CaptureEligibilityPlan(enabled);
            if (!capturePlan.getAssignable()) {
                source.sendSuccess(
                    () -> Tr.c("zianutilities.quests.trial.vista_previa_de_captura_pausada", "Vista previa de captura: PAUSADA; no hay generaciones activas."),
                    false
                );
                return Command.SINGLE_SUCCESS;
            }
            String generations = generationIds(capturePlan.getEnabledGenerations());
            source.sendSuccess(
                () -> Tr.c("zianutilities.quests.trial.vista_previa_sin_asignar_captura", "Vista previa (sin asignar): captura 1 Pokémon de cualquier generación activa (%s). Objetivos por tipo: desactivados.", String.valueOf(generations)),
                false
            );
            return Command.SINGLE_SUCCESS;
        } catch (Exception error) {
            LOGGER.error("[ZIAN-QUEST-PLAN] result=error", error);
            source.sendFailure(Tr.c("zianutilities.quests.trial.no_se_pudo_leer_el_2", "No se pudo leer el plan de captura."));
            return 0;
        }
    }

    private static int start(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Tr.c("zianutilities.quests.trial.el_ensayo_debe_iniciarlo_un", "El ensayo debe iniciarlo un jugador operador."));
            return 0;
        }
        try {
            Set<Generation> enabled = new NeoForgeGenerationStateStore(source.getServer())
                .load().getEnabled();
            if (enabled.isEmpty()) {
                source.sendFailure(Tr.c("zianutilities.quests.trial.activa_una_generacion_antes_de", "Activa una generación antes de iniciar el ensayo."));
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
            source.sendFailure(Tr.c("zianutilities.quests.trial.no_se_pudo_guardar_el", "No se pudo guardar el ensayo; no captures para probarlo."));
            return 0;
        }
    }

    private static int status(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Tr.c("zianutilities.quests.trial.consulta_el_ensayo_como_jugador", "Consulta el ensayo como jugador operador."));
            return 0;
        }
        try {
            Set<Generation> enabled = new NeoForgeGenerationStateStore(source.getServer())
                .load().getEnabled();
            CaptureTrial trial = CaptureTrialRuntime.service(source.getServer())
                .synchronize(player.getUUID(), enabled);
            if (trial == null) {
                source.sendSuccess(() -> Tr.c("zianutilities.quests.trial.ensayo_de_captura_no_iniciado", "Ensayo de captura: no iniciado."), false);
            } else {
                showTrial(source, trial, enabled);
            }
            return Command.SINGLE_SUCCESS;
        } catch (Exception error) {
            LOGGER.error("[ZIAN-QUEST-TRIAL] action=status result=error", error);
            source.sendFailure(Tr.c("zianutilities.quests.trial.no_se_pudo_leer_el", "No se pudo leer el ensayo; no lo reinicies."));
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
        Component generations = trial.getCapturedSpeciesId() == null && currentlyEnabled.isEmpty()
            ? Tr.c("zianutilities.quests.trial.generations_none", "ninguna")
            : Component.literal(generationIds(trial.getTargetGenerations()));
        Component progress = trial.getCapturedSpeciesId() != null
            ? Tr.c("zianutilities.quests.trial.progress_done", "1/1 COMPLETADO")
            : currentlyEnabled.isEmpty() ? Tr.c("zianutilities.quests.trial.progress_paused", "0/1 PAUSADO") : Tr.c("zianutilities.quests.trial.progress_active", "0/1 ACTIVO");
        Component species = trial.getCapturedSpeciesId() == null
            ? Component.empty() : Tr.c("zianutilities.quests.trial.species", " especie=%s", trial.getCapturedSpeciesId());
        source.sendSuccess(
            () -> Tr.c("zianutilities.quests.trial.status", "Ensayo captura %s: %s generaciones=%s%s; sin recompensa.",
                String.valueOf(trial.getAssignmentId()), progress, generations, species),
            false
        );
    }
}

