package com.zianblk.zianutilities.neoforge.economy;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvecoinsContractProbeTest {
    @Test
    void acceptsInspectedWalletShapeWithoutTouchingBalances() throws Exception {
        var result = AvecoinsContractProbe.inspectLayout(Crafting.class, Store.class, Data.class);
        assertTrue(result.compatible());
        assertEquals(java.util.List.of("coin", "token"), result.currencies());
    }

    @Test
    void rejectsUnexpectedWalletCapacity() throws Exception {
        var result = AvecoinsContractProbe.inspectLayout(Crafting.class, Store.class, WrongData.class);
        assertFalse(result.compatible());
        assertEquals("estructura de cartera incompatible", result.detail());
    }

    @Test
    void rejectsWalletMethodsWithWrongReturnTypes() throws Exception {
        var result = AvecoinsContractProbe.inspectLayout(Crafting.class, WrongStore.class, Data.class);
        assertFalse(result.compatible());
        assertEquals("firmas de cartera incompatibles", result.detail());
    }

    public static final class Crafting {
        public static final Set<String> MANAGED_RESULTS = Set.of("token", "coin");
    }

    public static final class Store {
        public static Data get() { throw new AssertionError("probe must not read wallet"); }
        public static boolean save(Data data) { throw new AssertionError("probe must not save wallet"); }
        public static boolean save(WrongData data) { throw new AssertionError("probe must not save wallet"); }
    }

    public static final class WrongStore {
        public static Data get() { throw new AssertionError("probe must not read wallet"); }
        public static void save(Data data) { throw new AssertionError("probe must not save wallet"); }
    }

    public static class Data {
        public static final int SLOT_COUNT = 27;
        public static final int STACK_SIZE = 64;

        public Data copy() { throw new AssertionError("probe must not copy wallet"); }
        public long balance(UUID player, String currency) { throw new AssertionError("probe must not read wallet"); }
        public Map<String, Long> balances(UUID player) { throw new AssertionError("probe must not read wallet"); }
        public void credit(UUID player, String currency, long amount) { throw new AssertionError("probe must not credit"); }
        public boolean debit(UUID player, String currency, long amount) { throw new AssertionError("probe must not debit"); }
    }

    public static final class WrongData extends Data {
        public static final int SLOT_COUNT = 26;

        @Override public WrongData copy() { throw new AssertionError("probe must not copy wallet"); }
    }
}

