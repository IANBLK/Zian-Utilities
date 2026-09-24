package com.zianblk.zianutilities.neoforge.generation;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.zianblk.zianutilities.core.generation.Generation;
import com.zianblk.zianutilities.core.generation.GenerationMutationResult;
import com.zianblk.zianutilities.core.generation.GenerationService;
import com.zianblk.zianutilities.core.generation.GenerationState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.stream.Collectors;

public final class GenerationCommands {
    private static final Logger LOGGER =
        LoggerFactory.getLogger("ZianUtilities/GenerationCommands");

    private static final String ARG_GENERATION = "gen";

    private GenerationCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("zian")
                .then(
                    Commands.literal("generation")
                        .then(
                            Commands.literal("enable")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .then(
                                    Commands.argument(ARG_GENERATION, StringArgumentType.word())
                                        .suggests((context, builder) ->
                                            SharedSuggestionProvider.suggest(
                                                java.util.Arrays.stream(Generation.values())
                                                    .map(Generation::getId),
                                                builder
                                            )
                                        )
                                        .executes(context -> setEnabled(context, true))
                                )
                        )
                        .then(
                            Commands.literal("disable")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .then(
                                    Commands.argument(ARG_GENERATION, StringArgumentType.word())
                                        .suggests((context, builder) ->
                                            SharedSuggestionProvider.suggest(
                                                java.util.Arrays.stream(Generation.values())
                                                    .map(Generation::getId),
                                                builder
                                            )
                                        )
                                        .executes(context -> setEnabled(context, false))
                                )
                        )
                        .then(
                            Commands.literal("active")
                                .executes(GenerationCommands::showActive)
                        )
                        .then(
                            Commands.literal("status")
                                .executes(GenerationCommands::showSummary)
                                .then(
                                    Commands.argument(ARG_GENERATION, StringArgumentType.word())
                                        .suggests((context, builder) ->
                                            SharedSuggestionProvider.suggest(
                                                java.util.Arrays.stream(Generation.values())
                                                    .map(Generation::getId),
                                                builder
                                            )
                                        )
                                        .executes(GenerationCommands::showStatus)
                                )
                        )
                )
        );
    }

    private static int setEnabled(
        CommandContext<CommandSourceStack> context,
        boolean enabled
    ) {
        CommandSourceStack source = context.getSource();
        Generation generation = parseGeneration(context);

        if (generation == null) {
            source.sendFailure(
                Component.literal(
                    "Generación desconocida. Usa gen1, gen2, ... gen9."
                )
            );
            return 0;
        }

        GenerationService service = service(source);

        GenerationMutationResult result = enabled
            ? service.enable(generation)
            : service.disable(generation);

        if (result == GenerationMutationResult.CHANGED) {
            String action = enabled ? "habilitada" : "deshabilitada";
            source.sendSuccess(
                () -> Component.literal(
                    "Generación " + generation.getId() + " " + action + "."
                ),
                true
            );
            audit(source, enabled ? "generation_enable" : "generation_disable",
                generation, "changed");
            return Command.SINGLE_SUCCESS;
        }

        String state = enabled ? "ya estaba habilitada" : "ya estaba deshabilitada";
        source.sendSuccess(
            () -> Component.literal(
                "Generación " + generation.getId() + " " + state + "."
            ),
            false
        );
        audit(source, enabled ? "generation_enable" : "generation_disable",
            generation, "noop");
        return Command.SINGLE_SUCCESS;
    }

    private static int showActive(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        GenerationState state = service(source).snapshot();

        String active = state.getEnabled().stream()
            .sorted(Comparator.comparingInt(Enum::ordinal))
            .map(Generation::getId)
            .collect(Collectors.joining(", "));

        if (active.isBlank()) {
            active = "ninguna";
        }

        String finalActive = active;
        source.sendSuccess(
            () -> Component.literal("Generaciones activas: " + finalActive),
            false
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int showSummary(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        GenerationState state = service(source).snapshot();

        source.sendSuccess(
            () -> Component.literal(
                "Generation Control: "
                    + state.getEnabled().size()
                    + "/"
                    + Generation.values().length
                    + " generaciones activas."
            ),
            false
        );

        return Command.SINGLE_SUCCESS;
    }

    private static int showStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Generation generation = parseGeneration(context);

        if (generation == null) {
            source.sendFailure(
                Component.literal(
                    "Generación desconocida. Usa gen1, gen2, ... gen9."
                )
            );
            return 0;
        }

        boolean enabled = service(source).isEnabled(generation);
        source.sendSuccess(
            () -> Component.literal(
                generation.getId()
                    + ": "
                    + (enabled ? "habilitada" : "deshabilitada")
            ),
            false
        );

        return Command.SINGLE_SUCCESS;
    }

    private static Generation parseGeneration(
        CommandContext<CommandSourceStack> context
    ) {
        String raw = StringArgumentType.getString(context, ARG_GENERATION);
        return Generation.Companion.fromCanonicalId(raw);
    }

    private static GenerationService service(CommandSourceStack source) {
        return new GenerationService(
            new NeoForgeGenerationStateStore(source.getServer())
        );
    }

    private static void audit(
        CommandSourceStack source,
        String action,
        Generation generation,
        String result
    ) {
        String actor = source.getDisplayName().getString();
        String actorUuid = "-";

        if (source.getEntity() instanceof ServerPlayer player) {
            actorUuid = player.getUUID().toString();
        }

        LOGGER.info(
            "[ZIAN-AUDIT] action={} actor={} actorUuid={} target={} result={}",
            action,
            actor,
            actorUuid,
            generation.getId(),
            result
        );
    }
}
