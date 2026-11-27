package com.zianblk.zianutilities.neoforge.pase;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.zianblk.zianutilities.core.pase.MissionDef;
import com.zianblk.zianutilities.core.pase.MissionType;
import com.zianblk.zianutilities.core.pase.PaseRules;
import com.zianblk.zianutilities.core.storage.StorageFiles;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * config/zianutilities-pase.json. formatVersion 1, disabled by default. Any problem (unknown key, bad value, newer
 * formatVersion, broken JSON) throws and the module stays off: it never runs on a half-understood file. The XP values
 * are a starting point to calibrate on the server (see the PaseSim simulator).
 */
public record PaseConfig(boolean enabled, PaseRules rules, Instant firstStart, int afkSeconds, Map<String, Source> sources) {
  public static final int FORMAT_VERSION = 1;

  /** XP and daily cap (base XP) of a non-mission source. */
  public record Source(boolean enabled, int xp, int dailyCap) {}

  static final String BOSS = "boss", RAID = "raid", WEEKLY_RAID = "weeklyRaid";
  private static final Set<String> TOP = Set.of("formatVersion", "enabled", "xp", "season", "daily", "streak", "afk", "missions", BOSS, RAID, WEEKLY_RAID);
  private static final Set<String> XP = Set.of("levels", "xpPerLevel", "dailyCap", "boostCap", "boostFactor", "boostDays", "extraLevelXp", "maxExtraLevels");
  private static final Set<String> SEASON = Set.of("months", "firstStart");
  private static final Set<String> DAILY = Set.of("resetTime", "zone");
  private static final Set<String> STREAK = Set.of("enabled");
  private static final Set<String> AFK = Set.of("seconds");
  private static final Set<String> MISSIONS = Set.of("dailyCap", "perDay", "types");
  private static final Set<String> MISSION = Set.of("enabled", "target", "xp");
  private static final Set<String> SOURCE = Set.of("enabled", "xp", "dailyCap");

  static String key(MissionType t) {
    return switch (t) {
      case BREAK_BLOCKS -> "breakBlocks";
      case HARVEST_CROPS -> "harvestCrops";
      case KILL_MOBS -> "killMobs";
      case BREED_ANIMALS -> "breedAnimals";
      case TRADE -> "trade";
      case EXPLORE -> "explore";
      case FISH -> "fish";
    };
  }

  public Source source(String name) { return sources.get(name); }

  public static PaseConfig defaults() { return parse(defaultJson()); }

  public static PaseConfig parse(String json) {
    try {
      return parse0(json);
    } catch (IllegalStateException | UnsupportedOperationException | ClassCastException | NumberFormatException bad) {
      throw new IllegalArgumentException("Valor con tipo incorrecto: " + bad.getMessage(), bad);
    } catch (DateTimeException bad) {
      throw new IllegalArgumentException("Fecha, hora o zona inválida: " + bad.getMessage(), bad);
    }
  }

  private static PaseConfig parse0(String json) {
    JsonObject root;
    try {
      JsonElement element = JsonParser.parseString(json);
      if (!element.isJsonObject()) throw new IllegalArgumentException("El archivo debe ser un objeto JSON");
      root = element.getAsJsonObject();
    } catch (JsonParseException broken) {
      throw new IllegalArgumentException("JSON inválido: " + broken.getMessage(), broken);
    }
    unknown(root, TOP, "raíz");
    if (!root.has("formatVersion") || !root.get("formatVersion").isJsonPrimitive() || root.get("formatVersion").getAsInt() != FORMAT_VERSION)
      throw new IllegalArgumentException("formatVersion no soportado (se esperaba " + FORMAT_VERSION + ")");
    PaseRules d = PaseRules.defaults();
    boolean enabled = bool(root, "enabled", false);

    JsonObject xp = section(root, "xp", XP);
    int dailyCap = integer(xp, "dailyCap", d.dailyCap(), 1, 1_000_000);
    int boostFactor = integer(xp, "boostFactor", d.boostFactor(), 1, 10);
    int boostCap = integer(xp, "boostCap", dailyCap * boostFactor, 1, 10_000_000);
    if (boostCap < dailyCap) throw new IllegalArgumentException("xp.boostCap no puede ser menor que xp.dailyCap");
    JsonObject season = section(root, "season", SEASON);
    JsonObject daily = section(root, "daily", DAILY);
    ZoneId zone = daily.has("zone") ? ZoneId.of(daily.get("zone").getAsString()) : d.zone();
    LocalTime reset = daily.has("resetTime") ? LocalTime.parse(daily.get("resetTime").getAsString()) : d.resetTime();
    Instant first = null;
    if (season.has("firstStart") && !season.get("firstStart").getAsString().isBlank()) {
      try {
        first = Instant.parse(season.get("firstStart").getAsString());
      } catch (DateTimeParseException bad) {
        throw new IllegalArgumentException("season.firstStart debe ser una fecha ISO como 2026-12-01T00:00:00Z");
      }
    }
    boolean streak = bool(section(root, "streak", STREAK), "enabled", false);
    int afk = integer(section(root, "afk", AFK), "seconds", 60, 5, 3600);

    JsonObject missions = section(root, "missions", MISSIONS);
    int perDay = integer(missions, "perDay", d.missionsPerDay(), 0, MissionType.values().length);
    int missionCap = integer(missions, "dailyCap", Math.min(1400, (int) Math.floor(PaseRules.MISSION_SHARE * dailyCap)), 0, dailyCap);
    if (missionCap > (int) Math.floor(PaseRules.MISSION_SHARE * dailyCap))
      throw new IllegalArgumentException("missions.dailyCap no puede pasar del 35 % del tope diario (" + (int) Math.floor(PaseRules.MISSION_SHARE * dailyCap) + ")");
    Map<MissionType, MissionDef> defs = new EnumMap<>(MissionType.class);
    JsonObject types = missions.has("types") ? missions.getAsJsonObject("types") : new JsonObject();
    Set<String> known = new java.util.HashSet<>();
    for (MissionType t : MissionType.values()) known.add(key(t));
    unknown(types, known, "missions.types");
    for (MissionType t : MissionType.values()) {
      MissionDef base = d.missions().get(t);
      JsonObject o = types.has(key(t)) ? types.getAsJsonObject(key(t)) : new JsonObject();
      unknown(o, MISSION, "missions.types." + key(t));
      if (!bool(o, "enabled", true)) continue;
      defs.put(t, new MissionDef(t, integer(o, "target", base.target(), 1, 100_000), integer(o, "xp", base.xp(), 0, dailyCap)));
    }

    Map<String, Source> sources = new LinkedHashMap<>();
    sources.put(BOSS, source(root, BOSS, 400, 1600, dailyCap));
    sources.put(RAID, source(root, RAID, 500, 1500, dailyCap));
    sources.put(WEEKLY_RAID, source(root, WEEKLY_RAID, 1500, 1500, dailyCap));

    Map<String, Integer> caps = Map.of(PaseRules.MISSION, missionCap, BOSS, sources.get(BOSS).dailyCap(), RAID, sources.get(RAID).dailyCap(),
        WEEKLY_RAID, sources.get(WEEKLY_RAID).dailyCap());
    PaseRules rules = new PaseRules(
        integer(xp, "levels", d.levels(), 1, 10_000), integer(xp, "xpPerLevel", d.xpPerLevel(), 1, 10_000_000), dailyCap, boostCap, boostFactor,
        integer(xp, "boostDays", d.boostDays(), 0, 365), integer(xp, "extraLevelXp", d.extraLevelXp(), 1, 10_000_000),
        integer(xp, "maxExtraLevels", d.maxExtraLevels(), 0, 100_000), reset, zone, integer(season, "months", d.seasonMonths(), 1, 24), streak, caps, perDay, defs);
    return new PaseConfig(enabled, rules, first, afk, Map.copyOf(sources));
  }

  private static Source source(JsonObject root, String name, int xp, int cap, int dailyCap) {
    JsonObject o = section(root, name, SOURCE);
    return new Source(bool(o, "enabled", true), integer(o, "xp", xp, 0, dailyCap), integer(o, "dailyCap", Math.min(cap, dailyCap), 0, dailyCap));
  }

  private static JsonObject section(JsonObject parent, String name, Set<String> allowed) {
    if (!parent.has(name)) return new JsonObject();
    if (!parent.get(name).isJsonObject()) throw new IllegalArgumentException(name + " debe ser un objeto");
    JsonObject o = parent.getAsJsonObject(name);
    unknown(o, allowed, name);
    return o;
  }

  private static int integer(JsonObject o, String key, int fallback, int min, int max) {
    if (!o.has(key)) return fallback;
    var p = o.get(key).getAsJsonPrimitive();
    if (!p.isNumber() || p.getAsDouble() != Math.rint(p.getAsDouble())) throw new IllegalArgumentException(key + " debe ser un número entero");
    int v = p.getAsInt();
    if (v < min || v > max) throw new IllegalArgumentException(key + " debe estar entre " + min + " y " + max);
    return v;
  }

  private static boolean bool(JsonObject o, String key, boolean fallback) {
    if (!o.has(key)) return fallback;
    var p = o.get(key).getAsJsonPrimitive();
    if (!p.isBoolean()) throw new IllegalArgumentException(key + " debe ser true o false");
    return p.getAsBoolean();
  }

  private static void unknown(JsonObject o, Set<String> allowed, String where) {
    for (String k : o.keySet()) if (!allowed.contains(k)) throw new IllegalArgumentException("Clave desconocida en " + where + ": " + k);
  }

  /** The file written the first time. */
  public static String defaultJson() {
    PaseRules d = PaseRules.defaults();
    var root = new LinkedHashMap<String, Object>();
    root.put("formatVersion", FORMAT_VERSION);
    root.put("enabled", false);
    var xp = new LinkedHashMap<String, Object>();
    xp.put("levels", d.levels());
    xp.put("xpPerLevel", d.xpPerLevel());
    xp.put("dailyCap", d.dailyCap());
    xp.put("boostCap", d.boostCap());
    xp.put("boostFactor", d.boostFactor());
    xp.put("boostDays", d.boostDays());
    xp.put("extraLevelXp", d.extraLevelXp());
    xp.put("maxExtraLevels", d.maxExtraLevels());
    root.put("xp", xp);
    var season = new LinkedHashMap<String, Object>();
    season.put("months", d.seasonMonths());
    season.put("firstStart", "");
    root.put("season", season);
    var daily = new LinkedHashMap<String, Object>();
    daily.put("resetTime", "04:00");
    daily.put("zone", d.zone().getId());
    root.put("daily", daily);
    root.put("streak", Map.of("enabled", false));
    root.put("afk", Map.of("seconds", 60));
    var missions = new LinkedHashMap<String, Object>();
    missions.put("dailyCap", d.sourceCaps().get(PaseRules.MISSION));
    missions.put("perDay", d.missionsPerDay());
    var types = new LinkedHashMap<String, Object>();
    for (MissionType t : MissionType.values()) {
      var m = new LinkedHashMap<String, Object>();
      m.put("enabled", true);
      m.put("target", d.missions().get(t).target());
      m.put("xp", d.missions().get(t).xp());
      types.put(key(t), m);
    }
    missions.put("types", types);
    root.put("missions", missions);
    root.put(BOSS, sourceJson(400, 1600));
    root.put(RAID, sourceJson(500, 1500));
    root.put(WEEKLY_RAID, sourceJson(1500, 1500));
    return new GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n";
  }

  private static Map<String, Object> sourceJson(int xp, int cap) {
    var m = new LinkedHashMap<String, Object>();
    m.put("enabled", true);
    m.put("xp", xp);
    m.put("dailyCap", cap);
    return m;
  }

  /** Reads the file, creating it with the defaults the first time. */
  public static PaseConfig load(Path path) throws IOException {
    if (!StorageFiles.exists(path)) {
      StorageFiles.createDirectories(path.getParent());
      Path temp = StorageFiles.createTempFile(path.getParent(), "zianutilities-pase-", ".tmp");
      try {
        StorageFiles.writeString(temp, defaultJson());
        StorageFiles.move(temp, path, StandardCopyOption.ATOMIC_MOVE);
      } finally {
        StorageFiles.deleteIfExists(temp);
      }
    }
    return parse(StorageFiles.readString(path));
  }
}
