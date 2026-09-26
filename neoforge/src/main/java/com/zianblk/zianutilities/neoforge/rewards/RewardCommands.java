package com.zianblk.zianutilities.neoforge.rewards;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.zianblk.zianutilities.core.rewards.ClaimStatus;
import com.zianblk.zianutilities.core.rewards.FileRewardClaimStore;
import com.zianblk.zianutilities.core.rewards.RewardClaimRecord;
import com.zianblk.zianutilities.neoforge.economy.AvecoinsContractProbe;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Collectors;

/** Admin-only, read-only diagnostics for the M2 integration gate. */
public final class RewardCommands {
    private RewardCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("zian")
                .then(Commands.literal("reward")
                    .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.literal("status").executes(context -> status(context.getSource())))
                    .then(Commands.literal("inspect")
                        .then(Commands.argument("claimId", StringArgumentType.word())
                            .executes(context -> inspect(
                                context.getSource(),
                                StringArgumentType.getString(context, "claimId")
                            ))
                        )
                    )
                )
        );
    }

    private static int status(CommandSourceStack source) {
        var result = AvecoinsContractProbe.inspect();
        source.sendSuccess(
            () -> Component.literal("M2 recompensas: registro preparado; entrega desactivada."),
            false
        );
        source.sendSuccess(() -> Component.literal(result.detail()), false);
        if (result.compatible()) {
            String currencies = result.currencies().isEmpty()
                ? "ninguna"
                : String.join(", ", result.currencies());
            source.sendSuccess(
                () -> Component.literal("Monedas administradas: " + currencies),
                false
            );
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int inspect(CommandSourceStack source, String rawId) {
        UUID claimId;
        try {
            claimId = UUID.fromString(rawId);
        } catch (IllegalArgumentException error) {
            source.sendFailure(Component.literal("ID de reclamación inválido."));
            return 0;
        }

        Path directory = source.getServer().getWorldPath(LevelResource.ROOT)
            .resolve("data").resolve("zianutilities").resolve("reward_claims");
        try {
            RewardClaimRecord record = new FileRewardClaimStore(directory).load(claimId);
            if (record == null) {
                source.sendFailure(Component.literal("Reclamación no encontrada."));
                return 0;
            }
            ClaimStatus state = record.status();
            String components = record.getComponents().entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue().getStatus().name())
                .sorted()
                .collect(Collectors.joining(", "));
            source.sendSuccess(
                () -> Component.literal("Reclamación " + claimId + ": " + state.name()
                    + " [" + components + "]"),
                false
            );
            return Command.SINGLE_SUCCESS;
        } catch (Exception error) {
            source.sendFailure(Component.literal(
                "No se pudo leer el registro: " + error.getClass().getSimpleName()
            ));
            return 0;
        }
    }
}

