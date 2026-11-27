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

  /** A number with thousands separators: "." for Spanish clients, "," for the rest. Sent as plain text, so no client formatting is needed. */
  static Component num(long value, String lang) {
    var symbols = new java.text.DecimalFormatSymbols(java.util.Locale.ROOT);
    boolean es = lang != null && lang.toLowerCase(java.util.Locale.ROOT).startsWith("es");
    symbols.setGroupingSeparator(es ? '.' : ',');
    return Component.literal(new java.text.DecimalFormat("#,##0", symbols).format(value));
  }

  /** Short mission name for the compact lines of pase ver. */
  static Component missionShort(MissionType type) {
    return switch (type) {
      case BREAK_BLOCKS -> Tr.c("zianutilities.pase.mission_short.break_blocks", "Minar bloques");
      case HARVEST_CROPS -> Tr.c("zianutilities.pase.mission_short.harvest_crops", "Cosechar cultivos");
      case KILL_MOBS -> Tr.c("zianutilities.pase.mission_short.kill_mobs", "Matar monstruos");
      case BREED_ANIMALS -> Tr.c("zianutilities.pase.mission_short.breed_animals", "Criar animales");
      case TRADE -> Tr.c("zianutilities.pase.mission_short.trade", "Comerciar");
      case EXPLORE -> Tr.c("zianutilities.pase.mission_short.explore", "Explorar");
      case FISH -> Tr.c("zianutilities.pase.mission_short.fish", "Pescar");
    };
  }

  static Component levelUp(int level) {
    return Tr.c("zianutilities.pase.msg.level", "§6¡Has alcanzado el nivel %s del pase!", level);
  }

  static Component extraLevelUp(int level) {
    return Tr.c("zianutilities.pase.msg.extra_level", "§6¡Has alcanzado el nivel extra %s del pase!", level);
  }

  static Component missionDone(MissionType type, int target, long xp, String lang) {
    return Tr.c("zianutilities.pase.msg.mission_done", "§a¡Misión completada: %s! §7(+%s XP)", missionName(type, target), num(xp, lang));
  }

  static Component sourceName(String source) {
    return switch (source) {
      case "mission" -> Tr.c("zianutilities.pase.source.mission", "misiones");
      case "boss" -> Tr.c("zianutilities.pase.source.boss", "jefes");
      case "raid" -> Tr.c("zianutilities.pase.source.raid", "raids");
      case "weeklyRaid" -> Tr.c("zianutilities.pase.source.weekly_raid", "la raid semanal");
      default -> Component.literal(source);
    };
  }

  /** "day" is the daily total; anything else is the name of a source. */
  static Component capReached(String cap, String resetTime) {
    return "day".equals(cap)
        ? Tr.c("zianutilities.pase.msg.cap_day", "§eHas alcanzado el tope diario de XP del pase. Vuelve mañana a las %s.", resetTime)
        : Tr.c("zianutilities.pase.msg.cap_source", "§eHas alcanzado el tope diario de XP de %s.", sourceName(cap));
  }

  /** The lines of /ZianUtilities pase ver: only the first one gets the server prefix; the rest are short and indented. */
  static List<Component> view(PaseEngine.View v, String resetTime, String lang) {
    List<Component> out = new ArrayList<>();
    out.add(Tr.c("zianutilities.pase.view.header", "§6Pase de batalla §7(%s)", v.seasonId()));
    if (v.level() >= v.maxLevel()) {
      out.add(Tr.c("zianutilities.pase.view.complete", "  §aPase completado §7· extra §e%s§7/%s · XP §e%s§7/%s", v.extraLevels(), v.maxExtraLevels(), num(v.extraIntoLevel(), lang), num(extraXp(v), lang)));
    } else {
      out.add(Tr.c("zianutilities.pase.view.level", "  §7Nivel §e%s§7/%s · XP §e%s§7/%s", v.level(), v.maxLevel(), num(v.xpIntoLevel(), lang), num(v.xpPerLevel(), lang)));
    }
    if (v.previousId() != null) {
      out.add(v.previousComplete()
          ? Tr.c("zianutilities.pase.view.previous_done", "  §7Anterior %s: §acompletado", v.previousId())
          : Tr.c("zianutilities.pase.view.previous", "  §7Anterior %s: nivel §e%s§7/%s (va primero)", v.previousId(), v.previousLevel(), v.maxLevel()));
    }
    Component premium = v.premium() ? Tr.c("zianutilities.pase.view.premium_yes", "§así") : Tr.c("zianutilities.pase.view.premium_no", "§cno");
    out.add(Tr.c("zianutilities.pase.view.today", "  §7Hoy quedan §e%s§7/%s XP · §e%s§7 días · Premium %s", num(v.capLeft(), lang), num(v.capTotal(), lang), v.daysLeft(), premium));
    if (v.boost()) out.add(Tr.c("zianutilities.pase.view.boost", "  §dDoble XP activo hasta el final de la temporada"));
    if (v.missions().isEmpty()) {
      out.add(Tr.c("zianutilities.pase.view.no_missions", "  §7Hoy no hay misiones."));
    } else {
      out.add(Tr.c("zianutilities.pase.view.missions", "  §6Misiones §7(reinicio a las %s)", resetTime));
      for (var m : v.missions()) {
        out.add(m.done()
            ? Tr.c("zianutilities.pase.view.mission_done", "    §a✔ %s §7· §e+%s XP", missionShort(m.type()), num(m.xp(), lang))
            : Tr.c("zianutilities.pase.view.mission", "    §7▸ %s §8%s/%s §7· §e+%s XP", missionShort(m.type()), m.progress(), m.target(), num(m.xp(), lang)));
      }
    }
    return out;
  }

  private static long extraXp(PaseEngine.View v) { return PaseModule.config == null ? 1500 : PaseModule.config.rules().extraLevelXp(); }

  static Component xpGiven(long xp, String player, String lang) {
    return Tr.c("zianutilities.pase.admin.xp_given", "§aDados %s XP del pase a %s.", num(xp, lang), player);
  }

  static Component xpNone(String player) {
    return Tr.c("zianutilities.pase.admin.xp_none", "§e%s no ha recibido XP (el pase está completo o su archivo está bloqueado).", player);
  }

  static Component xpRemoved(long xp, String player, String lang) {
    return Tr.c("zianutilities.pase.admin.xp_removed", "§aQuitados %s XP del pase a %s.", num(xp, lang), player);
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

  // ---------------------------------------------------------------- rewards

  static Component rewardItem(int count, Component name) {
    return Tr.c("zianutilities.pase.reward.item", "%sx %s", count, name);
  }

  static Component rewardCoins(long amount, Component currency) {
    return Tr.c("zianutilities.pase.reward.coins", "%s %s", amount, currency);
  }

  static Component rewardPokemon(Component species) {
    return Tr.c("zianutilities.pase.reward.pokemon", "Pokémon %s", species);
  }

  static Component rewardPokemonRandom() {
    return Tr.c("zianutilities.pase.reward.pokemon_random", "un Pokémon");
  }

  static Component rewardCommand() {
    return Tr.c("zianutilities.pase.reward.command", "un premio especial");
  }

  static Component rewardClaimed(Component reward) {
    return Tr.c("zianutilities.pase.msg.reward_claimed", "§a¡Premio reclamado: %s!", reward);
  }

  static Component rewardsClaimed(long count) {
    return Tr.c("zianutilities.pase.msg.rewards_claimed", "§a¡Has reclamado %s premios del pase!", count);
  }

  static Component rewardsPending(long count) {
    return Tr.c("zianutilities.pase.msg.rewards_pending", "§eTienes %s premios del pase pendientes. Usa /ZianUtilities pase reclamar.", count);
  }

  static Component rewardsNone() {
    return Tr.c("zianutilities.pase.msg.rewards_none", "§7No tienes premios del pase pendientes.");
  }

  static Component uncertain(long count) {
    return Tr.c("zianutilities.pase.msg.reward_uncertain", "§c%s premios quedaron en revisión: un administrador los comprobará.", count);
  }

  static Component proof() {
    return Tr.c("zianutilities.pase.msg.reward_proof", "§7Tu Pokémon se está guardando; se confirmará en unos segundos.");
  }

  static Component premiumOn(String season) {
    return Tr.c("zianutilities.pase.msg.premium_on", "§6¡Pase premium de %s activado! Usa /ZianUtilities pase reclamar para recoger tus premios.", season);
  }

  /** Why prizes stay pending: a full inventory or PC has its own sentence, every other code becomes "N premios siguen pendientes (motivo)". */
  static Component blocked(String reason, long count) {
    return switch (reason == null ? "" : reason) {
      case "INVENTORY_FULL" -> Tr.c("zianutilities.pase.msg.inventory_full", "§eInventario lleno: libera espacio y vuelve a reclamar (%s premios pendientes).", count);
      case "PC_FULL" -> Tr.c("zianutilities.pase.msg.pc_full", "§eTu PC está lleno: libera espacio y vuelve a reclamar (%s premios pendientes).", count);
      default -> Tr.c("zianutilities.pase.msg.reward_blocked", "§e%s premios siguen pendientes (%s).", count, reason(reason));
    };
  }

  static Component reason(String code) {
    return switch (code == null ? "" : code) {
      case "CONFIG_INVALID" -> Tr.c("zianutilities.pase.reason.config_invalid", "la configuración de premios no es válida");
      case "NO_ECONOMY" -> Tr.c("zianutilities.pase.reason.no_economy", "AVECOINS no está disponible");
      case "UNSUPPORTED_CURRENCY", "unsupported_currency" -> Tr.c("zianutilities.pase.reason.unsupported_currency", "moneda no admitida");
      case "wallet_full" -> Tr.c("zianutilities.pase.reason.wallet_full", "cartera llena");
      case "GENERATION_DISABLED" -> Tr.c("zianutilities.pase.reason.generation_disabled", "generación desactivada");
      case "NO_SPECIES" -> Tr.c("zianutilities.pase.reason.no_species", "no hay especie disponible");
      case "COMMAND_NOT_ALLOWED" -> Tr.c("zianutilities.pase.reason.command_not_allowed", "comando no permitido");
      case "COMMAND_REJECTED" -> Tr.c("zianutilities.pase.reason.command_rejected", "comando rechazado");
      case "BAD_ITEM" -> Tr.c("zianutilities.pase.reason.bad_item", "objeto no válido");
      case "BAD_PLAYER_NAME" -> Tr.c("zianutilities.pase.reason.bad_player_name", "nombre de jugador no válido");
      default -> Tr.c("zianutilities.pase.reason.other", "razón %s", code == null ? "" : code);
    };
  }

  static Component premiumGiven(String season, String player, String transaction) {
    return Tr.c("zianutilities.pase.admin.premium_given", "§aPremium de %s dado a %s (transacción %s).", season, player, transaction);
  }

  static Component premiumDuplicate(String transaction) {
    return Tr.c("zianutilities.pase.admin.premium_duplicate", "§eLa transacción %s ya se usó: se ignora.", transaction);
  }

  static Component premiumRemoved(String season, String player, int dropped) {
    return Tr.c("zianutilities.pase.admin.premium_removed", "§aPremium de %s retirado a %s (%s premios sin entregar anulados).", season, player, dropped);
  }

  static Component premiumBadSeason(String current, String previous) {
    return Tr.c("zianutilities.pase.admin.premium_bad_season", "§cLa temporada debe ser la actual (%s) o la anterior (%s).", current, previous == null ? "-" : previous);
  }

  static Component premiumBadTransaction() {
    return Tr.c("zianutilities.pase.admin.premium_bad_transaction", "§cTransacción no válida: letras, números, guion, guion bajo y punto (máximo 64).");
  }

  static Component premiumFailed(String error) {
    return Tr.c("zianutilities.pase.admin.premium_failed", "§cNo se pudo dar el premium (se puede repetir el mismo comando): %s", error);
  }

  static Component premiumRemoveFailed() {
    return Tr.c("zianutilities.pase.admin.premium_remove_failed", "§cNo se pudo retirar el premium: el archivo del jugador no se puede escribir.");
  }

  static Component prizesHeader(String player, long pending, long delivered, long review) {
    return Tr.c("zianutilities.pase.admin.prizes_header", "§6Premios de %s: §e%s§7 pendientes, §a%s§7 entregados, §c%s§7 por revisar.", player, pending, delivered, review);
  }

  static Component prizesLine(String id, String state, String reason) {
    return Tr.c("zianutilities.pase.admin.prizes_line", "  §7%s §8%s §7%s", id, state, reason);
  }

  static Component prizesMore(long count) {
    return Tr.c("zianutilities.pase.admin.prizes_more", "  §7… y %s más", count);
  }

  static Component prizesReopened(long count, String player, String note) {
    return Tr.c("zianutilities.pase.admin.prizes_reopened", "§a%s premios por revisar de %s vuelven a pendientes. %s", count, player, note);
  }

  static Component prizesOffline() {
    return Tr.c("zianutilities.pase.admin.prizes_offline", "§7El jugador está desconectado: los entregará al conectarse y usar /ZianUtilities pase reclamar.");
  }

  static Component rewardsInvalid(String problem) {
    return Tr.c("zianutilities.pase.admin.rewards_invalid", "§c[Pase] Premios no disponibles: %s. Los niveles se siguen ganando y los premios quedan pendientes hasta que el archivo sea válido.", problem);
  }
}
