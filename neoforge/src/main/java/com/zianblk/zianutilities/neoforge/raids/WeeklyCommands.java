package com.zianblk.zianutilities.neoforge.raids;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zianblk.zianutilities.neoforge.Messages;
import com.zianblk.zianutilities.neoforge.Tr;
import com.zianblk.zianutilities.neoforge.UtilitiesPermissions;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /ZianUtilities raid semanal ... Players (permission zianutilities.raid.use): ver, salir-cola.
 * Admins (zianutilities.admin.raid, OP only unless LuckPerms grants it): rotar, fijar, recargar, jugador reiniciar, reaparecer, cola ver|limpiar.
 */
final class WeeklyCommands {
  private WeeklyCommands() {}

  private static boolean use(CommandSourceStack s) { return s.getPlayer() != null && UtilitiesPermissions.allows(s, "raid.use", false); }

  private static boolean admin(CommandSourceStack s) { return UtilitiesPermissions.allows(s, "admin.raid", true); }

  private static void say(CommandSourceStack s, Component c) { s.sendSuccess(() -> Messages.format(c), false); }

  private static int fail(CommandSourceStack s, String encoded) {
    s.sendFailure(Tr.dec(encoded));
    return 0;
  }

  static LiteralArgumentBuilder<CommandSourceStack> node() {
    LiteralArgumentBuilder<CommandSourceStack> weekly = Commands.literal("semanal").requires(s -> true);
    weekly.then(Commands.literal("ver").requires(s -> use(s) || admin(s)).executes(c -> view(c.getSource())));
    weekly.then(Commands.literal("salir-cola").requires(WeeklyCommands::use).executes(c -> {
      ServerPlayer p = c.getSource().getPlayerOrException();
      String error = WeeklyRuntime.leave(p);
      return error == null ? 1 : fail(c.getSource(), error);
    }));
    weekly.then(Commands.literal("rotar").requires(WeeklyCommands::admin).executes(c -> {
      String error = WeeklyRuntime.forceRotate();
      if (error != null) return fail(c.getSource(), error);
      RaidCommands.audit(c.getSource(), "weekly rotate -> " + WeeklyRuntime.active.id());
      say(c.getSource(), Tr.c("zianutilities.raids.weekly.admin.rotated", "§aRaid semanal rotada: ahora es %s. Termina el %s.", WeeklyRuntime.active.displayName(), WeeklyRuntime.when(WeeklyRuntime.state.week.endsAt())));
      return 1;
    }));
    weekly.then(Commands.literal("fijar").requires(WeeklyCommands::admin)
        .then(Commands.argument("id", StringArgumentType.word())
            .suggests((c, b) -> SharedSuggestionProvider.suggest(WeeklyRuntime.config == null ? java.util.List.<String>of() : WeeklyRuntime.config.ids(), b))
            .executes(c -> {
              String id = StringArgumentType.getString(c, "id");
              String error = WeeklyRuntime.fix(id);
              if (error != null) return fail(c.getSource(), error);
              RaidCommands.audit(c.getSource(), "weekly fix " + id);
              say(c.getSource(), Tr.c("zianutilities.raids.weekly.admin.fixed", "§aRaid semanal fijada: %s. Termina el %s.", WeeklyRuntime.active.displayName(), WeeklyRuntime.when(WeeklyRuntime.state.week.endsAt())));
              return 1;
            })));
    weekly.then(Commands.literal("recargar").requires(WeeklyCommands::admin).executes(c -> {
      String error = WeeklyRuntime.reload();
      if (error != null) {
        c.getSource().sendFailure(Tr.c("zianutilities.raids.weekly.admin.reload_failed", "§cConfiguración semanal inválida, se conserva la anterior: %s", error));
        return 0;
      }
      RaidCommands.audit(c.getSource(), "weekly reload");
      say(c.getSource(), Tr.c("zianutilities.raids.weekly.admin.reloaded", "§aConfiguración semanal recargada. La raid activa sigue con su copia congelada hasta la próxima rotación."));
      return 1;
    }));
    weekly.then(Commands.literal("jugador").requires(WeeklyCommands::admin)
        .then(Commands.literal("reiniciar").then(Commands.argument("jugador", EntityArgument.player()).executes(c -> {
          ServerPlayer p = EntityArgument.getPlayer(c, "jugador");
          if (WeeklyRuntime.state == null) return fail(c.getSource(), Tr.enc("zianutilities.raids.weekly.err.unavailable", "§eLa raid semanal no está activa (%s).", WeeklyRuntime.problem));
          boolean had = WeeklyRuntime.state.counts.reset(p.getUUID());
          try {
            WeeklyRuntime.state.save(WeeklyRuntime.statePath());
          } catch (java.io.IOException failure) {
            return fail(c.getSource(), Tr.enc("zianutilities.raids.err.failed", "§cNo se pudo completar la acción: %s", String.valueOf(failure.getMessage())));
          }
          RaidCommands.audit(c.getSource(), "weekly player reset " + p.getGameProfile().getName());
          say(c.getSource(), had ? Tr.c("zianutilities.raids.weekly.admin.player_reset", "§aIntentos y premios semanales de %s reiniciados.", p.getGameProfile().getName())
              : Tr.c("zianutilities.raids.weekly.admin.player_none", "§e%s no había usado intentos esta semana.", p.getGameProfile().getName()));
          return 1;
        }))));
    weekly.then(Commands.literal("reaparecer").requires(WeeklyCommands::admin).executes(c -> {
      String error = WeeklyRuntime.respawn();
      if (error != null) return fail(c.getSource(), error);
      RaidCommands.audit(c.getSource(), "weekly respawn");
      say(c.getSource(), Tr.c("zianutilities.raids.weekly.admin.respawned", "§aEl jefe semanal reaparecerá en unos segundos."));
      return 1;
    }));
    weekly.then(Commands.literal("cola").requires(WeeklyCommands::admin)
        .then(Commands.literal("ver").executes(c -> queueView(c.getSource())))
        .then(Commands.literal("limpiar").executes(c -> {
          int dropped = WeeklyRuntime.clearQueue();
          RaidCommands.audit(c.getSource(), "weekly queue cleared (" + dropped + ")");
          say(c.getSource(), Tr.c("zianutilities.raids.weekly.admin.queue_cleared", "§aCola vaciada (%s jugadores). Quien está jugando sigue en su turno.", dropped));
          return 1;
        })));
    return weekly;
  }

  private static int view(CommandSourceStack s) {
    if (!WeeklyRuntime.ready()) {
      say(s, Tr.c("zianutilities.raids.weekly.status.off", "§eLa raid semanal no está activa (%s).", WeeklyRuntime.problem));
      return 1;
    }
    var week = WeeklyRuntime.state.week;
    var raid = WeeklyRuntime.active;
    say(s, Tr.c("zianutilities.raids.weekly.status.raid", "§6Raid semanal: %s §7(%s, tier %s)", raid.displayName(), raid.mode(), raid.tier()));
    say(s, Tr.c("zianutilities.raids.weekly.status.time", "§7Tiempo restante: %s (cambia el %s)", WeeklyRuntime.remaining(week.endsAt()), WeeklyRuntime.when(week.endsAt())));
    ServerPlayer p = s.getPlayer();
    if (p != null) {
      var count = WeeklyRuntime.state.counts.of(p.getUUID());
      say(s, Tr.c("zianutilities.raids.weekly.status.attempts", "§7Tus intentos: %s de %s. Tus premios: %s de %s. (0 = sin límite)", count.attempts(), raid.attemptsPerWeek(), count.rewards(), raid.rewardsPerWeek()));
      int position = WeeklyRuntime.queue.position(p.getUUID());
      if (position == 0) say(s, Tr.c("zianutilities.raids.weekly.status.called", "§6Es tu turno: acércate al jefe."));
      else if (position > 0) say(s, Tr.c("zianutilities.raids.weekly.status.position", "§eTu posición en la cola: %s.", position));
      else if (WeeklyRuntime.queue.playing() != null && WeeklyRuntime.queue.playing().equals(p.getUUID()))
        say(s, Tr.c("zianutilities.raids.weekly.status.playing", "§eAhora mismo juegas tú."));
    }
    int waiting = WeeklyRuntime.queue.waiting().size();
    say(s, Tr.c("zianutilities.raids.weekly.status.queue", "§7En la cola: %s. %s", waiting,
        WeeklyRuntime.queue.playing() == null ? Tr.c("zianutilities.raids.weekly.status.free", "§aLa raid está libre.") : Tr.c("zianutilities.raids.weekly.status.busy", "§eHay alguien jugando.")));
    return 1;
  }

  private static String name(UUID id) {
    ServerPlayer p = RaidsModule.server == null ? null : RaidsModule.server.getPlayerList().getPlayer(id);
    return p == null ? id.toString() : p.getGameProfile().getName();
  }

  private static int queueView(CommandSourceStack s) {
    var q = WeeklyRuntime.queue;
    say(s, Tr.c("zianutilities.raids.weekly.admin.queue_playing", "§7Jugando: %s", q.playing() == null ? "-" : name(q.playing())));
    say(s, Tr.c("zianutilities.raids.weekly.admin.queue_called", "§7Llamado: %s", q.called() == null ? "-" : name(q.called())));
    int i = 1;
    for (UUID w : q.waiting()) say(s, Tr.c("zianutilities.raids.weekly.admin.queue_entry", "§7%s. %s", i++, name(w)));
    return 1;
  }
}
