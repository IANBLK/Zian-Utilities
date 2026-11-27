package com.zianblk.zianutilities.neoforge.pase;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zianblk.zianutilities.core.pase.Season;
import com.zianblk.zianutilities.neoforge.Messages;
import com.zianblk.zianutilities.neoforge.UtilitiesPermissions;
import com.zianblk.zianutilities.neoforge.ZianUtilitiesMod;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /ZianUtilities pase ver (permission zianutilities.pase.use, everybody unless LuckPerms denies it).
 * /ZianUtilities pase admin ... (zianutilities.admin.pase, OP only unless LuckPerms grants it): xp dar|quitar, nivel dar,
 * temporada ver|nueva, recargar, jugador reiniciar. Every admin node has its own .requires: under Youer the root is public.
 */
final class PaseCommands {
  private PaseCommands() {}

  private static boolean use(CommandSourceStack s) { return s.getPlayer() != null && UtilitiesPermissions.allows(s, "pase.use", false); }

  private static boolean admin(CommandSourceStack s) { return UtilitiesPermissions.allows(s, "admin.pase", true); }

  private static void say(CommandSourceStack s, Component c) { s.sendSuccess(() -> Messages.format(c), false); }

  private static boolean ready(CommandSourceStack s) {
    if (PaseModule.active()) return true;
    s.sendFailure(Messages.format(PaseMessages.off(PaseModule.problem)));
    return false;
  }

  static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    LiteralArgumentBuilder<CommandSourceStack> pase = Commands.literal("pase").requires(s -> true);
    pase.then(Commands.literal("ver").requires(PaseCommands::use).executes(c -> view(c.getSource())));

    LiteralArgumentBuilder<CommandSourceStack> admin = Commands.literal("admin").requires(PaseCommands::admin);
    admin.then(Commands.literal("xp").requires(PaseCommands::admin)
        .then(Commands.literal("dar").requires(PaseCommands::admin).then(Commands.argument("jugador", EntityArgument.player())
            .then(Commands.argument("cantidad", LongArgumentType.longArg(1, 10_000_000)).executes(c -> giveXp(c.getSource(), EntityArgument.getPlayer(c, "jugador"), LongArgumentType.getLong(c, "cantidad"))))))
        .then(Commands.literal("quitar").requires(PaseCommands::admin).then(Commands.argument("jugador", EntityArgument.player())
            .then(Commands.argument("cantidad", LongArgumentType.longArg(1, 10_000_000)).executes(c -> removeXp(c.getSource(), EntityArgument.getPlayer(c, "jugador"), LongArgumentType.getLong(c, "cantidad")))))));
    admin.then(Commands.literal("nivel").requires(PaseCommands::admin)
        .then(Commands.literal("dar").requires(PaseCommands::admin).then(Commands.argument("jugador", EntityArgument.player())
            .then(Commands.argument("niveles", IntegerArgumentType.integer(1, 10_000)).executes(c -> giveLevels(c.getSource(), EntityArgument.getPlayer(c, "jugador"), IntegerArgumentType.getInteger(c, "niveles")))))));
    admin.then(Commands.literal("temporada").requires(PaseCommands::admin)
        .then(Commands.literal("ver").requires(PaseCommands::admin).executes(c -> seasonView(c.getSource())))
        .then(Commands.literal("nueva").requires(PaseCommands::admin).executes(c -> seasonNew(c.getSource()))));
    admin.then(Commands.literal("recargar").requires(PaseCommands::admin).executes(c -> reload(c.getSource())));
    admin.then(Commands.literal("jugador").requires(PaseCommands::admin)
        .then(Commands.literal("reiniciar").requires(PaseCommands::admin).then(Commands.argument("jugador", EntityArgument.player())
            .executes(c -> resetPlayer(c.getSource(), EntityArgument.getPlayer(c, "jugador"))))));
    pase.then(admin);
    dispatcher.register(Commands.literal(ZianUtilitiesMod.COMMAND_ROOT).requires(s -> true).then(pase));
  }

  private static String date(Instant at) {
    return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(PaseModule.config.rules().zone()).format(at);
  }

  private static int view(CommandSourceStack s) {
    if (!ready(s)) return 0;
    var v = PaseModule.engine.view(s.getPlayer().getUUID());
    if (v == null) {
      s.sendFailure(Messages.format(PaseMessages.failed("archivo del pase ilegible")));
      return 0;
    }
    var rules = PaseModule.config.rules();
    boolean first = true;
    for (Component line : PaseMessages.view(v, rules.resetTime().toString(), PaseSources.lang(s.getPlayer()))) {
      Component shown = first ? Messages.format(line) : line;
      s.sendSuccess(() -> shown, false);
      first = false;
    }
    return 1;
  }

  private static int giveXp(CommandSourceStack s, ServerPlayer p, long xp) {
    if (!ready(s)) return 0;
    var g = PaseModule.engine.adminGrant(p.getUUID(), xp);
    if (!g.any()) {
      s.sendFailure(Messages.format(PaseMessages.xpNone(p.getGameProfile().getName())));
      return 0;
    }
    PaseSources.notify(p, g);
    done(s, "xp dar " + p.getGameProfile().getName() + " " + g.applied());
    say(s, PaseMessages.xpGiven(g.applied(), p.getGameProfile().getName(), PaseSources.lang(p)));
    return 1;
  }

  private static int removeXp(CommandSourceStack s, ServerPlayer p, long xp) {
    if (!ready(s)) return 0;
    long taken = PaseModule.engine.adminRemove(p.getUUID(), xp);
    done(s, "xp quitar " + p.getGameProfile().getName() + " " + taken);
    say(s, PaseMessages.xpRemoved(taken, p.getGameProfile().getName(), PaseSources.lang(p)));
    return 1;
  }

  private static int giveLevels(CommandSourceStack s, ServerPlayer p, int levels) {
    if (!ready(s)) return 0;
    var g = PaseModule.engine.adminGiveLevels(p.getUUID(), levels);
    if (!g.any()) {
      s.sendFailure(Messages.format(PaseMessages.xpNone(p.getGameProfile().getName())));
      return 0;
    }
    PaseSources.notify(p, g);
    done(s, "nivel dar " + p.getGameProfile().getName() + " " + levels);
    say(s, PaseMessages.levelsGiven(g.levels().size(), p.getGameProfile().getName()));
    return 1;
  }

  private static int seasonView(CommandSourceStack s) {
    if (!ready(s)) return 0;
    Season cur = PaseModule.seasons.current();
    say(s, PaseMessages.season(cur.id(), date(cur.start()), date(cur.end()), false));
    Season prev = PaseModule.seasons.previous();
    say(s, prev == null ? PaseMessages.noPreviousSeason() : PaseMessages.season(prev.id(), date(prev.start()), date(prev.end()), true));
    return 1;
  }

  private static int seasonNew(CommandSourceStack s) {
    if (!ready(s)) return 0;
    Season next = PaseModule.engine.newSeason();
    try {
      PaseModule.saveAll();
    } catch (java.io.IOException failure) {
      s.sendFailure(Messages.format(PaseMessages.failed(String.valueOf(failure.getMessage()))));
      return 0;
    }
    done(s, "temporada nueva " + next.id());
    say(s, PaseMessages.seasonStarted(next.id(), date(next.end())));
    return 1;
  }

  private static int reload(CommandSourceStack s) {
    String error = PaseModule.reload();
    if (error != null) {
      s.sendFailure(Messages.format(PaseMessages.reloadFailed(error)));
      return 0;
    }
    done(s, "recargar");
    say(s, PaseMessages.reloaded());
    return 1;
  }

  private static int resetPlayer(CommandSourceStack s, ServerPlayer p) {
    if (!ready(s)) return 0;
    PaseModule.engine.resetDaily(p.getUUID());
    done(s, "jugador reiniciar " + p.getGameProfile().getName());
    say(s, PaseMessages.playerReset(p.getGameProfile().getName()));
    return 1;
  }

  private static void done(CommandSourceStack s, String what) {
    PaseModule.audit(s, what);
    try {
      PaseModule.saveAll();
    } catch (java.io.IOException failure) {
      PaseModule.LOG.error("No se pudo guardar el pase tras una acción de admin", failure);
    }
  }
}
