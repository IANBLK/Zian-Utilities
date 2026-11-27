package com.zianblk.zianutilities.neoforge.pase;

import com.zianblk.zianutilities.core.pase.PaseEngine;
import com.zianblk.zianutilities.core.pase.PlayerPase;
import com.zianblk.zianutilities.core.pase.SeasonBook;
import com.zianblk.zianutilities.core.storage.StorageFiles;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;

/**
 * Pass data on disk: seasons.json and players/&lt;uuid&gt;.json, written atomically. A player file that cannot be read
 * (broken, or from a newer version) gives a locked record: the engine ignores that player and the file is never touched.
 */
final class PaseStore implements PaseEngine.Store {
  private final Path dir;
  private final Logger log;
  private final Map<UUID, PlayerPase> players = new HashMap<>();

  PaseStore(Path dir, Logger log) {
    this.dir = dir;
    this.log = log;
  }

  Path seasonsFile() { return dir.resolve("seasons.json"); }

  Path playerFile(UUID id) { return dir.resolve("players").resolve(id + ".json"); }

  /** null when there is no seasons file yet. A file that cannot be read throws: the module stays off rather than start a fresh season. */
  SeasonBook loadSeasons() throws IOException {
    if (!StorageFiles.exists(seasonsFile())) return null;
    try {
      return PaseStateJson.readSeasons(StorageFiles.readString(seasonsFile()));
    } catch (RuntimeException bad) {
      throw new IOException("seasons.json no se puede leer: " + bad.getMessage(), bad);
    }
  }

  void saveSeasons(SeasonBook book) throws IOException {
    atomic(seasonsFile(), PaseStateJson.writeSeasons(book));
    book.dirty = false;
  }

  @Override
  public synchronized PlayerPase get(UUID id) {
    PlayerPase cached = players.get(id);
    if (cached != null) return cached;
    PlayerPase p;
    Path file = playerFile(id);
    try {
      p = StorageFiles.exists(file) ? PaseStateJson.readPlayer(StorageFiles.readString(file), id) : new PlayerPase(id);
    } catch (IOException | RuntimeException bad) {
      log.error("El archivo del pase de {} no se puede leer; ese jugador queda sin pase hasta que se arregle y el archivo no se toca: {}", id, bad.toString());
      p = new PlayerPase(id);
      p.locked = true;
    }
    players.put(id, p);
    return p;
  }

  /** Writes every changed player. Returns false if any write failed (it stays dirty and is tried again). */
  synchronized boolean flush() {
    boolean ok = true;
    for (PlayerPase p : players.values()) if (p.dirty && !p.locked) ok &= save(p);
    return ok;
  }

  private boolean save(PlayerPase p) {
    try {
      atomic(playerFile(p.id), PaseStateJson.writePlayer(p));
      p.dirty = false;
      return true;
    } catch (IOException failure) {
      log.error("No se pudo guardar el pase de {}; se reintentará", p.id, failure);
      return false;
    }
  }

  /** Saves one player and frees the memory when they leave. */
  synchronized void release(UUID id) {
    PlayerPase p = players.get(id);
    if (p == null) return;
    if (p.dirty && !p.locked && !save(p)) return;
    players.remove(id);
  }

  private static void atomic(Path file, String text) throws IOException {
    StorageFiles.createDirectories(file.getParent());
    Path temp = StorageFiles.createTempFile(file.getParent(), "pase-", ".tmp");
    try {
      StorageFiles.writeString(temp, text);
      StorageFiles.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      StorageFiles.deleteIfExists(temp);
    }
  }
}
