package com.zianblk.zianutilities.neoforge.pase;

import com.zianblk.zianutilities.core.pase.MissionType;
import com.zianblk.zianutilities.core.pase.PaseEngine;
import com.zianblk.zianutilities.neoforge.Tr;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
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
    return Tr.c("zianutilities.pase.status.off", "El pase de batalla no está activo (%s).", problem(problem)).withStyle(ChatFormatting.YELLOW);
  }

  /** "1 vez" / "N veces": singular and plural keys, and the target with thousands separators. */
  static Component missionName(MissionType type, int target, String lang) {
    boolean one = target == 1;
    Component n = num(target, lang);
    return switch (type) {
      case BREAK_BLOCKS -> one ? Tr.c("zianutilities.pase.mission.break_blocks.one", "Minar 1 bloque natural") : Tr.c("zianutilities.pase.mission.break_blocks", "Minar %s bloques naturales", n);
      case HARVEST_CROPS -> one ? Tr.c("zianutilities.pase.mission.harvest_crops.one", "Cosechar 1 cultivo maduro") : Tr.c("zianutilities.pase.mission.harvest_crops", "Cosechar %s cultivos maduros", n);
      case KILL_MOBS -> one ? Tr.c("zianutilities.pase.mission.kill_mobs.one", "Derrotar 1 monstruo") : Tr.c("zianutilities.pase.mission.kill_mobs", "Derrotar %s monstruos", n);
      case BREED_ANIMALS -> one ? Tr.c("zianutilities.pase.mission.breed_animals.one", "Criar animales 1 vez") : Tr.c("zianutilities.pase.mission.breed_animals", "Criar animales %s veces", n);
      case TRADE -> one ? Tr.c("zianutilities.pase.mission.trade.one", "Comerciar 1 vez con aldeanos o niveles distintos") : Tr.c("zianutilities.pase.mission.trade", "Comerciar %s veces con aldeanos o niveles distintos", n);
      case EXPLORE -> one ? Tr.c("zianutilities.pase.mission.explore.one", "Descubrir 1 bioma o estructura nuevo") : Tr.c("zianutilities.pase.mission.explore", "Descubrir %s biomas o estructuras nuevos", n);
      case FISH -> one ? Tr.c("zianutilities.pase.mission.fish.one", "Pescar 1 vez sin quedarte quieto") : Tr.c("zianutilities.pase.mission.fish", "Pescar %s veces sin quedarte quieto", n);
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
    return Tr.c("zianutilities.pase.msg.level", "¡Has alcanzado el nivel %s del pase!", level).withStyle(ChatFormatting.GOLD);
  }

  static Component extraLevelUp(int level) {
    return Tr.c("zianutilities.pase.msg.extra_level", "¡Has alcanzado el nivel extra %s del pase!", level).withStyle(ChatFormatting.GOLD);
  }

  static Component missionDone(MissionType type, int target, long xp, String lang) {
    return Tr.c("zianutilities.pase.msg.mission_done", "¡Misión completada: %s! (+%s XP)", missionName(type, target, lang), num(xp, lang)).withStyle(ChatFormatting.GREEN);
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
        ? Tr.c("zianutilities.pase.msg.cap_day", "Has alcanzado el tope diario de XP del pase. Vuelve mañana a las %s.", resetTime).withStyle(ChatFormatting.YELLOW)
        : Tr.c("zianutilities.pase.msg.cap_source", "Has alcanzado el tope diario de XP de %s.", sourceName(cap)).withStyle(ChatFormatting.YELLOW);
  }

  /** The lines of /ZianUtilities pase ver: only the first one gets the server prefix; the rest are short and indented. */
  static List<Component> view(PaseEngine.View v, String resetTime, String lang) {
    List<Component> out = new ArrayList<>();
    out.add(Tr.c("zianutilities.pase.view.header", "Pase de batalla (%s)", v.seasonId()).withStyle(ChatFormatting.GOLD));
    if (v.level() >= v.maxLevel()) {
      out.add(Tr.c("zianutilities.pase.view.complete", "  Pase completado · extra %s/%s · XP %s/%s", hl(v.extraLevels(), ChatFormatting.YELLOW), v.maxExtraLevels(), hl(num(v.extraIntoLevel(), lang), ChatFormatting.YELLOW), num(extraXp(v), lang)).withStyle(ChatFormatting.GREEN));
    } else {
      out.add(Tr.c("zianutilities.pase.view.level", "  Nivel %s/%s · XP %s/%s", hl(v.level(), ChatFormatting.YELLOW), v.maxLevel(), hl(num(v.xpIntoLevel(), lang), ChatFormatting.YELLOW), num(v.xpPerLevel(), lang)).withStyle(ChatFormatting.GRAY));
    }
    if (v.previousId() != null) {
      out.add(v.previousComplete()
          ? Tr.c("zianutilities.pase.view.previous_done", "  Anterior %s: completado", v.previousId()).withStyle(ChatFormatting.GRAY)
          : Tr.c("zianutilities.pase.view.previous", "  Anterior %s: nivel %s/%s (va primero)", v.previousId(), hl(v.previousLevel(), ChatFormatting.YELLOW), v.maxLevel()).withStyle(ChatFormatting.GRAY));
    }
    Component premium = v.premium() ? Tr.c("zianutilities.pase.view.premium_yes", "sí").withStyle(ChatFormatting.GREEN) : Tr.c("zianutilities.pase.view.premium_no", "no").withStyle(ChatFormatting.RED);
    out.add(Tr.c("zianutilities.pase.view.today", "  Hoy quedan %s/%s XP · %s días · Premium %s", hl(num(v.capLeft(), lang), ChatFormatting.YELLOW), num(v.capTotal(), lang), hl(v.daysLeft(), ChatFormatting.YELLOW), premium).withStyle(ChatFormatting.GRAY));
    if (v.boost()) out.add(Tr.c("zianutilities.pase.view.boost", "  Doble XP activo hasta el final de la temporada").withStyle(ChatFormatting.LIGHT_PURPLE));
    if (v.missions().isEmpty()) {
      out.add(Tr.c("zianutilities.pase.view.no_missions", "  Hoy no hay misiones.").withStyle(ChatFormatting.GRAY));
    } else {
      out.add(Tr.c("zianutilities.pase.view.missions", "  Misiones (reinicio a las %s)", resetTime).withStyle(ChatFormatting.GOLD));
      for (var m : v.missions()) {
        out.add(m.done()
            ? Tr.c("zianutilities.pase.view.mission_done", "    ✔ %s · +%s XP", missionShort(m.type()), hl(num(m.xp(), lang), ChatFormatting.YELLOW)).withStyle(ChatFormatting.GREEN)
            : Tr.c("zianutilities.pase.view.mission", "    ▸ %s %s/%s · +%s XP", missionShort(m.type()), hl(m.progress(), ChatFormatting.DARK_GRAY), hl(m.target(), ChatFormatting.DARK_GRAY), hl(num(m.xp(), lang), ChatFormatting.YELLOW)).withStyle(ChatFormatting.GRAY));
      }
    }
    return out;
  }

  /** An argument with its own color: the color of the whole message does not reach into it, and a colored argument does not leak out. */
  private static Component hl(Object value, ChatFormatting color) {
    return (value instanceof Component c ? c.copy() : Component.literal(String.valueOf(value))).withStyle(color);
  }

  private static long extraXp(PaseEngine.View v) { return PaseModule.config == null ? 1500 : PaseModule.config.rules().extraLevelXp(); }

  static Component xpGiven(long xp, String player, String lang) {
    return Tr.c("zianutilities.pase.admin.xp_given", "Dados %s XP del pase a %s.", num(xp, lang), player).withStyle(ChatFormatting.GREEN);
  }

  static Component xpNone(String player) {
    return Tr.c("zianutilities.pase.admin.xp_none", "%s no ha recibido XP (el pase está completo o su archivo está bloqueado).", player).withStyle(ChatFormatting.YELLOW);
  }

  static Component xpRemoved(long xp, String player, String lang) {
    return Tr.c("zianutilities.pase.admin.xp_removed", "Quitados %s XP del pase a %s.", num(xp, lang), player).withStyle(ChatFormatting.GREEN);
  }

  static Component levelsGiven(int levels, String player) {
    return Tr.c("zianutilities.pase.admin.levels_given", "Dados %s niveles del pase a %s.", levels, player).withStyle(ChatFormatting.GREEN);
  }

  static Component season(String id, String start, String end, boolean previous) {
    return previous
        ? Tr.c("zianutilities.pase.admin.season_previous", "Temporada anterior: %s (del %s al %s).", id, start, end).withStyle(ChatFormatting.GRAY)
        : Tr.c("zianutilities.pase.admin.season", "Temporada actual: %s (del %s al %s).", id, start, end).withStyle(ChatFormatting.GOLD);
  }

  static Component noPreviousSeason() {
    return Tr.c("zianutilities.pase.admin.season_none_previous", "No hay temporada anterior.").withStyle(ChatFormatting.GRAY);
  }

  static Component seasonStarted(String id, String end) {
    return Tr.c("zianutilities.pase.admin.season_new", "Nueva temporada: %s. Termina el %s. La anterior queda como pase anterior.", id, end).withStyle(ChatFormatting.GREEN);
  }

  static Component reloaded() {
    return Tr.c("zianutilities.pase.admin.reloaded", "Configuración del pase recargada.").withStyle(ChatFormatting.GREEN);
  }

  static Component reloadFailed(String error) {
    return Tr.c("zianutilities.pase.admin.reload_failed", "Configuración inválida, se conserva la anterior: %s", error).withStyle(ChatFormatting.RED);
  }

  static Component playerReset(String player) {
    return Tr.c("zianutilities.pase.admin.player_reset", "Contadores de hoy de %s reiniciados (topes, misiones y exploración de la temporada).", player).withStyle(ChatFormatting.GREEN);
  }

  static Component failed(String error) {
    return Tr.c("zianutilities.pase.err.failed", "No se pudo completar la acción: %s", error).withStyle(ChatFormatting.RED);
  }

  static Component failed(Component error) {
    return Tr.c("zianutilities.pase.err.failed", "No se pudo completar la acción: %s", error).withStyle(ChatFormatting.RED);
  }

  static Component lockedFile() {
    return Tr.c("zianutilities.pase.err.locked", "el archivo del pase de ese jugador está bloqueado o es ilegible");
  }

  static Component pickOne() {
    return Tr.c("zianutilities.pase.err.pick_one", "elige un solo jugador");
  }

  // ---------------------------------------------------------------- rewards

  /** "one" text when n is 1, otherwise the "many" text; the many text gets the number (with separators) as its first argument. */
  private static Component plural(ChatFormatting color, long n, String lang, String oneKey, String oneEs, String manyKey, String manyEs, Object... rest) {
    if (n == 1) return Tr.c(oneKey, oneEs, rest).withStyle(color);
    Object[] all = new Object[rest.length + 1];
    all[0] = num(n, lang);
    System.arraycopy(rest, 0, all, 1, rest.length);
    return Tr.c(manyKey, manyEs, all).withStyle(color);
  }

  static Component rewardItem(int count, Component name, String lang) {
    return Tr.c("zianutilities.pase.reward.item", "%sx %s", num(count, lang), name);
  }

  static Component rewardCoins(long amount, Component currency, String lang) {
    return Tr.c("zianutilities.pase.reward.coins", "%s %s", num(amount, lang), currency);
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
    return Tr.c("zianutilities.pase.msg.reward_claimed", "¡Premio reclamado: %s!", reward).withStyle(ChatFormatting.GREEN);
  }

  static Component rewardsClaimed(long count, String lang) {
    return plural(ChatFormatting.GREEN, count, lang, "zianutilities.pase.msg.rewards_claimed.one", "¡Has reclamado 1 premio del pase!",
        "zianutilities.pase.msg.rewards_claimed", "¡Has reclamado %s premios del pase!");
  }

  static Component rewardsPending(long count, String lang) {
    return plural(ChatFormatting.YELLOW, count, lang, "zianutilities.pase.msg.rewards_pending.one", "Tienes 1 premio del pase pendiente. Usa /ZianUtilities pase reclamar.",
        "zianutilities.pase.msg.rewards_pending", "Tienes %s premios del pase pendientes. Usa /ZianUtilities pase reclamar.");
  }

  static Component rewardsNone() {
    return Tr.c("zianutilities.pase.msg.rewards_none", "No tienes premios del pase pendientes.").withStyle(ChatFormatting.GRAY);
  }

  static Component remaining(long count, String lang) {
    return plural(ChatFormatting.YELLOW, count, lang, "zianutilities.pase.msg.remaining.one", "Queda 1 premio: vuelve a usar /ZianUtilities pase reclamar.",
        "zianutilities.pase.msg.remaining", "Quedan %s premios: vuelve a usar /ZianUtilities pase reclamar.");
  }

  static Component uncertain(long count, String lang) {
    return plural(ChatFormatting.RED, count, lang, "zianutilities.pase.msg.reward_uncertain.one", "1 premio quedó en revisión: un administrador lo comprobará.",
        "zianutilities.pase.msg.reward_uncertain", "%s premios quedaron en revisión: un administrador los comprobará.");
  }

  static Component proof() {
    return Tr.c("zianutilities.pase.msg.reward_proof", "Tu Pokémon se está guardando; se confirmará en unos segundos.").withStyle(ChatFormatting.GRAY);
  }

  static Component premiumOn(String season) {
    return Tr.c("zianutilities.pase.msg.premium_on", "¡Pase premium de %s activado! Usa /ZianUtilities pase reclamar para recoger tus premios.", season).withStyle(ChatFormatting.GOLD);
  }

  static Component itemDropped(Component item) {
    return Tr.c("zianutilities.pase.msg.item_dropped", "Tu inventario se llenó: %s se ha soltado a tus pies.", item).withStyle(ChatFormatting.YELLOW);
  }

  /** Why prizes stay pending: a full inventory or PC has its own sentence, every other code becomes "N premios siguen pendientes (motivo)". */
  static Component blocked(String reason, long count, String lang) {
    return switch (reason == null ? "" : reason) {
      case "INVENTORY_FULL" -> plural(ChatFormatting.YELLOW, count, lang, "zianutilities.pase.msg.inventory_full.one", "Inventario lleno: libera espacio y vuelve a reclamar (1 premio pendiente).",
          "zianutilities.pase.msg.inventory_full", "Inventario lleno: libera espacio y vuelve a reclamar (%s premios pendientes).");
      case "PC_FULL" -> plural(ChatFormatting.YELLOW, count, lang, "zianutilities.pase.msg.pc_full.one", "Tu PC está lleno: libera espacio y vuelve a reclamar (1 premio pendiente).",
          "zianutilities.pase.msg.pc_full", "Tu PC está lleno: libera espacio y vuelve a reclamar (%s premios pendientes).");
      default -> plural(ChatFormatting.YELLOW, count, lang, "zianutilities.pase.msg.reward_blocked.one", "1 premio sigue pendiente (%s).",
          "zianutilities.pase.msg.reward_blocked", "%s premios siguen pendientes (%s).", reason(reason));
    };
  }

  static Component reason(String code) {
    String c = code == null ? "" : code;
    if (c.startsWith("COINS_")) c = "COINS_UNCERTAIN";
    if (c.startsWith("COMMAND_RESULT_")) c = "COMMAND_RESULT";
    return switch (c) {
      case "" -> Component.empty();
      case "INVENTORY_FULL" -> Tr.c("zianutilities.pase.reason.inventory_full", "inventario lleno");
      case "PC_FULL" -> Tr.c("zianutilities.pase.reason.pc_full", "PC lleno");
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
      case "NO_PREMIUM" -> Tr.c("zianutilities.pase.reason.no_premium", "sin premium de esa temporada");
      case "RESUME" -> Tr.c("zianutilities.pase.reason.resume", "se reanudará tras el reinicio");
      case "INTERRUPTED" -> Tr.c("zianutilities.pase.reason.interrupted", "interrumpido por una caída: revisar");
      case "ADMIN_REDELIVER" -> Tr.c("zianutilities.pase.reason.admin_redeliver", "reabierto por un administrador");
      case "PROOF" -> Tr.c("zianutilities.pase.reason.proof", "esperando la comprobación del PC");
      case "SAVE_FAILED" -> Tr.c("zianutilities.pase.reason.save_failed", "no se pudo escribir el diario");
      case "CHECK_FAILED", "EXCEPTION" -> Tr.c("zianutilities.pase.reason.failed", "error al entregar");
      case "DELIVERY_NOT_PROVEN", "DELIVERY_MISMATCH" -> Tr.c("zianutilities.pase.reason.not_proven", "entrega no comprobada en el PC");
      case "COINS_UNCERTAIN" -> Tr.c("zianutilities.pase.reason.coins_uncertain", "monedas sin confirmar");
      case "COMMAND_RESULT" -> Tr.c("zianutilities.pase.reason.command_result", "el comando no confirmó su efecto");
      default -> Tr.c("zianutilities.pase.reason.other", "motivo desconocido: %s", c);
    };
  }

  static Component state(com.zianblk.zianutilities.core.pase.Prize.State state) {
    return switch (state) {
      case PENDING -> Tr.c("zianutilities.pase.state.pending", "pendiente");
      case DELIVERING -> Tr.c("zianutilities.pase.state.delivering", "entregando");
      case DELIVERED -> Tr.c("zianutilities.pase.state.delivered", "entregado");
      case UNCERTAIN -> Tr.c("zianutilities.pase.state.uncertain", "incierto");
    };
  }

  /** A problem with a season file, as the OPs see it: the translatable message of the exception, with nested problems rendered too. */
  static Component problem(Throwable problem) {
    if (problem instanceof com.zianblk.zianutilities.neoforge.LocalizedException.Info info) {
      Object[] args = info.args().clone();
      for (int i = 0; i < args.length; i++) if (args[i] instanceof Throwable inner) args[i] = problem(inner);
      return Tr.c(info.key(), info.es(), args);
    }
    return Tr.c("zianutilities.pase.problem.generic", "%s", String.valueOf(problem.getMessage()));
  }

  static Component premiumGiven(String season, String player, String transaction) {
    return Tr.c("zianutilities.pase.admin.premium_given", "Premium de %s dado a %s (transacción %s).", season, player, transaction).withStyle(ChatFormatting.GREEN);
  }

  static Component premiumDuplicate(String transaction) {
    return Tr.c("zianutilities.pase.admin.premium_duplicate", "La transacción %s ya se usó: se ignora.", transaction).withStyle(ChatFormatting.YELLOW);
  }

  static Component premiumRemoved(String season, String player, long dropped, String lang) {
    return plural(ChatFormatting.GREEN, dropped, lang, "zianutilities.pase.admin.premium_removed.one", "Premium de %s retirado a %s (1 premio sin entregar anulado).",
        "zianutilities.pase.admin.premium_removed", "Premium de %2$s retirado a %3$s (%1$s premios sin entregar anulados).", season, player);
  }

  static Component premiumBadSeason(String current, String previous) {
    if (previous == null) return Tr.c("zianutilities.pase.admin.premium_bad_season_current", "La temporada debe ser la actual (%s).", current).withStyle(ChatFormatting.RED);
    return Tr.c("zianutilities.pase.admin.premium_bad_season", "La temporada debe ser la actual (%s) o la anterior (%s).", current, previous == null ? "-" : previous).withStyle(ChatFormatting.RED);
  }

  static Component premiumBadTransaction() {
    return Tr.c("zianutilities.pase.admin.premium_bad_transaction", "Transacción no válida: letras, números, guion, guion bajo y punto (máximo 64).").withStyle(ChatFormatting.RED);
  }

  static Component premiumFailed(Component error) {
    return Tr.c("zianutilities.pase.admin.premium_failed", "No se pudo dar el premium (se puede repetir el mismo comando): %s", error).withStyle(ChatFormatting.RED);
  }

  static Component premiumLocked() {
    return Tr.c("zianutilities.pase.admin.premium_locked", "El archivo del pase del jugador está bloqueado");
  }

  static Component premiumNotRegistered() {
    return Tr.c("zianutilities.pase.admin.premium_not_registered", "El premium no quedó registrado");
  }

  static Component premiumTxUnreadable() {
    return Tr.c("zianutilities.pase.admin.premium_tx_unreadable", "El archivo de transacciones no se puede leer; no se da ningún premium");
  }

  static Component premiumRemoveFailed() {
    return Tr.c("zianutilities.pase.admin.premium_remove_failed", "No se pudo retirar el premium: el archivo del jugador no se puede escribir.").withStyle(ChatFormatting.RED);
  }

  /** What the OPs are told when a Tebex command fails: the reason, the transaction and the player. */
  static Component tebexProblem(Component reason, String transaction, String player) {
    return Tr.c("zianutilities.pase.admin.tebex_problem", "[Tebex] %s (transacción %s, jugador %s)", reason, transaction, player).withStyle(ChatFormatting.RED);
  }

  static Component tebexPending(String list) {
    return Tr.c("zianutilities.pase.admin.tebex_pending", "[Tebex] Transacciones sin completar tras una caída: %s. Repite su comando para terminarlas.", list).withStyle(ChatFormatting.YELLOW);
  }

  static Component prizesHeader(String player, long pending, long delivered, long review, String lang) {
    return Tr.c("zianutilities.pase.admin.prizes_header", "Premios de %s · pendientes %s · entregados %s · por revisar %s", player, hl(num(pending, lang), ChatFormatting.YELLOW), hl(num(delivered, lang), ChatFormatting.GREEN), hl(num(review, lang), ChatFormatting.RED)).withStyle(ChatFormatting.GOLD);
  }

  static Component prizesLine(String id, Component state, Component reason) {
    return Tr.c("zianutilities.pase.admin.prizes_line", "  %s %s %s", id, state, reason).withStyle(ChatFormatting.GRAY);
  }

  static Component prizesMore(long count, String lang) {
    return Tr.c("zianutilities.pase.admin.prizes_more", "  … y %s más", num(count, lang)).withStyle(ChatFormatting.GRAY);
  }

  static Component prizesReopened(long count, String player, String lang) {
    return plural(ChatFormatting.GREEN, count, lang, "zianutilities.pase.admin.prizes_reopened.one", "1 premio por revisar de %s vuelve a pendiente.",
        "zianutilities.pase.admin.prizes_reopened", "%s premios por revisar de %s vuelven a pendientes.", player);
  }

  static Component prizesOffline() {
    return Tr.c("zianutilities.pase.admin.prizes_offline", "El jugador está desconectado: los entregará al conectarse y usar /ZianUtilities pase reclamar.").withStyle(ChatFormatting.GRAY);
  }

  static Component rewardsInvalid(Component problem) {
    return Tr.c("zianutilities.pase.admin.rewards_invalid", "[Pase] Premios no disponibles: %s. Los niveles se siguen ganando y los premios quedan pendientes hasta que el archivo sea válido.", problem).withStyle(ChatFormatting.RED);
  }
}
