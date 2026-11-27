package com.zianblk.zianutilities.neoforge.raids;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zianblk.zianutilities.neoforge.Tr;

import com.zianblk.zianutilities.core.raids.weekly.WeeklyCounts;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class WeeklyMessagesTest {
    private static WeeklyConfig.Raid raid(String mode) {
        String json = "{\"formatVersion\":1,\"raids\":[{\"id\":\"a\",\"mode\":\"" + mode + "\",\"boss\":{\"pokemon\":{\"species\":\"charizard\"},\"raid_tier\":\"TIER_FIVE\",\"raid_type\":\"FIRE\"}}]}";
        return WeeklyConfig.parse(json).raids().get(0);
    }

    /** Translatable arguments must be Component, String, Number or Boolean (anything else makes the client throw), checked recursively. */
    static boolean validArguments(Component c) {
        if (c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t)
            for (Object arg : t.getArgs()) {
                if (arg instanceof Component inner) { if (!validArguments(inner)) return false; }
                else if (!(arg instanceof String || arg instanceof Number || arg instanceof Boolean)) return false;
            }
        for (Component sibling : c.getSiblings()) if (!validArguments(sibling)) return false;
        return true;
    }

    private static void renders(Component c) {
        String text = c.getString();
        assertFalse(text.isEmpty());
        assertTrue(validArguments(c), "argumentos no válidos en " + text);
    }

    /** In this Minecraft version the constructor itself refuses a bad argument, so a message built with an enum fails the test at once. */
    @Test void aMessageWithAnEnumOrAnIdCannotEvenBeBuilt() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> Tr.c("k", "x %s", WeeklyConfig.Mode.DUEL));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> Tr.c("k", "x %s", java.util.UUID.randomUUID()));
        assertTrue(validArguments(Tr.c("k", "x %s %s %s", "a", 1, Tr.c("k2", "y"))));
    }

    @Test void everyMessageWithTypedArgumentsRendersInBothModes() {
        for (String mode : new String[] {"DUEL", "BATTLE"}) {
            var raid = raid(mode);
            for (Component c : List.of(
                WeeklyMessages.raidLine(raid), WeeklyMessages.newRaid(raid), WeeklyMessages.mode(raid.mode()),
                WeeklyMessages.attempts(new WeeklyCounts.Count(1, 0), raid), WeeklyMessages.queueLine(2, true), WeeklyMessages.queueLine(0, false), WeeklyMessages.blocked("charizard")))
                renders(c);
        }
    }

    @Test void unlimitedLimitsAreShownAsWordsAndNotAsZero() {
        var raid = WeeklyConfig.parse("{\"formatVersion\":1,\"limits\":{\"attemptsPerWeek\":0,\"rewardsPerWeek\":0},\"raids\":[{\"id\":\"a\",\"boss\":{\"pokemon\":{\"species\":\"charizard\"},\"raid_tier\":\"TIER_FIVE\",\"raid_type\":\"FIRE\"}}]}").raids().get(0);
        String text = WeeklyMessages.attempts(new WeeklyCounts.Count(3, 1), raid).getString();
        assertFalse(text.contains(" 0."), text);
    }

    @Test void problemReasonsAreTranslatableAndNeverRawEnglish() {
        for (String reason : new String[] {"disabled in config", "not started", "formatVersion no soportado", "", null}) {
            renders(WeeklyMessages.unavailable(reason));
            renders(WeeklyMessages.off(reason));
        }
        assertFalse(WeeklyMessages.off("disabled in config").getString().contains("disabled in config"));
        assertFalse(WeeklyMessages.problemText("not started").contains("not started"));
    }

    @Test void unavailableTravelsAsOwnKeyPerReason() {
        assertTrue(WeeklyMessages.unavailableEnc("disabled in config").contains("zianutilities.raids.weekly.err.unavailable_disabled"));
        assertTrue(WeeklyMessages.unavailableEnc("not started").contains("zianutilities.raids.weekly.err.unavailable_not_started"));
        assertFalse(WeeklyMessages.unavailableEnc("not started").contains("not started"));
        renders(Tr.dec(WeeklyMessages.unavailableEnc("formatVersion no soportado")));
    }
}
