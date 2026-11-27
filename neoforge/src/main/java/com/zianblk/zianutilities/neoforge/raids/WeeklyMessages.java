package com.zianblk.zianutilities.neoforge.raids;

import com.zianblk.zianutilities.core.raids.weekly.WeeklyCounts;
import com.zianblk.zianutilities.neoforge.Tr;
import net.minecraft.network.chat.Component;

/**
 * Messages of the raids module that take typed values. Translatable arguments must be a Component, Number, Boolean or
 * String: an enum or an id there makes the client throw when it draws the text. Everything with such arguments is
 * built here so WeeklyMessagesTest can build and render all of it.
 */
final class WeeklyMessages {
  private WeeklyMessages() {}

  /** Why a module is off, as translatable text. Unknown reasons (an error message) are shown as they are. */
  static Component problem(String problem) {
    return switch (problem == null ? "" : problem) {
      case "disabled in config" -> Tr.c("zianutilities.raids.problem.disabled", "desactivada en la configuración");
      case "not started" -> Tr.c("zianutilities.raids.problem.not_started", "todavía no ha arrancado");
      default -> Component.literal(problem == null ? "" : problem);
    };
  }

  /** Same, as plain text for the network-safe encoded messages. */
  static String problemText(String problem) { return problem(problem).getString(); }

  static Component mode(WeeklyConfig.Mode mode) {
    return mode == WeeklyConfig.Mode.DUEL
        ? Tr.c("zianutilities.raids.weekly.mode.duel", "duelo")
        : Tr.c("zianutilities.raids.weekly.mode.battle", "batalla Pokémon");
  }

  static Component unavailable(String problem) {
    return Tr.c("zianutilities.raids.weekly.err.unavailable", "§eLa raid semanal no está activa (%s).", problem(problem));
  }

  static Component blocked(String boss) {
    return Tr.c("zianutilities.raids.weekly.msg.blocked_ops", "§cLa raid semanal está bloqueada: el filtro de generaciones no permite a %s. Activa su generación o cambia de raid.", boss);
  }

  static Component off(String problem) {
    return Tr.c("zianutilities.raids.weekly.status.off", "§eLa raid semanal no está activa (%s).", problem(problem));
  }

  static Component raidLine(WeeklyConfig.Raid raid) {
    return Tr.c("zianutilities.raids.weekly.status.raid", "§6Raid semanal: %s §7(%s, tier %s)", raid.displayName(), mode(raid.mode()), raid.tier());
  }

  static Component newRaid(WeeklyConfig.Raid raid) {
    return Tr.c("zianutilities.raids.weekly.msg.new_raid", "§6Nueva raid semanal: %s (%s, tier %s). ¡Desafíala en el spawn!", raid.displayName(), mode(raid.mode()), raid.tier());
  }

  private static Component limit(int limit) {
    return limit <= 0 ? Tr.c("zianutilities.raids.weekly.status.unlimited", "sin límite") : Component.literal(String.valueOf(limit));
  }

  static Component attempts(WeeklyCounts.Count count, WeeklyConfig.Raid raid) {
    return Tr.c("zianutilities.raids.weekly.status.attempts", "§7Tus intentos: %s de %s. Tus premios: %s de %s.",
        count.attempts(), limit(raid.attemptsPerWeek()), count.rewards(), limit(raid.rewardsPerWeek()));
  }

  static Component queueLine(int waiting, boolean busy) {
    return Tr.c("zianutilities.raids.weekly.status.queue", "§7En la cola: %s. %s", waiting,
        busy ? Tr.c("zianutilities.raids.weekly.status.busy", "§eHay alguien jugando.") : Tr.c("zianutilities.raids.weekly.status.free", "§aLa raid está libre."));
  }
}
