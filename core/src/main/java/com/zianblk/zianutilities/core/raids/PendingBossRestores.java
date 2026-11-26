package com.zianblk.zianutilities.core.raids;

import com.zianblk.zianutilities.core.storage.StorageFiles;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/**
 * Bosses whose invulnerability and place must be put back, persisted until it is done. An entry is written before a
 * duel touches the boss and removed once the boss was restored, so after a crash whatever is left is restored the
 * moment the boss entity loads. A corrupt or newer file throws and is never overwritten.
 */
public final class PendingBossRestores {
    public static final int FORMAT_VERSION = 1;

    public record Entry(boolean invulnerable, double x, double y, double z, float yRot) {}

    private final Path file;
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    private PendingBossRestores(Path file) { this.file = file; }

    public static PendingBossRestores load(Path file) throws IOException {
        PendingBossRestores out = new PendingBossRestores(file);
        if (!StorageFiles.exists(file)) return out;
        Properties p = new Properties();
        try (InputStream in = StorageFiles.newInputStream(file)) { p.load(in); }
        if (!String.valueOf(FORMAT_VERSION).equals(p.getProperty("formatVersion"))) throw new IOException("Unsupported pending boss formatVersion");
        for (String key : p.stringPropertyNames()) {
            if (key.equals("formatVersion")) continue;
            String[] f = p.getProperty(key).split("\\|", -1);
            if (f.length != 5) throw new IOException("Bad pending boss entry " + key);
            out.entries.put(UUID.fromString(key), new Entry(Boolean.parseBoolean(f[0]), Double.parseDouble(f[1]), Double.parseDouble(f[2]), Double.parseDouble(f[3]), Float.parseFloat(f[4])));
        }
        return out;
    }

    public Entry get(UUID boss) { return entries.get(boss); }

    public Map<UUID, Entry> all() { return Map.copyOf(entries); }

    public void put(UUID boss, Entry entry) throws IOException {
        Map<UUID, Entry> before = new LinkedHashMap<>(entries);
        entries.put(boss, entry);
        save(before);
    }

    /** Returns whether there was an entry. */
    public boolean remove(UUID boss) throws IOException {
        if (!entries.containsKey(boss)) return false;
        Map<UUID, Entry> before = new LinkedHashMap<>(entries);
        entries.remove(boss);
        save(before);
        return true;
    }

    private void save(Map<UUID, Entry> before) throws IOException {
        try {
            Properties p = new Properties();
            p.setProperty("formatVersion", String.valueOf(FORMAT_VERSION));
            entries.forEach((id, e) -> p.setProperty(id.toString(), e.invulnerable() + "|" + e.x() + "|" + e.y() + "|" + e.z() + "|" + e.yRot()));
            StorageFiles.createDirectories(file.getParent());
            Path temp = StorageFiles.createTempFile(file.getParent(), "pending-bosses-", ".tmp");
            try {
                try (OutputStream out = StorageFiles.newOutputStream(temp)) { p.store(out, "Zian Utilities raid bosses to restore"); }
                StorageFiles.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                StorageFiles.deleteIfExists(temp);
            }
        } catch (IOException | RuntimeException failure) {
            entries.clear();
            entries.putAll(before);
            throw failure;
        }
    }
}
