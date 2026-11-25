package com.zianblk.zianutilities.neoforge.quests;
import com.zianblk.zianutilities.neoforge.Tr;

import com.zianblk.zianutilities.neoforge.ZianUtilitiesMod;

import com.mojang.brigadier.Command;
import com.zianblk.zianutilities.core.generation.Generation;
import com.zianblk.zianutilities.core.quests.CaptureEligibilityPlan;
import com.zianblk.zianutilities.core.quests.CaptureMission;
import com.zianblk.zianutilities.neoforge.generation.NeoForgeGenerationStateStore;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.stream.Collectors;

/** Gated first mission: operator assignment, self status, and no reward delivery. */
public final class CaptureMissionCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger("ZianUtilities/CaptureMission");
    static final String TEST_FLAG = "zianutilities.captureMissionTestEnabled";

    private CaptureMissionCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal(ZianUtilitiesMod.COMMAND_ROOT)
                .requires(source -> true)
                .then(Commands.literal("quest")
                    .then(Commands.literal("capture")
                        .requires(source -> Boolean.getBoolean(TEST_FLAG))
                        .then(Commands.literal("accept")
                            .requires(source -> source.hasPermission(3))
                            .executes(context -> accept(context.getSource())))
                        .then(Commands.literal("assign")
                            .requires(source -> source.hasPermission(3))
                            .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> assign(
                                    context.getSource(),
                                    EntityArgument.getPlayer(context, "player")
                                ))))
                        .then(Commands.literal("status")
                            .executes(context -> status(context.getSource())))
                    )
                )
        );
    }

    private static int accept(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Tr.c("zianutilities.quests.mission.la_mision_debe_aceptarla_un", "La misión debe aceptarla un jugador operador."));
            return 0;
        }
        return assign(source, player);
    }

    private static int assign(CommandSourceStack source, ServerPlayer player) {
        try {
            Set<Generation> enabled = activeGenerations(source);
            var service = CaptureMissionRuntime.service(source.getServer());
            CaptureMission existing = service.inspect(player.getUUID());
            if (existing == null && enabled.isEmpty()) {
                source.sendFailure(Tr.c("zianutilities.quests.mission.no_hay_generaciones_activas_no", "No hay generaciones activas; no se asignó la misión."));
                return 0;
            }
            CaptureMission mission = service.accept(player.getUUID(), enabled);
            LOGGER.info(
                "[ZIAN-QUEST] action=capture_assign actor={} playerUuid={} assignmentId={} completed={}",
                source.getTextName(), player.getUUID(), mission.getAssignmentId(), mission.getCompleted()
            );
            if (source.getEntity() != player) {
                source.sendSuccess(() -> Tr.c("zianutilities.quests.mission.asignacion_de_captura_para", "Asignación de captura para %s:", String.valueOf(player.getGameProfile().getName())), false);
            }
            show(source, mission, enabled);
            return Command.SINGLE_SUCCESS;
        } catch (Exception error) {
            LOGGER.error("[ZIAN-QUEST] action=capture_assign result=error", error);
            source.sendFailure(Tr.c("zianutilities.quests.mission.no_se_pudo_guardar_la", "No se pudo guardar la misión; no captures para probarla."));
            return 0;
        }
    }

    private static int status(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Tr.c("zianutilities.quests.mission.consulta_tu_mision_desde_el", "Consulta tu misión desde el juego."));
            return 0;
        }
        try {
            CaptureMission mission = CaptureMissionRuntime.service(source.getServer())
                .inspect(player.getUUID());
            if (mission == null) {
                source.sendSuccess(() -> Tr.c("zianutilities.quests.mission.mision_captura_no_asignada", "Misión captura: no asignada."), false);
            } else {
                show(source, mission, activeGenerations(source));
            }
            return Command.SINGLE_SUCCESS;
        } catch (Exception error) {
            LOGGER.error("[ZIAN-QUEST] action=capture_status result=error", error);
            source.sendFailure(Tr.c("zianutilities.quests.mission.no_se_pudo_leer_la", "No se pudo leer la misión; no la reinicies."));
            return 0;
        }
    }

    private static Set<Generation> activeGenerations(CommandSourceStack source) {
        return new NeoForgeGenerationStateStore(source.getServer()).load().getEnabled();
    }

    private static void show(
        CommandSourceStack source,
        CaptureMission mission,
        Set<Generation> enabled
    ) {
        Component detail;
        if (mission.getCompleted()) {
            detail = Tr.c("zianutilities.quests.mission.detail_done", "1/1 COMPLETADA especie=%s generación=%s",
                String.valueOf(mission.getCapturedSpeciesId()), mission.getCapturedGeneration().getId());
        } else {
            CaptureEligibilityPlan plan = new CaptureEligibilityPlan(enabled);
            String generations = plan.generationCandidates().stream()
                .map(Generation::getId)
                .collect(Collectors.joining(","));
            detail = plan.getAssignable()
                ? Tr.c("zianutilities.quests.mission.detail_active", "0/1 ACTIVA generaciones=%s", generations)
                : Tr.c("zianutilities.quests.mission.detail_paused", "0/1 PAUSADA sin generaciones activas");
        }
        source.sendSuccess(
            () -> Tr.c("zianutilities.quests.mission.status", "Misión captura %s: %s; sin recompensa.", String.valueOf(mission.getAssignmentId()), detail),
            false
        );
    }
}

