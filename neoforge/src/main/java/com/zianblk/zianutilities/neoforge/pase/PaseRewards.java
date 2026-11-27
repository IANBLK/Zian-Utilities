package com.zianblk.zianutilities.neoforge.pase;

import com.zianblk.zianutilities.core.config.ConfigHistory;
import com.zianblk.zianutilities.core.pase.PaseReward;
import com.zianblk.zianutilities.core.pase.PlayerPase;
import com.zianblk.zianutilities.core.pase.Prize;
import com.zianblk.zianutilities.core.pase.PrizeClaimer;
import com.zianblk.zianutilities.core.pase.PrizePlanner;
import com.zianblk.zianutilities.core.pase.SeasonRewards;
import com.zianblk.zianutilities.core.rewards.FileRewardClaimStore;
import com.zianblk.zianutilities.core.rewards.RewardClaimService;
import com.zianblk.zianutilities.core.rewards.EconomyRewardDelivery;
import com.zianblk.zianutilities.core.storage.StorageFiles;
import com.zianblk.zianutilities.neoforge.Messages;
import com.zianblk.zianutilities.neoforge.UtilitiesPermissions;
import com.zianblk.zianutilities.neoforge.economy.AvecoinsEconomyPort;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Rewards of the pass: loads the season files, turns progress into pending prizes, delivers them through the journal and
 * handles the Tebex premium. Fail closed: a season file that is missing, broken or names something that does not exist
 * gives no rewards for that season (nothing is delivered, the OPs are told, levels keep being earned and the prizes
 * are created and stay pending as soon as the file is valid). Everything here runs on the server thread.
 */
final class PaseRewards {
  static final String FIRST_SEASON = "temporada-1";
  private static final Map<String, SeasonRewards> LOADED = new HashMap<>();
  private static final Map<String, String> FAILED = new LinkedHashMap<>();
  private static Set<String> commands;
  private static String commandsProblem;
  private static AvecoinsEconomyPort port;
  private static RewardClaimService coins;
  private static PaseTransactions transactions;

  private PaseRewards() {}

  // ---------------------------------------------------------------- files

  static Path seasonFile(String seasonId) { return FMLPaths.CONFIGDIR.get().resolve("zianutilities-pase-" + seasonId + ".json"); }

  static Path commandsFile() { return FMLPaths.CONFIGDIR.get().resolve("zianutilities-pase-commands.json"); }

  /** Forgets everything loaded (server start, stop and /pase admin recargar) so the next use reads the files again. */
  static void reset() {
    LOADED.clear();
    FAILED.clear();
    commands = null;
    commandsProblem = null;
    port = null;
    coins = null;
    transactions = null;
  }

  private static void writeNew(Path file, String text) throws IOException {
    StorageFiles.createDirectories(file.getParent());
    Path temp = StorageFiles.createTempFile(file.getParent(), "zianutilities-pase-", ".tmp");
    try {
      StorageFiles.writeString(temp, text);
      StorageFiles.move(temp, file, StandardCopyOption.ATOMIC_MOVE);
    } finally {
      StorageFiles.deleteIfExists(temp);
    }
  }

  /** The command templates a reward may run (empty when the file is missing, which is created empty). Never null. */
  static Set<String> commands() {
    if (commands != null) return commands;
    try {
      Path file = commandsFile();
      if (!StorageFiles.exists(file)) writeNew(file, PaseRewardJson.whitelistDefaultJson());
      commands = PaseRewardJson.parseWhitelist(StorageFiles.readString(file));
      commandsProblem = null;
    } catch (IOException | RuntimeException bad) {
      commands = Set.of();
      commandsProblem = String.valueOf(bad.getMessage());
      PaseModule.LOG.error("La lista blanca de comandos de premios ({}) no es válida; ningún premio de comando se entrega: {}", commandsFile().getFileName(), bad.toString());
    }
    return commands;
  }

  /** The rewards of a season, or null when its file is missing or invalid (the reason is in {@link #problem}). */
  static SeasonRewards rewardsOf(String seasonId) {
    if (seasonId == null || !PaseModule.active()) return null;
    SeasonRewards loaded = LOADED.get(seasonId);
    if (loaded != null) return loaded;
    if (FAILED.containsKey(seasonId)) return null;
    try {
      Path file = seasonFile(seasonId);
      if (!StorageFiles.exists(file)) {
        if (!FIRST_SEASON.equals(seasonId)) throw new IllegalArgumentException("falta el archivo config/" + file.getFileName());
        writeNew(file, PaseSeasonExample.json());
        PaseModule.LOG.info("Creada la temporada de ejemplo {}", file.getFileName());
      }
      SeasonRewards parsed = PaseRewardJson.parseSeason(StorageFiles.readString(file), PaseModule.config.rules().levels());
      String bad = validate(parsed);
      if (bad != null) throw new IllegalArgumentException(bad);
      LOADED.put(seasonId, parsed);
      return parsed;
    } catch (IOException | RuntimeException bad) {
      FAILED.put(seasonId, String.valueOf(bad.getMessage()));
      PaseModule.LOG.error("Premios de {} no disponibles (no se entrega nada de esa temporada hasta que el archivo sea válido; /ZianUtilities pase admin recargar): {}", seasonId, bad.toString());
      return null;
    }
  }

  /** The first reason a season of the pass has no rewards, or null. */
  static String problem() {
    if (!PaseModule.active()) return null;
    var seasons = PaseModule.seasons;
    rewardsOf(seasons.current().id());
    if (seasons.previous() != null) rewardsOf(seasons.previous().id());
    if (commandsProblem != null) return commandsFile().getFileName() + ": " + commandsProblem;
    return FAILED.entrySet().stream().findFirst().map(e -> e.getKey() + ": " + e.getValue()).orElse(null);
  }

  /** Checks that every item, species and command named in the season exists or is allowed, so a typo is caught on load and not at claim time. */
  private static String validate(SeasonRewards s) {
    var registries = PaseModule.server.registryAccess();
    for (var e : new java.util.TreeMap<>(s.levels()).entrySet()) {
      for (PaseReward r : e.getValue().free()) { String bad = PaseDelivery.validate(r, registries, commands()); if (bad != null) return "nivel " + e.getKey() + " (free): " + bad; }
      for (PaseReward r : e.getValue().premium()) { String bad = PaseDelivery.validate(r, registries, commands()); if (bad != null) return "nivel " + e.getKey() + " (premium): " + bad; }
    }
    for (var entry : s.loot()) { String bad = PaseDelivery.validate(entry.reward(), registries, commands()); if (bad != null) return "extra.loot: " + bad; }
    return null;
  }

  /** The reload command: forgets what was loaded, reads the current and previous season again and copies good files to the history. */
  static String reload() {
    reset();
    String problem = problem();
    if (problem == null) {
      for (String id : seasonIds()) {
        try {
          Path dir = PaseModule.dataDir();
          new ConfigHistory(seasonFile(id), dir.resolve("config-backups"), dir.resolve("changes.jsonl"), 10, Clock.systemUTC()).backup();
        } catch (Exception failure) {
          PaseModule.LOG.warn("No se pudo copiar los premios de {} al historial", id, failure);
        }
      }
    }
    return problem;
  }

  static List<String> seasonIds() {
    List<String> ids = new ArrayList<>();
    if (PaseModule.seasons.previous() != null) ids.add(PaseModule.seasons.previous().id());
    ids.add(PaseModule.seasons.current().id());
    return ids;
  }

  // ---------------------------------------------------------------- economy and transactions

  static AvecoinsEconomyPort economyPort() {
    if (port == null) {
      try {
        port = new AvecoinsEconomyPort();
      } catch (RuntimeException | LinkageError unavailable) {
        PaseModule.LOG.warn("AVECOINS no está disponible: los premios de monedas quedan pendientes: {}", unavailable.toString());
        return null;
      }
    }
    return port;
  }

  /** The claim journal shared with the other modules (/ZianUtilities reward inspect reads it). */
  static RewardClaimService coins() {
    var economy = economyPort();
    if (economy == null) return null;
    if (coins == null) coins = new RewardClaimService(new FileRewardClaimStore(PaseModule.dataDir().getParent().resolve("reward_claims")), new EconomyRewardDelivery(economy));
    return coins;
  }

  static PaseTransactions transactions() {
    if (transactions == null) transactions = new PaseTransactions(PaseModule.dataDir().resolve("transactions.json"));
    return transactions;
  }

  // ---------------------------------------------------------------- prizes

  /** Creates the prizes the player has earned and does not have yet. Returns how many were added. */
  static int sync(PlayerPase p) {
    if (!PaseModule.active() || p.locked) return 0;
    List<String> seasons = seasonIds();
    int added = PrizePlanner.sync(p, PaseModule.config.rules(), seasons, PaseRewards::rewardsOf, PaseModule.CLOCK.millis());
    PrizePlanner.prune(p, seasons);
    // The level events were only a trigger: the prizes come from the XP, so they can go once the current season has its rewards.
    if (!p.events.isEmpty() && rewardsOf(PaseModule.seasons.current().id()) != null) {
      p.events.clear();
      p.dirty = true;
    }
    return added;
  }

  /** Called when a player reaches levels: the new prizes wait for /pase reclamar. Never throws into the game. */
  static void onLevels(ServerPlayer player) {
    try {
      PlayerPase p = PaseModule.store.get(player.getUUID());
      int added = sync(p);
      if (added > 0) tellPending(player, p);
    } catch (RuntimeException | LinkageError failure) {
      PaseModule.LOG.error("No se pudieron crear los premios de {}; quedan para la próxima vez", player.getGameProfile().getName(), failure);
    }
  }

  /** When a player joins: new prizes, a word about pending ones, and the OPs hear if the rewards are closed. */
  static void onLogin(ServerPlayer player) {
    if (!PaseModule.active()) return;
    try {
      PlayerPase p = PaseModule.store.get(player.getUUID());
      sync(p);
      tellPending(player, p);
      String problem = problem();
      if (problem != null && UtilitiesPermissions.allows(player, "admin.pase", true))
        player.sendSystemMessage(Messages.format(PaseMessages.rewardsInvalid(problem)));
    } catch (RuntimeException | LinkageError failure) {
      PaseModule.LOG.error("Revisión de premios al entrar fallida para {}", player.getGameProfile().getName(), failure);
    }
  }

  static void tellPending(ServerPlayer player, PlayerPase p) {
    long pending = PrizePlanner.pending(p);
    if (pending > 0) player.sendSystemMessage(Messages.format(PaseMessages.rewardsPending(pending)));
  }

  /** Delivers everything pending to an online player and tells them what happened. */
  static PrizeClaimer.Report claim(ServerPlayer player) {
    PlayerPase p = PaseModule.store.get(player.getUUID());
    sync(p);
    var report = PrizeClaimer.claim(p, new PaseDelivery(player), PaseModule.store::saveNow, PaseModule.LOG::warn, PaseModule.CLOCK.millis());
    tell(player, report);
    return report;
  }

  static void tell(ServerPlayer player, PrizeClaimer.Report r) {
    String lang = PaseSources.lang(player);
    if (r.delivered().size() > 8) player.sendSystemMessage(Messages.format(PaseMessages.rewardsClaimed(r.delivered().size())));
    else for (Prize z : r.delivered()) player.sendSystemMessage(Messages.format(PaseMessages.rewardClaimed(PaseDelivery.describe(z, player.registryAccess()))));
    Map<String, Integer> blocked = new LinkedHashMap<>();
    for (Prize z : r.blocked()) blocked.merge(z.reason, 1, Integer::sum);
    blocked.forEach((reason, n) -> player.sendSystemMessage(Messages.format(PaseMessages.blocked(reason, n))));
    if (!r.uncertain().isEmpty()) player.sendSystemMessage(Messages.format(PaseMessages.uncertain(r.uncertain().size())));
    if (!r.proving().isEmpty()) player.sendSystemMessage(Messages.format(PaseMessages.proof()));
    if (r.empty()) player.sendSystemMessage(Messages.format(PaseMessages.rewardsNone()));
  }

  // ---------------------------------------------------------------- Tebex premium

  enum Status { GIVEN, DUPLICATE, BAD_SEASON, BAD_TRANSACTION, FAILED }

  record PremiumResult(Status status, String detail) {}

  /** The Tebex command. The transaction is on disk as PENDING before the premium is given and APPLIED after; a repeated one is ignored. */
  static PremiumResult premiumGive(UUID id, String season, String transaction) {
    var seasons = PaseModule.seasons;
    boolean known = season.equals(seasons.current().id()) || (seasons.previous() != null && season.equals(seasons.previous().id()));
    if (!known) return new PremiumResult(Status.BAD_SEASON, "");
    if (!PaseTransactions.validId(transaction)) return new PremiumResult(Status.BAD_TRANSACTION, "");
    var tx = transactions();
    try {
      tx.load();
      var seen = tx.get(transaction);
      if (seen != null && seen.state() == PaseTransactions.State.APPLIED) {
        PaseModule.LOG.warn("[ZIAN-AUDIT] tebex transacción repetida ignorada: {} (ya usada para {} en {})", transaction, seen.uuid(), seen.season());
        return new PremiumResult(Status.DUPLICATE, "");
      }
      if (seen != null && (!seen.uuid().equals(id.toString()) || !seen.season().equals(season))) {
        PaseModule.LOG.warn("[ZIAN-AUDIT] tebex transacción {} pendiente de {} en {} llegó con otros datos ({} {}): se ignora", transaction, seen.uuid(), seen.season(), id, season);
        return new PremiumResult(Status.DUPLICATE, "");
      }
      if (seen == null) tx.begin(transaction, id, season, PaseModule.CLOCK.millis());
      var player = PaseModule.server.getPlayerList().getPlayer(id);
      PlayerPase p = PaseModule.store.get(id);
      try {
        if (p.locked) throw new IOException("el archivo del pase del jugador está bloqueado");
        PaseModule.engine.setPremium(id, season, true);
        PaseModule.store.saveNow(p);
        tx.applied(transaction);
        sync(p);
        PaseModule.store.saveNow(p);
        if (player != null) {
          player.sendSystemMessage(Messages.format(PaseMessages.premiumOn(season)));
          tellPending(player, p);
        }
      } finally {
        if (player == null) PaseModule.store.release(id);
      }
      PaseModule.LOG.info("[ZIAN-AUDIT] tebex premium {} dado a {} (transacción {})", season, id, transaction);
      return new PremiumResult(Status.GIVEN, "");
    } catch (IOException | RuntimeException failure) {
      PaseModule.LOG.error("Premium de la transacción {} sin completar (se puede repetir el mismo comando): {}", transaction, failure.toString());
      return new PremiumResult(Status.FAILED, String.valueOf(failure.getMessage()));
    }
  }

  /** Refund: takes the premium away and drops the paid prizes not delivered yet. Returns the number dropped, or -1 if it failed. */
  static int premiumRemove(UUID id, String season) {
    var player = PaseModule.server.getPlayerList().getPlayer(id);
    PlayerPase p = PaseModule.store.get(id);
    try {
      if (p.locked) return -1;
      PaseModule.engine.setPremium(id, season, false);
      int dropped = PrizePlanner.revokePremium(p, season);
      PaseModule.store.saveNow(p);
      PaseModule.LOG.info("[ZIAN-AUDIT] premium {} retirado a {} ({} premios sin entregar anulados)", season, id, dropped);
      return dropped;
    } catch (IOException | RuntimeException failure) {
      PaseModule.LOG.error("No se pudo retirar el premium de {} a {}", season, id, failure);
      return -1;
    } finally {
      if (player == null) PaseModule.store.release(id);
    }
  }
}
