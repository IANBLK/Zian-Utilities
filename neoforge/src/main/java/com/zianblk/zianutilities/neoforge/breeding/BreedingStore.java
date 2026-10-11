package com.zianblk.zianutilities.neoforge.breeding;

import com.google.gson.*;
import com.zianblk.zianutilities.core.breeding.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

final class BreedingStore implements AutoCloseable {
  private record Reference(UUID owner, BreedingJob.Status status, BreedingJournal.Frame frame) {}

  private final LinkedHashMap<UUID, Reference> references = new LinkedHashMap<>();
  private final LinkedHashMap<UUID, BreedingJob> pending = new LinkedHashMap<>();
  final Map<UUID, BreedingJob> jobs =
      new AbstractMap<>() {
        @Override
        public BreedingJob get(Object id) {
          return lookup(id);
        }

        @Override
        public int size() {
          return references.size();
        }

        @Override
        public boolean containsKey(Object id) {
          return references.containsKey(id);
        }

        @Override
        public Set<UUID> keySet() {
          return Collections.unmodifiableSet(references.keySet());
        }

        @Override
        public Set<Map.Entry<UUID, BreedingJob>> entrySet() {
          return new AbstractSet<>() {
            @Override
            public int size() {
              return references.size();
            }

            @Override
            public Iterator<Map.Entry<UUID, BreedingJob>> iterator() {
              var ids = references.keySet().iterator();
              return new Iterator<>() {
                public boolean hasNext() {
                  return ids.hasNext();
                }

                public Map.Entry<UUID, BreedingJob> next() {
                  var id = ids.next();
                  return new SimpleImmutableEntry<>(id, lookup(id));
                }
              };
            }
          };
        }
      };
  private final Map<UUID, LinkedHashSet<UUID>> owners = new HashMap<>();
  private final Map<UUID, Set<UUID>> parents = new HashMap<>();
  private final Set<UUID> purchaseIds = new HashSet<>();
  private final LinkedHashSet<UUID> ticking = new LinkedHashSet<>();
  private boolean compactionCompatible = true;
  private final Gson gson = new Gson();
  private final BreedingJournal journal;
  long clock, sequence;

  BreedingStore(Path path) throws IOException {
    journal = BreedingJournal.openIndexed(path, this::replay, ignored -> {});
  }

  private void replay(BreedingJournal.Frame frame, String text) {
    var e = JsonParser.parseString(text).getAsJsonObject();
    if (e.get("schema").getAsInt() != 1 || e.get("sequence").getAsLong() != sequence + 1)
      throw new IllegalArgumentException("Journal sequence");
    sequence++;
    clock = Math.max(clock, e.get("observedAt").getAsLong());
    if (e.has("job")) {
      var j = gson.fromJson(e.get("job"), BreedingJob.class);
      var jobJson = e.getAsJsonObject("job");
      compactionCompatible &=
          knownFields(jobJson, BreedingJob.class)
              && knownFields(jobJson.getAsJsonObject("child"), BreedingJob.Child.class);
      for (var purchase : jobJson.getAsJsonArray("purchases"))
        compactionCompatible &= knownFields(purchase.getAsJsonObject(), BreedingJob.Purchase.class);
      index(j, frame);
    }
    compactionCompatible &=
        Set.of("schema", "sequence", "observedAt", "job").containsAll(e.keySet());
  }

  private static boolean knownFields(JsonObject value, Class<?> type) {
    var names = new HashSet<String>();
    for (var field : type.getRecordComponents()) names.add(field.getName());
    return names.containsAll(value.keySet());
  }

  boolean compactionCompatible() {
    return compactionCompatible;
  }

  long now() {
    return clock = Math.max(clock, System.currentTimeMillis());
  }

  void put(BreedingJob job) throws IOException {
    if (journal.locked()) throw new IOException("Breeding journal locked");
    if (job.equals(jobs.get(job.id()))) return;
    var e = event();
    e.add("job", gson.toJsonTree(job));
    var frame = journal.appendIndexed(gson.toJson(e));
    sequence++;
    index(job, frame);
  }

  private void index(BreedingJob job, BreedingJournal.Frame frame) {
    var old = pending.remove(job.id());
    var oldReference = references.put(job.id(), new Reference(job.owner(), job.status(), frame));
    if (oldReference != null && !oldReference.owner().equals(job.owner()))
      owners.get(oldReference.owner()).remove(job.id());
    if (old != null) {
      var owner = owners.get(old.owner());
      if (!old.owner().equals(job.owner())) owner.remove(old.id());
      if (old.occupiesSlot())
        for (var parent : List.of(old.first(), old.second())) {
          var leases = parents.get(parent);
          leases.remove(old.id());
          if (leases.isEmpty()) parents.remove(parent);
        }
    }
    owners.computeIfAbsent(job.owner(), ignored -> new LinkedHashSet<>()).add(job.id());
    if (job.occupiesSlot())
      for (var parent : List.of(job.first(), job.second()))
        parents.computeIfAbsent(parent, ignored -> new HashSet<>()).add(job.id());
    if (job.occupiesSlot()) pending.put(job.id(), job);
    for (var purchase : job.purchases()) purchaseIds.add(purchase.id());
    if (job.status() == BreedingJob.Status.ACTIVE || job.status() == BreedingJob.Status.DELIVERING)
      ticking.add(job.id());
    else ticking.remove(job.id());
  }

  private BreedingJob lookup(Object id) {
    var active = pending.get(id);
    if (active != null) return active;
    var ref = references.get(id);
    if (ref == null) return null;
    try {
      return gson.fromJson(
          JsonParser.parseString(journal.readFrame(ref.frame())).getAsJsonObject().get("job"),
          BreedingJob.class);
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  List<BreedingJob> pending() {
    return List.copyOf(pending.values());
  }

  List<BreedingJob> pendingMine(UUID owner) {
    var ids = owners.get(owner);
    if (ids == null) return List.of();
    return ids.stream().filter(pending::containsKey).map(pending::get).toList();
  }

  void rememberUncertain(BreedingJob job) {
    if (job.status() != BreedingJob.Status.UNCERTAIN || !references.containsKey(job.id()))
      throw new IllegalArgumentException("Existing uncertain job required");
    index(job, references.get(job.id()).frame());
  }

  List<BreedingJob> mine(UUID owner) {
    return owners.getOrDefault(owner, new LinkedHashSet<>()).stream().map(jobs::get).toList();
  }

  List<BreedingJob> ticking() {
    return ticking.stream().map(jobs::get).toList();
  }

  boolean parentBusy(UUID parent) {
    return parents.containsKey(parent);
  }

  boolean hasPending() {
    return !parents.isEmpty();
  }

  Set<UUID> purchaseIndex() {
    return Set.copyOf(purchaseIds);
  }

  void archive(Path target) throws IOException {
    journal.archive(target);
  }

  void archiveCompressed(Path target) throws IOException {
    journal.archiveCompressed(target);
  }

  void checkpoint() throws IOException {
    journal.append(gson.toJson(event()));
    sequence++;
  }

  private JsonObject event() {
    var e = new JsonObject();
    e.addProperty("schema", 1);
    e.addProperty("sequence", sequence + 1);
    e.addProperty("observedAt", now());
    return e;
  }

  boolean locked() {
    return journal.locked();
  }

  boolean purchaseExists(UUID nonce) {
    return purchaseIds.contains(nonce);
  }

  public void close() throws IOException {
    journal.close();
  }
}
