package com.zianblk.zianutilities.neoforge.pase;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.zianblk.zianutilities.core.pase.LootTable;
import com.zianblk.zianutilities.core.pase.PaseReward;
import com.zianblk.zianutilities.core.pase.SeasonRewards;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Reading and writing of the reward files: config/zianutilities-pase-temporada-N.json (the rewards of a season) and
 * config/zianutilities-pase-commands.json (the console commands a reward may run). Every file has formatVersion; anything
 * unknown, mistyped or from a newer version throws IllegalArgumentException, and the rewards module stays closed.
 */
final class PaseRewardJson {
  static final int FORMAT_VERSION = 1;
  private static final Set<String> SEASON = Set.of("formatVersion", "levelCap", "levels", "extra");
  private static final Set<String> LEVEL = Set.of("free", "premium");
  private static final Set<String> EXTRA = Set.of("every", "loot");
  private static final Set<String> LOOT = Set.of("weight", "reward");
  private static final Set<String> ITEM = Set.of("type", "id", "count", "components");
  private static final Set<String> COINS = Set.of("type", "currency", "amount");
  private static final Set<String> COMMAND = Set.of("type", "command");
  private static final Set<String> POKEMON = Set.of("type", "species", "pool", "legendary", "level", "ivs", "min_perfect_ivs", "alpha", "shiny", "ignore_generation", "ignore_level_cap");

  private PaseRewardJson() {}

  // ---------------------------------------------------------------- rewards

  static PaseReward reward(JsonObject o) {
    try {
      String type = string(o, "type");
      return switch (type) {
        case "item" -> {
          unknown(o, ITEM, "item");
          yield PaseReward.item(string(o, "id"), o.has("count") ? integer(o, "count") : 1, o.has("components") ? string(o, "components") : null);
        }
        case "coins" -> {
          unknown(o, COINS, "coins");
          yield PaseReward.coins(string(o, "currency"), integer(o, "amount"));
        }
        case "command" -> {
          unknown(o, COMMAND, "command");
          yield PaseReward.command(string(o, "command"));
        }
        case "pokemon" -> {
          unknown(o, POKEMON, "pokemon");
          List<String> pool = new ArrayList<>();
          if (o.has("pool")) for (JsonElement e : array(o, "pool")) pool.add(e.getAsString());
          String legendary = o.has("legendary") ? string(o, "legendary").toUpperCase(java.util.Locale.ROOT) : "ANY";
          yield PaseReward.pokemon(new PaseReward.PokemonSpec(o.has("species") ? string(o, "species") : null, pool, PaseReward.Legendary.valueOf(legendary),
              integer(o, "level"), o.has("ivs") ? integer(o, "ivs") : -1, o.has("min_perfect_ivs") ? integer(o, "min_perfect_ivs") : 0,
              bool(o, "alpha"), bool(o, "shiny"), bool(o, "ignore_generation"), bool(o, "ignore_level_cap")));
        }
        default -> throw new IllegalArgumentException("tipo de premio desconocido: " + type);
      };
    } catch (IllegalStateException | UnsupportedOperationException | ClassCastException | NumberFormatException bad) {
      throw new IllegalArgumentException("premio con un valor de tipo incorrecto: " + bad.getMessage(), bad);
    }
  }

  static JsonObject toJson(PaseReward r) {
    JsonObject o = new JsonObject();
    switch (r.kind()) {
      case ITEM -> {
        o.addProperty("type", "item");
        o.addProperty("id", r.item());
        o.addProperty("count", r.count());
        if (r.components() != null) o.addProperty("components", r.components());
      }
      case COINS -> {
        o.addProperty("type", "coins");
        o.addProperty("currency", r.currency());
        o.addProperty("amount", r.amount());
      }
      case COMMAND -> {
        o.addProperty("type", "command");
        o.addProperty("command", r.command());
      }
      case POKEMON -> {
        var s = r.pokemon();
        o.addProperty("type", "pokemon");
        if (s.species() != null) o.addProperty("species", s.species());
        if (!s.pool().isEmpty()) {
          JsonArray pool = new JsonArray();
          s.pool().forEach(pool::add);
          o.add("pool", pool);
        }
        o.addProperty("legendary", s.legendary().name().toLowerCase(java.util.Locale.ROOT));
        o.addProperty("level", s.level());
        if (s.ivs() >= 0) o.addProperty("ivs", s.ivs());
        if (s.minPerfectIvs() > 0) o.addProperty("min_perfect_ivs", s.minPerfectIvs());
        o.addProperty("alpha", s.alpha());
        o.addProperty("shiny", s.shiny());
        o.addProperty("ignore_generation", s.ignoreGeneration());
        o.addProperty("ignore_level_cap", s.ignoreLevelCap());
      }
    }
    return o;
  }

  // ---------------------------------------------------------------- season file

  static SeasonRewards parseSeason(String json, int maxLevel) {
    try {
      JsonObject root = root(json);
      unknown(root, SEASON, "raíz");
      version(root);
      int cap = root.has("levelCap") ? integer(root, "levelCap") : 100;
      Map<Integer, SeasonRewards.Level> levels = new TreeMap<>();
      if (root.has("levels"))
        for (var e : object(root, "levels").entrySet()) {
          int n;
          try {
            n = Integer.parseInt(e.getKey());
          } catch (NumberFormatException bad) {
            throw new IllegalArgumentException("levels." + e.getKey() + " no es un número de nivel");
          }
          if (n < 1 || n > maxLevel) throw new IllegalArgumentException("levels." + n + " está fuera de 1-" + maxLevel);
          JsonObject lv = e.getValue().getAsJsonObject();
          unknown(lv, LEVEL, "levels." + n);
          levels.put(n, new SeasonRewards.Level(rewards(lv, "free", n), rewards(lv, "premium", n)));
        }
      int every = 5;
      List<LootTable.Entry> loot = new ArrayList<>();
      if (root.has("extra")) {
        JsonObject extra = object(root, "extra");
        unknown(extra, EXTRA, "extra");
        if (extra.has("every")) every = integer(extra, "every");
        if (extra.has("loot"))
          for (JsonElement el : array(extra, "loot")) {
            JsonObject entry = el.getAsJsonObject();
            unknown(entry, LOOT, "extra.loot");
            loot.add(new LootTable.Entry(integer(entry, "weight"), reward(object(entry, "reward"))));
          }
      }
      return new SeasonRewards(cap, levels, every, loot);
    } catch (IllegalStateException | UnsupportedOperationException | ClassCastException | NumberFormatException bad) {
      throw new IllegalArgumentException("Valor con tipo incorrecto: " + bad.getMessage(), bad);
    }
  }

  private static List<PaseReward> rewards(JsonObject level, String key, int n) {
    List<PaseReward> out = new ArrayList<>();
    if (level.has(key))
      for (JsonElement e : array(level, key)) {
        try {
          out.add(reward(e.getAsJsonObject()));
        } catch (IllegalArgumentException bad) {
          throw new IllegalArgumentException("nivel " + n + " (" + key + "): " + bad.getMessage(), bad);
        }
      }
    return out;
  }

  static String toJson(SeasonRewards s) {
    JsonObject root = new JsonObject();
    root.addProperty("formatVersion", FORMAT_VERSION);
    root.addProperty("levelCap", s.levelCap());
    JsonObject levels = new JsonObject();
    new TreeMap<>(s.levels()).forEach((n, lv) -> {
      JsonObject o = new JsonObject();
      o.add("free", list(lv.free()));
      o.add("premium", list(lv.premium()));
      levels.add(Integer.toString(n), o);
    });
    root.add("levels", levels);
    JsonObject extra = new JsonObject();
    extra.addProperty("every", s.extraEvery());
    JsonArray loot = new JsonArray();
    for (LootTable.Entry e : s.loot()) {
      JsonObject o = new JsonObject();
      o.addProperty("weight", e.weight());
      o.add("reward", toJson(e.reward()));
      loot.add(o);
    }
    extra.add("loot", loot);
    root.add("extra", extra);
    return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root) + "\n";
  }

  private static JsonArray list(List<PaseReward> rewards) {
    JsonArray a = new JsonArray();
    rewards.forEach(r -> a.add(toJson(r)));
    return a;
  }

  // ---------------------------------------------------------------- command whitelist

  /** The command templates a reward may run; no file or an empty list means command rewards are refused. */
  static Set<String> parseWhitelist(String json) {
    try {
      JsonObject root = root(json);
      unknown(root, Set.of("formatVersion", "allowed"), "raíz");
      version(root);
      Set<String> allowed = new LinkedHashSet<>();
      if (root.has("allowed")) for (JsonElement e : array(root, "allowed")) allowed.add(e.getAsString());
      return Set.copyOf(allowed);
    } catch (IllegalStateException | UnsupportedOperationException | ClassCastException bad) {
      throw new IllegalArgumentException("Valor con tipo incorrecto: " + bad.getMessage(), bad);
    }
  }

  static String whitelistDefaultJson() {
    return "{\n  \"formatVersion\": 1,\n  \"allowed\": []\n}\n";
  }

  // ---------------------------------------------------------------- helpers

  private static JsonObject root(String json) {
    try {
      JsonElement e = JsonParser.parseString(json);
      if (!e.isJsonObject()) throw new IllegalArgumentException("El archivo debe ser un objeto JSON");
      return e.getAsJsonObject();
    } catch (JsonParseException broken) {
      throw new IllegalArgumentException("JSON inválido: " + broken.getMessage(), broken);
    }
  }

  private static void version(JsonObject root) {
    if (!root.has("formatVersion") || !root.get("formatVersion").isJsonPrimitive()) throw new IllegalArgumentException("falta formatVersion");
    int v = root.get("formatVersion").getAsInt();
    if (v > FORMAT_VERSION) throw new IllegalArgumentException("formatVersion " + v + " es de una versión más nueva que esta (" + FORMAT_VERSION + ")");
    if (v < 1) throw new IllegalArgumentException("formatVersion " + v + " no soportado");
  }

  private static void unknown(JsonObject o, Set<String> allowed, String where) {
    for (String k : o.keySet()) if (!allowed.contains(k)) throw new IllegalArgumentException("Clave desconocida en " + where + ": " + k);
  }

  private static String string(JsonObject o, String key) {
    if (!o.has(key) || !o.get(key).isJsonPrimitive() || !o.get(key).getAsJsonPrimitive().isString()) throw new IllegalArgumentException(key + " debe ser un texto");
    return o.get(key).getAsString();
  }

  private static long integerLong(JsonObject o, String key) {
    if (!o.has(key) || !o.get(key).isJsonPrimitive() || !o.get(key).getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException(key + " debe ser un número entero");
    double d = o.get(key).getAsDouble();
    if (d != Math.rint(d)) throw new IllegalArgumentException(key + " debe ser un número entero");
    return o.get(key).getAsLong();
  }

  private static int integer(JsonObject o, String key) {
    long v = integerLong(o, key);
    if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) throw new IllegalArgumentException(key + " fuera de rango");
    return (int) v;
  }

  private static boolean bool(JsonObject o, String key) {
    if (!o.has(key)) return false;
    if (!o.get(key).isJsonPrimitive() || !o.get(key).getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException(key + " debe ser true o false");
    return o.get(key).getAsBoolean();
  }

  private static JsonObject object(JsonObject o, String key) {
    if (!o.has(key) || !o.get(key).isJsonObject()) throw new IllegalArgumentException(key + " debe ser un objeto");
    return o.getAsJsonObject(key);
  }

  private static JsonArray array(JsonObject o, String key) {
    if (!o.has(key) || !o.get(key).isJsonArray()) throw new IllegalArgumentException(key + " debe ser una lista");
    return o.getAsJsonArray(key);
  }
}
