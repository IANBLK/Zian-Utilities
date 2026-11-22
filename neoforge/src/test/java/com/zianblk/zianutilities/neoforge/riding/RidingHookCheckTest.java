package com.zianblk.zianutilities.neoforge.riding;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class RidingHookCheckTest {
    static class Hooked implements ZianRidingHooked {
        static void travel$lambda$0() {}
        static void setRideBar$lambda$0() {}
    }

    @Test
    void reportsNothingWhenMixinAndTargetsPresent() {
        assertEquals(List.of(), RidingHookCheck.missing(Hooked.class));
    }

    @Test
    void reportsMixinAndTargetsWhenAbsent() {
        assertEquals(List.of("mixin", "travel$lambda$0", "setRideBar$lambda$0"), RidingHookCheck.missing(Object.class));
    }
}
