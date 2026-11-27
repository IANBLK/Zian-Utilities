package com.zianblk.zianutilities.neoforge.pase;

import com.zianblk.zianutilities.core.pase.AntiFarm;
import com.zianblk.zianutilities.core.pase.GrantResult;
import com.zianblk.zianutilities.core.pase.LevelEvent;
import com.zianblk.zianutilities.core.pase.MissionType;
import com.zianblk.zianutilities.core.pase.PaseEngine;
import com.zianblk.zianutilities.neoforge.Messages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Where XP comes from. Each handler is small and calls the engine; the rules that can be tested without Minecraft live
 * in core (AntiFarm, PlacedBlocks, AfkDetector, the engine). Creative and spectator players, fake players (machines) and
 * a disabled module never count. Anything unclear counts as not farmed-for: it simply gives nothing.
 */
public final class PaseSources {
  /** Carried in the mob NBT: set at spawn only for mobs that appeared naturally. No mark, no XP. */
  static final String NATURAL_TAG = "zianutilities_pase_natural";

  private PaseSources() {}

  static void install() {
    var bus = NeoForge.EVENT_BUS;
    bus.addListener((BlockEvent.EntityPlaceEvent e) -> { if (e.getLevel() instanceof ServerLevel l) onPlaced(l, e.getPos(), e.getPlacedBlock()); });
    bus.addListener((BlockEvent.FluidPlaceBlockEvent e) -> { if (e.getLevel() instanceof ServerLevel l) onPlaced(l, e.getPos(), e.getNewState()); });
    // Lowest priority and not cancelled: a break that another mod cancels must neither pay nor clear the mark.
    bus.addListener(net.neoforged.bus.api.EventPriority.LOWEST, (BlockEvent.BreakEvent e) -> {
      if (!e.isCanceled() && e.getPlayer() instanceof ServerPlayer p && !(p instanceof FakePlayer) && e.getLevel() instanceof ServerLevel l) onBreak(p, l, e.getPos(), e.getState());
    });
    bus.addListener((FinalizeSpawnEvent e) -> onSpawn(e.getEntity(), e.getSpawnType().name()));
    bus.addListener((LivingDeathEvent e) -> {
      if (e.getSource().getEntity() instanceof ServerPlayer p && !(p instanceof FakePlayer)) onKill(p, e.getEntity());
    });
    bus.addListener((BabyEntitySpawnEvent e) -> {
      if (!e.isCanceled() && e.getChild() != null && e.getCausedByPlayer() instanceof ServerPlayer p && !(p instanceof FakePlayer)) mission(p, MissionType.BREED_ANIMALS, 1, null);
    });
    bus.addListener((TradeWithVillagerEvent e) -> {
      if (e.getEntity() instanceof ServerPlayer p && !(p instanceof FakePlayer)) onTrade(p, e.getAbstractVillager());
    });
    bus.addListener((ItemFishedEvent e) -> {
      if (!e.isCanceled() && e.getEntity() instanceof ServerPlayer p && !(p instanceof FakePlayer)) onFished(p);
    });
  }

  /** The player can earn pass XP at all right now. */
  static boolean eligible(ServerPlayer p) {
    return PaseModule.active() && !p.isCreative() && !p.isSpectator();
  }

  // ---------------------------------------------------------------- blocks

  /** Natural blocks worth counting for the mining mission: stone, ores, logs, dirt, sand, gravel, clay. */
  static boolean naturalBlock(BlockState s) {
    return s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.BASE_STONE_NETHER) || s.is(BlockTags.LOGS) || s.is(Tags.Blocks.ORES)
        || s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY);
  }

  /** Only fully grown crops count. */
  static boolean matureCrop(BlockState s) {
    if (s.getBlock() instanceof CropBlock c) return c.isMaxAge(s);
    if (s.getBlock() instanceof NetherWartBlock) return s.getValue(NetherWartBlock.AGE) >= 3;
    return s.is(Blocks.MELON) || s.is(Blocks.PUMPKIN);
  }

  /** Anything put in the world by a player, a dispenser or a fluid, that could be broken again for XP, is remembered. */
  static void onPlaced(ServerLevel level, BlockPos pos, BlockState state) {
    if (!PaseModule.active() || !(naturalBlock(state) || matureCrop(state))) return;
    PaseBlockMarks.of(level).mark(pos);
  }

  /** A block is about to break: a block somebody placed never pays, whoever breaks it. */
  static void onBreak(ServerPlayer p, ServerLevel level, BlockPos pos, BlockState state) {
    if (!PaseModule.active()) return;
    var marks = PaseBlockMarks.of(level);
    boolean placed = marks.consume(pos);
    if (placed || marks.saturated(pos) || !eligible(p)) return;
    if (matureCrop(state)) mission(p, MissionType.HARVEST_CROPS, 1, null);
    else if (naturalBlock(state)) mission(p, MissionType.BREAK_BLOCKS, 1, null);
  }

  // ---------------------------------------------------------------- mobs

  /** Marks hostile mobs that appeared naturally (whitelist of spawn types); the mark travels with the mob in its NBT. */
  static void onSpawn(LivingEntity entity, String spawnType) {
    if (entity instanceof Enemy && AntiFarm.spawnTypeCounts(spawnType)) entity.getPersistentData().putBoolean(NATURAL_TAG, true);
  }

  static void onKill(ServerPlayer p, LivingEntity victim) {
    if (!eligible(p) || !(victim instanceof Enemy)) return;
    boolean leashed = victim instanceof Mob m && m.isLeashed();
    if (AntiFarm.mobCounts(victim.getPersistentData().getBoolean(NATURAL_TAG), victim.hasCustomName(), leashed, true)) mission(p, MissionType.KILL_MOBS, 1, null);
  }

  // ---------------------------------------------------------------- trade, fish, explore

  static void onTrade(ServerPlayer p, net.minecraft.world.entity.npc.AbstractVillager villager) {
    if (!eligible(p)) return;
    int level = villager instanceof Villager v ? v.getVillagerData().getLevel() : 0;
    mission(p, MissionType.TRADE, 1, villager.getUUID() + ":" + level);
  }

  /** A catch counts only if the player has moved or turned in the last afk.seconds: no XP for standing still. */
  static void onFished(ServerPlayer p) {
    if (!eligible(p)) return;
    if (PaseModule.AFK.afk(p.getUUID(), PaseModule.ticks * 50, PaseModule.config.afkSeconds() * 1000L)) return;
    mission(p, MissionType.FISH, 1, null);
  }

  /** Every few seconds: the biome and structures each player stands in, while the explore mission is open. */
  static void explore() {
    for (ServerPlayer p : PaseModule.server.getPlayerList().getPlayers()) {
      if (p instanceof FakePlayer || !eligible(p) || !PaseModule.engine.missionOpen(p.getUUID(), MissionType.EXPLORE)) continue;
      ServerLevel level = p.serverLevel();
      BlockPos pos = p.blockPosition();
      level.getBiome(pos).unwrapKey().ifPresent(k -> exploreKey(p, "biome:" + k.location()));
      var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
      for (var structure : level.structureManager().getAllStructuresAt(pos).keySet()) {
        var id = registry.getKey(structure);
        if (id != null) exploreKey(p, "structure:" + id);
      }
    }
  }

  static void exploreKey(ServerPlayer p, String key) {
    var r = PaseModule.engine.explore(p.getUUID(), key);
    if (r != null) done(p, r);
  }

  // ---------------------------------------------------------------- bosses and raids

  /** XP of a boss or raid source: its own XP value and cap from the config. Returns what was applied. */
  static GrantResult award(ServerPlayer p, String sourceName) {
    if (!PaseModule.active()) return GrantResult.NOTHING;
    var source = PaseModule.config.source(sourceName);
    if (source == null || !source.enabled() || source.xp() <= 0) return GrantResult.NOTHING;
    GrantResult g = PaseModule.engine.grant(p.getUUID(), sourceName, source.xp());
    notify(p, g);
    return g;
  }

  // ---------------------------------------------------------------- shared

  static void mission(ServerPlayer p, MissionType type, int amount, String key) {
    var r = PaseModule.engine.progress(p.getUUID(), type, amount, key);
    if (r != null && r.completed()) done(p, r);
  }

  private static void done(ServerPlayer p, PaseEngine.MissionResult r) {
    if (r.completed()) p.sendSystemMessage(Messages.format(PaseMessages.missionDone(r.type(), r.target(), r.grant().applied(), lang(p))));
    if (r.grant() != null) notify(p, r.grant());
  }

  /** Tells the player about the levels just reached (one line for the highest normal level, one for the highest extra). */
  static void notify(ServerPlayer p, GrantResult g) {
    LevelEvent normal = null, extra = null;
    for (LevelEvent e : g.levels()) {
      if (e.extra()) extra = e;
      else normal = e;
    }
    if (normal != null) p.sendSystemMessage(Messages.format(PaseMessages.levelUp(normal.level())));
    if (extra != null) p.sendSystemMessage(Messages.format(PaseMessages.extraLevelUp(extra.level())));
    for (String cap : g.caps()) p.sendSystemMessage(Messages.format(PaseMessages.capReached(cap, PaseModule.config.rules().resetTime().toString())));
  }

  /** The client language, for number formatting. */
  static String lang(ServerPlayer p) {
    return p.clientInformation().language();
  }
}
