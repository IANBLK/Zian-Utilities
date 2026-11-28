package com.zianblk.zianutilities.neoforge.pase;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zianblk.zianutilities.core.storage.StorageFiles;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Tebex transactions already used, in transactions.json (formatVersion 1, atomic writes). A transaction is written as PENDING
 * before the premium is given and as APPLIED after, so a crash in between lets the same command finish the job, while a
 * transaction that is APPLIED is ignored. A file that cannot be read throws and nothing is given.
 */
final class PaseTransactions {
  static final int FORMAT_VERSION = 1;
  private static final Pattern ID = Pattern.compile("[A-Za-z0-9_.\\-]{1,64}");

  enum State { PENDING, APPLIED }

  record Entry(String uuid, String season, State state, long at) {}

  private final Path file;
  private final Map<String, Entry> entries = new LinkedHashMap<>();
  /** The last read failed: the file is left alone and nothing is recorded until it reads again. */
  private boolean unreadable;

  PaseTransactions(Path file) { this.file = file; }

  static boolean validId(String id) { return id != null && ID.matcher(id).matches(); }

  /** Reads the file (an empty one when it does not exist). Throws IOException when it is broken or from a newer version. */
  synchronized void load() throws IOException {
    entries.clear();
    unreadable = false;
    if (!StorageFiles.exists(file)) return;
    try {
      JsonObject o = JsonParser.parseString(StorageFiles.readString(file)).getAsJsonObject();
      if (!o.has("formatVersion")) throw new IllegalArgumentException("falta formatVersion");
      int v = o.get("formatVersion").getAsInt();
      if (v > FORMAT_VERSION || v < 1) throw new IllegalArgumentException("formatVersion " + v + " no soportado (esta versión usa " + FORMAT_VERSION + ")");
      for (var e : o.getAsJsonObject("transactions").entrySet()) {
        JsonObject t = e.getValue().getAsJsonObject();
        entries.put(e.getKey(), new Entry(t.get("uuid").getAsString(), t.get("season").getAsString(), State.valueOf(t.get("state").getAsString()), t.get("at").getAsLong()));
      }
    } catch (RuntimeException bad) {
      entries.clear();
      unreadable = true;
      throw new IOException("transactions.json no se puede leer (no se da ningún premium): " + bad.getMessage(), bad);
    }
  }

  synchronized Entry get(String transaction) { return entries.get(transaction); }

  /** The transactions that were begun but never marked APPLIED. */
  synchronized java.util.List<String> pendingIds() {
    return entries.entrySet().stream().filter(e -> e.getValue().state() == State.PENDING).map(Map.Entry::getKey).toList();
  }

  /** Records the transaction as PENDING on disk. If the write fails it is forgotten again and the premium must not be given. */
  synchronized void begin(String transaction, UUID player, String season, long at) throws IOException {
    put(transaction, new Entry(player.toString(), season, State.PENDING, at));
  }

  synchronized void applied(String transaction) throws IOException {
    Entry e = entries.get(transaction);
    if (e == null) throw new IOException("transacción desconocida " + transaction);
    put(transaction, new Entry(e.uuid(), e.season(), State.APPLIED, e.at()));
  }

  private void put(String transaction, Entry entry) throws IOException {
    if (unreadable) throw new IOException("transactions.json no se pudo leer: no se escribe encima");
    Entry before = entries.put(transaction, entry);
    try {
      write();
    } catch (IOException | RuntimeException failure) {
      if (before == null) entries.remove(transaction);
      else entries.put(transaction, before);
      throw failure instanceof IOException io ? io : new IOException(failure);
    }
  }

  private void write() throws IOException {
    JsonObject root = new JsonObject();
    root.addProperty("formatVersion", FORMAT_VERSION);
    JsonObject all = new JsonObject();
    for (var e : entries.entrySet()) {
      JsonObject t = new JsonObject();
      t.addProperty("uuid", e.getValue().uuid());
      t.addProperty("season", e.getValue().season());
      t.addProperty("state", e.getValue().state().name());
      t.addProperty("at", e.getValue().at());
      all.add(e.getKey(), t);
    }
    root.add("transactions", all);
    StorageFiles.createDirectories(file.getParent());
    Path temp = StorageFiles.createTempFile(file.getParent(), "pase-tx-", ".tmp");
    try {
      StorageFiles.writeString(temp, new GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n");
      StorageFiles.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      StorageFiles.deleteIfExists(temp);
    }
  }
}
