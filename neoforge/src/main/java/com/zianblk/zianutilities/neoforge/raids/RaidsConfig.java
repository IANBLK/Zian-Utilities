package com.zianblk.zianutilities.neoforge.raids;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zianblk.zianutilities.core.raids.DuelParams;
import com.zianblk.zianutilities.core.raids.DuelPhase;
import com.zianblk.zianutilities.core.raids.RaidTierPolicy;
import com.zianblk.zianutilities.core.storage.StorageFiles;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * config/zianutilities-raids.json. formatVersion 1, disabled by default. Any problem (unknown key, bad value,
 * newer formatVersion, broken JSON) throws and the module stays off: it never runs on a half-understood file.
 */
public record RaidsConfig(
    boolean enabled, int duelFromTier, boolean respectGenerationFilter, boolean safeRewardDelivery,
    Map<Integer, DuelParams> tiers
) {
  public static final int FORMAT_VERSION = 1;
  private static final Set<String> TOP = Set.of("formatVersion", "enabled", "duelFromTier", "respectGenerationFilter", "rewardDelivery", "tiers");
  private static final Set<String> TIER = Set.of("timeLimitSeconds", "bossHealth", "bossDamage", "attackIntervalTicks", "telegraphTicks", "maxDamagePerHitFraction", "cooldownMinutes", "phases");
  private static final Set<String> PHASE = Set.of("hpBelow", "damageMult", "intervalMult", "radius");

  public static RaidsConfig defaults() {
    return parse(defaultJson());
  }

  public DuelParams paramsFor(int tier) {
    return tiers.getOrDefault(tier, DuelParams.defaults(tier));
  }

  public static RaidsConfig parse(String json) {
    try {
      return parse0(json);
    } catch (IllegalStateException | UnsupportedOperationException | ClassCastException bad) {
      throw new IllegalArgumentException("Valor con tipo incorrecto: " + bad.getMessage(), bad);
    }
  }

  private static RaidsConfig parse0(String json) {
    JsonObject root;
    try {
      JsonElement element = JsonParser.parseString(json);
      if (!element.isJsonObject()) throw new IllegalArgumentException("El archivo debe ser un objeto JSON");
      root = element.getAsJsonObject();
    } catch (com.google.gson.JsonParseException broken) {
      throw new IllegalArgumentException("JSON inválido: " + broken.getMessage(), broken);
    }
    unknown(root, TOP, "raíz");
    if (!root.has("formatVersion") || root.get("formatVersion").getAsInt() != FORMAT_VERSION)
      throw new IllegalArgumentException("formatVersion no soportado (se esperaba " + FORMAT_VERSION + ")");
    boolean enabled = bool(root, "enabled", false);
    int from = root.has("duelFromTier") ? root.get("duelFromTier").getAsInt() : 5;
    if (!RaidTierPolicy.validTier(from)) throw new IllegalArgumentException("duelFromTier debe estar entre 1 y 7");
    boolean generation = bool(root, "respectGenerationFilter", true);
    String delivery = root.has("rewardDelivery") ? root.get("rewardDelivery").getAsString() : "safe";
    if (!delivery.equals("safe") && !delivery.equals("raiddens")) throw new IllegalArgumentException("rewardDelivery debe ser safe o raiddens");
    Map<Integer, DuelParams> tiers = new TreeMap<>();
    JsonObject given = root.has("tiers") ? root.getAsJsonObject("tiers") : new JsonObject();
    for (String key : given.keySet()) {
      int tier;
      try {
        tier = Integer.parseInt(key);
      } catch (NumberFormatException bad) {
        throw new IllegalArgumentException("Tier desconocido: " + key);
      }
      if (!RaidTierPolicy.validTier(tier)) throw new IllegalArgumentException("Tier fuera de rango: " + key);
    }
    for (int tier = RaidTierPolicy.MIN_TIER; tier <= RaidTierPolicy.MAX_TIER; tier++)
      tiers.put(tier, tierParams(tier, given.has(String.valueOf(tier)) ? given.getAsJsonObject(String.valueOf(tier)) : null));
    return new RaidsConfig(enabled, from, generation, delivery.equals("safe"), Map.copyOf(tiers));
  }

  private static DuelParams tierParams(int tier, JsonObject o) {
    DuelParams d = DuelParams.defaults(tier);
    if (o == null) return d;
    unknown(o, TIER, "tier " + tier);
    List<DuelPhase> phases = d.phases();
    if (o.has("phases")) {
      JsonArray array = o.getAsJsonArray("phases");
      phases = new java.util.ArrayList<>();
      for (JsonElement e : array) {
        JsonObject p = e.getAsJsonObject();
        unknown(p, PHASE, "fase de tier " + tier);
        phases.add(new DuelPhase(num(p, "hpBelow", 1), num(p, "damageMult", 1), num(p, "intervalMult", 1), num(p, "radius", 3.5)));
      }
    }
    return new DuelParams(
        o.has("timeLimitSeconds") ? o.get("timeLimitSeconds").getAsInt() : d.timeLimitSeconds(),
        num(o, "bossHealth", d.bossHealth()), num(o, "bossDamage", d.bossDamage()),
        o.has("attackIntervalTicks") ? o.get("attackIntervalTicks").getAsInt() : d.attackIntervalTicks(),
        o.has("telegraphTicks") ? o.get("telegraphTicks").getAsInt() : d.telegraphTicks(),
        num(o, "maxDamagePerHitFraction", d.maxDamagePerHitFraction()),
        o.has("cooldownMinutes") ? o.get("cooldownMinutes").getAsInt() : d.cooldownMinutes(), phases);
  }

  private static double num(JsonObject o, String key, double fallback) {
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

  /** The file written the first time: every tier from 5 up listed with the numbers in use. */
  public static String defaultJson() {
    var gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
    var root = new LinkedHashMap<String, Object>();
    root.put("formatVersion", FORMAT_VERSION);
    root.put("enabled", false);
    root.put("duelFromTier", 5);
    root.put("respectGenerationFilter", true);
    root.put("rewardDelivery", "safe");
    var tiers = new LinkedHashMap<String, Object>();
    for (int tier = 5; tier <= 7; tier++) {
      DuelParams d = DuelParams.defaults(tier);
      var t = new LinkedHashMap<String, Object>();
      t.put("timeLimitSeconds", d.timeLimitSeconds());
      t.put("bossHealth", d.bossHealth());
      t.put("bossDamage", d.bossDamage());
      t.put("attackIntervalTicks", d.attackIntervalTicks());
      t.put("telegraphTicks", d.telegraphTicks());
      t.put("maxDamagePerHitFraction", d.maxDamagePerHitFraction());
      t.put("cooldownMinutes", d.cooldownMinutes());
      t.put("phases", d.phases());
      tiers.put(String.valueOf(tier), t);
    }
    root.put("tiers", tiers);
    return gson.toJson(root) + "\n";
  }

  public static RaidsConfig load(Path path) throws IOException {
    if (!StorageFiles.exists(path)) {
      StorageFiles.createDirectories(path.getParent());
      Path temp = StorageFiles.createTempFile(path.getParent(), "zianutilities-raids-", ".tmp");
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
