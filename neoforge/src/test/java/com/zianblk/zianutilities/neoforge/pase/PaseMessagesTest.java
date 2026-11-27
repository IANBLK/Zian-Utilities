package com.zianblk.zianutilities.neoforge.pase;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zianblk.zianutilities.core.pase.MissionType;
import com.zianblk.zianutilities.core.pase.PaseEngine;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class PaseMessagesTest {
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

    private static PaseEngine.View view(boolean done, boolean previous, boolean boost, int level) {
        List<PaseEngine.MissionLine> lines = new ArrayList<>();
        for (MissionType t : MissionType.values()) lines.add(new PaseEngine.MissionLine(t, 2, 5, 300, done));
        return new PaseEngine.View("temporada-2", level, 120, 800, 200, 3, 100, 400, previous ? "temporada-1" : null, 150, done, boost, 4000, 1200, 17, done, List.copyOf(lines));
    }

    @Test void everyMessageWithTypedArgumentsBuildsAndRenders() {
        for (MissionType t : MissionType.values()) {
            renders(PaseMessages.missionName(t, 3));
            renders(PaseMessages.missionDone(t, 3, 300));
        }
        for (boolean flag : new boolean[] {true, false})
            for (int level : new int[] {5, 200}) {
                for (Component c : PaseMessages.view(view(flag, flag, flag, level), "04:00", "Europe/Madrid")) renders(c);
            }
        List<PaseEngine.MissionLine> none = List.of();
        for (Component c : PaseMessages.view(new PaseEngine.View("temporada-1", 0, 0, 800, 200, 0, 100, 0, null, 0, false, false, 4000, 4000, 60, false, none), "04:00", "Europe/Madrid")) renders(c);
        for (Component c : List.of(
            PaseMessages.levelUp(5), PaseMessages.extraLevelUp(2), PaseMessages.xpGiven(500L, "Ian"), PaseMessages.xpNone("Ian"), PaseMessages.xpRemoved(10L, "Ian"),
            PaseMessages.levelsGiven(3, "Ian"), PaseMessages.season("temporada-2", "2026-12-01 00:00", "2027-02-01 00:00", false),
            PaseMessages.season("temporada-1", "2026-10-01 00:00", "2026-12-01 00:00", true), PaseMessages.noPreviousSeason(),
            PaseMessages.seasonStarted("temporada-3", "2027-04-01 00:00"), PaseMessages.reloaded(), PaseMessages.reloadFailed("x"),
            PaseMessages.playerReset("Ian"), PaseMessages.failed("boom")))
            renders(c);
    }

    @Test void problemReasonsAreTranslatableAndNeverRawEnglish() {
        for (String reason : new String[] {"disabled in config", "not started", "formatVersion no soportado", "", null}) renders(PaseMessages.off(reason));
        assertFalse(PaseMessages.off("disabled in config").getString().contains("disabled in config"));
        assertFalse(PaseMessages.off("not started").getString().contains("not started"));
    }

    @Test void aMessageWithAnEnumOrAnIdCannotEvenBeBuilt() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> com.zianblk.zianutilities.neoforge.Tr.c("k", "x %s", MissionType.FISH));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> com.zianblk.zianutilities.neoforge.Tr.c("k", "x %s", java.util.UUID.randomUUID()));
    }
}
