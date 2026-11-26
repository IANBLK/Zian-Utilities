package com.zianblk.zianutilities.neoforge.raids;

import com.zianblk.zianutilities.core.raids.DuelLog;
import com.zianblk.zianutilities.core.raids.RaidCooldowns;
import com.zianblk.zianutilities.core.config.ConfigHistory;
import java.nio.file.Path;
import java.time.Clock;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Raid duels for Cobblemon Raid Dens 0.14.3. Nothing in this package that touches Raid Dens is loaded unless the
 * mod is present, so without it Utilities behaves exactly as before. Off by default; fails closed on a bad config.
 */
public final class RaidsModule {
  static final Logger LOG = LoggerFactory.getLogger("ZianUtilities/Raids");
  static final String RAID_CHALLENGE = "com.necro.raid.dens.common.network.packets.RaidChallengePacket";
  static MinecraftServer server;
  static RaidsConfig config;
  static RaidCooldowns cooldowns;
  static DuelLog log;
  static String problem = "not started";
  private static boolean subscribed;

  private RaidsModule() {}

  public static Path configPath() { return FMLPaths.CONFIGDIR.get().resolve("zianutilities-raids.json"); }

  static Path dataDir() { return server.getWorldPath(LevelResource.ROOT).resolve("data/zianutilities/raids_v1"); }

  static ConfigHistory history() {
    Path dir = dataDir();
    return new ConfigHistory(configPath(), dir.resolve("config-backups"), dir.resolve("changes.jsonl"), 10, Clock.systemUTC());
  }

  public static boolean raidDensLoaded() { return ModList.get().isLoaded(RaidsMixinPlugin.RAID_DENS); }

  /** True only when Raid Dens is present, the config loaded, it says enabled, and the journals are healthy. */
  public static boolean active() {
    return server != null && config != null && config.enabled() && cooldowns != null && log != null && !log.locked();
  }

  public static void install(IEventBus modBus) {
    if (!raidDensLoaded()) return;
    modBus.addListener((FMLCommonSetupEvent e) -> {
      try {
        RaidDuelHookCheck.verifyOrThrow(Class.forName(RAID_CHALLENGE));
      } catch (ClassNotFoundException missing) {
        throw new IllegalStateException("Zian Utilities (raids): Raid Dens esta cargado pero falta " + RAID_CHALLENGE, missing);
      }
    });
    NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> start(e.getServer()));
    NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> stop());
    NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> { if (server != null) { RaidDuelRuntime.tick(); RaidPendingRewards.tick(); smokeTick(); } });
    NeoForge.EVENT_BUS.addListener(RaidsModule::registerCommands);
    RaidDuelRuntime.install();
  }

  private static boolean smokeChecked, smokeOn;

  /** Local-only fixture: present in the server copy built with -PkeepRaidSmoke, absent from the release jar. */
  private static void smokeTick() {
    if (!smokeChecked) { smokeChecked = true; smokeOn = "true".equals(System.getenv("ZIANUTILITIES_RAIDS_SMOKE")); }
    if (!smokeOn) return;
    try {
      Class.forName("com.zianblk.zianutilities.neoforge.raids.RaidSmoke").getDeclaredMethod("tick").invoke(null);
    } catch (ReflectiveOperationException unavailable) {
      smokeOn = false;
      LOG.error("Raid smoke fixture unavailable", unavailable);
    }
  }

  private static void registerCommands(RegisterCommandsEvent e) { RaidCommands.register(e.getDispatcher()); }

  static void start(MinecraftServer s) {
    server = s;
    config = null;
    cooldowns = null;
    log = null;
    try {
      config = RaidsConfig.load(configPath());
      problem = config.enabled() ? "" : "disabled in config";
      Path dir = dataDir();
      cooldowns = RaidCooldowns.load(dir.resolve("cooldowns.properties"));
      log = new DuelLog(dir.resolve("duels.wal"));
      RaidDuelRuntime.recover();
      RaidPendingRewards.load();
      if (!subscribed) { RaidDensEvents.install(); subscribed = true; }
      LOG.info("Raids ready: enabled={} duelFromTier={}", config.enabled(), config.duelFromTier());
    } catch (Exception | LinkageError failure) {
      config = null;
      problem = String.valueOf(failure.getMessage());
      LOG.error("Raids unavailable (module stays off, Raid Dens runs untouched): {}", failure.toString(), failure);
    }
  }

  static void stop() {
    try {
      RaidDuelRuntime.abortAll();
      if (log != null) log.close();
    } catch (Exception failure) {
      LOG.error("Raids shutdown failed", failure);
    }
    log = null;
    server = null;
  }

  /** Reads the config again; on failure the previous one is kept and the error is returned. */
  static String reload() {
    try {
      var next = RaidsConfig.load(configPath());
      config = next;
      problem = next.enabled() ? "" : "disabled in config";
      return null;
    } catch (Exception failure) {
      return failure.getMessage();
    }
  }
}
