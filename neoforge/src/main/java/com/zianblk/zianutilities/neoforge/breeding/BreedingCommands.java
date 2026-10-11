package com.zianblk.zianutilities.neoforge.breeding;

import com.mojang.brigadier.arguments.*;
import com.zianblk.zianutilities.core.breeding.*;
import com.zianblk.zianutilities.neoforge.ZianUtilitiesMod;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class BreedingCommands {
  private BreedingCommands() {}

  private interface Action {
    void run() throws Exception;
  }

  private static int run(CommandSourceStack s, Action a) {
    try {
      a.run();
      return 1;
    } catch (Exception e) {
      s.sendFailure(
          Component.literal(
              e.getMessage() == null ? "No se pudo completar la acción" : e.getMessage()));
      return 0;
    }
  }

  private static UUID id(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c) {
    return UUID.fromString(StringArgumentType.getString(c, "id"));
  }

  public static void register(RegisterCommandsEvent e) {
    var root =
        Commands.literal("crianza")
            .requires(
                s ->
                    s.getPlayer() != null
                        && BreedingPermissions.allows(s.getPlayer(), "use", false))
            .executes(
                c -> {
                  BreedingMenu.open(c.getSource().getPlayerOrException());
                  return 1;
                });
    root.then(
        Commands.literal("status")
            .executes(
                c ->
                    run(
                        c.getSource(),
                        () -> {
                          var p = c.getSource().getPlayerOrException();
                          BreedingRuntime.check(p);
                          for (var j : BreedingRuntime.mine(p))
                            p.sendSystemMessage(
                                Component.literal(
                                    j.id()
                                        + " · "
                                        + j.status()
                                        + " · "
                                        + BreedingRuntime.remaining(j)));
                        })));
    root.then(
        Commands.literal("start")
            .then(
                Commands.argument("first", IntegerArgumentType.integer(1, 6))
                    .then(
                        Commands.argument("second", IntegerArgumentType.integer(1, 6))
                            .executes(
                                c ->
                                    run(
                                        c.getSource(),
                                        () -> {
                                          var p = c.getSource().getPlayerOrException();
                                          var party =
                                              com.cobblemon.mod.common.Cobblemon.INSTANCE
                                                  .getStorage()
                                                  .getParty(p);
                                          var one =
                                              party.get(
                                                  IntegerArgumentType.getInteger(c, "first") - 1);
                                          var two =
                                              party.get(
                                                  IntegerArgumentType.getInteger(c, "second") - 1);
                                          if (one == null || two == null)
                                            throw new IllegalArgumentException("Ranura vacía");
                                          var j =
                                              BreedingRuntime.start(
                                                  p,
                                                  one.getUuid(),
                                                  two.getUuid(),
                                                  UUID.randomUUID());
                                          p.sendSystemMessage(
                                              Component.literal("Crianza reservada: " + j.id()));
                                        })))));
    for (String action : List.of("claim", "cancel"))
      root.then(
          Commands.literal(action)
              .then(
                  Commands.argument("id", StringArgumentType.word())
                      .suggests(BreedingCommands::suggestIds)
                      .suggests(
                          (c, b) ->
                              SharedSuggestionProvider.suggest(
                                  BreedingRuntime.mine(c.getSource().getPlayerOrException())
                                      .stream()
                                      .filter(BreedingJob::occupiesSlot)
                                      .map(j -> j.id().toString()),
                                  b))
                      .executes(
                          c ->
                              run(
                                  c.getSource(),
                                  () -> {
                                    if (action.equals("claim"))
                                      BreedingRuntime.claim(
                                          c.getSource().getPlayerOrException(), id(c));
                                    else
                                      BreedingRuntime.cancel(
                                          c.getSource().getPlayerOrException(), id(c));
                                  }))));
    root.then(
        Commands.literal("accelerate")
            .then(
                Commands.argument("id", StringArgumentType.word())
                    .suggests(BreedingCommands::suggestIds)
                    .then(
                        Commands.argument("currency", StringArgumentType.word())
                            .suggests(
                                (c, b) ->
                                    SharedSuggestionProvider.suggest(
                                        BreedingRuntime.currencies(), b))
                            .then(
                                Commands.argument("units", LongArgumentType.longArg(1))
                                    .executes(
                                        c ->
                                            run(
                                                c.getSource(),
                                                () ->
                                                    BreedingRuntime.accelerate(
                                                        c.getSource().getPlayerOrException(),
                                                        id(c),
                                                        StringArgumentType.getString(c, "currency"),
                                                        LongArgumentType.getLong(c, "units"),
                                                        UUID.randomUUID())))))));
    var admin =
        Commands.literal("breeding-admin")
            .requires(
                s ->
                    s.getPlayer() == null
                        ? s.hasPermission(2)
                        : BreedingPermissions.allows(s.getPlayer(), "admin", true));
    admin.then(
        Commands.literal("complete")
            .then(
                Commands.argument("id", StringArgumentType.word())
                    .suggests(
                        (c, b) ->
                            SharedSuggestionProvider.suggest(
                                BreedingRuntime.store == null
                                    ? java.util.stream.Stream.<String>empty()
                                    : BreedingRuntime.store.jobs.values().stream()
                                        .filter(
                                            j ->
                                                j.status() == BreedingJob.Status.ACTIVE
                                                    || j.status() == BreedingJob.Status.BLOCKED)
                                        .map(j -> j.id().toString()),
                                b))
                    .executes(
                        c ->
                            run(
                                c.getSource(),
                                () -> {
                                  BreedingRuntime.completeNow(id(c), c.getSource().getTextName());
                                  c.getSource()
                                      .sendSuccess(
                                          () ->
                                              Component.literal(
                                                  "Crianza adelantada para prueba. La entrega"
                                                      + " conserva las comprobaciones del PC y las"
                                                      + " generaciones."),
                                          true);
                                }))));
    admin.then(
        Commands.literal("reload")
            .executes(
                c ->
                    run(
                        c.getSource(),
                        () -> {
                          BreedingRuntime.reload();
                          c.getSource()
                              .sendSuccess(
                                  () -> Component.literal("Configuración de crianza cargada"),
                                  false);
                        })));
    admin.then(
        Commands.literal("inspect")
            .then(
                Commands.argument("id", StringArgumentType.word())
                    .suggests(BreedingCommands::suggestIds)
                    .executes(
                        c ->
                            run(
                                c.getSource(),
                                () -> {
                                  var j = BreedingRuntime.store.jobs.get(id(c));
                                  if (j == null) throw new IllegalArgumentException("No existe");
                                  c.getSource()
                                      .sendSuccess(
                                          () ->
                                              Component.literal(
                                                  j.id()
                                                      + " owner="
                                                      + j.owner()
                                                      + " state="
                                                      + j.status()
                                                      + " reason="
                                                      + j.reason()
                                                      + " purchases="
                                                      + j.purchases()),
                                          false);
                                }))));
    admin.then(
        Commands.literal("resolve-debit")
            .then(
                Commands.argument("id", StringArgumentType.word())
                    .suggests(BreedingCommands::suggestIds)
                    .then(
                        Commands.argument("charged", BoolArgumentType.bool())
                            .then(
                                Commands.argument("evidence", StringArgumentType.greedyString())
                                    .executes(
                                        c ->
                                            run(
                                                c.getSource(),
                                                () -> {
                                                  evidence(c);
                                                  var j = BreedingRuntime.store.jobs.get(id(c));
                                                  if (j == null
                                                      || j.status() != BreedingJob.Status.UNCERTAIN
                                                      || !j.reason().startsWith("PAYMENT"))
                                                    throw new IllegalArgumentException(
                                                        "No hay cobro incierto");
                                                  var resolved =
                                                      j.payment(
                                                          BoolArgumentType.getBool(c, "charged")
                                                              ? BreedingJob.Payment.APPLIED
                                                              : BreedingJob.Payment.REJECTED,
                                                          BreedingRuntime.store.now());
                                                  BreedingRuntime.store.put(
                                                      resolved.phase(
                                                          resolved.status(),
                                                          "Operator: "
                                                              + c.getSource().getTextName()
                                                              + " · "
                                                              + StringArgumentType.getString(
                                                                  c, "evidence"),
                                                          BreedingRuntime.store.now()));
                                                  c.getSource()
                                                      .sendSuccess(
                                                          () ->
                                                              Component.literal(
                                                                  "Revisión registrada; no se"
                                                                      + " repitió ni devolvió el"
                                                                      + " cobro"),
                                                          true);
                                                }))))));
    admin.then(
        Commands.literal("resolve-delivery")
            .then(
                Commands.argument("id", StringArgumentType.word())
                    .suggests(BreedingCommands::suggestIds)
                    .then(
                        Commands.argument("delivered", BoolArgumentType.bool())
                            .then(
                                Commands.argument("evidence", StringArgumentType.greedyString())
                                    .executes(
                                        c ->
                                            run(
                                                c.getSource(),
                                                () -> {
                                                  evidence(c);
                                                  var j = BreedingRuntime.store.jobs.get(id(c));
                                                  if (j == null
                                                      || j.status() != BreedingJob.Status.UNCERTAIN
                                                      || !j.reason().startsWith("DELIVERY"))
                                                    throw new IllegalArgumentException(
                                                        "No hay entrega incierta");
                                                  if (!BoolArgumentType.getBool(c, "delivered")) {
                                                    var p =
                                                        BreedingRuntime.server
                                                            .getPlayerList()
                                                            .getPlayer(j.owner());
                                                    if (p == null)
                                                      throw new IllegalArgumentException(
                                                          "El dueño debe estar conectado para"
                                                              + " comprobar ausencia");
                                                    var h = BreedingStorage.open(p);
                                                    if (BreedingStorage.live(h, j.child().uuid())
                                                            != null
                                                        || BreedingStorage.saved(
                                                                h, p, j.child().uuid())
                                                            != null)
                                                      throw new IllegalArgumentException(
                                                          "El UUID ya está presente; no se permite"
                                                              + " reintentar");
                                                  }
                                                  BreedingRuntime.store.put(
                                                      j.phase(
                                                          BoolArgumentType.getBool(c, "delivered")
                                                              ? BreedingJob.Status.DELIVERED
                                                              : BreedingJob.Status.ACTIVE,
                                                          "Operator: "
                                                              + StringArgumentType.getString(
                                                                  c, "evidence"),
                                                          BreedingRuntime.store.now()));
                                                  c.getSource()
                                                      .sendSuccess(
                                                          () ->
                                                              Component.literal(
                                                                  "Revisión de entrega registrada"),
                                                          true);
                                                }))))));
    e.getDispatcher()
        .register(
            Commands.literal(ZianUtilitiesMod.COMMAND_ROOT)
                .requires(s -> true)
                .then(root)
                .then(admin));
  }

  private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>
      suggestIds(
          com.mojang.brigadier.context.CommandContext<CommandSourceStack> c,
          com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
    if (BreedingRuntime.store == null) return b.buildFuture();
    var player = c.getSource().getPlayer();
    boolean admin =
        player == null
            ? c.getSource().hasPermission(2)
            : BreedingPermissions.allows(player, "admin", true);
    String input = c.getInput();
    return SharedSuggestionProvider.suggest(
        BreedingRuntime.store.jobs.values().stream()
            .filter(
                j ->
                    (admin && input.contains(" breeding-admin "))
                        || (player != null && j.owner().equals(player.getUUID())))
            .filter(
                j ->
                    input.contains(" resolve-debit ")
                        ? j.status() == BreedingJob.Status.UNCERTAIN
                            && j.reason().startsWith("PAYMENT")
                        : input.contains(" resolve-delivery ")
                            ? j.status() == BreedingJob.Status.UNCERTAIN
                                && j.reason().startsWith("DELIVERY")
                            : input.contains(" inspect ")
                                || ((j.status() == BreedingJob.Status.ACTIVE
                                        || j.status() == BreedingJob.Status.BLOCKED)
                                    && (!input.contains(" accelerate ")
                                        || (j.status() == BreedingJob.Status.ACTIVE
                                            && j.readyAt() > BreedingRuntime.store.now()))))
            .map(j -> j.id().toString()),
        b);
  }

  private static void evidence(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c) {
    String value = StringArgumentType.getString(c, "evidence");
    if (value.length() < 8 || value.length() > 256)
      throw new IllegalArgumentException("Evidencia: 8–256 caracteres");
  }
}
