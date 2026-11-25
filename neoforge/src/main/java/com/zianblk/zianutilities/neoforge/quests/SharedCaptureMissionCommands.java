package com.zianblk.zianutilities.neoforge.quests;
import com.zianblk.zianutilities.neoforge.Tr;

import com.zianblk.zianutilities.neoforge.ZianUtilitiesMod;

import com.mojang.brigadier.Command;
import com.zianblk.zianutilities.core.generation.Generation;
import com.zianblk.zianutilities.core.quests.SharedCaptureMission;
import com.zianblk.zianutilities.core.quests.SharedCaptureProgress;
import com.zianblk.zianutilities.core.quests.SharedCaptureStatus;
import com.zianblk.zianutilities.neoforge.generation.NeoForgeGenerationStateStore;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/** The offer is global; accepting and status are private to each player. */
public final class SharedCaptureMissionCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger("ZianUtilities/SharedCapture");
    static final String TEST_FLAG = "zianutilities.sharedCaptureMissionTestEnabled";
    private static final ZoneId SCHEDULE_ZONE = com.zianblk.zianutilities.core.quests.QuestSchedule.ZONE;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private SharedCaptureMissionCommands() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal(ZianUtilitiesMod.COMMAND_ROOT).requires(source -> true)
                .then(Commands.literal("quest")
                    .then(Commands.literal("shared")
                        .requires(source -> Boolean.getBoolean(TEST_FLAG))
                        .then(Commands.literal("accept")
                            .executes(context -> accept(context.getSource())))
                        .then(Commands.literal("status")
                            .executes(context -> status(context.getSource())))))
        );
    }

    private static int accept(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Tr.c("zianutilities.quests.shared.acepta_la_mision_desde_el", "Acepta la misión desde el juego."));
            return 0;
        }
        try {
            SharedCaptureStatus status = SharedCaptureMissionRuntime.service(source.getServer())
                .accept(player.getUUID());
            if (status.getProgress() == null) {
                // Reloj del servidor atrasado respecto a la misión guardada (ver SharedCaptureMissionService).
                source.sendFailure(Tr.c("zianutilities.quests.shared.tu_mision_guardada_es_mas", "Tu misión guardada es más reciente que el reloj del servidor; inténtalo en unos minutos."));
                return 0;
            }
            LOGGER.info("[ZIAN-SHARED-QUEST] action=accept playerUuid={} missionId={} progress={}",
                player.getUUID(), status.getMission().getMissionId(),
                status.getProgress().getCaptures().size());
            show(source, status, activeGenerations(source));
            return Command.SINGLE_SUCCESS;
        } catch (Exception error) {
            LOGGER.error("[ZIAN-SHARED-QUEST] action=accept result=error", error);
            source.sendFailure(Tr.c("zianutilities.quests.shared.no_se_pudo_guardar_la", "No se pudo guardar la aceptación; no captures para probarla."));
            return 0;
        }
    }

    private static int status(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Tr.c("zianutilities.quests.shared.consulta_la_mision_desde_el", "Consulta la misión desde el juego."));
            return 0;
        }
        try {
            SharedCaptureStatus status = SharedCaptureMissionRuntime.service(source.getServer())
                .inspect(player.getUUID());
            show(source, status, activeGenerations(source));
            return Command.SINGLE_SUCCESS;
        } catch (Exception error) {
            LOGGER.error("[ZIAN-SHARED-QUEST] action=status result=error", error);
            source.sendFailure(Tr.c("zianutilities.quests.mission.no_se_pudo_leer_la", "No se pudo leer la misión; no la reinicies."));
            return 0;
        }
    }

    private static Set<Generation> activeGenerations(CommandSourceStack source) {
        return new NeoForgeGenerationStateStore(source.getServer()).load().getEnabled();
    }

    private static void show(
        CommandSourceStack source, SharedCaptureStatus status, Set<Generation> enabled
    ) {
        SharedCaptureMission mission = status.getMission();
        SharedCaptureProgress progress = status.getProgress();
        Component state = progress == null ? Tr.c("zianutilities.quests.shared.state_unaccepted", "SIN ACEPTAR")
            : progress.getCompleted() ? Tr.c("zianutilities.quests.shared.state_done", "COMPLETADA")
            : enabled.isEmpty() ? Tr.c("zianutilities.quests.shared.state_paused", "PAUSADA sin generaciones activas") : Tr.c("zianutilities.quests.shared.state_active", "ACTIVA");
        int count = progress == null ? 0 : progress.getCaptures().size();
        long minutes = Duration.ofMillis(Math.max(0,
            mission.getExpiresAtEpochMs() - System.currentTimeMillis())).toMinutes();
        String resetTime = Instant.ofEpochMilli(mission.getExpiresAtEpochMs())
            .atZone(SCHEDULE_ZONE).format(TIME_FORMAT);
        source.sendSuccess(() -> Tr.c("zianutilities.quests.shared.status", "Misión global %s: %s; tu avance %s/%s %s; cambia a las %s (Ecuador), quedan %s min; sin recompensa.%s",
            String.valueOf(mission.getMissionId()), String.valueOf(mission.getObjective().getDescription()), String.valueOf(count),
            String.valueOf(SharedCaptureMission.GOAL), state, resetTime, String.valueOf(minutes),
            progress == null ? Tr.c("zianutilities.quests.shared.accept_hint", " Usa /ZianUtilities quest shared accept.") : Component.empty()), false);
    }
}

