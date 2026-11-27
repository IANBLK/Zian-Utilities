package com.zianblk.zianutilities.neoforge.pase;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.zianblk.zianutilities.core.pase.PlayerPase;
import com.zianblk.zianutilities.core.pase.Prize;
import com.zianblk.zianutilities.core.pase.PrizeClaimer;
import java.util.UUID;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.UuidArgument;
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
 * temporada ver|nueva, recargar, jugador reiniciar, premium dar|quitar (Tebex, works from the console) and premios ver|reentregar.
 * /ZianUtilities pase reclamar delivers the pending rewards of the player. Every admin node has its own .requires: under Youer the root is public.
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
    pase.then(Commands.literal("reclamar").requires(PaseCommands::use).executes(c -> claim(c.getSource())));

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
    SuggestionProvider<CommandSourceStack> seasons = (c, b) -> SharedSuggestionProvider.suggest(PaseModule.active() ? PaseRewards.seasonIds() : java.util.List.of(), b);
    admin.then(Commands.literal("premium").requires(PaseCommands::admin)
        .then(Commands.literal("dar").requires(PaseCommands::admin).then(Commands.argument("uuid", UuidArgument.uuid())
            .then(Commands.argument("temporada", StringArgumentType.word()).suggests(seasons)
                .then(Commands.argument("transaction", StringArgumentType.word())
                    .executes(c -> premiumGive(c.getSource(), UuidArgument.getUuid(c, "uuid"), StringArgumentType.getString(c, "temporada"), StringArgumentType.getString(c, "transaction")))))))
        .then(Commands.literal("quitar").requires(PaseCommands::admin).then(Commands.argument("uuid", UuidArgument.uuid())
            .then(Commands.argument("temporada", StringArgumentType.word()).suggests(seasons)
                .executes(c -> premiumRemove(c.getSource(), UuidArgument.getUuid(c, "uuid"), StringArgumentType.getString(c, "temporada")))))));
    admin.then(Commands.literal("premios").requires(PaseCommands::admin)
        .then(Commands.literal("ver").requires(PaseCommands::admin).then(Commands.argument("jugador", GameProfileArgument.gameProfile())
            .executes(c -> prizesView(c.getSource(), GameProfileArgument.getGameProfiles(c, "jugador").stream().toList()))))
        .then(Commands.literal("reentregar").requires(PaseCommands::admin).then(Commands.argument("jugador", GameProfileArgument.gameProfile())
            .executes(c -> prizesRedeliver(c.getSource(), GameProfileArgument.getGameProfiles(c, "jugador").stream().toList())))));
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

  private static int claim(CommandSourceStack s) {
    if (!ready(s)) return 0;
    try {
      return PaseRewards.claim(s.getPlayer()).delivered().size();
    } catch (RuntimeException failure) {
      PaseModule.LOG.error("Reclamar premios falló para {}", s.getTextName(), failure);
      s.sendFailure(Messages.format(PaseMessages.failed(String.valueOf(failure.getMessage()))));
      return 0;
    }
  }

  private static int premiumGive(CommandSourceStack s, UUID id, String season, String transaction) {
    if (!ready(s)) return 0;
    var r = PaseRewards.premiumGive(id, season, transaction);
    String what = "premium dar " + id + " " + season + " " + transaction;
    switch (r.status()) {
      case GIVEN -> {
        done(s, what);
        say(s, PaseMessages.premiumGiven(season, id.toString(), transaction));
        return 1;
      }
      case DUPLICATE -> {
        done(s, what + " (repetida, ignorada)");
        say(s, PaseMessages.premiumDuplicate(transaction));
        return 1;
      }
      case BAD_SEASON -> s.sendFailure(Messages.format(PaseMessages.premiumBadSeason(PaseModule.seasons.current().id(), PaseModule.seasons.previous() == null ? null : PaseModule.seasons.previous().id())));
      case BAD_TRANSACTION -> s.sendFailure(Messages.format(PaseMessages.premiumBadTransaction()));
      default -> s.sendFailure(Messages.format(PaseMessages.premiumFailed(r.detail())));
    }
    return 0;
  }

  private static int premiumRemove(CommandSourceStack s, UUID id, String season) {
    if (!ready(s)) return 0;
    var seasons = PaseModule.seasons;
    if (!season.equals(seasons.current().id()) && (seasons.previous() == null || !season.equals(seasons.previous().id()))) {
      s.sendFailure(Messages.format(PaseMessages.premiumBadSeason(seasons.current().id(), seasons.previous() == null ? null : seasons.previous().id())));
      return 0;
    }
    int dropped = PaseRewards.premiumRemove(id, season);
    if (dropped < 0) {
      s.sendFailure(Messages.format(PaseMessages.premiumRemoveFailed()));
      return 0;
    }
    done(s, "premium quitar " + id + " " + season);
    say(s, PaseMessages.premiumRemoved(season, id.toString(), dropped));
    return 1;
  }

  private static com.mojang.authlib.GameProfile single(CommandSourceStack s, java.util.List<com.mojang.authlib.GameProfile> profiles) {
    if (profiles.size() == 1) return profiles.get(0);
    s.sendFailure(Messages.format(PaseMessages.failed("elige un solo jugador")));
    return null;
  }

  private static int prizesView(CommandSourceStack s, java.util.List<com.mojang.authlib.GameProfile> profiles) {
    if (!ready(s)) return 0;
    var profile = single(s, profiles);
    if (profile == null) return 0;
    boolean online = PaseModule.server.getPlayerList().getPlayer(profile.getId()) != null;
    PlayerPase p = PaseModule.store.get(profile.getId());
    try {
      if (p.locked) {
        s.sendFailure(Messages.format(PaseMessages.failed("archivo del pase ilegible")));
        return 0;
      }
      long pending = 0, delivered = 0, review = 0;
      for (Prize z : p.prizes.values()) {
        switch (z.state) {
          case DELIVERED -> delivered++;
          case UNCERTAIN -> review++;
          default -> pending++;
        }
      }
      say(s, PaseMessages.prizesHeader(profile.getName(), pending, delivered, review));
      int shown = 0;
      long open = pending + review;
      for (Prize z : p.prizes.values()) {
        if (z.state == Prize.State.DELIVERED) continue;
        if (shown >= 20) break;
        shown++;
        say(s, PaseMessages.prizesLine(z.id, z.state.name(), z.reason));
      }
      if (open > shown) say(s, PaseMessages.prizesMore(open - shown));
      return 1;
    } finally {
      if (!online) PaseModule.store.release(profile.getId());
    }
  }

  private static int prizesRedeliver(CommandSourceStack s, java.util.List<com.mojang.authlib.GameProfile> profiles) {
    if (!ready(s)) return 0;
    var profile = single(s, profiles);
    if (profile == null) return 0;
    ServerPlayer online = PaseModule.server.getPlayerList().getPlayer(profile.getId());
    PlayerPase p = PaseModule.store.get(profile.getId());
    try {
      if (p.locked) {
        s.sendFailure(Messages.format(PaseMessages.failed("archivo del pase ilegible")));
        return 0;
      }
      int reopened = PrizeClaimer.reopen(p);
      PaseModule.store.saveNow(p);
      done(s, "premios reentregar " + profile.getName() + " (" + reopened + " reabiertos)");
      say(s, PaseMessages.prizesReopened(reopened, profile.getName(), ""));
      if (online == null) {
        say(s, PaseMessages.prizesOffline());
        return 1;
      }
      return Math.max(1, PaseRewards.claim(online).delivered().size());
    } catch (java.io.IOException | RuntimeException failure) {
      PaseModule.LOG.error("Reentregar premios de {} falló", profile.getName(), failure);
      s.sendFailure(Messages.format(PaseMessages.failed(String.valueOf(failure.getMessage()))));
      return 0;
    } finally {
      if (online == null) PaseModule.store.release(profile.getId());
    }
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
