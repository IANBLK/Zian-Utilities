package com.zianblk.zianutilities.neoforge.pase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zianblk.zianutilities.core.pase.LootTable;
import com.zianblk.zianutilities.core.pase.PaseEngine;
import com.zianblk.zianutilities.core.pase.PaseReward;
import com.zianblk.zianutilities.core.pase.PaseRules;
import com.zianblk.zianutilities.core.pase.PlayerPase;
import com.zianblk.zianutilities.core.pase.Prize;
import com.zianblk.zianutilities.core.pase.PrizeClaimer;
import com.zianblk.zianutilities.core.pase.PrizePlanner;
import com.zianblk.zianutilities.core.pase.Season;
import com.zianblk.zianutilities.core.pase.SeasonBook;
import com.zianblk.zianutilities.core.pase.SeasonRewards;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Rewards of the pass without Minecraft: loot table, planner, delivery journal, Tebex transactions and the file formats. */
class PaseRewardsTest {
  private static final String PREV = "temporada-1", CUR = "temporada-2";
  private final UUID id = UUID.randomUUID();
  private final SeasonRewards example = PaseSeasonExample.build(200);
  private final PaseRules rules = PaseRules.defaults();
  private final Map<UUID, PlayerPase> players = new HashMap<>();
  private final PaseEngine engine;

  PaseRewardsTest() {
    Instant start = Instant.parse("2026-12-01T00:00:00Z");
    var book = new SeasonBook(Season.ofMonths(CUR, start, 2, ZoneOffset.UTC), Season.ofMonths(PREV, Instant.parse("2026-10-01T00:00:00Z"), 2, ZoneOffset.UTC));
    engine = new PaseEngine(rules, book, uuid -> players.computeIfAbsent(uuid, PlayerPase::new), Clock.fixed(Instant.parse("2026-12-10T12:00:00Z"), ZoneOffset.UTC));
  }

  private PlayerPase player() { return players.computeIfAbsent(id, PlayerPase::new); }

  private int sync(PlayerPase p) { return PrizePlanner.sync(p, rules, List.of(PREV, CUR), s -> example, 1L); }

  private static long count(PlayerPase p, Prize.State state) { return p.prizes.values().stream().filter(z -> z.state == state).count(); }

  private int rewardsUpTo(int level, boolean premium) {
    int n = 0;
    for (int l = 1; l <= level; l++) n += (premium ? example.level(l).premium() : example.level(l).free()).size();
    return n;
  }

  /** A gateway that records what it gives; {@code block} holds prize ids it refuses, {@code crashOn} the one whose delivery kills the "server". */
  private static final class Fake implements PrizeClaimer.Gateway {
    final List<String> given = new ArrayList<>();
    final Set<String> block = new HashSet<>();
    String crashOn;

    @Override public String check(Prize z) { return block.contains(z.id) ? "INVENTORY_FULL" : null; }

    @Override public PrizeClaimer.Outcome give(Prize z) {
      given.add(z.id);
      if (z.id.equals(crashOn)) throw new Crash();
      return PrizeClaimer.Outcome.delivered();
    }
  }

  private static final class Crash extends Error {}

  /** A "disk": every save keeps the JSON, so a restart is reading the last one. */
  private static final class Disk implements PrizeClaimer.Saver {
    String last;
    boolean failing;

    @Override public void save(PlayerPase p) throws IOException {
      if (failing) throw new IOException("disco lleno");
      last = PaseStateJson.writePlayer(p);
    }
  }

  // ---------------------------------------------------------------- loot table

  @Test void lootWeightsAreRespected() {
    var a = new LootTable.Entry(1, PaseReward.item("minecraft:dirt", 1, null));
    var b = new LootTable.Entry(3, PaseReward.item("minecraft:stone", 1, null));
    var c = new LootTable.Entry(6, PaseReward.item("minecraft:gold_ingot", 1, null));
    int[] hits = new int[3];
    for (int seed = 0; seed < 20_000; seed++) {
      PaseReward r = LootTable.draw(List.of(a, b, c), seed);
      hits[r.item().equals("minecraft:dirt") ? 0 : r.item().equals("minecraft:stone") ? 1 : 2]++;
    }
    assertEquals(0.10, hits[0] / 20_000.0, 0.02);
    assertEquals(0.30, hits[1] / 20_000.0, 0.02);
    assertEquals(0.60, hits[2] / 20_000.0, 0.02);
    assertNull(LootTable.draw(List.of(), 5));
    assertThrows(IllegalArgumentException.class, () -> new LootTable.Entry(0, a.reward()));
    assertEquals(c.reward(), LootTable.draw(List.of(c), 99));
  }

  @Test void theDrawIsDeterministicPerPlayerSeasonAndLevel() {
    long seed = LootTable.seed(id, CUR, 5);
    assertEquals(seed, LootTable.seed(id, CUR, 5));
    assertNotEquals(seed, LootTable.seed(id, CUR, 10));
    assertNotEquals(seed, LootTable.seed(id, PREV, 5));
    assertNotEquals(seed, LootTable.seed(UUID.randomUUID(), CUR, 5));
    for (int i = 0; i < 50; i++) assertEquals(LootTable.draw(example.loot(), seed), LootTable.draw(example.loot(), seed));
    // Claiming twice cannot change it: drop the prize, sync again, same reward.
    PlayerPase p = player();
    p.progress(CUR).xp = rules.totalXp();
    p.progress(CUR).extraXp = 10L * rules.extraLevelXp();
    p.premium.add(CUR);
    sync(p);
    PaseReward first = p.prizes.get(Prize.idOf(CUR, Prize.Track.EXTRA, 5, 0)).reward;
    p.prizes.clear();
    sync(p);
    assertEquals(first, p.prizes.get(Prize.idOf(CUR, Prize.Track.EXTRA, 5, 0)).reward);
  }

  // ---------------------------------------------------------------- planner

  @Test void everyLevelCrossedLeavesItsFreePrizesPendingAndNothingPremium() {
    engine.adminGiveLevels(id, 30);
    PlayerPase p = player();
    int added = sync(p);
    assertEquals(rewardsUpTo(30, false), added);
    assertEquals(added, count(p, Prize.State.PENDING));
    assertTrue(p.prizes.values().stream().allMatch(z -> z.track == Prize.Track.FREE && z.seasonId.equals(CUR)));
    assertEquals(0, sync(p), "running it again adds nothing");
  }

  @Test void aPremiumBoughtLateCollectsEverythingAlreadyEarned() {
    engine.adminGiveLevels(id, 30);
    PlayerPase p = player();
    sync(p);
    var disk = new Disk();
    PrizeClaimer.claim(p, new Fake(), disk, m -> { }, 2L);
    assertEquals(rewardsUpTo(30, false), count(p, Prize.State.DELIVERED));
    assertTrue(engine.setPremium(id, CUR, true));
    int added = sync(p);
    assertEquals(rewardsUpTo(30, true), added);
    var fake = new Fake();
    PrizeClaimer.claim(p, fake, disk, m -> { }, 3L);
    assertEquals(added, fake.given.size());
    assertTrue(fake.given.stream().allMatch(s -> s.contains("|premium|")), "only the paid track is left to give, the free one is not repeated");
  }

  @Test void thePreviousSeasonAndTheCurrentOneBothPayOut() {
    PlayerPase p = player();
    p.progress(PREV).xp = 10L * rules.xpPerLevel();
    p.progress(CUR).xp = 5L * rules.xpPerLevel();
    p.premium.add(PREV);
    sync(p);
    assertEquals(rewardsUpTo(10, false) + rewardsUpTo(10, true) + rewardsUpTo(5, false), p.prizes.size());
    assertEquals(Set.of(PREV, CUR), new HashSet<>(p.prizes.values().stream().map(z -> z.seasonId).toList()));
    var fake = new Fake();
    PrizeClaimer.claim(p, fake, new Disk(), m -> { }, 2L);
    assertEquals(p.prizes.size(), fake.given.size());
  }

  @Test void extraLevelsPayEveryFifthRollAndOnlyOnThePaidTrack() {
    PlayerPase p = player();
    p.progress(CUR).xp = rules.totalXp();
    p.progress(CUR).extraXp = 12L * rules.extraLevelXp();
    sync(p);
    assertTrue(p.prizes.values().stream().noneMatch(z -> z.track == Prize.Track.EXTRA), "free track gets no extra rolls");
    p.premium.add(CUR);
    sync(p);
    var extra = p.prizes.values().stream().filter(z -> z.track == Prize.Track.EXTRA).map(z -> z.level).toList();
    assertEquals(List.of(5, 10), extra);
    p.progress(CUR).extraXp = 15L * rules.extraLevelXp();
    sync(p);
    assertEquals(3, p.prizes.values().stream().filter(z -> z.track == Prize.Track.EXTRA).count());
  }

  @Test void withoutRewardsForASeasonNothingIsCreatedAndLaterItCatchesUp() {
    engine.adminGiveLevels(id, 20);
    PlayerPase p = player();
    assertEquals(0, PrizePlanner.sync(p, rules, List.of(PREV, CUR), s -> null, 1L));
    assertTrue(p.prizes.isEmpty());
    assertEquals(rewardsUpTo(20, false), sync(p), "the levels were kept; the prizes appear once the file is valid");
  }

  @Test void aRefundDropsOnlyThePaidPrizesNotGivenYet() {
    engine.adminGiveLevels(id, 10);
    engine.setPremium(id, CUR, true);
    PlayerPase p = player();
    sync(p);
    var fake = new Fake();
    PrizePlanner.revokePremium(p, PREV);
    PrizeClaimer.claim(p, fake, new Disk(), m -> { }, 2L);
    Prize delivered = p.prizes.values().stream().filter(z -> z.track == Prize.Track.PREMIUM).findFirst().orElseThrow();
    long beforePaid = p.prizes.values().stream().filter(Prize::premiumOnly).count();
    // everything was delivered: nothing to drop, and the delivered ones stay for the admin
    assertEquals(0, PrizePlanner.revokePremium(p, CUR));
    assertEquals(beforePaid, p.prizes.values().stream().filter(Prize::premiumOnly).count());
    assertEquals(Prize.State.DELIVERED, delivered.state);
    // a fresh one: pending paid prizes go
    PlayerPase q = new PlayerPase(UUID.randomUUID());
    q.progress(CUR).xp = 10L * rules.xpPerLevel();
    q.premium.add(CUR);
    sync(q);
    int paid = (int) q.prizes.values().stream().filter(Prize::premiumOnly).count();
    assertTrue(paid > 0);
    assertEquals(paid, PrizePlanner.revokePremium(q, CUR));
    assertTrue(q.prizes.values().stream().noneMatch(Prize::premiumOnly));
  }

  // ---------------------------------------------------------------- journal

  @Test void anOfflinePlayerKeepsHisPrizesPendingUntilTheyAreClaimed() throws Exception {
    engine.adminGiveLevels(id, 12);
    PlayerPase p = player();
    int added = sync(p);
    assertEquals(added, PrizePlanner.pending(p));
    // the player is offline: the file is written and read back, nothing was delivered
    PlayerPase back = PaseStateJson.readPlayer(PaseStateJson.writePlayer(p), id);
    assertEquals(added, PrizePlanner.pending(back));
    assertEquals(0, count(back, Prize.State.DELIVERED));
    var fake = new Fake();
    PrizeClaimer.claim(back, fake, new Disk(), m -> { }, 5L);
    assertEquals(added, fake.given.size());
    assertEquals(0, PrizePlanner.pending(back));
  }

  @Test void aPrizeThatCannotBeGivenStaysPendingAndIsNotLost() {
    engine.adminGiveLevels(id, 4);
    PlayerPase p = player();
    sync(p);
    String stuck = Prize.idOf(CUR, Prize.Track.FREE, 2, 0);
    var fake = new Fake();
    fake.block.add(stuck);
    var report = PrizeClaimer.claim(p, fake, new Disk(), m -> { }, 2L);
    assertEquals(1, report.blocked().size());
    assertEquals("INVENTORY_FULL", p.prizes.get(stuck).reason);
    assertEquals(Prize.State.PENDING, p.prizes.get(stuck).state);
    assertFalse(fake.given.contains(stuck));
    fake.block.clear();
    PrizeClaimer.claim(p, fake, new Disk(), m -> { }, 3L);
    assertEquals(Prize.State.DELIVERED, p.prizes.get(stuck).state);
    assertEquals(1, fake.given.stream().filter(stuck::equals).count());
  }

  @Test void aPrizeIsNeverGivenTwice() {
    engine.adminGiveLevels(id, 6);
    PlayerPase p = player();
    sync(p);
    var fake = new Fake();
    var disk = new Disk();
    PrizeClaimer.claim(p, fake, disk, m -> { }, 2L);
    int once = fake.given.size();
    PrizeClaimer.claim(p, fake, disk, m -> { }, 3L);
    sync(p);
    PrizeClaimer.claim(p, fake, disk, m -> { }, 4L);
    assertEquals(once, fake.given.size());
    assertEquals(once, new HashSet<>(fake.given).size());
  }

  @Test void ifTheJournalCannotBeWrittenNothingIsGiven() {
    engine.adminGiveLevels(id, 3);
    PlayerPase p = player();
    sync(p);
    var fake = new Fake();
    var disk = new Disk();
    disk.failing = true;
    var warnings = new ArrayList<String>();
    PrizeClaimer.claim(p, fake, disk, warnings::add, 2L);
    assertTrue(fake.given.isEmpty());
    assertEquals(0, count(p, Prize.State.DELIVERED));
    assertFalse(warnings.isEmpty());
  }

  @Test void aCrashHalfWayLeavesTheItemUncertainAndItIsNotGivenAgain() {
    engine.adminGiveLevels(id, 5);
    PlayerPase p = player();
    sync(p);
    String victim = Prize.idOf(CUR, Prize.Track.FREE, 3, 0);
    var fake = new Fake();
    fake.crashOn = victim;
    var disk = new Disk();
    assertThrows(Crash.class, () -> PrizeClaimer.claim(p, fake, disk, m -> { }, 2L));
    assertTrue(fake.given.contains(victim), "the server died after giving it but before writing DELIVERED");

    PlayerPase back = PaseStateJson.readPlayer(disk.last, id);
    assertEquals(Prize.State.UNCERTAIN, back.prizes.get(victim).state, "the disk said DELIVERING: after a restart it is uncertain, not pending");
    assertEquals("INTERRUPTED", back.prizes.get(victim).reason);
    var again = new Fake();
    PrizeClaimer.claim(back, again, new Disk(), m -> { }, 3L);
    assertFalse(again.given.contains(victim), "an uncertain prize is not delivered by a normal claim");
    assertEquals(Prize.State.DELIVERED, back.prizes.get(Prize.idOf(CUR, Prize.Track.FREE, 1, 0)).state);

    assertEquals(1, PrizeClaimer.reopen(back));
    var admin = new Fake();
    PrizeClaimer.claim(back, admin, new Disk(), m -> { }, 4L);
    assertEquals(List.of(victim), admin.given);
    assertEquals(Prize.State.DELIVERED, back.prizes.get(victim).state);
  }

  @Test void coinsAndPokemonInterruptedMidwayResumeWithTheSameIdsInsteadOfBecomingUncertain() {
    PlayerPase p = player();
    Prize coins = new Prize(CUR, Prize.Track.FREE, 10, 1, PaseReward.coins("avecoins:coppercoin", 32));
    Prize mon = new Prize(CUR, Prize.Track.FREE, 25, 1, example.level(25).free().stream().filter(r -> r.kind() == PaseReward.Kind.POKEMON).findFirst().orElseThrow());
    Prize command = new Prize(CUR, Prize.Track.FREE, 11, 0, PaseReward.command("say hola {player}"));
    for (Prize z : List.of(coins, mon, command)) {
      z.state = Prize.State.DELIVERING;
      p.prizes.put(z.id, z);
    }
    PlayerPase back = PaseStateJson.readPlayer(PaseStateJson.writePlayer(p), id);
    assertEquals(Prize.State.PENDING, back.prizes.get(coins.id).state);
    assertEquals("RESUME", back.prizes.get(coins.id).reason);
    assertEquals(Prize.State.PENDING, back.prizes.get(mon.id).state);
    assertEquals(Prize.State.UNCERTAIN, back.prizes.get(command.id).state);
  }

  // ---------------------------------------------------------------- files

  @Test void theExampleSeasonIsComplete() {
    assertEquals(200, example.levels().size());
    for (int l = 1; l <= 200; l++) {
      assertFalse(example.level(l).free().isEmpty(), "free " + l);
      assertFalse(example.level(l).premium().isEmpty(), "premium " + l);
      if (l % 10 == 0) {
        assertTrue(example.level(l).free().stream().anyMatch(r -> r.kind() == PaseReward.Kind.COINS), "coins free " + l);
        assertTrue(example.level(l).premium().stream().anyMatch(r -> r.kind() == PaseReward.Kind.COINS), "coins premium " + l);
      }
    }
    for (int l : new int[] {25, 50, 100, 150, 200}) {
      assertTrue(example.level(l).free().stream().anyMatch(r -> r.kind() == PaseReward.Kind.POKEMON), "pokemon free " + l);
      var paid = example.level(l).premium().stream().filter(r -> r.kind() == PaseReward.Kind.POKEMON).findFirst().orElseThrow().pokemon();
      assertTrue(paid.shiny() || paid.minPerfectIvs() > 0, "paid pokemon at " + l + " is shiny or has perfect IVs");
    }
    assertFalse(example.loot().isEmpty());
    assertTrue(example.levelCap() >= 50 && example.level(200).premium().stream().filter(r -> r.kind() == PaseReward.Kind.POKEMON).allMatch(r -> r.pokemon().level() <= example.levelCap()));
  }

  @Test void theExampleFileRoundTripsAndEveryRewardKindSurvives() {
    String json = PaseSeasonExample.json();
    assertEquals(example, PaseRewardJson.parseSeason(json, 200));
    assertTrue(json.contains("\"formatVersion\": 1"));
    var all = List.of(PaseReward.item("minecraft:diamond_sword", 1, "minecraft:enchantments={levels:{'minecraft:sharpness':3}}"), PaseReward.coins("avecoins:goldcoin", 5),
        PaseReward.command("say hola {player}"),
        PaseReward.pokemon(new PaseReward.PokemonSpec("cobblemon:eevee", List.of("pikachu"), PaseReward.Legendary.NONE, 22, 31, 0, true, true, true, true)));
    for (PaseReward r : all) assertEquals(r, PaseRewardJson.reward(PaseRewardJson.toJson(r)));
  }

  @Test void aBadSeasonFileIsRefusedWholeSoTheRewardsModuleStaysClosed() {
    String good = PaseSeasonExample.json();
    assertThrows(IllegalArgumentException.class, () -> PaseRewardJson.parseSeason("{ broken", 200));
    assertThrows(IllegalArgumentException.class, () -> PaseRewardJson.parseSeason(good.replace("\"formatVersion\": 1", "\"formatVersion\": 2"), 200));
    assertThrows(IllegalArgumentException.class, () -> PaseRewardJson.parseSeason(good.replace("\"formatVersion\": 1,", ""), 200));
    assertThrows(IllegalArgumentException.class, () -> PaseRewardJson.parseSeason(good.replace("\"levelCap\"", "\"levelcap\""), 200));
    assertThrows(IllegalArgumentException.class, () -> PaseRewardJson.parseSeason(good, 100), "a level above the pass length");
    assertThrows(IllegalArgumentException.class, () -> PaseRewardJson.parseSeason(good.replace("\"count\": 16", "\"count\": 0"), 200));
    assertThrows(IllegalArgumentException.class, () -> PaseRewardJson.parseSeason(good.replace("\"weight\": 30", "\"weight\": 0"), 200));
    assertThrows(IllegalArgumentException.class, () -> PaseRewardJson.parseSeason(good.replace("\"type\": \"item\"", "\"type\": \"banana\""), 200));
    assertThrows(IllegalArgumentException.class, () -> PaseRewardJson.parseSeason(good.replace("\"shiny\": true", "\"shiny\": \"si\""), 200));
    assertThrows(IllegalArgumentException.class, () -> PaseRewardJson.parseSeason(good.replace("\"min_perfect_ivs\": 3", "\"ivs\": 31, \"min_perfect_ivs\": 3"), 200));
    assertThrows(IllegalArgumentException.class, () -> PaseRewardJson.parseSeason("[1]", 200));
    assertThrows(IllegalArgumentException.class, () -> PaseRewardJson.parseWhitelist("{\"formatVersion\":1,\"allowed\":\"say\"}"));
    assertThrows(IllegalArgumentException.class, () -> PaseRewardJson.parseWhitelist("{\"formatVersion\":7,\"allowed\":[]}"));
    assertEquals(Set.of("say hola {player}"), PaseRewardJson.parseWhitelist("{\"formatVersion\":1,\"allowed\":[\"say hola {player}\"]}"));
    assertTrue(PaseRewardJson.parseWhitelist(PaseRewardJson.whitelistDefaultJson()).isEmpty(), "the default whitelist allows no command");
  }

  @Test void versionOnePlayerFilesMigrateAndTheirEarnedLevelsBecomePendingPrizes() {
    String v1 = "{\"formatVersion\":1,\"id\":\"" + id + "\",\"seasons\":{\"" + CUR + "\":{\"xp\":" + (7L * rules.xpPerLevel()) + "}},\"premium\":[],\"events\":[]}";
    PlayerPase p = PaseStateJson.readPlayer(v1, id);
    assertTrue(p.prizes.isEmpty());
    assertEquals(rewardsUpTo(7, false), sync(p));
    String written = PaseStateJson.writePlayer(p);
    assertTrue(written.contains("\"formatVersion\": 2"));
    assertEquals(p.prizes.size(), PaseStateJson.readPlayer(written, id).prizes.size());
    assertThrows(IllegalArgumentException.class, () -> PaseStateJson.readPlayer(written.replace("\"formatVersion\": 2", "\"formatVersion\": 3"), id));
  }

  @Test void theJournalRoundTripsEveryStateAndRefusesACorruptOne() {
    PlayerPase p = player();
    p.progress(CUR).xp = 3L * rules.xpPerLevel();
    sync(p);
    var it = p.prizes.values().iterator();
    it.next().state = Prize.State.DELIVERED;
    Prize u = it.next();
    u.state = Prize.State.UNCERTAIN;
    u.reason = "INTERRUPTED";
    u.detail = "cobblemon:pikachu";
    u.attempts = 3;
    String json = PaseStateJson.writePlayer(p);
    PlayerPase back = PaseStateJson.readPlayer(json, id);
    assertEquals(p.prizes.keySet(), back.prizes.keySet());
    assertEquals(Prize.State.UNCERTAIN, back.prizes.get(u.id).state);
    assertEquals("cobblemon:pikachu", back.prizes.get(u.id).detail);
    assertEquals(3, back.prizes.get(u.id).attempts);
    assertThrows(RuntimeException.class, () -> PaseStateJson.readPlayer(json.replaceFirst("\"state\": \"PENDING\"", "\"state\": \"SENT\""), id));
    assertThrows(RuntimeException.class, () -> PaseStateJson.readPlayer(json.replaceFirst("\"id\": \"temporada-2\\|free\\|1\\|0\"", "\"id\": \"otra\""), id));
  }

  // ---------------------------------------------------------------- Tebex transactions

  @Test void aRepeatedTebexTransactionIsRecognisedEvenAfterARestart(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("transactions.json");
    var tx = new PaseTransactions(file);
    tx.load();
    assertNull(tx.get("tbx-1"));
    tx.begin("tbx-1", id, CUR, 10L);
    assertEquals(PaseTransactions.State.PENDING, tx.get("tbx-1").state());
    tx.applied("tbx-1");
    var restarted = new PaseTransactions(file);
    restarted.load();
    assertEquals(PaseTransactions.State.APPLIED, restarted.get("tbx-1").state());
    assertEquals(id.toString(), restarted.get("tbx-1").uuid());
    assertTrue(Files.readString(file).contains("\"formatVersion\": 1"));
    try (var files = Files.list(dir)) {
      assertEquals(List.of("transactions.json"), files.map(f -> f.getFileName().toString()).toList(), "atomic write leaves no temporary files");
    }
    assertTrue(PaseTransactions.validId("tbx-1_A.2"));
    assertFalse(PaseTransactions.validId("a b"));
    assertFalse(PaseTransactions.validId("x".repeat(65)));
    assertFalse(PaseTransactions.validId(null));
  }

  @Test void aTransactionWrittenBeforeTheCrashStaysPendingSoTheSameCommandFinishesTheJob(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("transactions.json");
    var tx = new PaseTransactions(file);
    tx.begin("tbx-9", id, CUR, 10L);
    var restarted = new PaseTransactions(file);
    restarted.load();
    assertEquals(PaseTransactions.State.PENDING, restarted.get("tbx-9").state());
  }

  @Test void aBrokenOrFutureTransactionsFileGivesNoPremiumAndIsNeverOverwritten(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("transactions.json");
    Files.writeString(file, "{ broken");
    var tx = new PaseTransactions(file);
    assertThrows(IOException.class, tx::load);
    Files.writeString(file, "{\"formatVersion\":9,\"transactions\":{}}");
    assertThrows(IOException.class, tx::load);
    assertThrows(IOException.class, () -> tx.begin("tbx-2", id, CUR, 1L) , "nothing is written over a file that could not be read");
    assertEquals("{\"formatVersion\":9,\"transactions\":{}}", Files.readString(file));
    assertNotNull(tx);
  }
}
