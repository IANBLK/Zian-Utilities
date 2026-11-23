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

/** Rotating backups, undo and an append-only change log for one config file. */
public final class ConfigHistory {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);
    private final Path file, backupDir, changeLog;
    private final int keep;
    private final Clock clock;

    public ConfigHistory(Path file, Path backupDir, Path changeLog, int keep, Clock clock) {
        if (keep < 1 || keep > 100) throw new IllegalArgumentException("keep must be between 1 and 100");
        this.file = file; this.backupDir = backupDir; this.changeLog = changeLog; this.keep = keep; this.clock = clock;
    }

    public void backup() throws IOException {
        if (!StorageFiles.exists(file)) return;
        byte[] data = StorageFiles.readAllBytes(file);
        StorageFiles.createDirectories(backupDir);
        String base = file.getFileName() + "." + STAMP.format(clock.instant());
        Path target = backupDir.resolve(base + ".bak");
        for (int i = 1; StorageFiles.exists(target); i++) target = backupDir.resolve(base + "-" + i + ".bak");
        atomicWrite(target, data, false);
        List<Path> all = backups();
        for (int i = 0; i < all.size() - keep; i++) StorageFiles.deleteIfExists(all.get(i));
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
        try (FileChannel c = StorageFiles.channel(changeLog, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            c.write(ByteBuffer.wrap(line.getBytes(StandardCharsets.UTF_8)));
            c.force(true);
        }
    }

    private List<Path> backups() throws IOException {
        if (!StorageFiles.isDirectory(backupDir)) return List.of();
        String prefix = file.getFileName() + ".";
        try (var s = StorageFiles.list(backupDir)) {
            return s.filter(p -> p.getFileName().toString().startsWith(prefix) && p.getFileName().toString().endsWith(".bak"))
                .sorted(Comparator.comparing((Path p) -> key(p, prefix))).toList();
        }
    }

    // Sort by stamp, then numeric collision suffix (plain name = 0); stamp is yyyyMMdd-HHmmss-SSS.
    private static String key(Path p, String prefix) {
        String n = p.getFileName().toString();
        String core = n.substring(prefix.length(), n.length() - 4);
        int dash2 = core.indexOf('-', core.indexOf('-') + 1);
        int dash3 = dash2 < 0 ? -1 : core.indexOf('-', dash2 + 1);
        String stamp = dash3 < 0 ? core : core.substring(0, dash3);
        int seq = dash3 < 0 ? 0 : Integer.parseInt(core.substring(dash3 + 1));
        return stamp + String.format("-%09d", seq);
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
