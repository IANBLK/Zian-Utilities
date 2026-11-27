package com.zianblk.zianutilities.neoforge.raids;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.necro.raid.dens.common.data.raid.RaidBoss;
import com.necro.raid.dens.common.events.RaidEvents;
import com.necro.raid.dens.common.raids.RaidSession;
import com.necro.raid.dens.common.raids.helpers.RaidHelper;
import com.necro.raid.dens.common.util.IRaidAccessor;
import com.zianblk.zianutilities.core.config.ConfigHistory;
import com.zianblk.zianutilities.core.raids.DuelParams;
import com.zianblk.zianutilities.core.raids.SafeSteps;
import com.zianblk.zianutilities.core.raids.weekly.WeeklyQueue;
import com.zianblk.zianutilities.core.raids.weekly.WeeklySchedule;
import com.zianblk.zianutilities.neoforge.Messages;
import com.zianblk.zianutilities.neoforge.Tr;
import com.zianblk.zianutilities.neoforge.UtilitiesPermissions;
import com.zianblk.zianutilities.neoforge.breeding.LuckPermsLookup;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLPaths;

/**
 * The weekly raid: one custom boss standing on a pedestal at a fixed spot, the same all week, rotated by the calendar.
 * It plays one player at a time (the queue), with weekly limits of attempts and rewards. DUEL mode runs the R1 duel;
 * BATTLE mode lets Raid Dens run its own battle. Every rule of the R1 holds: nobody dies, the inventory is never
 * touched, rewards go through RaidRewardGate and Raid Dens owns the pending reward.
 */
final class WeeklyRuntime {
  static final String TAG = "zian_weekly_boss";
  static final String JOURNAL_PREFIX = "weekly:";
  private static final int RETIRE_DELAY_TICKS = 200;
  private static final int BATTLE_START_TICKS = 600;
  private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z", Locale.ROOT);

  enum Decision { HANDLED, BATTLE, DUEL }

  private static final class Live {
    final RaidSession session;
    final RaidBoss raidBoss;
    final UUID raidId;
    final long weekNumber;
    final int spawnedTick;
    PokemonEntity boss;
    int retireAt = -1;

    Live(RaidSession session, RaidBoss raidBoss, UUID raidId, long weekNumber, int spawnedTick) {
      this.session = session;
      this.raidBoss = raidBoss;
      this.raidId = raidId;
      this.weekNumber = weekNumber;
      this.spawnedTick = spawnedTick;
    }

    boolean ready() { return boss != null; }
  }

  /** A BATTLE-mode attempt: the player holds the raid from the challenge until Raid Dens ends it. */
  private static final class BattleTurn {
    final UUID player;
    final int sinceTick;
    boolean started;
    int idleTicks;

    BattleTurn(UUID player, int sinceTick) {
      this.player = player;
      this.sinceTick = sinceTick;
    }
  }

  static Clock clock = Clock.systemUTC();
  static WeeklyConfig config;
  static WeeklyState state;
  static WeeklyConfig.Raid active;
  static String problem = "not started";
  static WeeklyQueue queue = new WeeklyQueue(30_000);
  private static Live live;
  private static BattleTurn battle;
  private static int ticks, spawnNotBefore;
  private static boolean stateBlocked, subscribed;

  private WeeklyRuntime() {}

  // ---------------------------------------------------------------- paths and state

  static Path configPath() { return FMLPaths.CONFIGDIR.get().resolve("zianutilities-weekly-raids.json"); }

  static Path statePath() { return RaidsModule.dataDir().resolve("weekly-state.json"); }

  static ConfigHistory history() {
    Path dir = RaidsModule.dataDir();
    return new ConfigHistory(configPath(), dir.resolve("weekly-config-backups"), dir.resolve("changes.jsonl"), 10, Clock.systemUTC());
  }

  static boolean ready() {
    return RaidsModule.infraReady() && config != null && config.enabled() && state != null && active != null && !stateBlocked;
  }

  static boolean owns(UUID raidId) { return live != null && live.raidId.equals(raidId); }

  static int tier() { return active.tier(); }

  static DuelParams duelParams() { return active.duel(); }

  static long now() { return clock.millis(); }

  // ---------------------------------------------------------------- start and stop

  /** Fails closed: any surprise while starting leaves the weekly raid off with the reason in the log. */
  static void start() {
    try {
      start0();
    } catch (Exception | LinkageError failure) {
      stateBlocked = true;
      problem = String.valueOf(failure.getMessage());
      RaidsModule.LOG.error("Weekly raid paused: it could not start", failure);
    }
  }

  private static void start0() {
    reset();
    try {
      config = WeeklyConfig.load(configPath());
      problem = config.enabled() ? "" : "disabled in config";
      if (config.enabled()) validateBosses(config);
    } catch (Exception | LinkageError failure) {
      config = null;
      problem = String.valueOf(failure.getMessage());
      RaidsModule.LOG.error("Weekly raid unavailable (stays off): {}", failure.toString());
      return;
    }
    if (!subscribed) { subscribe(); subscribed = true; }
    if (!config.enabled()) return;
    try {
      state = WeeklyState.load(statePath());
    } catch (IOException unreadable) {
      stateBlocked = true;
      problem = "weekly-state.json: " + unreadable.getMessage();
      RaidsModule.LOG.error("Weekly raid paused: the state file is corrupt or from a newer version and was not touched", unreadable);
      return;
    }
    queue = new WeeklyQueue(config.turnSeconds() * 1000L);
    RaidsModule.LOG.warn("[ZIAN-AUDIT] weekly raid queue starts empty: the waiting line is kept in memory only");
    try {
      syncWeek();
      registerBosses();
    } catch (Exception | LinkageError failure) {
      stateBlocked = true;
      problem = String.valueOf(failure.getMessage());
      RaidsModule.LOG.error("Weekly raid paused: could not settle the active week", failure);
    }
  }

  private static void reset() {
    config = null;
    state = null;
    active = null;
    live = null;
    battle = null;
    stateBlocked = false;
    spawnNotBefore = 0;
    queue = new WeeklyQueue(30_000);
    problem = "not started";
  }

  static void stop() {
    try {
      if (live != null) retire();
    } catch (Throwable failure) {
      RaidsModule.LOG.error("Weekly raid shutdown failed", failure);
    }
    config = null;
    state = null;
    active = null;
  }

  private static void validateBosses(WeeklyConfig cfg) {
    for (WeeklyConfig.Raid raid : cfg.raids()) WeeklyBossFactory.build(raid);
  }

  // ---------------------------------------------------------------- the calendar

  /** Brings the stored week up to date with the clock: first week, the single due rotation, or just restores the snapshot. */
  private static void syncWeek() throws IOException {
    Instant at = clock.instant();
    if (state == null) {
      var week = WeeklySchedule.first(config.rule(), config.ids(), at, new Random().nextLong());
      adopt(week, config.raid(week.activeId()), "first week");
    } else if (WeeklySchedule.due(state.week, at)) {
      var week = WeeklySchedule.rotateAuto(config.rule(), state.week, config.ids(), at);
      adopt(week, config.raid(week.activeId()), "scheduled rotation");
    } else if (active == null) {
      active = WeeklyConfig.parseRaid(state.snapshot.deepCopy(), config.attemptsPerWeek(), config.rewardsPerWeek());
      RaidsModule.LOG.info("[ZIAN-AUDIT] weekly raid {} continues (week {}, until {})", active.id(), state.week.weekNumber(), state.week.endsAt());
    }
  }

  private static void adopt(WeeklySchedule.Week week, WeeklyConfig.Raid raid, String why) throws IOException {
    var next = new WeeklyState(week, raid.raw().deepCopy());
    if (state != null) {
      next.previous.add(new WeeklyState.Past(state.week.weekNumber(), state.snapshot));
      next.previous.addAll(state.previous);
      while (next.previous.size() > WeeklyState.KEEP_PAST) next.previous.remove(next.previous.size() - 1);
    }
    next.save(statePath());
    state = next;
    active = raid;
    if (live != null && live.retireAt < 0) live.retireAt = ticks;
    if (config != null && config.announce() && !"START".equals(week.reason())) announceRaid(raid);
    RaidsModule.LOG.info("[ZIAN-AUDIT] weekly raid is now {} (week {}, {}, until {}) by {}", raid.id(), week.weekNumber(), week.reason(), week.endsAt(), why);
  }

  /** Tells everybody online which raid is now on the pedestal. */
  private static void announceRaid(WeeklyConfig.Raid raid) {
    if (RaidsModule.server == null) return;
    for (ServerPlayer p : RaidsModule.server.getPlayerList().getPlayers()) p.sendSystemMessage(Messages.format(WeeklyMessages.newRaid(raid)));
  }

  /** Admin: the next raid of the list now. It ends at the next scheduled boundary and attempts start from zero. */
  static String forceRotate() {
    if (!ready()) return Tr.enc("zianutilities.raids.weekly.err.unavailable", "§eLa raid semanal no está activa (%s).", WeeklyMessages.problemText(problem));
    try {
      adopt(WeeklySchedule.rotateForced(config.rule(), state.week, config.ids(), clock.instant()), config.raid(nextId()), "admin rotation");
      return null;
    } catch (Exception failure) {
      return String.valueOf(failure.getMessage());
    }
  }

  private static String nextId() {
    var week = WeeklySchedule.rotateForced(config.rule(), state.week, config.ids(), clock.instant());
    return week.activeId();
  }

  /** Admin: pin one raid of the list. */
  static String fix(String id) {
    if (!ready()) return Tr.enc("zianutilities.raids.weekly.err.unavailable", "§eLa raid semanal no está activa (%s).", WeeklyMessages.problemText(problem));
    WeeklyConfig.Raid raid = config.raid(id);
    if (raid == null) return Tr.enc("zianutilities.raids.weekly.err.unknown_raid", "§cNo hay ninguna raid con el id %s en la configuración.", id);
    try {
      adopt(WeeklySchedule.fix(config.rule(), state.week, config.ids(), id, clock.instant()), raid, "admin fix");
      return null;
    } catch (Exception failure) {
      return String.valueOf(failure.getMessage());
    }
  }

  /** Admin: read the file again. The active week keeps its snapshot; only the next rotation and the entrance see the change. */
  static String reload() {
    try {
      WeeklyConfig next = WeeklyConfig.parse(com.zianblk.zianutilities.core.storage.StorageFiles.readString(configPath()));
      if (next.enabled()) validateBosses(next);
      var history = history();
      history.backup();
      boolean entranceMoved = config != null && !config.entrance().equals(next.entrance());
      config = next;
      problem = next.enabled() ? "" : "disabled in config";
      queue.setTurnMillis(next.turnSeconds() * 1000L);
      if (!next.enabled()) {
        if (live != null && !busy()) retire();
        return null;
      }
      if (state == null && !stateBlocked) state = WeeklyState.load(statePath());
      if (!stateBlocked) { syncWeek(); registerBosses(); }
      if (entranceMoved && live != null && live.retireAt < 0) live.retireAt = ticks;
      return null;
    } catch (Exception | LinkageError failure) {
      return String.valueOf(failure.getMessage());
    }
  }

  // ---------------------------------------------------------------- Raid Dens events

  private static void subscribe() {
    RaidEvents.RAID_BATTLE_START.subscribe(Priority.NORMAL, event -> {
      if (!WeeklyBossFactory.isWeekly(event.getRaidBoss()) || battle == null || !battle.player.equals(event.getPlayer().getUUID())) return;
      battle.started = true;
    });
    RaidEvents.RAID_BATTLE_END.subscribe(Priority.NORMAL, event -> {
      if (!WeeklyBossFactory.isWeekly(event.raidBoss()) || !event.isWin()) return;
      if (active == null || active.mode() != WeeklyConfig.Mode.BATTLE || battle == null || !battle.player.equals(event.player().getUUID())) return;
      countReward(event.player().getUUID());
    });
    RaidEvents.RAID_JOIN.subscribe(Priority.HIGHEST, event -> {
      if (!WeeklyBossFactory.isWeekly(event.getRaid())) return;
      if (event.getPlayer().getUUID().equals(queue.playing())) return;
      event.cancel();
    });
    RaidEvents.RAID_END.subscribe(Priority.NORMAL, event -> {
      if (!WeeklyBossFactory.isWeekly(event.raidBoss()) || battle == null) return;
      endBattle(true);
    });
  }

  // ---------------------------------------------------------------- the challenge

  /** The player pressed the boss. Decides: queue and refuse (HANDLED), let Raid Dens fight (BATTLE) or start the duel here (DUEL). */
  static Decision gate(ServerPlayer p, RaidSession session, PokemonEntity boss) {
    if (!ready()) return refuse(p, WeeklyMessages.unavailable(problem));
    boolean mine = queue.playing() != null && queue.playing().equals(p.getUUID());
    if (!mine && (live == null || live.retireAt >= 0 || session.isFinished() || session.getActive() == null))
      return refuse(p, Tr.c("zianutilities.raids.weekly.err.recovering", "§eEl jefe se está recuperando; vuelve a intentarlo en unos segundos."));
    if (!UtilitiesPermissions.allows(p, "raid.use", false))
      return refuse(p, Tr.c("zianutilities.raids.err.no_permission", "§cNo tienes permiso para los duelos de raid."));
    if (RaidDuelRuntime.inDuel(p.getUUID()))
      return refuse(p, Tr.c("zianutilities.raids.err.already_dueling", "§cYa estás en un duelo."));
    if (BattleRegistry.getBattleByParticipatingPlayer(p) != null)
      return refuse(p, Tr.c("zianutilities.raids.err.in_battle", "§cNo puedes iniciar un duelo durante una batalla."));
    if (p.distanceTo(boss) > 24)
      return refuse(p, Tr.c("zianutilities.raids.err.too_far", "§cEstás demasiado lejos del jefe."));
    if (RaidGenerationFilter.deniedWeekly(session.boss()))
      return refuse(p, Tr.c("zianutilities.raids.err.generation_denied", "§cEste jefe pertenece a una generación desactivada."));
    UUID id = p.getUUID();
    if (RaidHelper.REWARD_QUEUE.containsKey(id))
      return refuse(p, Tr.c("zianutilities.raids.weekly.err.pending_reward", "§eReclama primero tu recompensa pendiente."));
    if (!bypassesLimits(p)) {
      if (!state.counts.attemptsLeft(id, active.attemptsPerWeek()))
        return refuse(p, Tr.c("zianutilities.raids.weekly.err.attempts", "§cYa has usado tus %s intentos de esta semana. La raid cambia el %s.", active.attemptsPerWeek(), when(state.week.endsAt())));
      if (!state.counts.rewardsLeft(id, active.rewardsPerWeek()))
        return refuse(p, Tr.c("zianutilities.raids.weekly.err.rewards", "§cYa has conseguido el premio de esta semana. La raid cambia el %s.", when(state.week.endsAt())));
    }
    var result = queue.request(id, now());
    switch (result.entry()) {
      case QUEUED -> {
        p.sendSystemMessage(Messages.format(Tr.c("zianutilities.raids.weekly.msg.queued", "§eLa raid semanal está ocupada. Estás en la cola, posición %s. Te avisaremos cuando sea tu turno.", result.position())));
        RaidsModule.LOG.info("[ZIAN-AUDIT] weekly queue join player={} position={}", id, result.position());
        return Decision.HANDLED;
      }
      case ALREADY_PLAYING, GRANTED -> {
        if (active.mode() == WeeklyConfig.Mode.DUEL) return Decision.DUEL;
        if (battle == null || !battle.player.equals(id)) battle = new BattleTurn(id, ticks);
        return Decision.BATTLE;
      }
    }
    return Decision.HANDLED;
  }

  private static Decision refuse(ServerPlayer p, net.minecraft.network.chat.Component message) {
    RaidsModule.LOG.info("Weekly challenge refused for {}: {}", p.getGameProfile().getName(), message.getString());
    p.sendSystemMessage(Messages.format(message));
    return Decision.HANDLED;
  }

  private static boolean bypassesLimits(ServerPlayer p) {
    try {
      return LuckPermsLookup.lookup(p, "zianutilities.raid.bypass_limits") == LuckPermsLookup.Decision.TRUE;
    } catch (Exception | LinkageError unavailable) {
      return false;
    }
  }

  /** The duel could not start: the raid goes back to the line. */
  static void startFailed(ServerPlayer p) {
    announce(queue.release(p.getUUID(), now()));
  }

  /** A duel of the weekly raid is over. The attempt is spent unless the server fell or an admin cancelled it. */
  static void onDuelFinished(UUID player, boolean success, boolean refund) {
    if (!refund) spendAttempt(player);
    if (success) countReward(player);
    announce(queue.release(player, now()));
    if (live != null && live.retireAt < 0) live.retireAt = ticks + RETIRE_DELAY_TICKS;
  }

  private static void endBattle(boolean spend) {
    BattleTurn turn = battle;
    battle = null;
    if (turn == null) return;
    if (spend && turn.started) spendAttempt(turn.player);
    announce(queue.release(turn.player, now()));
    if (live != null && live.retireAt < 0) live.retireAt = ticks + RETIRE_DELAY_TICKS;
  }

  private static boolean currentWeek() { return live == null || state == null || live.weekNumber == state.week.weekNumber(); }

  private static void spendAttempt(UUID player) {
    if (state == null || !currentWeek()) return;
    state.counts.addAttempt(player);
    persist();
  }

  private static void countReward(UUID player) {
    if (state == null || !currentWeek()) return;
    state.counts.addReward(player);
    persist();
  }

  private static void persist() {
    try {
      state.save(statePath());
    } catch (IOException failure) {
      RaidsModule.LOG.error("Weekly state could not be written; the counts stay in memory only", failure);
    }
  }

  // ---------------------------------------------------------------- tick

  static void tick() {
    if (RaidsModule.server == null) return;
    ticks++;
    if (ticks % 20 != 0) return;
    SafeSteps.guard(WeeklyRuntime::tickSecond, failure -> RaidsModule.LOG.error("Weekly raid tick failed", failure));
  }

  private static void tickSecond() throws IOException {
    if (config == null || !config.enabled() || stateBlocked) {
      if (live != null && !busy()) retire();
      return;
    }
    if (state != null && WeeklySchedule.due(state.week, clock.instant())) syncWeek();
    registerBosses();
    boolean bossReady = live != null && live.ready() && live.retireAt < 0 && live.boss.isAlive() && !live.session.isFinished() && live.session.getActive() != null;
    announce(queue.tick(now(), id -> RaidsModule.server.getPlayerList().getPlayer(id) != null, bossReady));
    watchBattle();
    ensureBoss();
  }

  private static void watchBattle() {
    if (battle == null) return;
    ServerPlayer p = RaidsModule.server.getPlayerList().getPlayer(battle.player);
    boolean timedOut = !battle.started && ticks - battle.sinceTick > BATTLE_START_TICKS;
    boolean over = battle.started && live != null && (live.session.isFinished() || (live.session.getActive() != null && live.session.getActive().isFinished()));
    boolean inBattle = p != null && BattleRegistry.getBattleByParticipatingPlayer(p) != null;
    battle.idleTicks = battle.started && !inBattle ? battle.idleTicks + 20 : 0;
    boolean tooLong = ticks - battle.sinceTick > config.maxTurnMinutes() * 1200L;
    if (p == null && battle.started) endBattle(true);
    else if (timedOut || p == null) endBattle(false);
    else if (over || tooLong || battle.idleTicks >= 100) endBattle(battle.started);
  }

  private static boolean busy() {
    UUID playing = queue.playing();
    return battle != null || (playing != null && RaidDuelRuntime.inDuel(playing));
  }

  // ---------------------------------------------------------------- the pedestal

  private static ServerLevel level() {
    var key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(config.entrance().dimension()));
    return RaidsModule.server.getLevel(key);
  }

  private static void ensureBoss() {
    if (!ready()) { if (live != null && !busy()) retire(); return; }
    ServerLevel level = level();
    if (level == null) {
      if (ticks % 600 == 0) RaidsModule.LOG.error("Weekly raid: the dimension {} does not exist", config.entrance().dimension());
      return;
    }
    var e = config.entrance();
    BlockPos pos = BlockPos.containing(e.x(), e.y(), e.z());
    if (!level.isLoaded(pos)) return;
    if (live != null) {
      if (busy()) return;
      adoptBoss();
      if (live.retireAt >= 0 && ticks >= live.retireAt) retire();
      else if (!live.ready()) {
        if (ticks - live.spawnedTick > 600) { RaidsModule.LOG.error("Weekly raid boss never appeared; trying again later"); retire(); spawnNotBefore = ticks + 1200; }
        return;
      } else if (live.boss.isRemoved() || !live.boss.isAlive()) retire();
      else if (live.session.isFinished() && live.retireAt < 0) { live.retireAt = ticks + RETIRE_DELAY_TICKS; return; }
      else { sweepOrphans(level, pos); return; }
    }
    sweepOrphans(level, pos);
    if (ticks >= spawnNotBefore) spawn(level);
  }

  private static final java.util.Map<String, RaidBoss> BUILT = new java.util.HashMap<>();
  private static java.lang.reflect.Field rewardBossId;
  private static int lastRegistryError = -100_000;

  /** One RaidBoss per (raid, week, content), so the registry, the session and the rewards all see the same object. */
  private static RaidBoss bossFor(WeeklyConfig.Raid raid, long week) {
    if (BUILT.size() > 64) BUILT.clear();
    return BUILT.computeIfAbsent(raid.id() + "#" + week + "#" + raid.raw(), k -> WeeklyBossFactory.build(raid, week));
  }

  /** Boss ids of the rewards Raid Dens still holds for players. They are only stored by id and found again in the registry. */
  private static java.util.Set<ResourceLocation> pendingBossIds() throws ReflectiveOperationException {
    var ids = new java.util.HashSet<ResourceLocation>();
    for (var handler : RaidHelper.REWARD_QUEUE.values()) {
      ResourceLocation id = rewardBoss(handler);
      if (id != null) ids.add(id);
    }
    return ids;
  }

  private static ResourceLocation rewardBoss(Object handler) throws ReflectiveOperationException {
    if (rewardBossId == null) {
      rewardBossId = handler.getClass().getDeclaredField("raidBossId");
      rewardBossId.setAccessible(true);
    }
    return (ResourceLocation) rewardBossId.get(handler);
  }

  /** True when the pending reward of this player comes from a weekly boss. */
  static boolean rewardIsWeekly(UUID player) {
    try {
      var handler = RaidHelper.REWARD_QUEUE.get(player);
      ResourceLocation id = handler == null ? null : rewardBoss(handler);
      return id != null && WeeklyBossFactory.NAMESPACE.equals(id.getNamespace()) && id.getPath().startsWith(WeeklyBossFactory.PATH_PREFIX);
    } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
      return false;
    }
  }

  /**
   * Keeps every weekly boss known to the Raid Dens registry whether or not its chunk is loaded: the raids of the
   * config, the active snapshot and the snapshots of earlier weeks while a reward of theirs is still waiting.
   */
  private static void registerBosses() {
    if (config == null || !config.enabled() || stateBlocked) return;
    try {
      for (WeeklyConfig.Raid raid : config.raids()) WeeklyBossFactory.ensureRegistered(bossFor(raid, 0));
      if (active != null && state != null) WeeklyBossFactory.ensureRegistered(bossFor(active, state.week.weekNumber()));
      if (state != null && !state.previous.isEmpty() && !RaidHelper.REWARD_QUEUE.isEmpty()) {
        var pending = pendingBossIds();
        for (WeeklyState.Past past : state.previous) {
          var raid = WeeklyConfig.parseRaid(past.snapshot().deepCopy(), 1, 1);
          RaidBoss boss = bossFor(raid, past.weekNumber());
          if (pending.contains(boss.getId())) WeeklyBossFactory.ensureRegistered(boss);
        }
      }
    } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
      if (ticks - lastRegistryError >= 1200) {
        lastRegistryError = ticks;
        RaidsModule.LOG.error("Weekly bosses could not be kept in the Raid Dens registry (logged once a minute)", failure);
      }
    }
  }

  private static void adoptBoss() {
    if (live.ready() || live.session.getActive() == null || live.session.getActive().getBossEntity() == null) return;
    live.boss = live.session.getActive().getBossEntity();
    var e = config.entrance();
    live.boss.addTag(TAG);
    live.boss.moveTo(e.x(), e.y(), e.z(), e.yaw(), 0);
    RaidsModule.LOG.info("[ZIAN-AUDIT] weekly raid boss {} stands at {} {} {} (raid {})", active.id(), e.x(), e.y(), e.z(), live.raidId);
  }

  private static void spawn(ServerLevel level) {
    Live made = null;
    try {
      RaidBoss boss = bossFor(active, state.week.weekNumber());
      var e = config.entrance();
      UUID raidId = UUID.randomUUID();
      RaidSession session = new RaidSession(null, raidId, boss, new Vec3(e.x(), e.y(), e.z()), level);
      made = new Live(session, boss, raidId, state.week.weekNumber(), ticks);
      WeeklyBossFactory.ensureRegistered(boss);
      RaidHelper.ACTIVE_RAIDS.put(raidId, session);
      live = made;
      session.setFlags(true, true, true);
      session.start(RaidsModule.server);
      if (session.isFinished()) throw new IllegalStateException("Raid Dens closed the session at once");
    } catch (Exception | LinkageError failure) {
      RaidsModule.LOG.error("Weekly raid could not be placed; trying again in a minute", failure);
      if (live == made && made != null) retire();
      spawnNotBefore = ticks + 1200;
    }
  }

  /** Closes the session and removes its boss. One step failing never skips the next. */
  private static void retire() {
    Live gone = live;
    live = null;
    if (gone == null) return;
    var failures = SafeSteps.runAll(
        () -> { if (!gone.session.isFinished()) gone.session.stopRaid(false); else gone.session.closeRaid(RaidsModule.server); },
        () -> { if (gone.boss != null && !gone.boss.isRemoved()) gone.boss.discard(); },
        () -> RaidHelper.ACTIVE_RAIDS.remove(gone.raidId));
    failures.forEach(f -> RaidsModule.LOG.error("Weekly raid close step failed raid={}", gone.raidId, f));
  }

  /** A second boss on the pedestal is never allowed: anything of ours (or a raid boss without a live session) that is not the current one goes. */
  private static void sweepOrphans(ServerLevel level, BlockPos pos) {
    if (live != null && !live.ready()) return;
    for (PokemonEntity e : level.getEntitiesOfClass(PokemonEntity.class, new AABB(pos).inflate(24))) {
      if (live != null && live.boss != null && e.getUUID().equals(live.boss.getUUID())) continue;
      boolean tagged = e.getTags().contains(TAG);
      boolean headless = e instanceof IRaidAccessor a && a.crd_isRaidBoss() && !RaidHelper.ACTIVE_RAIDS.containsKey(a.crd_getRaidId());
      if (!tagged && !headless) continue;
      e.discard();
      RaidsModule.LOG.warn("[ZIAN-AUDIT] weekly raid: removed a leftover boss {} at the pedestal", e.getUUID());
    }
  }

  /** Admin: recreate the boss on the pedestal. */
  static String respawn() {
    if (!ready()) return Tr.enc("zianutilities.raids.weekly.err.unavailable", "§eLa raid semanal no está activa (%s).", WeeklyMessages.problemText(problem));
    if (busy()) return Tr.enc("zianutilities.raids.weekly.err.busy", "§eHay una pelea en curso; espera a que termine.");
    if (live != null) retire();
    spawnNotBefore = 0;
    return null;
  }

  // ---------------------------------------------------------------- queue messages

  private static void announce(List<WeeklyQueue.Event> events) {
    var server = RaidsModule.server;
    if (server == null) return;
    for (WeeklyQueue.Event event : events) {
      try {
        switch (event) {
          case WeeklyQueue.Called c -> called(server.getPlayerList().getPlayer(c.player()));
          case WeeklyQueue.TimedOut t -> tell(t.player(), Tr.c("zianutilities.raids.weekly.msg.timeout", "§cNo llegaste a tiempo y pierdes tu turno. Puedes volver a ponerte en la cola."));
          case WeeklyQueue.Dropped d -> tell(d.player(), Tr.c("zianutilities.raids.weekly.msg.dropped", "§7Has salido de la cola de la raid semanal."));
          case WeeklyQueue.Moved m -> tell(m.player(), Tr.c("zianutilities.raids.weekly.msg.position", "§eTu posición en la cola de la raid semanal: %s.", m.position()));
        }
      } catch (RuntimeException failure) {
        RaidsModule.LOG.error("Queue message failed", failure);
      }
    }
  }

  private static void tell(UUID player, net.minecraft.network.chat.Component message) {
    ServerPlayer p = RaidsModule.server.getPlayerList().getPlayer(player);
    if (p != null) p.sendSystemMessage(Messages.format(message));
  }

  private static void called(ServerPlayer p) {
    if (p == null) return;
    long seconds = Math.max(1, (queue.calledUntil() - now() + 999) / 1000);
    p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 70, 15));
    p.connection.send(new ClientboundSetTitleTextPacket(Tr.c("zianutilities.raids.weekly.title.turn", "§6¡Es tu turno!")));
    p.connection.send(new ClientboundSetSubtitleTextPacket(Tr.c("zianutilities.raids.weekly.subtitle.turn", "§7Acércate al jefe semanal: tienes %s segundos", seconds)));
    p.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.0F);
    p.sendSystemMessage(Messages.format(Tr.c("zianutilities.raids.weekly.msg.turn", "§6Es tu turno en la raid semanal. Tienes %s segundos para retar al jefe.", seconds)));
    RaidsModule.LOG.info("[ZIAN-AUDIT] weekly queue called player={}", p.getUUID());
  }

  static void onLogout(UUID player) {
    if (config == null) return;
    announce(queue.leave(player, now()));
  }

  // ---------------------------------------------------------------- commands support

  static String when(Instant instant) {
    return instant.atZone(config == null ? java.time.ZoneId.systemDefault() : config.zone()).format(WHEN);
  }

  static String remaining(Instant until) {
    Duration d = Duration.between(clock.instant(), until);
    if (d.isNegative()) d = Duration.ZERO;
    return String.format(Locale.ROOT, "%dd %02dh %02dm", d.toDays(), d.toHoursPart(), d.toMinutesPart());
  }

  static String leave(ServerPlayer p) {
    if (queue.position(p.getUUID()) < 0) return Tr.enc("zianutilities.raids.weekly.err.not_queued", "§eNo estás en la cola de la raid semanal.");
    announce(queue.leave(p.getUUID(), now()));
    return null;
  }

  static int clearQueue() {
    var events = queue.clear();
    announce(events);
    return events.size();
  }

  static boolean paused() { return stateBlocked; }

  /** Test and smoke hook: the entity currently standing on the pedestal, if any. */
  static PokemonEntity bossEntity() { return live == null ? null : live.boss; }

  static RaidSession session() { return live == null ? null : live.session; }
}
