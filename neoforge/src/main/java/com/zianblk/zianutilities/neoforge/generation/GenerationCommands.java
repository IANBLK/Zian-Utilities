package com.zianblk.zianutilities.neoforge.generation;
import com.zianblk.zianutilities.neoforge.Tr;

import com.zianblk.zianutilities.neoforge.ZianUtilitiesMod;

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
            Commands.literal(ZianUtilitiesMod.COMMAND_ROOT)
                // Youer rewrites Brigadier's default root requirement to a Bukkit
                // permission. Keep the shared root public; children enforce access.
                .requires(source -> true)
                .then(
                    Commands.literal("generation")
                        .then(
                            Commands.literal("enable")
                                .requires(source -> com.zianblk.zianutilities.neoforge.UtilitiesPermissions.allows(source,"admin.generations",true))
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
                                .requires(source -> com.zianblk.zianutilities.neoforge.UtilitiesPermissions.allows(source,"admin.generations",true))
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
                Tr.c("zianutilities.generation.cmd.generacion_desconocida_usa_gen_gen", "Generación desconocida. Usa gen1, gen2, ... gen9.")
            );
            return 0;
        }

        GenerationService service = service(source);

        GenerationMutationResult result = enabled
            ? service.enable(generation)
            : service.disable(generation);

        if (result == GenerationMutationResult.CHANGED) {
            Component action = enabled ? Tr.c("zianutilities.generation.cmd.enabled", "habilitada") : Tr.c("zianutilities.generation.cmd.disabled", "deshabilitada");
            source.sendSuccess(
                () -> Tr.c("zianutilities.generation.cmd.changed", "Generación %s %s.", generation.getId(), action),
                true
            );
            audit(source, enabled ? "generation_enable" : "generation_disable",
                generation, "changed");
            return Command.SINGLE_SUCCESS;
        }

        Component state = enabled ? Tr.c("zianutilities.generation.cmd.already_enabled", "ya estaba habilitada") : Tr.c("zianutilities.generation.cmd.already_disabled", "ya estaba deshabilitada");
        source.sendSuccess(
            () -> Tr.c("zianutilities.generation.cmd.changed", "Generación %s %s.", generation.getId(), state),
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

        Component finalActive = active.equals("ninguna") ? Tr.c("zianutilities.generation.cmd.none", "ninguna") : Component.literal(active);
        source.sendSuccess(
            () -> Tr.c("zianutilities.generation.cmd.active", "Generaciones activas: %s", finalActive),
            false
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int showSummary(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        GenerationState state = service(source).snapshot();

        source.sendSuccess(
            () -> Tr.c("zianutilities.generation.cmd.generation_control_generaciones_activas", "Generation Control: %s/%s generaciones activas.", String.valueOf(state.getEnabled().size()), String.valueOf(Generation.values().length)),
            false
        );

        return Command.SINGLE_SUCCESS;
    }

    private static int showStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Generation generation = parseGeneration(context);

        if (generation == null) {
            source.sendFailure(
                Tr.c("zianutilities.generation.cmd.generacion_desconocida_usa_gen_gen", "Generación desconocida. Usa gen1, gen2, ... gen9.")
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

