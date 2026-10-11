package com.zianblk.zianutilities.neoforge.breeding;

import com.google.gson.*;
import com.zianblk.zianutilities.core.breeding.BreedingJournal;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

/** Offline maintenance. Retains every job, receipt, child and the complete original audit WAL. */
public final class BreedingStorageMaintenance {
  public record Result(long beforeBytes, long afterBytes, int jobs, Path archive) {}

  enum Point {
    BEFORE_REPLACE,
    AFTER_REPLACE
  }

  @FunctionalInterface
  interface Fault {
    void at(Point point) throws IOException;
  }

  public static Result compact(Path source) throws IOException {
    return compact(source, ignored -> {});
  }

  static Result compact(Path source, Fault fault) throws IOException {
    if (!Files.isRegularFile(source) || Files.isSymbolicLink(source))
      throw new IOException("Expected an existing regular journal");
    source = source.toAbsolutePath().normalize();
    Path directory = source.getParent();
    String token = UUID.randomUUID().toString();
    Path temporary = directory.resolve("journal.compact-" + token + ".wal");
    Path archive = directory.resolve("journal.archive-" + token + ".wal.gz");
    Path marker = source.resolveSibling(source.getFileName() + ".maintenance");
    boolean ownsMarker = false;
    long before = Files.size(source), after;
    int count;
    try (var original = new BreedingStore(source)) {
      if (!original.compactionCompatible())
        throw new IOException(
            "Unknown persisted fields; original journal must be preserved without compacting");
      Files.writeString(
          marker,
          "Offline compaction; archive="
              + archive.getFileName()
              + "; temporary="
              + temporary.getFileName(),
          StandardOpenOption.CREATE_NEW);
      ownsMarker = true;
      try (var markerChannel = FileChannel.open(marker, StandardOpenOption.WRITE)) {
        markerChannel.force(true);
      }
      forceDirectory(directory);
      count = original.jobs.size();
      long observed = original.clock;
      long sequence = 0;
      var gson = new Gson();
      try (var output = BreedingJournal.open(temporary, ignored -> {})) {
        // Keep the persisted clock even for a journal with no jobs.
        var clock = new JsonObject();
        clock.addProperty("schema", 1);
        clock.addProperty("sequence", ++sequence);
        clock.addProperty("observedAt", observed);
        output.append(gson.toJson(clock));
        for (var job : original.jobs.values()) {
          var event = new JsonObject();
          event.addProperty("schema", 1);
          event.addProperty("sequence", ++sequence);
          event.addProperty("observedAt", observed);
          event.add("job", gson.toJsonTree(job));
          output.append(gson.toJson(event));
        }
      }
      try (var verified = new BreedingStore(temporary)) {
        if (!original.jobs.equals(verified.jobs)
            || original.clock != verified.clock
            || !original.purchaseIndex().equals(verified.purchaseIndex()))
          throw new IOException("Compaction does not preserve original jobs, clock or receipt IDs");
      }
      after = Files.size(temporary);
      // Preserve the complete old transitions before replacing the active journal.
      original.archiveCompressed(archive);
      forceDirectory(directory);
      // No non-atomic fallback. Unsupported filesystems keep the original active journal.
      fault.at(Point.BEFORE_REPLACE);
      // The maintenance marker blocks new-version writers while Windows requires closing the WAL.
      original.close();
      Files.move(
          temporary, source, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      forceDirectory(directory);
      fault.at(Point.AFTER_REPLACE);
    } finally {
      Files.deleteIfExists(temporary);
      if (ownsMarker) {
        Files.deleteIfExists(marker);
        forceDirectory(directory);
      }
    }
    return new Result(before, after, count, archive);
  }

  private static void forceDirectory(Path path) throws IOException {
    if (!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows"))
      try (var directory = FileChannel.open(path, StandardOpenOption.READ)) {
        directory.force(true);
      }
  }

  public static void main(String[] args) throws Exception {
    if (args.length != 2 || !Set.of("inspect", "compact-offline").contains(args[0]))
      throw new IllegalArgumentException(
          "Usage: inspect|compact-offline <existing journal.wal>; stop server and disable restart"
              + " before compacting");
    var path = Path.of(args[1]);
    if (!Files.isRegularFile(path)) throw new IOException("Journal does not exist");
    if (args[0].equals("compact-offline")) {
      var result = compact(path);
      var json = new JsonObject();
      json.addProperty("beforeBytes", result.beforeBytes());
      json.addProperty("afterBytes", result.afterBytes());
      json.addProperty("jobs", result.jobs());
      json.addProperty("archive", result.archive().toString());
      System.out.println(json);
    } else
      try (var store = new BreedingStore(path)) {
        System.out.println(
            "bytes="
                + Files.size(path)
                + " jobs="
                + store.jobs.size()
                + " pending="
                + store.jobs.values().stream().filter(j -> j.occupiesSlot()).count()
                + " receiptIds="
                + store.purchaseIndex().size());
      }
  }
}
