package com.zianblk.zianutilities.neoforge.breeding;
import com.zianblk.zianutilities.core.storage.StorageFiles;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

/** Loaded only by the server; omitted currencies are disabled, never silently free. */
public final class BreedingConfig {
  public boolean enabled = false;

  /** Optional server policy; wild-spawn generation limits do not restrict breeding by default. */
  public boolean respectGenerationRestrictions = false;

  public long baseMinutes = 1440, minimumMinutes = 120;
  public int defaultSlots = 1, vipSlots = 2, maxSlots = 8;
  public double alphaChance = 0.05;
  public Map<String, Long> currencyReductionsMinutes =
      new LinkedHashMap<>(
          Map.of(
              "avecoins:coppercoin",
              2L,
              "avecoins:diamondcoin",
              20L,
              "avecoins:netheriteticket",
              220L));

  public static BreedingConfig load(Path path) throws Exception {
    var gson = new GsonBuilder().setPrettyPrinting().create();
    if (!StorageFiles.exists(path)) {
      StorageFiles.createDirectories(path.getParent());
      StorageFiles.writeString(path, gson.toJson(new BreedingConfig()));
    }
    String text = StorageFiles.readString(path);
    try (var reader = new com.google.gson.stream.JsonReader(new java.io.StringReader(text))) {
      reader.setLenient(false);
      uniqueKeys(reader);
      if (reader.peek() != com.google.gson.stream.JsonToken.END_DOCUMENT)
        throw new IllegalArgumentException("JSON adicional");
    }
    var c = gson.fromJson(text, BreedingConfig.class);
    if (c == null) throw new IllegalArgumentException("Configuración vacía");
    c.validate();
    return c;
  }

  private static void uniqueKeys(com.google.gson.stream.JsonReader r) throws java.io.IOException {
    switch (r.peek()) {
      case BEGIN_OBJECT -> {
        r.beginObject();
        var keys = new HashSet<String>();
        while (r.hasNext()) {
          String key = r.nextName();
          if (!keys.add(key)) throw new IllegalArgumentException("Clave duplicada: " + key);
          uniqueKeys(r);
        }
        r.endObject();
      }
      case BEGIN_ARRAY -> {
        r.beginArray();
        while (r.hasNext()) uniqueKeys(r);
        r.endArray();
      }
      default -> r.skipValue();
    }
  }

  public void validate() {
    if (baseMinutes < minimumMinutes
        || minimumMinutes < 120
        || baseMinutes > 525600
        || defaultSlots < 1
        || maxSlots > 8
        || maxSlots < defaultSlots
        || vipSlots < defaultSlots
        || vipSlots > maxSlots
        || !Double.isFinite(alphaChance)
        || alphaChance < 0
        || alphaChance > 1
        || currencyReductionsMinutes == null
        || currencyReductionsMinutes.size() > 128
        || currencyReductionsMinutes.entrySet().stream()
            .anyMatch(
                e ->
                    e.getKey() == null
                        || !e.getKey().matches("[a-z0-9_.-]+(?::[a-z0-9/._-]+)?")
                        || e.getValue() == null
                        || e.getValue() < 1
                        || e.getValue() > 525600))
      throw new IllegalArgumentException("Configuración de crianza inválida");
  }
}
