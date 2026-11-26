package com.zianblk.zianutilities.neoforge.raids;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;

class RaidDuelHookCheckTest {
    static class Hooked implements RaidDuelHooked {
        public void handleServer(ServerPlayer player) {}
    }

    static class Moved implements RaidDuelHooked {
        public void handleServer(ServerPlayer player, int extra) {}
    }

    @Test void reportsNothingWhenMarkerAndMethodArePresent() {
        assertEquals(List.of(), RaidDuelHookCheck.missing(Hooked.class));
        RaidDuelHookCheck.verifyOrThrow(Hooked.class);
    }

    @Test void reportsMarkerAndMethodWhenAbsent() {
        assertEquals(List.of("mixin", "handleServer(ServerPlayer)"), RaidDuelHookCheck.missing(Object.class));
    }

    @Test void aChangedSignatureIsReportedEvenWithTheMarker() {
        assertEquals(List.of("handleServer(ServerPlayer)"), RaidDuelHookCheck.missing(Moved.class));
    }

    @Test void throwsInsteadOfWarning() {
        var error = assertThrows(IllegalStateException.class, () -> RaidDuelHookCheck.verifyOrThrow(Object.class));
        assertTrue(error.getMessage().contains("no arranca"));
    }

    @Test void theMixinOnlyAppliesWhenRaidDensIsLoaded() {
        assertTrue(RaidsMixinPlugin.applies(true));
        assertFalse(RaidsMixinPlugin.applies(false));
    }
}
