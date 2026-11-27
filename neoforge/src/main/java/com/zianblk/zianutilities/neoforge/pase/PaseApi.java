package com.zianblk.zianutilities.neoforge.pase;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * What other code calls to give pass XP. Safe to call at any time: with the pass off, or a player offline, it does nothing,
 * and it never throws. No other mod is a dependency: Zian-Manager or Zian-Mobs call {@link #bossDefeated} (directly or by
 * reflection) when a dungeon boss falls, and without them the hook simply never fires.
 */
public final class PaseApi {
  private static final java.util.Map<String, Long> LAST_WIN = new java.util.concurrent.ConcurrentHashMap<>();

  private PaseApi() {}

  /** A dungeon boss was defeated by this player. Pays the "boss" source, within its daily cap. */
  public static void bossDefeated(ServerPlayer player) {
    guard(player, PaseConfig.BOSS);
  }

  /** The player won a raid (Raid Dens battle or Utilities duel); {@code weekly} is the weekly custom raid. Pays "raid" or "weeklyRaid". */
  public static void raidWon(UUID player, boolean weekly) {
    if (!PaseModule.active() || PaseModule.server == null) return;
    // A duel closing may also make Raid Dens report a battle end for the same win: one win pays once.
    Long last = LAST_WIN.put(player + (weekly ? ":w" : ":r"), PaseModule.ticks);
    if (last != null && PaseModule.ticks - last < 200) return;
    guard(PaseModule.server.getPlayerList().getPlayer(player), weekly ? PaseConfig.WEEKLY_RAID : PaseConfig.RAID);
  }

  private static void guard(ServerPlayer player, String source) {
    if (player == null || !PaseSources.eligible(player)) return;
    try {
      PaseSources.award(player, source);
    } catch (RuntimeException | LinkageError failure) {
      PaseModule.LOG.error("El XP del pase de la fuente {} falló; no afecta al juego", source, failure);
    }
  }
}
