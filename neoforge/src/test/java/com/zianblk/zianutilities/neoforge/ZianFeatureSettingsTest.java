package com.zianblk.zianutilities.neoforge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZianFeatureSettingsTest {
    @TempDir Path directory;

    @Test
    void createsEnabledDefaultsWithoutJvmFlags() throws Exception {
        Path path = directory.resolve("config/features.properties");
        var settings = ZianFeatureSettings.load(path);
        assertTrue(Files.exists(path));
        assertTrue(settings.quests());
        assertTrue(settings.questRewardsActive());
        assertTrue(settings.gachas());
        assertTrue(settings.gachaPaymentsActive());
    }

    @Test
    void disabledFeaturesCannotPayEvenIfTheirPaymentSettingIsTrue() throws Exception {
        Path path = directory.resolve("features.properties");
        Files.writeString(path, "quests.enabled=false\nquests.rewards.enabled=true\n"
            + "gachas.enabled=false\ngachas.payments.enabled=true\n");
        var settings = ZianFeatureSettings.load(path);
        assertFalse(settings.questRewardsActive());
        assertFalse(settings.gachaPaymentsActive());
    }

    @Test
    void missingOrInvalidSettingsFailClosed() throws Exception {
        Path path = directory.resolve("features.properties");
        Files.writeString(path, "quests.enabled=true\nquests.rewards.enabled=true\n"
            + "gachas.enabled=true\n");
        assertThrows(IllegalArgumentException.class, () -> ZianFeatureSettings.load(path));
        Files.writeString(path, "quests.enabled=maybe\nquests.rewards.enabled=true\n"
            + "gachas.enabled=true\ngachas.payments.enabled=true\n");
        assertThrows(IllegalArgumentException.class, () -> ZianFeatureSettings.load(path));
    }
}
