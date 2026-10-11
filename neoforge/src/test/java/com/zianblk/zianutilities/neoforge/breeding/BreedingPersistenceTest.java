package com.zianblk.zianutilities.neoforge.breeding;

import static org.junit.jupiter.api.Assertions.*;

import com.zianblk.zianutilities.core.breeding.BreedingJob;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BreedingPersistenceTest {
  @TempDir Path directory;

  @Test
  void closedHistoryLoadsOnDemandWithoutKeepingChildrenInPendingMemory() throws Exception {
    var path=directory.resolve("closed.wal");var original=job();
    var closed=original.phase(BreedingJob.Status.DELIVERED,"confirmed",1);
    try(var store=new BreedingStore(path)) {
      store.put(original);store.put(closed);
      assertTrue(store.pending().isEmpty());assertTrue(store.pendingMine(original.owner()).isEmpty());
      assertEquals(closed,store.jobs.get(original.id()));assertEquals(List.of(closed),store.mine(original.owner()));
      assertThrows(UnsupportedOperationException.class,()->store.jobs.put(original.id(),original));
    }
    try(var store=new BreedingStore(path)) {
      assertTrue(store.pending().isEmpty());assertEquals(closed,store.jobs.get(original.id()));
      store.checkpoint();assertEquals(closed,store.jobs.get(original.id()));
    }
  }

  @Test
  void interruptionAtCommitKeepsRecoverableArchiveAndExactCurrentState() throws Exception {
    for (var point : BreedingStorageMaintenance.Point.values()) {
      var path = directory.resolve(point + ".wal");
      var original = job().phase(BreedingJob.Status.DELIVERED, "confirmed", 1);
      try (var store = new BreedingStore(path)) { store.put(original); store.checkpoint(); }
      byte[] before = Files.readAllBytes(path);
      assertThrows(java.io.IOException.class, () -> BreedingStorageMaintenance.compact(path, p -> {
        if (p == point) throw new java.io.IOException("injected interruption");
      }));
      if (point == BreedingStorageMaintenance.Point.BEFORE_REPLACE) assertArrayEquals(before, Files.readAllBytes(path));
      assertFalse(Files.exists(path.resolveSibling(path.getFileName() + ".maintenance")));
      try (var store = new BreedingStore(path)) { assertEquals(original, store.jobs.get(original.id())); }
    }
  }

  @Test
  void indexesReleaseTerminalParentsAndRestorePendingReceiptsAfterRestart() throws Exception {
    var path = directory.resolve("indexed.wal");
    var original = job();
    var purchase =
        new BreedingJob.Purchase(
            UUID.randomUUID(), "diamondcoin", 1, 60000, 60000, BreedingJob.Payment.PENDING);
    var uncertain = original.begin(purchase, 1).payment(BreedingJob.Payment.UNCERTAIN, 2);
    try (var store = new BreedingStore(path)) {
      store.put(original);
      assertEquals(List.of(original), store.ticking());
      assertTrue(store.parentBusy(original.first()));
      store.put(uncertain);
      assertTrue(store.purchaseExists(purchase.id()));
      assertTrue(store.ticking().isEmpty());
      assertTrue(store.hasPending());
      assertEquals(List.of(uncertain), store.mine(original.owner()));
    }
    try (var store = new BreedingStore(path)) {
      assertEquals(List.of(uncertain), store.mine(original.owner()));
      assertTrue(store.purchaseExists(purchase.id()));
      assertTrue(store.parentBusy(original.second()));
      store.put(uncertain.phase(BreedingJob.Status.CANCELLED, "test", 3));
      assertFalse(store.parentBusy(original.first()));
      assertFalse(store.hasPending());
      assertTrue(store.purchaseExists(purchase.id()));
      long size = Files.size(path);
      store.put(store.jobs.get(original.id()));
      assertEquals(size, Files.size(path));
    }
  }

  @Test
  void offlineCompactionPreservesExactSnapshotsAndFullOriginalAudit() throws Exception {
    var path = directory.resolve("compact.wal");
    var original = job();
    var purchase =
        new BreedingJob.Purchase(
            UUID.randomUUID(), "diamondcoin", 1, 60000, 60000, BreedingJob.Payment.PENDING);
    var pending = original.begin(purchase, 1).payment(BreedingJob.Payment.UNCERTAIN, 2);
    long clock;
    try (var store = new BreedingStore(path)) {
      store.put(original);
      store.put(pending);
      for (int n = 0; n < 20; n++) store.checkpoint();
      clock = store.clock;
    }
    byte[] before = Files.readAllBytes(path);
    var result = BreedingStorageMaintenance.compact(path);
    assertTrue(result.afterBytes() < result.beforeBytes());
    try (var archived = new java.util.zip.GZIPInputStream(Files.newInputStream(result.archive()))) {
      assertArrayEquals(before, archived.readAllBytes());
    }
    try (var store = new BreedingStore(path)) {
      assertEquals(pending, store.jobs.get(original.id()));
      assertEquals(clock, store.clock);
      assertTrue(store.purchaseExists(purchase.id()));
      assertTrue(store.parentBusy(original.first()));
      assertEquals(2, store.sequence);
      store.put(pending.phase(BreedingJob.Status.BLOCKED, "PC_FULL", 3));
    }
    try (var store = new BreedingStore(path)) {
      assertEquals(BreedingJob.Status.BLOCKED, store.jobs.get(original.id()).status());
      assertTrue(store.purchaseExists(purchase.id()));
    }
  }

  @Test
  void compactRefusesActiveWriterOrCorruptedJournalWithoutChangingEvidence() throws Exception {
    var path = directory.resolve("busy.wal");
    var archived = directory.resolve("before.wal");
    try (var store = new BreedingStore(path)) {
      store.put(job());
      store.archive(archived);
      assertThrows(java.io.IOException.class, () -> BreedingStorageMaintenance.compact(path));
    }
    assertArrayEquals(Files.readAllBytes(archived), Files.readAllBytes(path));
    var bad = Files.readAllBytes(path);
    bad[bad.length - 1] ^= 1;
    Files.write(path, bad);
    assertThrows(java.io.IOException.class, () -> BreedingStorageMaintenance.compact(path));
    assertArrayEquals(bad, Files.readAllBytes(path));
  }

  @Test
  void breedingGenerationPolicyDefaultsIndependentAndCanBeRestricted() throws Exception {
    var path = directory.resolve("breeding.json");
    Files.writeString(path, "{\"enabled\":true}");
    assertFalse(BreedingConfig.load(path).respectGenerationRestrictions);
    Files.writeString(path, "{\"enabled\":true,\"respectGenerationRestrictions\":true}");
    assertTrue(BreedingConfig.load(path).respectGenerationRestrictions);
  }

  private BreedingJob job() {
    var child =
        new BreedingJob.Child(
            1,
            UUID.randomUUID(),
            "cobblemon:bulbasaur",
            "normal",
            "bulbasaur gender=female",
            Collections.nCopies(6, 31),
            "cobblemon:hardy",
            "overgrow",
            "FEMALE",
            "cobblemon:poke_ball",
            false,
            false,
            1);
    return new BreedingJob(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        child,
        0,
        86400000,
        7200000,
        0,
        0,
        BreedingJob.Status.ACTIVE,
        "",
        List.of());
  }

  @Test
  void adminDeadlinePersistsAndLegacySnapshotsStillLoad() throws Exception {
    var original = job();
    var ready = original.completeNow(10000, "admin-test");
    var path = directory.resolve("admin.wal");
    try (var store = new BreedingStore(path)) {
      store.put(ready);
    }
    try (var store = new BreedingStore(path)) {
      assertEquals(10000, store.jobs.get(original.id()).readyAt());
      assertEquals(original.startedAt(), store.jobs.get(original.id()).startedAt());
    }
    var event = new com.google.gson.JsonObject();
    event.addProperty("schema", 1);
    event.addProperty("sequence", 1);
    event.addProperty("observedAt", 10000);
    var payload = new com.google.gson.Gson().toJsonTree(original).getAsJsonObject();
    payload.remove("readyOverrideAt");
    event.add("job", payload);
    var legacy = directory.resolve("legacy.wal");
    try (var journal =
        com.zianblk.zianutilities.core.breeding.BreedingJournal.open(legacy, t -> {})) {
      journal.append(event.toString());
    }
    try (var store = new BreedingStore(legacy)) {
      assertEquals(original.readyAt(), store.jobs.get(original.id()).readyAt());
      assertEquals(0, store.jobs.get(original.id()).readyOverrideAt());
    }
  }

  @Test
  void pendingDebitReplaysWithExactNonceAndNoAssumedReduction() throws Exception {
    var original = job();
    var debit =
        new BreedingJob.Purchase(
            UUID.randomUUID(), "diamondcoin", 3, 1200000, 3600000, BreedingJob.Payment.PENDING);
    var pending = original.begin(debit, 1234);
    var path = directory.resolve("journal.wal");
    try (var store = new BreedingStore(path)) {
      store.put(pending);
      store.checkpoint();
    }
    try (var store = new BreedingStore(path)) {
      assertEquals(pending, store.jobs.get(original.id()));
      assertTrue(store.purchaseExists(debit.id()));
      assertEquals(0, store.jobs.get(original.id()).reductionMillis());
      assertTrue(store.jobs.get(original.id()).occupiesSlot());
      assertTrue(store.now() >= store.clock);
    }
  }

  @Test
  void savedDeadlineDoesNotChangeWhenRatesAreReloaded() throws Exception {
    var original = job();
    var purchase =
        new BreedingJob.Purchase(
            UUID.randomUUID(), "coppercoin", 10, 120000, 1200000, BreedingJob.Payment.PENDING);
    var paid = original.begin(purchase, 10).payment(BreedingJob.Payment.APPLIED, 20);
    var path = directory.resolve("journal.wal");
    try (var store = new BreedingStore(path)) {
      store.put(paid);
    }
    var changed = new BreedingConfig();
    changed.currencyReductionsMinutes = Map.of("coppercoin", 500L);
    changed.validate();
    try (var store = new BreedingStore(path)) {
      assertEquals(85200000, store.jobs.get(original.id()).readyAt());
    }
  }

  @Test
  void backwardsClockCannotMoveBelowPersistedCheckpoint() throws Exception {
    var path = directory.resolve("journal.wal");
    long future = System.currentTimeMillis() + 86400000;
    try (var store = new BreedingStore(path)) {
      store.clock = future;
      store.checkpoint();
    }
    try (var store = new BreedingStore(path)) {
      assertEquals(future, store.now());
    }
  }

  @Test
  void duplicateConfigurationKeysAreRejectedWithoutOverwritingFile() throws Exception {
    var path = directory.resolve("config.json");
    String source = "{\"currencyReductionsMinutes\":{\"goldcoin\":5,\"goldcoin\":99}}";
    Files.writeString(path, source);
    assertThrows(IllegalArgumentException.class, () -> BreedingConfig.load(path));
    assertEquals(source, Files.readString(path));
  }

  @Test
  void unknownManagedCurrenciesRemainConfigurationOnlyAndBadRatesFail() {
    var config = new BreedingConfig();
    config.currencyReductionsMinutes = Map.of("avecoins:custom_currency", 17L);
    config.validate();
    config.currencyReductionsMinutes = Map.of("custom_currency", 0L);
    assertThrows(IllegalArgumentException.class, config::validate);
    config.minimumMinutes = 119;
    assertThrows(IllegalArgumentException.class, config::validate);
  }
}
