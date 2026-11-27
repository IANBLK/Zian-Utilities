package com.zianblk.zianutilities.neoforge.raids;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.zianblk.zianutilities.core.raids.weekly.WeeklyCounts;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class WeeklyMessagesTest {
    private static WeeklyConfig.Raid raid(String mode) {
        String json = "{\"formatVersion\":1,\"raids\":[{\"id\":\"a\",\"mode\":\"" + mode + "\",\"boss\":{\"pokemon\":{\"species\":\"charizard\"},\"raid_tier\":\"TIER_FIVE\",\"raid_type\":\"FIRE\"}}]}";
        return WeeklyConfig.parse(json).raids().get(0);
    }

    private static void renders(Component c) {
        String text = c.getString();
        assertFalse(text.isEmpty());
    }

    @Test void everyMessageWithTypedArgumentsRendersInBothModes() {
        for (String mode : new String[] {"DUEL", "BATTLE"}) {
            var raid = raid(mode);
            for (Component c : List.of(
                WeeklyMessages.raidLine(raid), WeeklyMessages.newRaid(raid), WeeklyMessages.mode(raid.mode()),
                WeeklyMessages.attempts(new WeeklyCounts.Count(1, 0), raid), WeeklyMessages.queueLine(2, true), WeeklyMessages.queueLine(0, false)))
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
}
