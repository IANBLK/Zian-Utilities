package com.zianblk.zianutilities.core.raids;

import com.zianblk.zianutilities.core.storage.StorageFiles;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/**
 * Cooldown per player and tier, kept in memory and written atomically after every change. A corrupt file or a
 * newer formatVersion makes load throw and is never overwritten, so a bad file cannot silently free every cooldown.
 */
public final class RaidCooldowns {
    public static final int FORMAT_VERSION = 1;
    private final Path file;
    private final Map<String, Long> until = new HashMap<>();

    private RaidCooldowns(Path file) { this.file = file; }

    public static RaidCooldowns load(Path file) throws IOException {
        RaidCooldowns out = new RaidCooldowns(file);
        if (!StorageFiles.exists(file)) return out;
        Properties p = new Properties();
        try (InputStream in = StorageFiles.newInputStream(file)) { p.load(in); }
        String version = p.getProperty("formatVersion");
        if (!String.valueOf(FORMAT_VERSION).equals(version)) throw new IOException("Unsupported raid cooldown formatVersion " + version);
        for (String key : p.stringPropertyNames()) {
            if (key.equals("formatVersion")) continue;
            String[] parts = key.split("\\.", -1);
            if (parts.length != 2) throw new IOException("Bad raid cooldown key " + key);
            UUID.fromString(parts[0]);
            if (!RaidTierPolicy.validTier(Integer.parseInt(parts[1]))) throw new IOException("Bad raid cooldown tier " + key);
            out.until.put(key, Long.parseLong(p.getProperty(key)));
        }
        return out;
    }

    private static String key(UUID player, int tier) { return player + "." + tier; }

    public long remainingMillis(UUID player, int tier, long now) {
        return Math.max(0, until.getOrDefault(key(player, tier), 0L) - now);
    }

    public void start(UUID player, int tier, long untilMillis) throws IOException {
        Map<String, Long> before = new HashMap<>(until);
        until.put(key(player, tier), untilMillis);
        save(before);
    }

    /** Clears one tier, or every tier when tier is 0. Returns whether something was removed. */
    public boolean reset(UUID player, int tier) throws IOException {
        Map<String, Long> before = new HashMap<>(until);
        boolean removed = tier == 0
            ? until.keySet().removeIf(k -> k.startsWith(player + "."))
            : until.remove(key(player, tier)) != null;
        if (removed) save(before);
        return removed;
    }

    private void save(Map<String, Long> before) throws IOException {
        try {
            Properties p = new Properties();
            p.setProperty("formatVersion", String.valueOf(FORMAT_VERSION));
            until.forEach((k, v) -> p.setProperty(k, Long.toString(v)));
            StorageFiles.createDirectories(file.getParent());
            Path temp = StorageFiles.createTempFile(file.getParent(), "raid-cooldowns-", ".tmp");
            try {
                try (OutputStream out = StorageFiles.newOutputStream(temp)) { p.store(out, "Zian Utilities raid cooldowns"); }
                StorageFiles.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                StorageFiles.deleteIfExists(temp);
            }
        } catch (IOException | RuntimeException failure) {
            until.clear();
            until.putAll(before);
            throw failure;
        }
    }
}
