package com.zianblk.zianutilities.neoforge.raids;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.zianblk.zianutilities.core.raids.weekly.WeeklyCounts;
import com.zianblk.zianutilities.core.raids.weekly.WeeklySchedule;
import com.zianblk.zianutilities.core.storage.StorageFiles;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.UUID;

/**
 * weekly-state.json: the active week (id, start, end, frozen snapshot of that raid's config) and what each player
 * has used. Written atomically after every change. A corrupt file or a newer formatVersion makes load throw and is
 * never overwritten, so a bad file cannot silently hand out a fresh week.
 */
final class WeeklyState {
  static final int FORMAT_VERSION = 1;

  WeeklySchedule.Week week;
  JsonObject snapshot;
  final WeeklyCounts counts = new WeeklyCounts();
  /** Snapshots of earlier weeks, newest first: their bosses must stay known to Raid Dens while a reward is still pending. */
  final java.util.List<Past> previous = new java.util.ArrayList<>();
  static final int KEEP_PAST = 8;

  record Past(long weekNumber, JsonObject snapshot) {}

  WeeklyState(WeeklySchedule.Week week, JsonObject snapshot) {
    this.week = week;
    this.snapshot = snapshot;
  }

  /** null when there is no file yet. */
  static WeeklyState load(Path file) throws IOException {
    if (!StorageFiles.exists(file)) return null;
    try {
      return fromJson(StorageFiles.readString(file));
    } catch (RuntimeException bad) {
      throw new IOException("weekly-state.json no se puede leer: " + bad.getMessage(), bad);
    }
  }

  static WeeklyState fromJson(String text) {
    JsonObject o = JsonParser.parseString(text).getAsJsonObject();
    if (!o.has("formatVersion") || o.get("formatVersion").getAsInt() != FORMAT_VERSION)
      throw new IllegalArgumentException("formatVersion " + (o.has("formatVersion") ? o.get("formatVersion") : "ausente") + " no soportado");
    var week = new WeeklySchedule.Week(o.get("weekNumber").getAsLong(), o.get("activeId").getAsString(), Instant.parse(o.get("startedAt").getAsString()),
        Instant.parse(o.get("endsAt").getAsString()), o.get("listIndex").getAsInt(), o.get("shuffleSeed").getAsLong(), o.get("reason").getAsString());
    if (!week.endsAt().isAfter(week.startedAt())) throw new IllegalArgumentException("endsAt no es posterior a startedAt");
    WeeklyState state = new WeeklyState(week, o.getAsJsonObject("snapshot"));
    if (state.snapshot == null || !state.snapshot.has("id")) throw new IllegalArgumentException("falta el snapshot");
    for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("players").entrySet()) {
      JsonObject p = e.getValue().getAsJsonObject();
      state.counts.set(UUID.fromString(e.getKey()), p.get("attempts").getAsInt(), p.get("rewards").getAsInt());
    }
    if (o.has("previousSnapshots"))
      for (JsonElement e : o.getAsJsonArray("previousSnapshots")) {
        JsonObject p = e.getAsJsonObject();
        state.previous.add(new Past(p.get("weekNumber").getAsLong(), p.getAsJsonObject("snapshot")));
      }
    return state;
  }

  String toJson() {
    JsonObject o = new JsonObject();
    o.addProperty("formatVersion", FORMAT_VERSION);
    o.addProperty("weekNumber", week.weekNumber());
    o.addProperty("activeId", week.activeId());
    o.addProperty("startedAt", week.startedAt().toString());
    o.addProperty("endsAt", week.endsAt().toString());
    o.addProperty("listIndex", week.listIndex());
    o.addProperty("shuffleSeed", week.seed());
    o.addProperty("reason", week.reason());
    o.add("snapshot", snapshot);
    JsonObject players = new JsonObject();
    for (var e : counts.all().entrySet()) {
      JsonObject p = new JsonObject();
      p.addProperty("attempts", e.getValue().attempts());
      p.addProperty("rewards", e.getValue().rewards());
      players.add(e.getKey().toString(), p);
    }
    o.add("players", players);
    var past = new com.google.gson.JsonArray();
    for (Past p : previous) {
      JsonObject e = new JsonObject();
      e.addProperty("weekNumber", p.weekNumber());
      e.add("snapshot", p.snapshot());
      past.add(e);
    }
    o.add("previousSnapshots", past);
    return new GsonBuilder().setPrettyPrinting().create().toJson(o) + "\n";
  }

  void save(Path file) throws IOException {
    StorageFiles.createDirectories(file.getParent());
    Path temp = StorageFiles.createTempFile(file.getParent(), "weekly-state-", ".tmp");
    try {
      StorageFiles.writeString(temp, toJson());
      StorageFiles.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      StorageFiles.deleteIfExists(temp);
    }
  }
}
