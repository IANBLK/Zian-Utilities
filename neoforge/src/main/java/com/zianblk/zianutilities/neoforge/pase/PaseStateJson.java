package com.zianblk.zianutilities.neoforge.pase;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zianblk.zianutilities.core.pase.LevelEvent;
import com.zianblk.zianutilities.core.pase.PlayerPase;
import com.zianblk.zianutilities.core.pase.Season;
import com.zianblk.zianutilities.core.pase.SeasonBook;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Reading and writing of the pass data files (players/&lt;uuid&gt;.json and seasons.json). Every file carries formatVersion.
 * A newer version, a missing one or a broken file throws and is never overwritten. Version 1 is the first: older
 * versions would be migrated in {@link #migrate}, which today has nothing to do; fields added later are optional.
 */
final class PaseStateJson {
  static final int FORMAT_VERSION = 1;

  private PaseStateJson() {}

  /** Checks the version and brings an older file up to the current one. Newer files are refused so they are never half-read. */
  static JsonObject migrate(JsonObject o) {
    if (!o.has("formatVersion") || !o.get("formatVersion").isJsonPrimitive()) throw new IllegalArgumentException("falta formatVersion");
    int v = o.get("formatVersion").getAsInt();
    if (v > FORMAT_VERSION) throw new IllegalArgumentException("formatVersion " + v + " es de una versión más nueva que esta (" + FORMAT_VERSION + ")");
    if (v < 1) throw new IllegalArgumentException("formatVersion " + v + " no soportado");
    return o;
  }

  // ---------------------------------------------------------------- players

  static PlayerPase readPlayer(String text, UUID expected) {
    JsonObject o = migrate(JsonParser.parseString(text).getAsJsonObject());
    UUID id = UUID.fromString(o.get("id").getAsString());
    if (!id.equals(expected)) throw new IllegalArgumentException("el archivo es de otro jugador");
    PlayerPase p = new PlayerPase(id);
    if (o.has("seasons"))
      for (var e : o.getAsJsonObject("seasons").entrySet()) {
        JsonObject s = e.getValue().getAsJsonObject();
        PlayerPase.Progress pr = p.progress(e.getKey());
        pr.xp = nonNegative(s, "xp");
        pr.extraXp = nonNegative(s, "extraXp");
      }
    if (o.has("premium")) for (JsonElement e : o.getAsJsonArray("premium")) p.premium.add(e.getAsString());
    if (o.has("events"))
      for (JsonElement e : o.getAsJsonArray("events")) {
        JsonObject ev = e.getAsJsonObject();
        p.events.add(new LevelEvent(ev.get("season").getAsString(), ev.get("level").getAsInt(), ev.get("extra").getAsBoolean(), ev.get("at").getAsLong()));
      }
    if (o.has("explored"))
      for (var e : o.getAsJsonObject("explored").entrySet()) {
        Set<String> set = new LinkedHashSet<>();
        for (JsonElement k : e.getValue().getAsJsonArray()) set.add(k.getAsString());
        p.explored.put(e.getKey(), set);
      }
    if (o.has("day")) p.day = o.get("day").getAsString();
    if (o.has("baseToday")) p.baseToday = nonNegative(o, "baseToday");
    if (o.has("finalToday")) p.finalToday = nonNegative(o, "finalToday");
    if (o.has("perSource")) for (var e : o.getAsJsonObject("perSource").entrySet()) p.perSource.put(e.getKey(), e.getValue().getAsInt());
    if (o.has("missionProgress")) for (var e : o.getAsJsonObject("missionProgress").entrySet()) p.missionProgress.put(e.getKey(), e.getValue().getAsInt());
    if (o.has("missionDone")) for (JsonElement e : o.getAsJsonArray("missionDone")) p.missionDone.add(e.getAsString());
    if (o.has("trades")) for (JsonElement e : o.getAsJsonArray("trades")) p.trades.add(e.getAsString());
    if (o.has("warned")) for (JsonElement e : o.getAsJsonArray("warned")) p.warned.add(e.getAsString());
    return p;
  }

  private static long nonNegative(JsonObject o, String key) {
    long v = o.has(key) ? o.get(key).getAsLong() : 0;
    if (v < 0) throw new IllegalArgumentException(key + " negativo");
    return v;
  }

  static String writePlayer(PlayerPase p) {
    JsonObject o = new JsonObject();
    o.addProperty("formatVersion", FORMAT_VERSION);
    o.addProperty("id", p.id.toString());
    JsonObject seasons = new JsonObject();
    for (var e : p.seasons.entrySet()) {
      JsonObject s = new JsonObject();
      s.addProperty("xp", e.getValue().xp);
      s.addProperty("extraXp", e.getValue().extraXp);
      seasons.add(e.getKey(), s);
    }
    o.add("seasons", seasons);
    o.add("premium", strings(p.premium));
    JsonArray events = new JsonArray();
    for (LevelEvent ev : p.events) {
      JsonObject e = new JsonObject();
      e.addProperty("season", ev.seasonId());
      e.addProperty("level", ev.level());
      e.addProperty("extra", ev.extra());
      e.addProperty("at", ev.atMillis());
      events.add(e);
    }
    o.add("events", events);
    JsonObject explored = new JsonObject();
    for (var e : p.explored.entrySet()) explored.add(e.getKey(), strings(e.getValue()));
    o.add("explored", explored);
    o.addProperty("day", p.day);
    o.addProperty("baseToday", p.baseToday);
    o.addProperty("finalToday", p.finalToday);
    o.add("perSource", ints(p.perSource));
    o.add("missionProgress", ints(p.missionProgress));
    o.add("missionDone", strings(p.missionDone));
    o.add("trades", strings(p.trades));
    o.add("warned", strings(p.warned));
    return new GsonBuilder().setPrettyPrinting().create().toJson(o) + "\n";
  }

  private static JsonArray strings(Iterable<String> values) {
    JsonArray a = new JsonArray();
    for (String s : values) a.add(s);
    return a;
  }

  private static JsonObject ints(Map<String, Integer> values) {
    JsonObject o = new JsonObject();
    values.forEach(o::addProperty);
    return o;
  }

  // ---------------------------------------------------------------- seasons

  static SeasonBook readSeasons(String text) {
    JsonObject o = migrate(JsonParser.parseString(text).getAsJsonObject());
    Season current = season(o.getAsJsonObject("current"));
    Season previous = o.has("previous") && o.get("previous").isJsonObject() ? season(o.getAsJsonObject("previous")) : null;
    return new SeasonBook(current, previous);
  }

  private static Season season(JsonObject o) {
    return new Season(o.get("id").getAsString(), Instant.parse(o.get("start").getAsString()), Instant.parse(o.get("end").getAsString()));
  }

  static String writeSeasons(SeasonBook book) {
    JsonObject o = new JsonObject();
    o.addProperty("formatVersion", FORMAT_VERSION);
    o.add("current", season(book.current()));
    if (book.previous() != null) o.add("previous", season(book.previous()));
    return new GsonBuilder().setPrettyPrinting().create().toJson(o) + "\n";
  }

  private static JsonObject season(Season s) {
    JsonObject o = new JsonObject();
    o.addProperty("id", s.id());
    o.addProperty("start", s.start().toString());
    o.addProperty("end", s.end().toString());
    return o;
  }
}
