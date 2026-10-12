package com.zianblk.zianutilities.core.breeding;
import com.zianblk.zianutilities.core.storage.StorageFiles;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Append-only framed WAL. Corruption/torn records lock the journal; never truncate and guess. */
public final class BreedingJournal implements AutoCloseable {
  public record Frame(long offset, int length, byte[] previousHash, byte[] checksum) {
    public Frame {
      if(offset<0 || length<1 || length>1048576 || previousHash.length!=32 || checksum.length!=32)throw new IllegalArgumentException("Invalid journal pointer");
      previousHash = previousHash.clone();
      checksum = checksum.clone();
    }

    @Override
    public byte[] previousHash() {
      return previousHash.clone();
    }

    @Override
    public byte[] checksum() {
      return checksum.clone();
    }
  }

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
    return openIndexed(path, (frame, text) -> replay.accept(text), fault);
  }

  public static BreedingJournal openIndexed(
      Path path, java.util.function.BiConsumer<Frame, String> replay, Fault fault)
      throws IOException {
    StorageFiles.createDirectories(path.toAbsolutePath().getParent());
    Path maintenance = path.resolveSibling(path.getFileName() + ".maintenance");
    if (StorageFiles.exists(maintenance))
      throw new IOException("Breeding storage maintenance requires review before opening");
    var channel =
        StorageFiles.channel(
            path, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
    var journal = new BreedingJournal(channel, fault);
    try {
      journal.exclusive = channel.tryLock();
      if (journal.exclusive == null) throw new IOException("Breeding journal already open");
      if (StorageFiles.exists(maintenance))
        throw new IOException("Breeding storage maintenance in progress");
      channel.force(true);
      if (!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows")) {
        try (var directory =
            StorageFiles.channel(path.toAbsolutePath().getParent(), StandardOpenOption.READ)) {
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
        replay.accept(
            new Frame(at, length, journal.chain, expected),
            new String(bytes.array(), java.nio.charset.StandardCharsets.UTF_8));
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
    appendIndexed(json);
  }

  public synchronized Frame appendIndexed(String json) throws IOException {
    if (locked) throw new IOException("Breeding journal locked");
    byte[] data = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    if (data.length < 1 || data.length > 1048576) throw new IOException("Frame too large");
    byte[] previous = chain.clone();
    long offset = channel.position();
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
      return new Frame(offset, data.length, previous, next);
    } catch (IOException | RuntimeException e) {
      locked = true;
      throw e;
    }
  }

  public synchronized String readFrame(Frame frame) throws IOException {
    if (locked || !channel.isOpen()) throw new IOException("Breeding journal unavailable");
    try {
      var header = ByteBuffer.allocate(4);
      read(channel, header, frame.offset());
      if (header.flip().getInt() != frame.length()) throw new IOException("Frame length changed");
      var payload = ByteBuffer.allocate(frame.length());
      read(channel, payload, frame.offset() + 4);
      var checksum = ByteBuffer.allocate(32);
      read(channel, checksum, frame.offset() + 4 + frame.length());
      if (!MessageDigest.isEqual(checksum.array(), frame.checksum())
          || !MessageDigest.isEqual(hash(frame.previousHash(), payload.array()), frame.checksum()))
        throw new IOException("Frame checksum changed");
      return new String(payload.array(), java.nio.charset.StandardCharsets.UTF_8);
    } catch (IOException error) {
      locked = true;
      throw error;
    }
  }

  public boolean locked() {
    return locked;
  }

  /** Read through the owning locked channel (Windows rejects a second reader). */
  public synchronized void archive(Path target) throws IOException {
    if (locked) throw new IOException("Breeding journal locked");
    try (var output =
        StorageFiles.channel(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
      var buffer = ByteBuffer.allocate(65536);
      long offset = 0;
      while (offset < channel.size()) {
        buffer.clear();
        int read = channel.read(buffer, offset);
        if (read <= 0) throw new EOFException("Archive copy incomplete");
        offset += read;
        buffer.flip();
        while (buffer.hasRemaining()) output.write(buffer);
      }
      output.force(true);
      if (output.size() != channel.size()) throw new IOException("Archive size mismatch");
    }
  }

  /** Lossless audit archive, verified against bytes read from the owning channel. */
  public synchronized void archiveCompressed(Path target) throws IOException {
    if (locked) throw new IOException("Breeding journal locked");
    try {
      var originalDigest = MessageDigest.getInstance("SHA-256");
      long size = channel.size(), offset = 0;
      var buffer = ByteBuffer.allocate(65536);
      try (var output =
          new java.util.zip.GZIPOutputStream(
              StorageFiles.newOutputStream(target, StandardOpenOption.CREATE_NEW))) {
        while (offset < size) {
          buffer.clear();
          int read = channel.read(buffer, offset);
          if (read <= 0) throw new EOFException("Archive copy incomplete");
          offset += read;
          originalDigest.update(buffer.array(), 0, read);
          output.write(buffer.array(), 0, read);
        }
      }
      try (var output = StorageFiles.channel(target, StandardOpenOption.WRITE)) {
        output.force(true);
      }
      var archiveDigest = MessageDigest.getInstance("SHA-256");
      long restored = 0;
      byte[] bytes = new byte[65536];
      try (var input = new java.util.zip.GZIPInputStream(StorageFiles.newInputStream(target))) {
        int read;
        while ((read = input.read(bytes)) != -1) {
          restored += read;
          if (restored > size) throw new IOException("Archive expands beyond original size");
          archiveDigest.update(bytes, 0, read);
        }
      }
      if (restored != size
          || !MessageDigest.isEqual(originalDigest.digest(), archiveDigest.digest()))
        throw new IOException("Archive byte verification failed");
    } catch (NoSuchAlgorithmException failure) {
      throw new IOException(failure);
    }
  }

  public void close() throws IOException {
    channel.close();
  }
}
