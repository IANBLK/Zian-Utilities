package com.zianblk.zianutilities.neoforge.pase;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.zianblk.zianutilities.core.pase.LootTable;
import com.zianblk.zianutilities.core.pase.PaseDataException;
import com.zianblk.zianutilities.core.pase.PaseReward;
import com.zianblk.zianutilities.core.pase.SeasonRewards;
import com.zianblk.zianutilities.neoforge.LocalizedException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Reading and writing of the reward files: config/zianutilities-pase-temporada-N.json (the rewards of a season) and
 * config/zianutilities-pase-commands.json (the console commands a reward may run). Every file has formatVersion; anything
 * unknown, mistyped or from a newer version throws an IllegalArgumentException that carries a translatable message
 * (LocalizedException.Argument), and the rewards module stays closed. Every reward of a level carries an {@code id},
 * unique inside its level and track: it is part of the prize id, so editing the file later never duplicates or loses a prize.
 * Items name their item in {@code item}; {@code id} is always the reward id.
 */
final class PaseRewardJson {
  static final int FORMAT_VERSION = 1;
  static final String P = "zianutilities.pase.problem.";
  private static final Pattern REWARD_ID = Pattern.compile("[A-Za-z0-9_.\\-]{1,40}");
  private static final Set<String> SEASON = Set.of("formatVersion", "levelCap", "levels", "extra");
  private static final Set<String> LEVEL = Set.of("free", "premium");
  private static final Set<String> EXTRA = Set.of("every", "loot");
  private static final Set<String> LOOT = Set.of("weight", "reward");
  private static final Set<String> ITEM = Set.of("type", "id", "item", "count", "components");
  private static final Set<String> COINS = Set.of("type", "id", "currency", "amount");
  private static final Set<String> COMMAND = Set.of("type", "id", "command");
  private static final Set<String> POKEMON = Set.of("type", "id", "species", "pool", "legendary", "level", "ivs", "min_perfect_ivs", "alpha", "shiny", "ignore_generation", "ignore_level_cap");

  private PaseRewardJson() {}

  static LocalizedException.Argument bad(String key, String es, Object... args) { return new LocalizedException.Argument(P + key, es, args); }

  /** A value rejected by the core records, with the same translatable shape. */
  private static LocalizedException.Argument bad(PaseDataException e) {
    return switch (e.code) {
      case "range" -> bad("range", "«%s» debe estar entre %s y %s", e.args);
      case "missing" -> bad("missing", "Falta «%s»", e.args);
      default -> bad("conflict", "«%s» y «%s» no pueden ir juntos", e.args);
    };
  }

  // ---------------------------------------------------------------- rewards

  /** A reward as written in a file; {@code id} is null when the object has none (the season parser then refuses it). */
  static PaseReward reward(JsonObject o) {
    try {
      String type = string(o, "type");
      PaseReward r = switch (type) {
        case "item" -> {
          unknown(o, ITEM, "item");
          // The first P2 builds wrote the item in "id": no "item" means that old shape, which has no reward id.
          boolean old = !o.has("item") && o.has("id");
          String item = old ? string(o, "id") : string(o, "item");
          yield PaseReward.item(item, o.has("count") ? integer(o, "count") : 1, o.has("components") ? string(o, "components") : null).withId(old ? null : optionalId(o));
        }
        case "coins" -> {
          unknown(o, COINS, "coins");
          yield PaseReward.coins(string(o, "currency"), integer(o, "amount")).withId(optionalId(o));
        }
        case "command" -> {
          unknown(o, COMMAND, "command");
          yield PaseReward.command(string(o, "command")).withId(optionalId(o));
        }
        case "pokemon" -> {
          unknown(o, POKEMON, "pokemon");
          List<String> pool = new ArrayList<>();
          if (o.has("pool")) for (JsonElement e : array(o, "pool")) pool.add(e.getAsString());
          PaseReward.Legendary legendary;
          try {
            legendary = PaseReward.Legendary.valueOf((o.has("legendary") ? string(o, "legendary") : "any").toUpperCase(java.util.Locale.ROOT));
          } catch (IllegalArgumentException wrong) {
            throw bad("legendary", "«legendary» debe ser any, only o none");
          }
          yield PaseReward.pokemon(new PaseReward.PokemonSpec(o.has("species") ? string(o, "species") : null, pool, legendary,
              integer(o, "level"), o.has("ivs") ? integer(o, "ivs") : -1, o.has("min_perfect_ivs") ? integer(o, "min_perfect_ivs") : 0,
              bool(o, "alpha"), bool(o, "shiny"), bool(o, "ignore_generation"), bool(o, "ignore_level_cap"))).withId(optionalId(o));
        }
        default -> throw bad("reward_type", "Tipo de premio desconocido: %s", type);
      };
      return r;
    } catch (PaseDataException e) {
      throw bad(e);
    } catch (IllegalStateException | UnsupportedOperationException | ClassCastException | NumberFormatException e) {
      throw bad("type_generic", "Valor con tipo incorrecto: %s", String.valueOf(e.getMessage()));
    }
  }

  private static String optionalId(JsonObject o) { return o.has("id") ? string(o, "id") : null; }

  static JsonObject toJson(PaseReward r) {
    JsonObject o = new JsonObject();
    switch (r.kind()) {
      case ITEM -> {
        o.addProperty("type", "item");
        if (r.id() != null) o.addProperty("id", r.id());
        o.addProperty("item", r.item());
        o.addProperty("count", r.count());
        if (r.components() != null) o.addProperty("components", r.components());
      }
      case COINS -> {
        o.addProperty("type", "coins");
        if (r.id() != null) o.addProperty("id", r.id());
        o.addProperty("currency", r.currency());
        o.addProperty("amount", r.amount());
      }
      case COMMAND -> {
        o.addProperty("type", "command");
        if (r.id() != null) o.addProperty("id", r.id());
        o.addProperty("command", r.command());
      }
      case POKEMON -> {
        var s = r.pokemon();
        o.addProperty("type", "pokemon");
        if (r.id() != null) o.addProperty("id", r.id());
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
          } catch (NumberFormatException wrong) {
            throw bad("level_nan", "«%s» no es un número de nivel", e.getKey());
          }
          if (n < 1 || n > maxLevel) throw bad("level_range", "El nivel %s está fuera de 1-%s", n, maxLevel);
          if (!e.getValue().isJsonObject()) throw bad("type_object", "«%s» debe ser un objeto", "levels." + n);
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
            try {
              if (!el.isJsonObject()) throw bad("type_object", "«%s» debe ser un objeto", "extra.loot");
              JsonObject entry = el.getAsJsonObject();
              unknown(entry, LOOT, "extra.loot");
              loot.add(new LootTable.Entry(integer(entry, "weight"), reward(object(entry, "reward"))));
            } catch (PaseDataException wrong) {
              throw bad("in_loot", "En extra.loot: %s", bad(wrong));
            } catch (LocalizedException.Argument wrong) {
              throw bad("in_loot", "En extra.loot: %s", wrong);
            }
          }
      }
      return new SeasonRewards(cap, levels, every, loot);
    } catch (PaseDataException e) {
      throw bad(e);
    } catch (IllegalStateException | UnsupportedOperationException | ClassCastException | NumberFormatException e) {
      throw bad("type_generic", "Valor con tipo incorrecto: %s", String.valueOf(e.getMessage()));
    }
  }

  private static List<PaseReward> rewards(JsonObject level, String key, int n) {
    List<PaseReward> out = new ArrayList<>();
    Set<String> ids = new HashSet<>();
    if (level.has(key))
      for (JsonElement e : array(level, key)) {
        try {
          if (!e.isJsonObject()) throw bad("type_object", "«%s» debe ser un objeto", key);
          PaseReward r = reward(e.getAsJsonObject());
          if (r.id() == null) throw bad("id_missing", "Falta el id del premio");
          if (!REWARD_ID.matcher(r.id()).matches()) throw bad("id_bad", "El id «%s» no es válido (letras, números, guion, guion bajo y punto; máximo 40)", r.id());
          if (!ids.add(r.id())) throw bad("id_duplicate", "El id «%s» está repetido", r.id());
          out.add(r);
        } catch (PaseDataException wrong) {
          throw bad("at", "Nivel %s, pista %s: %s", n, key, bad(wrong));
        } catch (LocalizedException.Argument wrong) {
          throw bad("at", "Nivel %s, pista %s: %s", n, key, wrong);
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
    } catch (IllegalStateException | UnsupportedOperationException | ClassCastException e) {
      throw bad("type_generic", "Valor con tipo incorrecto: %s", String.valueOf(e.getMessage()));
    }
  }

  static String whitelistDefaultJson() {
    return "{\n  \"formatVersion\": 1,\n  \"allowed\": []\n}\n";
  }

  // ---------------------------------------------------------------- helpers

  private static JsonObject root(String json) {
    try {
      JsonElement e = JsonParser.parseString(json);
      if (!e.isJsonObject()) throw bad("not_object", "El archivo debe ser un objeto JSON");
      return e.getAsJsonObject();
    } catch (JsonParseException broken) {
      throw bad("syntax", "El archivo no es JSON válido: %s", String.valueOf(broken.getMessage()));
    }
  }

  private static void version(JsonObject root) {
    if (!root.has("formatVersion") || !root.get("formatVersion").isJsonPrimitive()) throw bad("version_missing", "Falta formatVersion");
    int v = root.get("formatVersion").getAsInt();
    if (v > FORMAT_VERSION || v < 1) throw bad("version", "formatVersion %s no soportado (esta versión usa %s)", v, FORMAT_VERSION);
  }

  private static void unknown(JsonObject o, Set<String> allowed, String where) {
    for (String k : o.keySet()) if (!allowed.contains(k)) throw bad("unknown_key", "Clave desconocida en %s: %s", where, k);
  }

  private static String string(JsonObject o, String key) {
    if (!o.has(key) || !o.get(key).isJsonPrimitive() || !o.get(key).getAsJsonPrimitive().isString()) throw bad("type_text", "«%s» debe ser un texto", key);
    return o.get(key).getAsString();
  }

  private static long integerLong(JsonObject o, String key) {
    if (!o.has(key) || !o.get(key).isJsonPrimitive() || !o.get(key).getAsJsonPrimitive().isNumber()) throw bad("type_int", "«%s» debe ser un número entero", key);
    double d = o.get(key).getAsDouble();
    if (d != Math.rint(d)) throw bad("type_int", "«%s» debe ser un número entero", key);
    return o.get(key).getAsLong();
  }

  private static int integer(JsonObject o, String key) {
    long v = integerLong(o, key);
    if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) throw bad("range", "«%s» debe estar entre %s y %s", key, Integer.MIN_VALUE, Integer.MAX_VALUE);
    return (int) v;
  }

  private static boolean bool(JsonObject o, String key) {
    if (!o.has(key)) return false;
    if (!o.get(key).isJsonPrimitive() || !o.get(key).getAsJsonPrimitive().isBoolean()) throw bad("type_bool", "«%s» debe ser true o false", key);
    return o.get(key).getAsBoolean();
  }

  private static JsonObject object(JsonObject o, String key) {
    if (!o.has(key) || !o.get(key).isJsonObject()) throw bad("type_object", "«%s» debe ser un objeto", key);
    return o.getAsJsonObject(key);
  }

  private static JsonArray array(JsonObject o, String key) {
    if (!o.has(key) || !o.get(key).isJsonArray()) throw bad("type_list", "«%s» debe ser una lista", key);
    return o.getAsJsonArray(key);
  }
}
