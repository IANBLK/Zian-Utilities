package com.zianblk.zianutilities.neoforge.pase;

import com.zianblk.zianutilities.core.pase.MissionType;
import com.zianblk.zianutilities.core.pase.PaseEngine;
import com.zianblk.zianutilities.neoforge.Tr;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * Every message of the pass that takes typed values. Translatable arguments must be a Component, Number, Boolean or
 * String: an enum or a UUID there makes the client throw. Everything is built here so PaseMessagesTest can build and
 * check all of it.
 */
final class PaseMessages {
  private PaseMessages() {}

  static Component problem(String problem) {
    return switch (problem == null ? "" : problem) {
      case "disabled in config" -> Tr.c("zianutilities.pase.problem.disabled", "desactivado en la configuración");
      case "not started" -> Tr.c("zianutilities.pase.problem.not_started", "todavía no ha arrancado");
      default -> Component.literal(problem == null ? "" : problem);
    };
  }

  static Component off(String problem) {
    return Tr.c("zianutilities.pase.status.off", "§eEl pase de batalla no está activo (%s).", problem(problem));
  }

  static Component missionName(MissionType type, int target) {
    return switch (type) {
      case BREAK_BLOCKS -> Tr.c("zianutilities.pase.mission.break_blocks", "Minar %s bloques naturales", target);
      case HARVEST_CROPS -> Tr.c("zianutilities.pase.mission.harvest_crops", "Cosechar %s cultivos maduros", target);
      case KILL_MOBS -> Tr.c("zianutilities.pase.mission.kill_mobs", "Derrotar %s monstruos", target);
      case BREED_ANIMALS -> Tr.c("zianutilities.pase.mission.breed_animals", "Criar animales %s veces", target);
      case TRADE -> Tr.c("zianutilities.pase.mission.trade", "Comerciar %s veces con aldeanos o niveles distintos", target);
      case EXPLORE -> Tr.c("zianutilities.pase.mission.explore", "Descubrir %s biomas o estructuras nuevos", target);
      case FISH -> Tr.c("zianutilities.pase.mission.fish", "Pescar %s veces sin quedarte quieto", target);
    };
  }

  static Component levelUp(int level) {
    return Tr.c("zianutilities.pase.msg.level", "§6¡Has alcanzado el nivel %s del pase!", level);
  }

  static Component extraLevelUp(int level) {
    return Tr.c("zianutilities.pase.msg.extra_level", "§6¡Has alcanzado el nivel extra %s del pase!", level);
  }

  static Component missionDone(MissionType type, int target, long xp) {
    return Tr.c("zianutilities.pase.msg.mission_done", "§a¡Misión completada: %s! §7(+%s XP)", missionName(type, target), xp);
  }

  /** The lines of /ZianUtilities pase ver. */
  static List<Component> view(PaseEngine.View v, String resetTime, String zone) {
    List<Component> out = new ArrayList<>();
    out.add(Tr.c("zianutilities.pase.view.header", "§6Pase de batalla §7(%s)", v.seasonId()));
    if (v.level() >= v.maxLevel()) {
      out.add(Tr.c("zianutilities.pase.view.complete", "§aHas completado el pase (nivel %s).", v.maxLevel()));
      out.add(Tr.c("zianutilities.pase.view.extra", "§7Niveles extra: §e%s§7 de %s (%s de %s XP del siguiente).", v.extraLevels(), v.maxExtraLevels(), v.extraIntoLevel(), extraXp(v)));
    } else {
      out.add(Tr.c("zianutilities.pase.view.level", "§7Nivel §e%s§7 de %s. XP del nivel: §e%s§7 de %s.", v.level(), v.maxLevel(), v.xpIntoLevel(), v.xpPerLevel()));
    }
    if (v.previousId() != null) {
      out.add(v.previousComplete()
          ? Tr.c("zianutilities.pase.view.previous_done", "§7Pase anterior (%s): §acompletado§7.", v.previousId())
          : Tr.c("zianutilities.pase.view.previous", "§7Pase anterior (%s): nivel §e%s§7 de %s. Tu XP va primero aquí.", v.previousId(), v.previousLevel(), v.maxLevel()));
    }
    out.add(Tr.c("zianutilities.pase.view.cap", "§7Tope de XP de hoy: te quedan §e%s§7 de %s.", v.capLeft(), v.capTotal()));
    if (v.boost()) out.add(Tr.c("zianutilities.pase.view.boost", "§dDoble XP activo hasta que acabe la temporada."));
    out.add(Tr.c("zianutilities.pase.view.days", "§7Días restantes de la temporada: §e%s§7.", v.daysLeft()));
    out.add(v.premium() ? Tr.c("zianutilities.pase.view.premium_yes", "§7Pista premium: §aactiva§7.") : Tr.c("zianutilities.pase.view.premium_no", "§7Pista premium: §cno§7."));
    if (v.missions().isEmpty()) {
      out.add(Tr.c("zianutilities.pase.view.no_missions", "§7Hoy no hay misiones."));
    } else {
      out.add(Tr.c("zianutilities.pase.view.missions", "§6Misiones de hoy §7(se reinician a las %s, %s):", resetTime, zone));
      for (var m : v.missions()) {
        Component state = m.done() ? Tr.c("zianutilities.pase.view.mission_done", "§a✔ completada") : Tr.c("zianutilities.pase.view.mission_pending", "§e+%s XP", m.xp());
        out.add(Tr.c("zianutilities.pase.view.mission", "§7- %s §8[%s de %s] %s", missionName(m.type(), m.target()), m.progress(), m.target(), state));
      }
    }
    return out;
  }

  private static long extraXp(PaseEngine.View v) { return PaseModule.config == null ? 1500 : PaseModule.config.rules().extraLevelXp(); }

  static Component xpGiven(long xp, String player) {
    return Tr.c("zianutilities.pase.admin.xp_given", "§aDados %s XP del pase a %s.", xp, player);
  }

  static Component xpNone(String player) {
    return Tr.c("zianutilities.pase.admin.xp_none", "§e%s no ha recibido XP (el pase está completo o su archivo está bloqueado).", player);
  }

  static Component xpRemoved(long xp, String player) {
    return Tr.c("zianutilities.pase.admin.xp_removed", "§aQuitados %s XP del pase a %s.", xp, player);
  }

  static Component levelsGiven(int levels, String player) {
    return Tr.c("zianutilities.pase.admin.levels_given", "§aDados %s niveles del pase a %s.", levels, player);
  }

  static Component season(String id, String start, String end, boolean previous) {
    return previous
        ? Tr.c("zianutilities.pase.admin.season_previous", "§7Temporada anterior: %s (del %s al %s).", id, start, end)
        : Tr.c("zianutilities.pase.admin.season", "§6Temporada actual: %s §7(del %s al %s).", id, start, end);
  }

  static Component noPreviousSeason() {
    return Tr.c("zianutilities.pase.admin.season_none_previous", "§7No hay temporada anterior.");
  }

  static Component seasonStarted(String id, String end) {
    return Tr.c("zianutilities.pase.admin.season_new", "§aNueva temporada: %s. Termina el %s. La anterior queda como pase anterior.", id, end);
  }

  static Component reloaded() {
    return Tr.c("zianutilities.pase.admin.reloaded", "§aConfiguración del pase recargada.");
  }

  static Component reloadFailed(String error) {
    return Tr.c("zianutilities.pase.admin.reload_failed", "§cConfiguración inválida, se conserva la anterior: %s", error);
  }

  static Component playerReset(String player) {
    return Tr.c("zianutilities.pase.admin.player_reset", "§aContadores de hoy de %s reiniciados (topes, misiones y exploración de la temporada).", player);
  }

  static Component failed(String error) {
    return Tr.c("zianutilities.pase.err.failed", "§cNo se pudo completar la acción: %s", error);
  }
}
