package com.zianblk.zianutilities.neoforge.raids;

import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.authlib.GameProfile;
import com.necro.raid.dens.common.network.packets.RaidChallengePacket;
import com.necro.raid.dens.common.util.IRaidAccessor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.phys.AABB;

/**
 * Local server smoke of the weekly raid (ZIANUTILITIES_RAIDS_SMOKE=weekly1, weekly2 or weekly3; never packaged in the jar).
 * weekly1: the boss stands on the pedestal, a duel is played, limits, the waiting line with fake players, a forced rotation.
 * weekly2: after a restart the week, the counts and a single boss on the pedestal survive.
 * weekly3: after the state file says the week ended long ago, the raid rotates exactly once.
 * Writes raids-smoke-weekly-N.txt next to the server and halts it.
 */
public final class RaidSmokeWeekly {
  private static final double X = 6.5, Y = -59.0, Z = 0.5;
  private static final UUID ID1 = UUID.nameUUIDFromBytes("RaidSmokeWeekly-P1".getBytes(StandardCharsets.UTF_8));
  private static final UUID ID2 = UUID.nameUUIDFromBytes("RaidSmokeWeekly-P2".getBytes(StandardCharsets.UTF_8));
  private static final UUID ID3 = UUID.nameUUIDFromBytes("RaidSmokeWeekly-P3".getBytes(StandardCharsets.UTF_8));
  private static final StringBuilder REPORT = new StringBuilder();
  private static int phase = -1, step, wait, age, waited;
  private static ServerPlayer p1, p2, p3;
  private static UUID oldBoss;

  private RaidSmokeWeekly() {}

  private static void check(boolean ok, String what) {
    REPORT.append(ok ? "PASS " : "FAIL ").append(what).append('\n');
    if (!ok) throw new IllegalStateException(what);
  }

  private static boolean until(boolean condition, int limit, String what) {
    if (condition) { waited = 0; return true; }
    if (++waited > limit) check(false, "timeout waiting for " + what);
    return false;
  }

  public static void tick() {
    var server = RaidsModule.server;
    if (server == null || step < 0) return;
    if (phase < 0) phase = Integer.parseInt(System.getenv("ZIANUTILITIES_RAIDS_SMOKE").substring("weekly".length()));
    age++;
    try {
      if (wait > 0) { wait--; return; }
      switch (phase) {
        case 1 -> phaseOne();
        case 2 -> phaseTwo();
        case 3 -> phaseThree();
        case 4 -> phaseFour();
        default -> check(false, "unknown phase");
      }
    } catch (Throwable failure) {
      REPORT.append("ERROR ").append(failure).append('\n');
      RaidsModule.LOG.error("Weekly raid smoke failed", failure);
      finish(false);
    }
  }

  // ---------------------------------------------------------------- helpers

  private static ServerPlayer fake(String name, UUID id) throws ReflectiveOperationException {
    var server = RaidsModule.server;
    GameProfile profile = new GameProfile(id, name);
    ServerPlayer player = new ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault());
    player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player, CommonListenerCookie.createInitial(profile, false)) {
      @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
      @Override public void send(net.minecraft.network.protocol.Packet<?> packet, net.minecraft.network.PacketSendListener listener) {}
    };
    online(player, true);
    var shield = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
    shield.setAccessible(true);
    shield.setInt(player, 0);
    player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
    player.moveTo(X - 3, Y, Z, 0, 0);
    player.setHealth(player.getMaxHealth());
    return player;
  }

  @SuppressWarnings("unchecked")
  private static void online(ServerPlayer p, boolean on) throws ReflectiveOperationException {
    var byUuid = net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");
    byUuid.setAccessible(true);
    var map = (java.util.Map<UUID, ServerPlayer>) byUuid.get(RaidsModule.server.getPlayerList());
    if (on) map.put(p.getUUID(), p); else map.remove(p.getUUID());
  }

  private static PokemonEntity boss() { return WeeklyRuntime.bossEntity(); }

  private static boolean bossUp() {
    var s = WeeklyRuntime.session();
    return boss() != null && s != null && s.getActive() != null && boss().isAlive();
  }

  /** Fake players never claim their reward; a pending one would (rightly) refuse the next challenge, which is not what these steps test. */
  private static void challenge(ServerPlayer p) {
    com.necro.raid.dens.common.raids.helpers.RaidHelper.REWARD_QUEUE.remove(p.getUUID());
    var packet = new RaidChallengePacket(boss().getId(), UUID.randomUUID(), BattleFormat.Companion.getGEN_9_SINGLES());
    packet.handleServer(p);
  }

  private static void lethal(ServerPlayer p) {
    p.setHealth(2.0F);
    p.invulnerableTime = 0;
    p.hurt(p.level().damageSources().mobAttack(boss()), 10F);
  }

  private static int bossesOnPedestal(boolean onlyTagged) {
    int n = 0;
    for (PokemonEntity e : RaidsModule.server.overworld().getEntitiesOfClass(PokemonEntity.class, new AABB(X, Y, Z, X, Y, Z).inflate(30))) {
      boolean tagged = e.getTags().contains(WeeklyRuntime.TAG);
      boolean raidBoss = e instanceof IRaidAccessor a && a.crd_isRaidBoss();
      if (onlyTagged ? tagged : (tagged || raidBoss)) n++;
    }
    return n;
  }

  private static boolean hitUntilEnd(ServerPlayer p, UUID id) {
    boss().hurt(p.damageSources().playerAttack(p), 200F);
    return until(!RaidDuelRuntime.inDuel(id), 3000, "the duel to end by hits");
  }

  private static boolean fresh() {
    if (!until(bossUp() && !boss().getUUID().equals(oldBoss), 1500, "a fresh boss")) return false;
    oldBoss = boss().getUUID();
    return true;
  }

  private static int attempts(UUID id) { return WeeklyRuntime.state.counts.of(id).attempts(); }

  private static int rewards(UUID id) { return WeeklyRuntime.state.counts.of(id).rewards(); }

  private static boolean raidIs(String id) { return WeeklyRuntime.session() != null && WeeklyRuntime.session().boss().getId().getPath().startsWith("weekly/" + id); }

  // ---------------------------------------------------------------- phase 1

  private static void phaseOne() throws Exception {
    switch (step) {
      case 0 -> {
        if (age < 100) return;
        new com.zianblk.zianutilities.neoforge.generation.NeoForgeGenerationStateStore(RaidsModule.server).save(com.zianblk.zianutilities.core.generation.GenerationState.Companion.all());
        check(WeeklyRuntime.ready(), "weekly raid ready with the weekly config enabled");
        check(WeeklyRuntime.state.week.weekNumber() == 1 && "semana-a".equals(WeeklyRuntime.active.id()), "week 1 is semana-a");
        step = 1;
      }
      case 1 -> {
        if (!until(bossUp(), 800, "the boss on the pedestal")) return;
        oldBoss = boss().getUUID();
        check(boss().position().distanceTo(new net.minecraft.world.phys.Vec3(X, Y, Z)) < 2.0, "boss stands on the pedestal");
        check(boss().getTags().contains(WeeklyRuntime.TAG), "boss carries the weekly tag");
        check(bossesOnPedestal(false) == 1, "exactly one boss on the pedestal");
        check(raidIs("semana-a"), "the session boss is semana-a");
        p1 = fake("SmokeP1", ID1);
        p2 = fake("SmokeP2", ID2);
        p3 = fake("SmokeP3", ID3);
        challenge(p1);
        wait = 5; step = 2;
      }
      case 2 -> {
        check(RaidDuelRuntime.inDuel(ID1) && ID1.equals(WeeklyRuntime.queue.playing()), "first player plays a weekly duel and holds the raid");
        challenge(p2);
        challenge(p3);
        check(!RaidDuelRuntime.inDuel(ID2) && WeeklyRuntime.queue.position(ID2) == 1, "second player is queued at position 1");
        check(WeeklyRuntime.queue.position(ID3) == 2, "third player is queued at position 2");
        online(p3, false);
        wait = 45; step = 3;
      }
      case 3 -> {
        check(WeeklyRuntime.queue.position(ID3) == -1 && WeeklyRuntime.queue.position(ID2) == 1, "disconnected player left the queue and the next one moved up");
        step = 4;
      }
      case 4 -> {
        if (hitUntilEnd(p1, ID1)) { wait = 90; step = 5; }
      }
      case 5 -> {
        check(attempts(ID1) == 1 && rewards(ID1) == 1, "won duel spent one attempt and counted ONE reward (no double count after 90 ticks)");
        step = 6;
      }
      case 6 -> {
        if (!until(WeeklyRuntime.queue.called() != null, 2000, "the next player to be called once the boss is back")) return;
        check(ID2.equals(WeeklyRuntime.queue.called()) && WeeklyRuntime.queue.playing() == null, "the queue advanced only when the boss was ready: second player is called");
        check(bossUp() && !boss().getUUID().equals(oldBoss), "the called player was called with a fresh boss in place");
        step = 7;
      }
      case 7 -> {
        if (!until(WeeklyRuntime.queue.called() == null, 1500, "the called player to time out")) return;
        check(WeeklyRuntime.queue.position(ID2) == -1 && !RaidDuelRuntime.inDuel(ID2), "called player who did not enter lost the turn");
        oldBoss = boss().getUUID();
        challenge(p1);
        wait = 5; step = 8;
      }
      case 8 -> {
        check(RaidDuelRuntime.inDuel(ID1), "first player plays a second duel (rewardsPerWeek=2)");
        step = 9;
      }
      case 9 -> {
        if (hitUntilEnd(p1, ID1)) { wait = 90; step = 10; }
      }
      case 10 -> {
        check(attempts(ID1) == 2 && rewards(ID1) == 2, "second win counted once: two attempts and two rewards");
        step = 11;
      }
      case 11 -> {
        if (!fresh()) return;
        challenge(p1);
        wait = 5; step = 12;
      }
      case 12 -> {
        check(!RaidDuelRuntime.inDuel(ID1) && WeeklyRuntime.queue.playing() == null && attempts(ID1) == 2, "reward limit of two reached: the challenge is refused and nothing is spent");
        challenge(p2);
        wait = 5; step = 13;
      }
      case 13 -> {
        check(RaidDuelRuntime.inDuel(ID2), "second player now plays");
        lethal(p2);
        wait = 40; step = 14;
      }
      case 14 -> {
        check(!RaidDuelRuntime.inDuel(ID2) && p2.isAlive(), "lethal hit ended the duel and nobody died");
        check(attempts(ID2) == 1 && rewards(ID2) == 0, "a defeat below requiredDamage spends the attempt and gives no reward");
        step = 15;
      }
      case 15 -> {
        if (!fresh()) return;
        challenge(p2);
        wait = 5; step = 16;
      }
      case 16 -> {
        check(RaidDuelRuntime.inDuel(ID2), "second player plays attempt two");
        lethal(p2);
        wait = 40; step = 17;
      }
      case 17 -> {
        check(attempts(ID2) == 2, "attempt two spent");
        step = 18;
      }
      case 18 -> {
        if (!fresh()) return;
        challenge(p2);
        wait = 5; step = 19;
      }
      case 19 -> {
        check(RaidDuelRuntime.inDuel(ID2), "second player plays attempt three");
        lethal(p2);
        wait = 40; step = 20;
      }
      case 20 -> {
        check(attempts(ID2) == 3, "attempt three spent");
        step = 21;
      }
      case 21 -> {
        if (!fresh()) return;
        challenge(p2);
        wait = 5; step = 22;
      }
      case 22 -> {
        check(!RaidDuelRuntime.inDuel(ID2) && WeeklyRuntime.queue.playing() == null && attempts(ID2) == 3, "attempt limit reached: attempt four is refused");
        check(WeeklyRuntime.state.counts.reset(ID2) && attempts(ID2) == 0, "admin reset frees the player");
        check(WeeklyRuntime.forceRotate() == null, "forced rotation accepted");
        check(WeeklyRuntime.state.week.weekNumber() == 2 && "semana-b".equals(WeeklyRuntime.active.id()), "week 2 is semana-b");
        check(rewards(ID1) == 0 && attempts(ID1) == 0, "a new week starts with empty counts");
        check(WeeklyRuntime.state.previous.size() == 1 && WeeklyRuntime.state.previous.get(0).weekNumber() == 1, "the snapshot of week 1 is kept for pending rewards");
        step = 23;
      }
      case 23 -> {
        if (!until(bossUp() && raidIs("semana-b") && !boss().getUUID().equals(oldBoss), 1500, "the rotated boss")) return;
        check(bossesOnPedestal(false) == 1, "exactly one boss after the rotation");
        check(com.necro.raid.dens.common.registry.CustomRaidRegistries.RAID_REGISTRY.cacheHas(WeeklyRuntime.session().boss().getId()), "the active weekly boss is in the Raid Dens registry");
        challenge(p1);
        wait = 5; step = 24;
      }
      case 24 -> {
        check(RaidDuelRuntime.inDuel(ID1), "player plays in the new week");
        lethal(p1);
        wait = 40; step = 25;
      }
      case 25 -> {
        check(attempts(ID1) == 1, "attempt of the new week persisted in memory");
        wait = 40; step = 26;
      }
      case 26 -> finish(true);
      default -> { }
    }
  }

  // ---------------------------------------------------------------- phases 2 and 3

  private static void phaseTwo() throws Exception {
    switch (step) {
      case 0 -> {
        if (age < 100) return;
        check(WeeklyRuntime.ready(), "weekly raid ready after the restart");
        check(WeeklyRuntime.state.week.weekNumber() == 2 && "semana-b".equals(WeeklyRuntime.active.id()), "week 2 semana-b kept across the restart (no rotation)");
        check(attempts(ID1) == 1, "player attempts kept across the restart");
        step = 1;
      }
      case 1 -> {
        if (!until(bossUp(), 1500, "the boss recreated after the restart")) return;
        check(raidIs("semana-b"), "recreated boss is the frozen semana-b");
        check(boss().position().distanceTo(new net.minecraft.world.phys.Vec3(X, Y, Z)) < 2.0, "recreated boss stands on the pedestal");
        wait = 100; step = 2;
      }
      case 2 -> {
        check(bossesOnPedestal(false) == 1, "exactly one boss on the pedestal after the restart (no duplicates)");
        finish(true);
      }
      default -> { }
    }
  }

  private static void phaseThree() throws Exception {
    switch (step) {
      case 0 -> {
        if (age < 100) return;
        check(WeeklyRuntime.ready(), "weekly raid ready after a long shutdown");
        check(WeeklyRuntime.state.week.weekNumber() == 3 && "semana-c".equals(WeeklyRuntime.active.id()), "a long shutdown rotated exactly once (week 3, semana-c)");
        check("AUTO".equals(WeeklyRuntime.state.week.reason()) && attempts(ID1) == 0, "scheduled rotation with fresh counts");
        step = 1;
      }
      case 1 -> {
        if (!until(bossUp(), 1500, "the rotated boss")) return;
        check(raidIs("semana-c"), "boss on the pedestal is semana-c (BATTLE mode also stands on the pedestal)");
        wait = 100; step = 2;
      }
      case 2 -> {
        check(bossesOnPedestal(false) == 1, "exactly one boss on the pedestal");
        finish(true);
      }
      default -> { }
    }
  }

  // ---------------------------------------------------------------- phase 4: BATTLE mode

  /**
   * A real Pokemon battle needs a client choosing a team, so it cannot be started with a fake player. What is exercised
   * for real: the gate (turn, queue, no duel), the turn bookkeeping and the watchdogs. What is simulated: the Raid Dens
   * events (RAID_BATTLE_START, RAID_BATTLE_END, RAID_END), which are called exactly as the subscribers receive them.
   */
  private static void phaseFour() throws Exception {
    switch (step) {
      case 0 -> {
        if (age < 100) return;
        new com.zianblk.zianutilities.neoforge.generation.NeoForgeGenerationStateStore(RaidsModule.server).save(com.zianblk.zianutilities.core.generation.GenerationState.Companion.all());
        check(WeeklyRuntime.ready(), "weekly raid ready");
        check(WeeklyRuntime.fix("semana-c") == null && WeeklyRuntime.active.mode() == WeeklyConfig.Mode.BATTLE, "semana-c (BATTLE) pinned");
        step = 1;
      }
      case 1 -> {
        if (!until(bossUp() && raidIs("semana-c"), 1500, "the BATTLE boss")) return;
        oldBoss = boss().getUUID();
        p1 = fake("SmokeP1", ID1);
        p2 = fake("SmokeP2", ID2);
        challenge(p1);
        wait = 5; step = 2;
      }
      case 2 -> {
        check(ID1.equals(WeeklyRuntime.queue.playing()) && WeeklyRuntime.battleHeld(ID1) && !RaidDuelRuntime.inDuel(ID1), "BATTLE: first player holds the turn and no duel starts");
        challenge(p2);
        check(WeeklyRuntime.queue.position(ID2) == 1 && !WeeklyRuntime.battleHeld(ID2), "BATTLE: second player is queued");
        var raidBoss = WeeklyRuntime.session().boss();
        check(WeeklyRuntime.cancelJoin(raidBoss, ID2) && !WeeklyRuntime.cancelJoin(raidBoss, ID1), "BATTLE: RAID_JOIN of the second player is cancelled, the turn holder may join");
        WeeklyRuntime.onBattleStart(raidBoss, ID1);
        WeeklyRuntime.onBattleEnd(raidBoss, ID2, true);
        check(rewards(ID2) == 0, "BATTLE: a win reported for a player without the turn counts nothing");
        WeeklyRuntime.onBattleEnd(raidBoss, ID1, true);
        check(rewards(ID1) == 1, "BATTLE: the win is counted once");
        WeeklyRuntime.onRaidEnd(raidBoss);
        WeeklyRuntime.onRaidEnd(raidBoss);
        check(attempts(ID1) == 1 && rewards(ID1) == 1 && WeeklyRuntime.queue.playing() == null && !WeeklyRuntime.battleHeld(ID1), "BATTLE: RAID_END spends one attempt (twice reported, once counted) and frees the turn");
        step = 3;
      }
      case 3 -> {
        if (!until(ID2.equals(WeeklyRuntime.queue.called()) && bossUp() && !boss().getUUID().equals(oldBoss), 2000, "the second player to be called with a fresh boss")) return;
        challenge(p2);
        wait = 5; step = 4;
      }
      case 4 -> {
        check(WeeklyRuntime.battleHeld(ID2), "BATTLE: the called player takes the turn");
        WeeklyRuntime.onBattleStart(WeeklyRuntime.session().boss(), ID2);
        step = 5;
      }
      case 5 -> {
        if (!until(!WeeklyRuntime.battleHeld(ID2), 600, "the idle BATTLE turn to close by idleTicks")) return;
        check(attempts(ID2) == 1 && WeeklyRuntime.queue.playing() == null, "BATTLE: a started turn with no battle in progress closes after 5 s and spends the attempt");
        finish(true);
      }
      default -> { }
    }
  }

  private static void finish(boolean ok) {
    step = -1;
    try {
      Files.writeString(Path.of("raids-smoke-weekly-" + phase + ".txt"), (ok ? "PASS\n" : "FAIL\n") + REPORT);
    } catch (Exception ignored) { }
    RaidsModule.LOG.info("WEEKLY RAID SMOKE {} phase {}\n{}", ok ? "PASS" : "FAIL", phase, REPORT);
    RaidsModule.server.halt(false);
  }
}
