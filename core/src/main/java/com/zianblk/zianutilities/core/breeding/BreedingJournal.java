package com.zianblk.zianutilities.core.breeding;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Append-only framed WAL. Corruption/torn records lock the journal; never truncate and guess. */
public final class BreedingJournal implements AutoCloseable {
  public enum Point {
    BEFORE_WRITE,
    AFTER_WRITE,
    AFTER_FORCE
  }

  @FunctionalInterface
  public interface Fault {
    void at(Point point) throws IOException;
  }

  private final FileChannel channel;
  private final Fault fault;
  private FileLock exclusive;
  private byte[] chain = new byte[32];
  private boolean locked;

  private BreedingJournal(FileChannel channel, Fault fault) {
    this.channel = channel;
    this.fault = fault;
  }

  public static BreedingJournal open(Path path, java.util.function.Consumer<String> replay)
      throws IOException {
    return open(path, replay, p -> {});
  }

  public static BreedingJournal open(
      Path path, java.util.function.Consumer<String> replay, Fault fault) throws IOException {
    Files.createDirectories(path.toAbsolutePath().getParent());
    var channel =
        FileChannel.open(
            path, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
    var journal = new BreedingJournal(channel, fault);
    try {
      journal.exclusive = channel.tryLock();
      if (journal.exclusive == null) throw new IOException("Breeding journal already open");
      channel.force(true);
      if (!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows")) {
        try (var directory =
            FileChannel.open(path.toAbsolutePath().getParent(), StandardOpenOption.READ)) {
          directory.force(true);
        }
      }
      long at = 0;
      while (at < channel.size()) {
        var header = ByteBuffer.allocate(4);
        read(channel, header, at);
        int length = header.flip().getInt();
        if (length < 1 || length > 1048576) throw new IOException("Journal frame size");
        var bytes = ByteBuffer.allocate(length);
        read(channel, bytes, at + 4);
        var hash = ByteBuffer.allocate(32);
        read(channel, hash, at + 4 + length);
        byte[] expected = hash(journal.chain, bytes.array());
        if (!MessageDigest.isEqual(expected, hash.array()))
          throw new IOException("Journal checksum");
        replay.accept(new String(bytes.array(), java.nio.charset.StandardCharsets.UTF_8));
        journal.chain = expected;
        at += 4L + length + 32;
      }
      channel.position(channel.size());
      return journal;
    } catch (Exception e) {
      channel.close();
      throw new IOException("Breeding journal preserved and unavailable", e);
    }
  }

  private static void read(FileChannel c, ByteBuffer b, long at) throws IOException {
    while (b.hasRemaining()) {
      int n = c.read(b, at);
      if (n <= 0) throw new EOFException("Torn journal record");
      at += n;
    }
  }

  private static byte[] hash(byte[] before, byte[] data) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      digest.update(before);
      return digest.digest(data);
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  public synchronized void append(String json) throws IOException {
    if (locked) throw new IOException("Breeding journal locked");
    byte[] data = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    if (data.length < 1 || data.length > 1048576) throw new IOException("Frame too large");
    byte[] next = hash(chain, data);
    var buffer =
        ByteBuffer.allocate(4 + data.length + 32).putInt(data.length).put(data).put(next).flip();
    try {
      fault.at(Point.BEFORE_WRITE);
      while (buffer.hasRemaining()) channel.write(buffer);
      fault.at(Point.AFTER_WRITE);
      channel.force(true);
      fault.at(Point.AFTER_FORCE);
      chain = next;
    } catch (IOException | RuntimeException e) {
      locked = true;
      throw e;
    }
  }

  public boolean locked() {
    return locked;
  }

  public void close() throws IOException {
    channel.close();
  }
}
