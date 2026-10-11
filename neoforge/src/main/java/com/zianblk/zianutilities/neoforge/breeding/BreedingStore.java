package com.zianblk.zianutilities.neoforge.breeding;

import com.google.gson.*;
import com.zianblk.zianutilities.core.breeding.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

final class BreedingStore implements AutoCloseable {
  final Map<UUID, BreedingJob> jobs = new LinkedHashMap<>();
  private final Gson gson = new Gson();
  private final BreedingJournal journal;
  long clock, sequence;

  BreedingStore(Path path) throws IOException {
    journal = BreedingJournal.open(path, this::replay);
  }

  private void replay(String text) {
    var e = JsonParser.parseString(text).getAsJsonObject();
    if (e.get("schema").getAsInt() != 1 || e.get("sequence").getAsLong() != sequence + 1)
      throw new IllegalArgumentException("Journal sequence");
    sequence++;
    clock = Math.max(clock, e.get("observedAt").getAsLong());
    if (e.has("job")) {
      var j = gson.fromJson(e.get("job"), BreedingJob.class);
      jobs.put(j.id(), j);
    }
  }

  long now() {
    return clock = Math.max(clock, System.currentTimeMillis());
  }

  void put(BreedingJob job) throws IOException {
    var e = event();
    e.add("job", gson.toJsonTree(job));
    journal.append(gson.toJson(e));
    sequence++;
    jobs.put(job.id(), job);
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
    return jobs.values().stream()
        .flatMap(j -> j.purchases().stream())
        .anyMatch(p -> p.id().equals(nonce));
  }

  public void close() throws IOException {
    journal.close();
  }
}
