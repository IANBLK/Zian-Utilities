package com.zianblk.zianutilities.neoforge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Server-owned feature switches. A host does not need custom JVM startup arguments. */
public final class ZianFeatureSettings {
    private static final Logger LOG = LoggerFactory.getLogger("ZianUtilities/Features");
    private static final Path PATH = Path.of("config", "zianutilities-features.properties");
    private static Settings current;

    private ZianFeatureSettings() {}

    public record Settings(boolean quests, boolean questRewards, boolean gachas, boolean gachaPayments) {
        public boolean questRewardsActive() { return quests && questRewards; }
        public boolean gachaPaymentsActive() { return gachas && gachaPayments; }
    }

    public static synchronized Settings get() {
        if (current != null) return current;
        try {
            current = load(PATH);
            LOG.info("[ZIAN-FEATURES] quests={} questRewards={} gachas={} gachaPayments={}",
                current.quests(), current.questRewardsActive(), current.gachas(), current.gachaPaymentsActive());
        } catch (Exception error) {
            // A broken or unreadable config must never turn on paid features implicitly.
            current = new Settings(false, false, false, false);
            LOG.error("[ZIAN-FEATURES] Cannot load {}; features and payments disabled", PATH, error);
        }
        return current;
    }

    static Settings load(Path path) throws IOException {
        if (!Files.exists(path)) writeDefaults(path);
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        return new Settings(
            requiredBoolean(properties, "quests.enabled"),
            requiredBoolean(properties, "quests.rewards.enabled"),
            requiredBoolean(properties, "gachas.enabled"),
            requiredBoolean(properties, "gachas.payments.enabled"));
    }

    private static boolean requiredBoolean(Properties properties, String key) {
        String value = properties.getProperty(key);
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        throw new IllegalArgumentException("Expected true or false for " + key);
    }

    private static void writeDefaults(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        Properties properties = new Properties();
        properties.setProperty("quests.enabled", "true");
        properties.setProperty("quests.rewards.enabled", "true");
        properties.setProperty("gachas.enabled", "true");
        properties.setProperty("gachas.payments.enabled", "true");
        Path temp = Files.createTempFile(path.getParent(), "zianutilities-features-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temp)) {
                properties.store(output, "Zian Utilities features; restart the server after editing");
            }
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}

