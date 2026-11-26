package com.zianblk.zianutilities.neoforge.raids;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.zianblk.zianutilities.core.raids.DuelParams;
import com.zianblk.zianutilities.core.raids.RaidTierPolicy;
import com.zianblk.zianutilities.core.raids.weekly.WeeklySchedule;
import com.zianblk.zianutilities.core.storage.StorageFiles;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * config/zianutilities-weekly-raids.json. formatVersion 1, disabled by default. Like the duel config, any problem
 * (unknown key, bad value, newer formatVersion, broken JSON) throws and the weekly raid stays off.
 * The boss of every raid is written with the keys of the Raid Dens datapack (pokemon, raid_tier, raid_type, health_multi,
 * scale, lives, required_damage, loot_table, reward...): it is handed to RaidBoss.GSON untouched.
 */
public record WeeklyConfig(
    boolean enabled, ZoneId zone, DayOfWeek day, LocalTime time, WeeklySchedule.PickOrder order,
    Entrance entrance, int attemptsPerWeek, int rewardsPerWeek, int turnSeconds, List<Raid> raids
) {
  public static final int FORMAT_VERSION = 1;
  private static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,40}");
  private static final Pattern DIMENSION = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
  private static final List<String> TIERS = List.of("TIER_ONE", "TIER_TWO", "TIER_THREE", "TIER_FOUR", "TIER_FIVE", "TIER_SIX", "TIER_SEVEN");
  private static final Set<String> TOP = Set.of("formatVersion", "enabled", "rotation", "entrance", "limits", "queue", "raids");
  private static final Set<String> ROTATION = Set.of("dayOfWeek", "time", "zone", "pickOrder");
  private static final Set<String> ENTRANCE = Set.of("dimension", "x", "y", "z", "yaw");
  private static final Set<String> LIMITS = Set.of("attemptsPerWeek", "rewardsPerWeek");
  private static final Set<String> QUEUE = Set.of("turnSeconds");
  private static final Set<String> RAID = Set.of("id", "displayName", "mode", "boss", "duel", "limits");
  private static final Set<String> DUEL = Set.of("timeLimitSeconds", "bossHealth", "bossDamage", "attackIntervalTicks", "telegraphTicks", "maxDamagePerHitFraction", "phases");

  public enum Mode { DUEL, BATTLE }

  public record Entrance(String dimension, double x, double y, double z, float yaw) {}

  /** One raid of the rotation. raw is exactly what is frozen into weekly-state.json, with the effective limits written in. */
  public record Raid(String id, String displayName, Mode mode, JsonObject boss, DuelParams duel, int attemptsPerWeek, int rewardsPerWeek, JsonObject raw) {
    public int tier() { return RaidTierPolicy.tierOfOrdinal(TIERS.indexOf(boss.get("raid_tier").getAsString())); }

    public String species() { return boss.getAsJsonObject("pokemon").get("species").getAsString(); }
  }

  public WeeklySchedule.Rule rule() { return new WeeklySchedule.Rule(zone, day, time, order); }

  public List<String> ids() { return raids.stream().map(Raid::id).toList(); }

  public Raid raid(String id) {
    for (Raid r : raids) if (r.id().equals(id)) return r;
    return null;
  }

  public static WeeklyConfig parse(String json) {
    try {
      return parse0(json);
    } catch (IllegalStateException | UnsupportedOperationException | ClassCastException | NumberFormatException bad) {
      throw new IllegalArgumentException("Valor con tipo incorrecto: " + bad.getMessage(), bad);
    }
  }

  private static WeeklyConfig parse0(String json) {
    JsonObject root = object(parseJson(json), "raíz");
    unknown(root, TOP, "raíz");
    if (!root.has("formatVersion") || root.get("formatVersion").getAsInt() != FORMAT_VERSION)
      throw new IllegalArgumentException("formatVersion no soportado (se esperaba " + FORMAT_VERSION + ")");
    boolean enabled = bool(root, "enabled", false);

    JsonObject rot = root.has("rotation") ? object(root.get("rotation"), "rotation") : new JsonObject();
    unknown(rot, ROTATION, "rotation");
    DayOfWeek day;
    ZoneId zone;
    LocalTime time;
    WeeklySchedule.PickOrder order;
    try {
      day = DayOfWeek.valueOf(text(rot, "dayOfWeek", "MONDAY"));
      time = LocalTime.parse(text(rot, "time", "04:00"));
      String zoneText = text(rot, "zone", "");
      zone = zoneText.isBlank() ? ZoneId.systemDefault() : ZoneId.of(zoneText);
      order = WeeklySchedule.PickOrder.valueOf(text(rot, "pickOrder", "SEQUENTIAL"));
    } catch (DateTimeException | IllegalArgumentException bad) {
      throw new IllegalArgumentException("rotation inválida: " + bad.getMessage(), bad);
    }

    JsonObject ent = root.has("entrance") ? object(root.get("entrance"), "entrance") : new JsonObject();
    unknown(ent, ENTRANCE, "entrance");
    String dimension = text(ent, "dimension", "minecraft:overworld");
    if (!DIMENSION.matcher(dimension).matches()) throw new IllegalArgumentException("entrance.dimension no es un id válido: " + dimension);
    double x = number(ent, "x", 0.5), y = number(ent, "y", 80), z = number(ent, "z", 0.5), yaw = number(ent, "yaw", 180);
    if (Math.abs(x) > 3.0E7 || Math.abs(z) > 3.0E7 || y < -2048 || y > 2048) throw new IllegalArgumentException("entrance fuera de rango");
    if (yaw < -360 || yaw > 360) throw new IllegalArgumentException("entrance.yaw debe estar entre -360 y 360");

    JsonObject lim = root.has("limits") ? object(root.get("limits"), "limits") : new JsonObject();
    unknown(lim, LIMITS, "limits");
    int attempts = limit(lim, "attemptsPerWeek", 3), rewards = limit(lim, "rewardsPerWeek", 1);

    JsonObject queue = root.has("queue") ? object(root.get("queue"), "queue") : new JsonObject();
    unknown(queue, QUEUE, "queue");
    int turn = queue.has("turnSeconds") ? queue.get("turnSeconds").getAsInt() : 30;
    if (turn < 5 || turn > 600) throw new IllegalArgumentException("queue.turnSeconds debe estar entre 5 y 600");

    List<Raid> raids = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    JsonArray array = root.has("raids") ? root.getAsJsonArray("raids") : new JsonArray();
    for (JsonElement e : array) {
      Raid raid = parseRaid(object(e, "raids[]"), attempts, rewards);
      if (!seen.add(raid.id())) throw new IllegalArgumentException("id de raid repetido: " + raid.id());
      raids.add(raid);
    }
    if (enabled && raids.isEmpty()) throw new IllegalArgumentException("enabled es true pero la lista raids está vacía");
    return new WeeklyConfig(enabled, zone, day, time, order, new Entrance(dimension, x, y, z, (float) yaw), attempts, rewards, turn, List.copyOf(raids));
  }

  /** Parses one raid. Also used to read a frozen snapshot, where the effective limits are already written in. */
  public static Raid parseRaid(JsonObject o, int defaultAttempts, int defaultRewards) {
    unknown(o, RAID, "raid");
    String id = o.has("id") ? o.get("id").getAsString() : "";
    if (!ID.matcher(id).matches()) throw new IllegalArgumentException("id de raid inválido (usa a-z, 0-9, _ y -): " + id);
    String where = "raid " + id;
    Mode mode;
    try {
      mode = Mode.valueOf(text(o, "mode", "DUEL"));
    } catch (IllegalArgumentException bad) {
      throw new IllegalArgumentException(where + ": mode debe ser DUEL o BATTLE");
    }
    if (!o.has("boss")) throw new IllegalArgumentException(where + ": falta boss");
    JsonObject boss = object(o.get("boss"), where + ".boss").deepCopy();
    JsonObject pokemon = boss.has("pokemon") ? object(boss.get("pokemon"), where + ".boss.pokemon") : null;
    if (pokemon == null || !pokemon.has("species") || pokemon.get("species").getAsString().isBlank())
      throw new IllegalArgumentException(where + ": boss.pokemon.species es obligatorio");
    if (!boss.has("raid_tier") || !TIERS.contains(boss.get("raid_tier").getAsString()))
      throw new IllegalArgumentException(where + ": boss.raid_tier debe ser uno de " + TIERS);
    if (!boss.has("raid_type") || boss.get("raid_type").getAsString().isBlank())
      throw new IllegalArgumentException(where + ": boss.raid_type es obligatorio (por ejemplo FIRE)");
    if (boss.has("required_damage")) {
      double need = boss.get("required_damage").getAsDouble();
      if (need < 0 || need > 1) throw new IllegalArgumentException(where + ": boss.required_damage es una proporción entre 0 y 1");
    }
    if (boss.has("pokemon") && pokemon.has("level")) {
      int level = pokemon.get("level").getAsInt();
      if (level < 1 || level > 100) throw new IllegalArgumentException(where + ": boss.pokemon.level debe estar entre 1 y 100");
    }
    int tier = RaidTierPolicy.tierOfOrdinal(TIERS.indexOf(boss.get("raid_tier").getAsString()));
    DuelParams duel;
    if (mode == Mode.DUEL) {
      duel = duelParams(tier, o.has("duel") ? object(o.get("duel"), where + ".duel") : null, where);
    } else {
      if (o.has("duel")) throw new IllegalArgumentException(where + ": duel solo se admite con mode DUEL");
      duel = null;
    }
    JsonObject lim = o.has("limits") ? object(o.get("limits"), where + ".limits") : new JsonObject();
    unknown(lim, LIMITS, where + ".limits");
    int attempts = limit(lim, "attemptsPerWeek", defaultAttempts), rewards = limit(lim, "rewardsPerWeek", defaultRewards);
    String display = o.has("displayName") ? o.get("displayName").getAsString() : id;
    if (display.isBlank() || display.length() > 80) throw new IllegalArgumentException(where + ": displayName entre 1 y 80 caracteres");

    JsonObject raw = new JsonObject();
    raw.addProperty("id", id);
    raw.addProperty("displayName", display);
    raw.addProperty("mode", mode.name());
    raw.add("boss", boss);
    if (o.has("duel")) raw.add("duel", o.get("duel").deepCopy());
    JsonObject limits = new JsonObject();
    limits.addProperty("attemptsPerWeek", attempts);
    limits.addProperty("rewardsPerWeek", rewards);
    raw.add("limits", limits);
    return new Raid(id, display, mode, boss, duel, attempts, rewards, raw);
  }

  private static DuelParams duelParams(int tier, JsonObject o, String where) {
    DuelParams base = DuelParams.defaults(tier);
    if (o == null) return base.withoutCooldown();
    unknown(o, DUEL, where + ".duel");
    try {
      return RaidsConfig.duelParams(tier, o, base).withoutCooldown();
    } catch (IllegalArgumentException bad) {
      throw new IllegalArgumentException(where + ".duel: " + bad.getMessage(), bad);
    }
  }

  private static int limit(JsonObject o, String key, int fallback) {
    int value = o.has(key) ? o.get(key).getAsInt() : fallback;
    if (value < 0 || value > 10_000) throw new IllegalArgumentException(key + " debe estar entre 0 (sin límite) y 10000");
    return value;
  }

  private static JsonElement parseJson(String json) {
    try {
      return JsonParser.parseString(json);
    } catch (JsonParseException broken) {
      throw new IllegalArgumentException("JSON inválido: " + broken.getMessage(), broken);
    }
  }

  private static JsonObject object(JsonElement e, String where) {
    if (e == null || !e.isJsonObject()) throw new IllegalArgumentException(where + " debe ser un objeto JSON");
    return e.getAsJsonObject();
  }

  private static String text(JsonObject o, String key, String fallback) {
    if (!o.has(key)) return fallback;
    var p = o.get(key).getAsJsonPrimitive();
    if (!p.isString()) throw new IllegalArgumentException(key + " debe ser un texto");
    return p.getAsString();
  }

  private static double number(JsonObject o, String key, double fallback) {
    return o.has(key) ? o.get(key).getAsDouble() : fallback;
  }

  private static boolean bool(JsonObject o, String key, boolean fallback) {
    if (!o.has(key)) return fallback;
    var p = o.get(key).getAsJsonPrimitive();
    if (!p.isBoolean()) throw new IllegalArgumentException(key + " debe ser true o false");
    return p.getAsBoolean();
  }

  private static void unknown(JsonObject o, Set<String> allowed, String where) {
    for (String key : o.keySet()) if (!allowed.contains(key)) throw new IllegalArgumentException("Clave desconocida en " + where + ": " + key);
  }

  /** The file written the first time: off, with one example raid so the format is visible. */
  public static String defaultJson() {
    return """
        {
          "formatVersion": 1,
          "enabled": false,
          "rotation": { "dayOfWeek": "MONDAY", "time": "04:00", "zone": "", "pickOrder": "SEQUENTIAL" },
          "entrance": { "dimension": "minecraft:overworld", "x": 0.5, "y": 80.0, "z": 0.5, "yaw": 180.0 },
          "limits": { "attemptsPerWeek": 3, "rewardsPerWeek": 1 },
          "queue": { "turnSeconds": 30 },
          "raids": [
            {
              "id": "semana-charizard",
              "displayName": "Furia de Charizard",
              "mode": "DUEL",
              "boss": {
                "pokemon": { "species": "charizard", "level": 90, "moves": ["flareblitz", "dragonclaw", "roost", "airslash"] },
                "raid_tier": "TIER_FIVE",
                "raid_type": "FIRE",
                "health_multi": 25,
                "lives": 3,
                "required_damage": 0.2,
                "scale": 1.5
              },
              "duel": { "timeLimitSeconds": 240, "bossHealth": 600, "bossDamage": 6.0, "attackIntervalTicks": 50, "telegraphTicks": 20, "maxDamagePerHitFraction": 0.15 }
            }
          ]
        }
        """;
  }

  public static WeeklyConfig load(Path path) throws IOException {
    if (!StorageFiles.exists(path)) {
      StorageFiles.createDirectories(path.getParent());
      Path temp = StorageFiles.createTempFile(path.getParent(), "zianutilities-weekly-raids-", ".tmp");
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
