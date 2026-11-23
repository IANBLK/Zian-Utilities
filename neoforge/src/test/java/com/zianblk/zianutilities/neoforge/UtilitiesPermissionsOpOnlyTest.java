package com.zianblk.zianutilities.neoforge;

import com.zianblk.zianutilities.neoforge.breeding.LuckPermsLookup.Decision;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UtilitiesPermissionsOpOnlyTest {
    @Test void opWithoutNodeIsAllowed() {
        assertTrue(UtilitiesPermissions.decideOpOnly(true, Decision.ABSENT, Decision.UNDEFINED));
    }
    @Test void nonOpWithExplicitTrueIsDenied() {
        assertFalse(UtilitiesPermissions.decideOpOnly(false, Decision.TRUE, Decision.TRUE));
    }
    @Test void opWithExplicitFalseIsDenied() {
        assertFalse(UtilitiesPermissions.decideOpOnly(true, Decision.FALSE, Decision.ABSENT));
        assertFalse(UtilitiesPermissions.decideOpOnly(true, Decision.TRUE, Decision.FALSE));
    }
}
