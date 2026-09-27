package com.zianblk.zianutilities.neoforge.quests;

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
    private static final ZoneId SCHEDULE_ZONE = ZoneId.of("America/Guayaquil");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private SharedCaptureMissionCommands() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("zian").requires(source -> true)
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
            source.sendFailure(Component.literal("Acepta la misión desde el juego."));
            return 0;
        }
        try {
            SharedCaptureStatus status = SharedCaptureMissionRuntime.service(source.getServer())
                .accept(player.getUUID());
            LOGGER.info("[ZIAN-SHARED-QUEST] action=accept playerUuid={} missionId={} progress={}",
                player.getUUID(), status.getMission().getMissionId(),
                status.getProgress().getCaptures().size());
            show(source, status, activeGenerations(source));
            return Command.SINGLE_SUCCESS;
        } catch (Exception error) {
            LOGGER.error("[ZIAN-SHARED-QUEST] action=accept result=error", error);
            source.sendFailure(Component.literal("No se pudo guardar la aceptación; no captures para probarla."));
            return 0;
        }
    }

    private static int status(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Consulta la misión desde el juego."));
            return 0;
        }
        try {
            SharedCaptureStatus status = SharedCaptureMissionRuntime.service(source.getServer())
                .inspect(player.getUUID());
            show(source, status, activeGenerations(source));
            return Command.SINGLE_SUCCESS;
        } catch (Exception error) {
            LOGGER.error("[ZIAN-SHARED-QUEST] action=status result=error", error);
            source.sendFailure(Component.literal("No se pudo leer la misión; no la reinicies."));
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
        String state = progress == null ? "SIN ACEPTAR"
            : progress.getCompleted() ? "COMPLETADA"
            : enabled.isEmpty() ? "PAUSADA sin generaciones activas" : "ACTIVA";
        int count = progress == null ? 0 : progress.getCaptures().size();
        long minutes = Duration.ofMillis(Math.max(0,
            mission.getExpiresAtEpochMs() - System.currentTimeMillis())).toMinutes();
        String resetTime = Instant.ofEpochMilli(mission.getExpiresAtEpochMs())
            .atZone(SCHEDULE_ZONE).format(TIME_FORMAT);
        source.sendSuccess(() -> Component.literal("Misión global " + mission.getMissionId() + ": "
            + mission.getObjective().getDescription() + "; tu avance " + count + "/"
            + SharedCaptureMission.GOAL + " " + state + "; cambia a las " + resetTime
            + " (Ecuador), quedan " + minutes + " min; sin recompensa."
            + (progress == null ? " Usa /zian quest shared accept." : "")), false);
    }
}

