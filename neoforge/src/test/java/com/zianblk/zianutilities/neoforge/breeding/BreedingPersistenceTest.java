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
