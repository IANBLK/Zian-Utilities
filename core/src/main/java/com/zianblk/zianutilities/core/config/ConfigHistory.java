package com.zianblk.zianutilities.core.config;

import com.zianblk.zianutilities.core.storage.StorageFiles;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Rotating backups, undo and an append-only change log for one config file. */
public final class ConfigHistory {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);
    private static final Pattern NAME = Pattern.compile("(\\d{8}-\\d{6}-\\d{3})(?:-(\\d{1,9}))?");
    private static final long MAX_LOG_BYTES = 1L << 20;
    private final Path file, backupDir, changeLog;
    private final int keep;
    private final Clock clock;
    private final long maxLogBytes;

    public ConfigHistory(Path file, Path backupDir, Path changeLog, int keep, Clock clock) {
        this(file, backupDir, changeLog, keep, clock, MAX_LOG_BYTES);
    }

    ConfigHistory(Path file, Path backupDir, Path changeLog, int keep, Clock clock, long maxLogBytes) {
        this.maxLogBytes = maxLogBytes;
        if (keep < 1 || keep > 100) throw new IllegalArgumentException("keep must be between 1 and 100");
        this.file = file; this.backupDir = backupDir; this.changeLog = changeLog; this.keep = keep; this.clock = clock;
    }

    /** Returns the copy created, or null if there was no file to copy. */
    public Path backup() throws IOException {
        if (!StorageFiles.exists(file)) return null;
        byte[] data = StorageFiles.readAllBytes(file);
        StorageFiles.createDirectories(backupDir);
        String base = file.getFileName() + "." + STAMP.format(clock.instant());
        Path target = backupDir.resolve(base + ".bak");
        for (int i = 1; StorageFiles.exists(target); i++) target = backupDir.resolve(base + "-" + i + ".bak");
        atomicWrite(target, data, false);
        List<Path> all = backups();
        for (int i = 0; i < all.size() - keep; i++) StorageFiles.deleteIfExists(all.get(i));
        return target;
    }

    /** Drops a copy returned by {@link #backup()} when the write it protected failed. */
    public void discard(Path backup) throws IOException {
        if (backup != null) StorageFiles.deleteIfExists(backup);
    }

    /** Undo cannot be redone: the used backup is deleted. */
    public boolean undoLast() throws IOException {
        List<Path> all = backups();
        if (all.isEmpty()) return false;
        Path last = all.getLast();
        atomicWrite(file, StorageFiles.readAllBytes(last), true);
        StorageFiles.delete(last);
        return true;
    }

    public void record(UUID actor, String actorName, String module, String summary) throws IOException {
        String line = "{\"at\":\"" + Instant.now(clock) + "\",\"actor\":\"" + actor + "\",\"actorName\":\"" + esc(actorName)
            + "\",\"module\":\"" + esc(module) + "\",\"summary\":\"" + esc(summary) + "\"}\n";
        StorageFiles.createDirectories(changeLog.getParent());
        if (StorageFiles.exists(changeLog) && StorageFiles.size(changeLog) > maxLogBytes)
            StorageFiles.move(changeLog, changeLog.resolveSibling(changeLog.getFileName() + ".1"), StandardCopyOption.REPLACE_EXISTING);
        try (FileChannel c = StorageFiles.channel(changeLog, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            c.write(ByteBuffer.wrap(line.getBytes(StandardCharsets.UTF_8)));
            c.force(true);
        }
    }

    private List<Path> backups() throws IOException {
        if (!StorageFiles.isDirectory(backupDir)) return List.of();
        String prefix = file.getFileName() + ".";
        try (var s = StorageFiles.list(backupDir)) {
            // Foreign files (unparseable name) are ignored so they never block backup or undo.
            return s.filter(p -> key(p, prefix) != null).sorted(Comparator.comparing((Path p) -> key(p, prefix))).toList();
        }
    }

    // Sort by stamp, then numeric collision suffix (plain name = 0); null if the name is not ours.
    private static String key(Path p, String prefix) {
        String n = p.getFileName().toString();
        if (!n.startsWith(prefix) || !n.endsWith(".bak") || n.length() < prefix.length() + 4) return null;
        Matcher m = NAME.matcher(n.substring(prefix.length(), n.length() - 4));
        if (!m.matches()) return null;
        return m.group(1) + String.format("-%09d", m.group(2) == null ? 0 : Integer.parseInt(m.group(2)));
    }

    private static void atomicWrite(Path target, byte[] data, boolean replace) throws IOException {
        Path dir = target.getParent();
        StorageFiles.createDirectories(dir);
        Path temp = StorageFiles.createTempFile(dir, "cfg-", ".tmp");
        try {
            StorageFiles.write(temp, data);
            try (FileChannel c = StorageFiles.channel(temp, StandardOpenOption.WRITE)) { c.force(true); }
            if (replace) StorageFiles.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            else StorageFiles.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            StorageFiles.deleteIfExists(temp);
        }
    }

    private static String esc(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) switch (ch) {
            case '"' -> b.append("\\\"");
            case '\\' -> b.append("\\\\");
            case '\n' -> b.append("\\n");
            case '\r' -> b.append("\\r");
            case '\t' -> b.append("\\t");
            default -> { if (ch < 0x20) b.append(String.format("\\u%04x", (int) ch)); else b.append(ch); }
        }
        return b.toString();
    }
}
