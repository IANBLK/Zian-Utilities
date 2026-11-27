package com.zianblk.zianutilities.neoforge.pase;

import com.mojang.authlib.GameProfile;
import com.zianblk.zianutilities.core.pase.MissionType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Local server smoke of the battle pass (ZIANUTILITIES_PASE_SMOKE=true, ZIANUTILITIES_PASE_PHASE=1 or 2; never packaged
 * in the release jar). Needs a config with every mission type on the board and small targets (the runner writes it).
 * Phase 1: sources, anti-farming, caps, daily reset, x2 window, season roll with the fake clock, admin commands, then saves.
 * Phase 2 (after a restart): data, seasons and placed-block marks survived; a broken or future file locks only that player.
 * Writes pase-smoke-N.txt next to the server and halts it.
 */
public final class PaseSmoke {
  private static final UUID ID1 = UUID.nameUUIDFromBytes("PaseSmoke-P1".getBytes(StandardCharsets.UTF_8));
  private static final UUID ID2 = UUID.nameUUIDFromBytes("PaseSmoke-P2".getBytes(StandardCharsets.UTF_8));
  private static final UUID ID3 = UUID.nameUUIDFromBytes("PaseSmoke-P3".getBytes(StandardCharsets.UTF_8));
  private static final BlockPos MARKED = new BlockPos(2, 120, 9);
  private static final StringBuilder REPORT = new StringBuilder();
  private static int phase = -1, started = 0;
  private static boolean done;
  private static ServerPlayer p1, p2;

  private PaseSmoke() {}

  private static void check(boolean ok, String what) {
    REPORT.append(ok ? "PASS " : "FAIL ").append(what).append('\n');
    if (!ok) throw new IllegalStateException(what);
  }

  public static void tick() {
    if (done || PaseModule.server == null) return;
    if (phase < 0) phase = Integer.parseInt(System.getenv().getOrDefault("ZIANUTILITIES_PASE_PHASE", "1"));
    if (++started < 140) return;
    done = true;
    try {
      if (phase == 1) phaseOne(); else phaseTwo();
      finish(true);
    } catch (Throwable failure) {
      REPORT.append("ERROR ").append(failure).append('\n');
      PaseModule.LOG.error("Pase smoke failed", failure);
      finish(false);
    }
  }

  // ---------------------------------------------------------------- helpers

  private static ServerPlayer fake(String name, UUID id) throws ReflectiveOperationException {
    var server = PaseModule.server;
    GameProfile profile = new GameProfile(id, name);
    ServerPlayer player = new ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault());
    player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player, CommonListenerCookie.createInitial(profile, false)) {
      @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
      @Override public void send(net.minecraft.network.protocol.Packet<?> packet, net.minecraft.network.PacketSendListener listener) {}
    };
    online(player, true);
    player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
    player.moveTo(0.5, 119, 0.5, 0, 0);
    return player;
  }

  /** getPlayer(UUID) reads playersByUUID, which only real logins fill. */
  @SuppressWarnings("unchecked")
  private static void online(ServerPlayer p, boolean on) throws ReflectiveOperationException {
    var byUuid = net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");
    byUuid.setAccessible(true);
    var map = (java.util.Map<UUID, ServerPlayer>) byUuid.get(PaseModule.server.getPlayerList());
    var list = net.minecraft.server.players.PlayerList.class.getDeclaredField("players");
    list.setAccessible(true);
    var players = (java.util.List<ServerPlayer>) list.get(PaseModule.server.getPlayerList());
    if (on) { map.put(p.getUUID(), p); players.add(p); } else { map.remove(p.getUUID()); players.remove(p); }
  }

  private static void forget(ServerPlayer p) throws ReflectiveOperationException {
    online(p, false);
  }

  private static ServerLevel level() { return PaseModule.server.overworld(); }

  private static long xp(UUID id, String season) { return PaseModule.store.get(id).xp(season); }

  private static long today(UUID id) { return PaseModule.store.get(id).baseToday; }

  private static int progress(UUID id, MissionType t) { return PaseModule.store.get(id).missionProgress.getOrDefault(t.name(), 0); }

  private static void place(ServerPlayer p, BlockPos pos, BlockState state) {
    level().setBlock(pos, state, 3);
    PaseSources.onPlaced(level(), pos, state);
  }

  private static void breakBlock(ServerPlayer p, BlockPos pos, BlockState state) {
    NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level(), pos, state, p));
    level().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
  }

  private static Zombie zombie(String spawnType) {
    Zombie z = EntityType.ZOMBIE.create(level());
    z.moveTo(0.5, 119, 0.5);
    PaseSources.onSpawn(z, spawnType);
    return z;
  }

  private static void kill(ServerPlayer p, net.minecraft.world.entity.LivingEntity e) {
    NeoForge.EVENT_BUS.post(new LivingDeathEvent(e, level().damageSources().playerAttack(p)));
  }

  // ---------------------------------------------------------------- phase 1

  private static void phaseOne() throws Exception {
    check(PaseModule.active(), "the module is active with the smoke config");
    var rules = PaseModule.config.rules();
    check(rules.missionsPerDay() == 7 && rules.missions().size() == 7, "every mission type is on the board");
    p1 = fake("PaseSmoke1", ID1);
    UUID id = ID1;
    var engine = PaseModule.engine;
    String s1 = engine.seasons().current().id();
    check("temporada-1".equals(s1) && engine.seasons().previous() == null, "first season created: " + s1);

    // blocks: a placed block never pays, a natural one does
    BlockState stone = Blocks.STONE.defaultBlockState();
    for (int i = 0; i < 3; i++) {
      BlockPos pos = new BlockPos(i, 120, 3);
      place(p1, pos, stone);
      check(PaseBlockMarks.of(level()).isMarked(pos), "placed block " + i + " is marked");
      breakBlock(p1, pos, stone);
      check(!PaseBlockMarks.of(level()).isMarked(pos), "the mark goes when it is broken");
    }
    check(progress(id, MissionType.BREAK_BLOCKS) == 0, "BREAK_BLOCKS: three placed-then-broken blocks count 0");
    for (int i = 0; i < 3; i++) {
      BlockPos pos = new BlockPos(i, 120, 4);
      level().setBlock(pos, stone, 3);
      breakBlock(p1, pos, stone);
    }
    check(engine.view(id).missions().stream().filter(m -> m.type() == MissionType.BREAK_BLOCKS).findFirst().orElseThrow().done(), "BREAK_BLOCKS: three natural blocks complete it");

    // crops: only ripe ones
    BlockState young = Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 3);
    BlockState ripe = Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7);
    level().setBlock(new BlockPos(0, 120, 5), young, 3);
    breakBlock(p1, new BlockPos(0, 120, 5), young);
    check(progress(id, MissionType.HARVEST_CROPS) == 0, "HARVEST_CROPS: an unripe crop counts 0");
    for (int i = 0; i < 2; i++) {
      level().setBlock(new BlockPos(i, 120, 5), ripe, 3);
      breakBlock(p1, new BlockPos(i, 120, 5), ripe);
    }
    check(engine.view(id).missions().stream().filter(m -> m.type() == MissionType.HARVEST_CROPS).findFirst().orElseThrow().done(), "HARVEST_CROPS: two ripe crops complete it");

    // mobs: spawner, named and non-hostile do not count; natural ones do
    kill(p1, zombie("SPAWNER"));
    Zombie named = zombie("NATURAL");
    named.setCustomName(net.minecraft.network.chat.Component.literal("Bob"));
    kill(p1, named);
    kill(p1, zombie("COMMAND"));
    var pig = EntityType.PIG.create(level());
    kill(p1, pig);
    check(progress(id, MissionType.KILL_MOBS) == 0, "KILL_MOBS: spawner, named, command-spawned and passive kills count 0");
    kill(p1, zombie("NATURAL"));
    kill(p1, zombie("CHUNK_GENERATION"));
    check(engine.view(id).missions().stream().filter(m -> m.type() == MissionType.KILL_MOBS).findFirst().orElseThrow().done(), "KILL_MOBS: two natural kills complete it");

    // breeding: the real event needs the player inside the level (getLoveCause reads it), so the handler path is called directly
    for (int i = 0; i < 2; i++) PaseSources.mission(p1, MissionType.BREED_ANIMALS, 1, null);
    check(engine.view(id).missions().stream().filter(m -> m.type() == MissionType.BREED_ANIMALS).findFirst().orElseThrow().done(), "BREED_ANIMALS: two births complete it (listener simulated)");

    // trades: the same villager and level twice counts once
    Villager v1 = EntityType.VILLAGER.create(level()), v2 = EntityType.VILLAGER.create(level());
    MerchantOffer offer = new MerchantOffer(new net.minecraft.world.item.trading.ItemCost(Items.EMERALD), new ItemStack(Items.BREAD), 10, 1, 0.05f);
    NeoForge.EVENT_BUS.post(new TradeWithVillagerEvent(p1, offer, v1));
    NeoForge.EVENT_BUS.post(new TradeWithVillagerEvent(p1, offer, v1));
    check(progress(id, MissionType.TRADE) == 1, "TRADE: the same villager and level counts once");
    NeoForge.EVENT_BUS.post(new TradeWithVillagerEvent(p1, offer, v2));
    check(engine.view(id).missions().stream().filter(m -> m.type() == MissionType.TRADE).findFirst().orElseThrow().done(), "TRADE: a second villager completes it");

    // exploring: the real scan counts the biome once; a repeated key does not count again
    PaseSources.explore();
    check(progress(id, MissionType.EXPLORE) >= 1, "EXPLORE: the scan counted the biome under the player");
    int before = progress(id, MissionType.EXPLORE);
    PaseSources.explore();
    check(progress(id, MissionType.EXPLORE) == before || engine.view(id).missions().stream().anyMatch(m -> m.type() == MissionType.EXPLORE && m.done()), "EXPLORE: the same biome again adds nothing");
    PaseSources.exploreKey(p1, "structure:smoke_a");
    PaseSources.exploreKey(p1, "structure:smoke_a");
    PaseSources.exploreKey(p1, "structure:smoke_b");
    check(engine.view(id).missions().stream().filter(m -> m.type() == MissionType.EXPLORE).findFirst().orElseThrow().done(), "EXPLORE: new structures complete it");

    // fishing: no XP while standing still
    long now = PaseModule.ticks * 50;
    PaseModule.AFK.sample(id, 0, 64, 0, 0, 0, 0);
    check(PaseModule.AFK.afk(id, now, PaseModule.config.afkSeconds() * 1000L), "the player counts as still after " + PaseModule.config.afkSeconds() + " s");
    PaseSources.onFished(p1);
    PaseSources.onFished(p1);
    check(progress(id, MissionType.FISH) == 0, "FISH: catches while still count 0");
    PaseModule.AFK.sample(id, 5, 64, 0, 90, 0, now - 1000);
    PaseSources.onFished(p1);
    PaseSources.onFished(p1);
    check(engine.view(id).missions().stream().filter(m -> m.type() == MissionType.FISH).findFirst().orElseThrow().done(), "FISH: catches while moving complete it");

    // missions together are capped at 35 % of the day
    check(PaseModule.store.get(id).perSource.get("mission") == 1400, "missions paid exactly 1400 base XP (35 % of 4000), not the 2100 they add up to");
    check(today(id) == 1400 && engine.view(id).capLeft() == 2600, "capLeft after the missions is 2600");

    // boss and raids: per-source caps, then the day cap
    for (int i = 0; i < 5; i++) com.zianblk.zianutilities.neoforge.pase.PaseApi.bossDefeated(p1);
    check(PaseModule.store.get(id).perSource.get("boss") == 1600, "boss XP stops at its source cap of 1600 (5 kills of 400)");
    PaseApi.raidWon(id, false);
    PaseApi.raidWon(id, true);
    check(today(id) == 4000 && engine.view(id).capLeft() == 0, "the day cap of 4000 trimmed the weekly raid");
    long totalBefore = xp(id, s1);
    PaseApi.bossDefeated(p1);
    check(xp(id, s1) == totalBefore && totalBefore == 4000, "nothing more today: pass XP is exactly 4000");

    PaseModule.saveAll();
    check(Files.exists(PaseModule.store.playerFile(id)) && Files.exists(PaseModule.store.seasonsFile()), "player and seasons files were written");

    // daily reset with the fake clock
    PaseModule.CLOCK.advance(Duration.ofHours(24));
    check(engine.view(id).capLeft() == 4000 && progress(id, MissionType.FISH) == 0, "next game day: cap and missions start again");
    PaseApi.bossDefeated(p1);
    check(xp(id, s1) == 4400, "next day pays again (400)");

    // x2 only in the last ten days
    var cur = engine.seasons().current();
    PaseModule.CLOCK.advance(Duration.between(engine.now(), cur.end().minus(Duration.ofDays(5))));
    var v = engine.view(id);
    check(v.boost() && v.capTotal() == 8000, "inside the last ten days: boost on, cap 8000");
    long b4 = xp(id, s1);
    PaseApi.bossDefeated(p1);
    check(xp(id, s1) - b4 == 800, "a boss kill pays 800 (400 x 2) in the window");
    PaseModule.CLOCK.advance(Duration.between(engine.now(), cur.end().plus(Duration.ofHours(1))));
    var after = engine.view(id);
    check("temporada-2".equals(after.seasonId()) && "temporada-1".equals(after.previousId()) && !after.boost(), "season rolled: temporada-2 current, temporada-1 previous, no boost");
    long prevBefore = xp(id, "temporada-1");
    PaseApi.bossDefeated(p1);
    check(xp(id, "temporada-1") - prevBefore == 400 && xp(id, "temporada-2") == 0, "XP goes to the previous pass first (400, no x2)");
    check(PaseModule.seasons.dirty, "the season change waits to be saved");

    // admin commands: players cannot, console can
    var dispatcher = PaseModule.server.getCommands().getDispatcher();
    check(dispatcher.execute("ZianUtilities pase ver", p1.createCommandSourceStack()) == 1, "/ZianUtilities pase ver works for a player");
    boolean denied = false;
    try {
      dispatcher.execute("ZianUtilities pase admin xp dar PaseSmoke1 100", p1.createCommandSourceStack());
    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) {
      denied = true;
    }
    check(denied, "a non-OP player cannot run pase admin");
    var console = PaseModule.server.createCommandSourceStack();
    long x0 = xp(id, "temporada-1");
    check(dispatcher.execute("ZianUtilities pase admin xp dar PaseSmoke1 200", console) == 1 && xp(id, "temporada-1") - x0 == 200, "admin xp dar gives 200 ignoring the cap");
    check(dispatcher.execute("ZianUtilities pase admin xp quitar PaseSmoke1 50", console) == 1 && xp(id, "temporada-2") == 0 && xp(id, "temporada-1") - x0 == 200, "admin xp quitar acts on the current season only (nothing to take there)");
    check(dispatcher.execute("ZianUtilities pase admin nivel dar PaseSmoke1 1", console) == 1, "admin nivel dar works");
    check(dispatcher.execute("ZianUtilities pase admin temporada ver", console) == 1, "admin temporada ver works");
    check(dispatcher.execute("ZianUtilities pase admin jugador reiniciar PaseSmoke1", console) == 1 && PaseModule.store.get(id).baseToday == 0, "admin jugador reiniciar clears today");
    check(dispatcher.execute("ZianUtilities pase admin recargar", console) == 1, "admin recargar works");

    // a placed block that must survive the restart
    level().setBlock(MARKED, stone, 3);
    PaseSources.onPlaced(level(), MARKED, stone);
    check(PaseBlockMarks.of(level()).isMarked(MARKED), "a block is marked for the restart check");
    PaseModule.saveAll();
    Files.writeString(Path.of("pase-smoke-state.txt"), "prev=" + xp(id, "temporada-1") + "\ncur=" + xp(id, "temporada-2") + "\n");
    forget(p1);
  }

  // ---------------------------------------------------------------- phase 2

  private static void phaseTwo() throws Exception {
    check(PaseModule.active(), "the module is active after the restart");
    var engine = PaseModule.engine;
    var saved = Files.readAllLines(Path.of("pase-smoke-state.txt"));
    long prev = Long.parseLong(saved.get(0).substring(5)), cur = Long.parseLong(saved.get(1).substring(4));
    check("temporada-2".equals(engine.seasons().current().id()) && "temporada-1".equals(engine.seasons().previous().id()), "seasons survived the restart");
    check(xp(ID1, "temporada-1") == prev && xp(ID1, "temporada-2") == cur, "player XP survived the restart: " + prev + " / " + cur);
    var v = engine.view(ID1);
    check(v != null && v.previousId().equals("temporada-1"), "the view works for the stored player");

    p2 = fake("PaseSmoke2", ID2);
    check(PaseBlockMarks.of(level()).isMarked(MARKED), "the placed-block mark survived the restart");
    BlockState stone = Blocks.STONE.defaultBlockState();
    level().setBlock(MARKED, stone, 3);
    breakBlock(p2, MARKED, stone);
    check(progress(ID2, MissionType.BREAK_BLOCKS) == 0 && !PaseBlockMarks.of(level()).isMarked(MARKED), "breaking it after the restart pays nothing and clears the mark");
    BlockPos natural = new BlockPos(7, 120, 7);
    level().setBlock(natural, stone, 3);
    breakBlock(p2, natural, stone);
    check(progress(ID2, MissionType.BREAK_BLOCKS) == 1, "an unmarked stone after the restart counts 1");

    // a future-version file locks only that player and is never overwritten
    Path file = PaseModule.store.playerFile(ID3);
    Files.createDirectories(file.getParent());
    String future = "{\"formatVersion\":9,\"id\":\"" + ID3 + "\"}";
    Files.writeString(file, future);
    check(engine.grant(ID3, "boss", 400).applied() == 0, "a player with a future-version file earns nothing");
    PaseModule.saveAll();
    check(future.equals(Files.readString(file)), "that file was not overwritten");
    check(engine.grant(ID2, "boss", 400).applied() == 400, "other players are unaffected");

    // a broken config on reload keeps the previous one
    Path config = PaseModule.configPath();
    String good = Files.readString(config);
    Files.writeString(config, "{ broken");
    check(PaseModule.reload() != null && PaseModule.active(), "a broken config fails the reload and keeps the previous config running");
    Files.writeString(config, good);
    check(PaseModule.reload() == null && PaseModule.active(), "the good config reloads again");
    check(Files.exists(PaseModule.dataDir().resolve("changes.jsonl")) || Files.isDirectory(PaseModule.dataDir().resolve("config-backups")), "the reload left a backup copy");
    forget(p2);
  }

  private static void finish(boolean ok) {
    try {
      if (p1 != null) forget(p1);
      if (p2 != null) forget(p2);
      p1 = null;
      p2 = null;
      Files.writeString(Path.of("pase-smoke-" + phase + ".txt"), (ok ? "PASS\n" : "FAIL\n") + REPORT);
    } catch (Exception ignored) { }
    PaseModule.LOG.info("PASE SMOKE {} phase {}\n{}", ok ? "PASS" : "FAIL", phase, REPORT);
    PaseModule.server.halt(false);
  }
}
