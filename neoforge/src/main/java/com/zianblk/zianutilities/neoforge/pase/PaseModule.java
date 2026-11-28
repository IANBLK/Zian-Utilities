package com.zianblk.zianutilities.neoforge.pase;

import com.zianblk.zianutilities.core.config.ConfigHistory;
import com.zianblk.zianutilities.core.pase.AfkDetector;
import com.zianblk.zianutilities.core.pase.OffsetClock;
import com.zianblk.zianutilities.core.pase.PaseEngine;
import com.zianblk.zianutilities.core.pase.Season;
import com.zianblk.zianutilities.core.pase.SeasonBook;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The battle pass: seasons, XP, daily caps and the vanilla XP sources. Off by default (enabled=false in
 * config/zianutilities-pase.json) and fail-closed: a bad config or unreadable seasons file leaves it off and touches nothing.
 */
public final class PaseModule {
  static final Logger LOG = LoggerFactory.getLogger("ZianUtilities/Pase");
  /** System time plus an offset only the local smoke fixture moves. */
  public static final OffsetClock CLOCK = new OffsetClock();
  static final AfkDetector AFK = new AfkDetector();

  static MinecraftServer server;
  static PaseConfig config;
  static PaseEngine engine;
  static PaseStore store;
  static SeasonBook seasons;
  static String problem = "not started";
  static long ticks;
  private static boolean installed;
  private static boolean smokeChecked;
  private static String smokeClass;

  private PaseModule() {}

  public static Path configPath() { return FMLPaths.CONFIGDIR.get().resolve("zianutilities-pase.json"); }

  static Path dataDir() { return server.getWorldPath(LevelResource.ROOT).resolve("data/zianutilities/pase_v1"); }

  static ConfigHistory history() {
    Path dir = dataDir();
    return new ConfigHistory(configPath(), dir.resolve("config-backups"), dir.resolve("changes.jsonl"), 10, Clock.systemUTC());
  }

  /** True when the config says enabled and everything loaded. */
  public static boolean active() { return server != null && config != null && config.enabled() && engine != null; }

  public static void install() {
    if (installed) return;
    installed = true;
    NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> start(e.getServer()));
    NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> stop());
    NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> { if (server != null) tick(); });
    NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> PaseCommands.register(e.getDispatcher()));
    NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
      if (e.getEntity() instanceof ServerPlayer player) PaseRewards.onLogin(player);
    });
    NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
      AFK.forget(e.getEntity().getUUID());
      PaseDelivery.forget(e.getEntity().getUUID());
      if (store != null) store.release(e.getEntity().getUUID());
    });
    PaseSources.install();
  }

  static void start(MinecraftServer s) {
    server = s;
    config = null;
    engine = null;
    store = null;
    seasons = null;
    ticks = 0;
    PaseRewards.reset();
    try {
      config = PaseConfig.load(configPath());
      problem = config.enabled() ? "" : "disabled in config";
      if (config.enabled()) {
        build();
        String rewards = PaseRewards.problemText();
        if (rewards != null) LOG.error("Premios del pase cerrados (los niveles se ganan y los premios quedan pendientes): {}", rewards);
        PaseRewards.reportPendingTransactions();
      }
      LOG.info("Pase listo: enabled={}", config.enabled());
    } catch (Exception | LinkageError failure) {
      config = null;
      engine = null;
      problem = String.valueOf(failure.getMessage());
      LOG.error("Pase no disponible (queda apagado y no toca nada): {}", failure.toString());
    }
  }

  /** Opens the data folder and builds the engine for the current config. Throws if the seasons file is unreadable. */
  private static void build() throws java.io.IOException {
    if (store == null) store = new PaseStore(dataDir(), LOG);
    if (seasons == null) {
      seasons = store.loadSeasons();
      if (seasons == null) {
        var first = config.firstStart() != null ? config.firstStart() : Season.alignedStart(CLOCK.instant(), config.rules().resetTime(), config.rules().zone());
        seasons = new SeasonBook(Season.ofMonths("temporada-1", first, config.rules().seasonMonths(), config.rules().zone()), null);
        store.saveSeasons(seasons);
      }
    }
    engine = new PaseEngine(config.rules(), seasons, store, CLOCK);
    engine.warn = LOG::warn;
  }

  static void stop() {
    try {
      saveAll();
    } catch (Exception failure) {
      LOG.error("No se pudo guardar el pase al parar", failure);
    }
    engine = null;
    store = null;
    seasons = null;
    server = null;
    PaseRewards.reset();
  }

  /** Writes every pending change (players and seasons). */
  static void saveAll() throws java.io.IOException {
    if (store == null) return;
    store.flush();
    if (seasons != null && seasons.dirty) store.saveSeasons(seasons);
  }

  private static void tick() {
    ticks++;
    if (!active()) return;
    if (ticks % 20 == 0) {
      for (ServerPlayer p : server.getPlayerList().getPlayers())
        AFK.sample(p.getUUID(), p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot(), ticks * 50);
    }
    if (ticks % 100 == 0) PaseSources.explore();
    if (ticks % 40 == 0) {
      try {
        PaseDelivery.proveAll();
      } catch (RuntimeException | LinkageError failure) {
        LOG.error("Comprobación de entregas del pase fallida", failure);
      }
    }
    if (ticks % 200 == 0) {
      try {
        engine.rollSeason();
        saveAll();
      } catch (Exception failure) {
        LOG.error("Guardado periódico del pase fallido", failure);
      }
    }
    smokeTick();
  }

  /** Local-only fixture: present in the server copy built with -PkeepPaseSmoke, absent from the release jar. */
  private static void smokeTick() {
    if (!smokeChecked) {
      smokeChecked = true;
      if ("true".equals(System.getenv("ZIANUTILITIES_PASE_SMOKE"))) smokeClass = "com.zianblk.zianutilities.neoforge.pase.PaseSmoke";
    }
    if (smokeClass == null) return;
    try {
      Class.forName(smokeClass).getDeclaredMethod("tick").invoke(null);
    } catch (ReflectiveOperationException unavailable) {
      smokeClass = null;
      LOG.error("Pase smoke fixture unavailable", unavailable);
    }
  }

  /** Reads the config again and swaps the engine rules; on failure the previous config is kept and the error returned. */
  static String reload() {
    try {
      PaseConfig next = PaseConfig.load(configPath());
      if (server == null) return "servidor no arrancado";
      var rollback = config;
      var oldEngine = engine;
      config = next;
      problem = next.enabled() ? "" : "disabled in config";
      try {
        if (next.enabled()) build();
        else engine = null;
      } catch (Exception failure) {
        config = rollback;
        engine = oldEngine;
        return String.valueOf(failure.getMessage());
      }
      try {
        history().backup();
      } catch (Exception failure) {
        LOG.warn("No se pudo copiar la configuración del pase al historial", failure);
      }
      String rewards = next.enabled() ? PaseRewards.reload() : null;
      if (rewards != null) LOG.error("Premios del pase cerrados tras recargar: {}", rewards);
      return null;
    } catch (Exception failure) {
      return String.valueOf(failure.getMessage());
    }
  }

  static void audit(net.minecraft.commands.CommandSourceStack s, String what) {
    LOG.info("[ZIAN-AUDIT] pase admin {} by {}", what, s.getTextName());
    try {
      ServerPlayer p = s.getPlayer();
      history().record(p == null ? new UUID(0, 0) : p.getUUID(), s.getTextName(), "pase", what);
    } catch (Exception failure) {
      LOG.warn("Change log write failed", failure);
    }
  }
}
