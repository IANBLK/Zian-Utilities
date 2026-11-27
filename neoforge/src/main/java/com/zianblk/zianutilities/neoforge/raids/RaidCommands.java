package com.zianblk.zianutilities.neoforge.raids;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zianblk.zianutilities.core.raids.RaidTierPolicy;
import com.zianblk.zianutilities.neoforge.Messages;
import com.zianblk.zianutilities.neoforge.Tr;
import com.zianblk.zianutilities.neoforge.UtilitiesPermissions;
import com.zianblk.zianutilities.neoforge.ZianUtilitiesMod;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /ZianUtilities raid ... Players: estado, cooldown (permission zianutilities.raid.use).
 * Admins (zianutilities.admin.raid, OP only unless LuckPerms grants it): reload, cooldown reset, duelo cancelar, config, premios.
 */
final class RaidCommands {
  private RaidCommands() {}

  private static boolean use(CommandSourceStack s) { return s.getPlayer() != null && UtilitiesPermissions.allows(s, "raid.use", false); }

  private static boolean admin(CommandSourceStack s) { return UtilitiesPermissions.allows(s, "admin.raid", true); }

  static String duration(long millis) {
    long s = Math.max(0, (millis + 999) / 1000);
    return String.format("%dh %02dm %02ds", s / 3600, s / 60 % 60, s % 60);
  }

  private static void say(CommandSourceStack s, Component c) { s.sendSuccess(() -> Messages.format(c), false); }

  static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    LiteralArgumentBuilder<CommandSourceStack> raid = Commands.literal("raid").requires(s -> true);

    raid.then(Commands.literal("estado").requires(RaidCommands::use).executes(c -> status(c.getSource())));
    raid.then(Commands.literal("cooldown").requires(RaidCommands::use)
        .executes(c -> cooldowns(c.getSource(), c.getSource().getPlayerOrException()))
        .then(Commands.literal("ver").requires(RaidCommands::admin)
            .then(Commands.argument("jugador", EntityArgument.player()).executes(c -> cooldowns(c.getSource(), EntityArgument.getPlayer(c, "jugador")))))
        .then(Commands.literal("reset").requires(RaidCommands::admin)
            .then(Commands.argument("jugador", EntityArgument.player())
                .executes(c -> resetCooldown(c.getSource(), EntityArgument.getPlayer(c, "jugador"), 0))
                .then(Commands.argument("tier", IntegerArgumentType.integer(1, 7))
                    .executes(c -> resetCooldown(c.getSource(), EntityArgument.getPlayer(c, "jugador"), IntegerArgumentType.getInteger(c, "tier")))))));
    raid.then(Commands.literal("reload").requires(RaidCommands::admin).executes(c -> reload(c.getSource())));
    raid.then(Commands.literal("duelo").requires(RaidCommands::admin)
        .then(Commands.literal("cancelar").then(Commands.argument("jugador", EntityArgument.player()).executes(c -> cancelDuel(c.getSource(), EntityArgument.getPlayer(c, "jugador"))))));
    raid.then(Commands.literal("config").requires(RaidCommands::admin)
        .then(Commands.literal("ver").executes(c -> configView(c.getSource())))
        .then(Commands.literal("deshacer").executes(c -> configUndo(c.getSource()))));
    raid.then(Commands.literal("premios").requires(RaidCommands::admin)
        .then(Commands.literal("revisar").executes(c -> rewardsReview(c.getSource())))
        .then(Commands.literal("confirmar-entregado").then(Commands.argument("id", StringArgumentType.word()).executes(c -> rewardsConfirm(c.getSource(), StringArgumentType.getString(c, "id"))))));

    raid.then(WeeklyCommands.node());

    dispatcher.register(Commands.literal(ZianUtilitiesMod.COMMAND_ROOT).requires(s -> true).then(raid));
  }

  private static int status(CommandSourceStack s) {
    var cfg = RaidsModule.config;
    if (cfg == null || !cfg.enabled()) {
      say(s, Tr.c("zianutilities.raids.status.off", "§eLos duelos de raid están desactivados (%s).", WeeklyMessages.problem(RaidsModule.problem)));
      return 1;
    }
    say(s, Tr.c("zianutilities.raids.status.on", "§6Duelos de raid activos desde el tier %s. Los tiers inferiores usan la batalla normal de Raid Dens.", cfg.duelFromTier()));
    ServerPlayer p = s.getPlayer();
    if (p != null && RaidDuelRuntime.inDuel(p.getUUID())) say(s, Tr.c("zianutilities.raids.status.in_duel", "§eEstás en un duelo ahora mismo."));
    return cooldowns(s, p);
  }

  private static int cooldowns(CommandSourceStack s, ServerPlayer p) {
    if (RaidsModule.cooldowns == null || p == null) return 0;
    long now = System.currentTimeMillis();
    for (int tier = Math.max(RaidTierPolicy.MIN_TIER, RaidsModule.config == null ? 5 : RaidsModule.config.duelFromTier()); tier <= RaidTierPolicy.MAX_TIER; tier++) {
      long left = RaidsModule.cooldowns.remainingMillis(p.getUUID(), tier, now);
      int shown = tier;
      say(s, left > 0
          ? Tr.c("zianutilities.raids.cooldown.waiting", "§7Tier %s: espera %s.", shown, duration(left))
          : Tr.c("zianutilities.raids.cooldown.ready", "§7Tier %s: disponible.", shown));
    }
    return 1;
  }

  private static int resetCooldown(CommandSourceStack s, ServerPlayer p, int tier) {
    try {
      boolean removed = RaidsModule.cooldowns.reset(p.getUUID(), tier);
      audit(s, "cooldown reset " + p.getGameProfile().getName() + " tier=" + (tier == 0 ? "all" : tier));
      say(s, removed ? Tr.c("zianutilities.raids.admin.cooldown_reset", "§aCooldown de %s reiniciado.", p.getGameProfile().getName())
          : Tr.c("zianutilities.raids.admin.cooldown_none", "§eEse jugador no tenía cooldown."));
      return 1;
    } catch (Exception failure) {
      s.sendFailure(Tr.c("zianutilities.raids.err.failed", "§cNo se pudo completar la acción: %s", String.valueOf(failure.getMessage())));
      return 0;
    }
  }

  private static int reload(CommandSourceStack s) {
    String error = RaidsModule.reload();
    if (error != null) {
      s.sendFailure(Tr.c("zianutilities.raids.admin.reload_failed", "§cConfiguración inválida, se conserva la anterior: %s", error));
      return 0;
    }
    audit(s, "reload");
    say(s, Tr.c("zianutilities.raids.admin.reloaded", "§aConfiguración de raids recargada."));
    return 1;
  }

  private static int cancelDuel(CommandSourceStack s, ServerPlayer p) {
    boolean done = RaidDuelRuntime.cancel(p.getUUID());
    audit(s, "duel cancel " + p.getGameProfile().getName());
    say(s, done ? Tr.c("zianutilities.raids.admin.duel_cancelled", "§aDuelo cancelado; el intento no se gasta.")
        : Tr.c("zianutilities.raids.admin.duel_none", "§eEse jugador no está en un duelo."));
    return done ? 1 : 0;
  }

  private static int configView(CommandSourceStack s) {
    var cfg = RaidsModule.config;
    if (cfg == null) {
      s.sendFailure(Tr.c("zianutilities.raids.status.off", "§eLos duelos de raid están desactivados (%s).", WeeklyMessages.problem(RaidsModule.problem)));
      return 0;
    }
    say(s, Tr.c("zianutilities.raids.admin.config_view", "§7enabled=%s duelFromTier=%s generationFilter=%s safeRewards=%s archivo=%s",
        cfg.enabled(), cfg.duelFromTier(), cfg.respectGenerationFilter(), cfg.safeRewardDelivery(), RaidsModule.configPath().getFileName()));
    return 1;
  }

  private static int configUndo(CommandSourceStack s) {
    try {
      boolean undone = RaidsModule.history().undoLast();
      if (undone) { audit(s, "config undo"); RaidsModule.reload(); }
      say(s, undone ? Tr.c("zianutilities.raids.admin.config_undone", "§aSe restauró la copia anterior de la configuración.")
          : Tr.c("zianutilities.raids.admin.config_no_backup", "§eNo hay copias anteriores."));
      return undone ? 1 : 0;
    } catch (Exception failure) {
      s.sendFailure(Tr.c("zianutilities.raids.err.failed", "§cNo se pudo completar la acción: %s", String.valueOf(failure.getMessage())));
      return 0;
    }
  }

  private static int rewardsReview(CommandSourceStack s) {
    var lines = RaidPendingRewards.review();
    if (lines.isEmpty()) { say(s, Tr.c("zianutilities.raids.admin.rewards_none", "§aNo hay premios pendientes.")); return 1; }
    lines.forEach(l -> say(s, Component.literal(l)));
    say(s, Tr.c("zianutilities.raids.admin.rewards_hint", "§7Si un premio sigue aquí pero el Pokémon ya está en el PC, o Raid Dens nunca llegó a insertarlo, comprueba el PC y resuélvelo con confirmar-entregado."));
    return lines.size();
  }

  private static int rewardsConfirm(CommandSourceStack s, String text) {
    try {
      UUID id = RaidPendingRewards.parse(text);
      boolean ok = id != null && RaidPendingRewards.confirmDelivered(id);
      if (ok) audit(s, "reward confirmed " + id);
      say(s, ok ? Tr.c("zianutilities.raids.admin.reward_confirmed", "§aPremio marcado como entregado.") : Tr.c("zianutilities.raids.admin.reward_unknown", "§eNo existe ese premio pendiente."));
      return ok ? 1 : 0;
    } catch (Exception failure) {
      s.sendFailure(Tr.c("zianutilities.raids.err.failed", "§cNo se pudo completar la acción: %s", String.valueOf(failure.getMessage())));
      return 0;
    }
  }

  static void audit(CommandSourceStack s, String what) {
    RaidsModule.LOG.info("[ZIAN-AUDIT] raid admin {} by {}", what, s.getTextName());
    try {
      ServerPlayer p = s.getPlayer();
      RaidsModule.history().record(p == null ? new UUID(0, 0) : p.getUUID(), s.getTextName(), "raids", what);
    } catch (Exception failure) {
      RaidsModule.LOG.warn("Change log write failed", failure);
    }
  }
}
