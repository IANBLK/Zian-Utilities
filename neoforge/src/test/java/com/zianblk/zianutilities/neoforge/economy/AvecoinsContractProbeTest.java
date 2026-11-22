package com.zianblk.zianutilities.neoforge.economy;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvecoinsContractProbeTest {
    @Test
    void acceptsNewerVersionsWithExtraMembersAsLongAsContractFits() throws Exception {
        // 3.0 / 4.1 add MAX_BALANCE, forPlayer, etc.; only the signatures Utilities uses matter.
        var result = AvecoinsContractProbe.inspectLayout(Crafting.class, NewerStore.class, NewerData.class);
        assertTrue(result.compatible());
    }

    @Test
    void failsClosedWhenAUsedMethodIsMissing() {
        assertThrows(NoSuchMethodException.class,
            () -> AvecoinsContractProbe.inspectLayout(Crafting.class, NoDebitStore.class, NoDebitData.class));
    }

    @Test
    void failsClosedWhenManagedResultsIsMissing() {
        assertThrows(NoSuchFieldException.class,
            () -> AvecoinsContractProbe.inspectLayout(Store.class, Store.class, Data.class));
    }

    @Test
    void acceptsInspectedWalletShapeWithoutTouchingBalances() throws Exception {
        var result = AvecoinsContractProbe.inspectLayout(Crafting.class, Store.class, Data.class);
        assertTrue(result.compatible());
        assertEquals(java.util.List.of("coin", "token"), result.currencies());
    }

    @Test
    void rejectsUnexpectedWalletCapacity() throws Exception {
        var result = AvecoinsContractProbe.inspectLayout(
            Crafting.class, WrongCapacityStore.class, WrongData.class
        );
        assertFalse(result.compatible());
        assertEquals("estructura de cartera incompatible", result.detail());
    }

    @Test
    void rejectsWalletMethodsWithWrongReturnTypes() throws Exception {
        var result = AvecoinsContractProbe.inspectLayout(Crafting.class, WrongStore.class, Data.class);
        assertFalse(result.compatible());
        assertEquals("firmas de cartera incompatibles", result.detail());
    }

    @Test
    void creditFailsWithoutSavingWhenCopyIsNotACopy() {
        WStore.data = new SameCopyData(); WStore.saves = 0;
        var wallet = new ReflectiveAvecoinsWallet(Crafting.class, WStore.class, WData.class);
        assertThrows(IllegalStateException.class, () -> wallet.credit(UUID.randomUUID(), "coin", 5));
        assertEquals(0, WStore.saves);
        assertEquals(10, WStore.data.coins);
    }

    @Test
    void debitFailsWithoutSavingWhenCopyIsNotACopy() {
        WStore.data = new SameCopyData(); WStore.saves = 0;
        var wallet = new ReflectiveAvecoinsWallet(Crafting.class, WStore.class, WData.class);
        assertThrows(IllegalStateException.class, () -> wallet.debit(UUID.randomUUID(), "coin", 5));
        assertEquals(0, WStore.saves);
        assertEquals(10, WStore.data.coins);
    }

    @Test
    void creditFailsWithoutSavingWhenWalletClipsSilently() {
        WStore.data = new ClippingData(); WStore.saves = 0;
        var wallet = new ReflectiveAvecoinsWallet(Crafting.class, WStore.class, WData.class);
        assertThrows(IllegalStateException.class, () -> wallet.credit(UUID.randomUUID(), "coin", 5));
        assertEquals(0, WStore.saves);
    }

    @Test
    void debitFailsWithoutSavingWhenWalletClipsSilently() {
        WStore.data = new ClippingData(); WStore.saves = 0;
        var wallet = new ReflectiveAvecoinsWallet(Crafting.class, WStore.class, WData.class);
        assertThrows(IllegalStateException.class, () -> wallet.debit(UUID.randomUUID(), "coin", 5));
        assertEquals(0, WStore.saves);
    }

    public static final class WStore {
        static WData data; static int saves;
        public static WData get() { return data; }
        public static boolean save(WData candidate) { saves++; data = candidate; return true; }
    }

    public static class WData {
        public static final int SLOT_COUNT = 27;
        public static final int STACK_SIZE = 64;
        long coins = 10;
        public WData copy() { var result = new WData(); result.coins = coins; return result; }
        public long balance(UUID player, String currency) { return coins; }
        public Map<String, Long> balances(UUID player) { return Map.of(); }
        public void credit(UUID player, String currency, long amount) { coins += amount; }
        public boolean debit(UUID player, String currency, long amount) { coins -= amount; return true; }
    }

    public static final class SameCopyData extends WData {
        @Override public WData copy() { return this; }
    }

    public static final class ClippingData extends WData {
        @Override public WData copy() { var result = new ClippingData(); result.coins = coins; return result; }
        @Override public void credit(UUID player, String currency, long amount) { coins += amount - 1; }
        @Override public boolean debit(UUID player, String currency, long amount) { coins -= amount - 1; return true; }
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

    public static final class WrongCapacityStore {
        public static WrongData get() { throw new AssertionError("probe must not read wallet"); }
        public static boolean save(WrongData data) { throw new AssertionError("probe must not save wallet"); }
    }

    @Test
    void rejectsCreditThatReturnsAValue() throws Exception {
        // Si credit devolviera false = "no abonado", se ignoraría y se daría por pagado.
        var result = AvecoinsContractProbe.inspectLayout(Crafting.class, BoolCreditStore.class, BoolCreditData.class);
        assertFalse(result.compatible());
    }

    public static final class BoolCreditStore {
        public static BoolCreditData get() { throw new AssertionError("probe must not read wallet"); }
        public static boolean save(BoolCreditData data) { throw new AssertionError("probe must not save wallet"); }
    }

    public static final class BoolCreditData {
        public static final int SLOT_COUNT = 27;
        public static final int STACK_SIZE = 64;
        public BoolCreditData copy() { throw new AssertionError("probe must not copy wallet"); }
        public long balance(UUID player, String currency) { throw new AssertionError("probe must not read wallet"); }
        public Map<String, Long> balances(UUID player) { throw new AssertionError("probe must not read wallet"); }
        public boolean credit(UUID player, String currency, long amount) { throw new AssertionError("probe must not credit"); }
        public boolean debit(UUID player, String currency, long amount) { throw new AssertionError("probe must not debit"); }
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

    public static final class NewerStore {
        public static NewerData get() { throw new AssertionError("probe must not read wallet"); }
        public static boolean save(NewerData data) { throw new AssertionError("probe must not save wallet"); }
    }

    public static final class NewerData extends Data {
        public static final int MAX_BALANCE = 1728;
        public static NewerData forPlayer(UUID player) { throw new AssertionError("probe must not read wallet"); }
        @Override public NewerData copy() { throw new AssertionError("probe must not copy wallet"); }
    }

    public static final class NoDebitStore {
        public static NoDebitData get() { throw new AssertionError("probe must not read wallet"); }
        public static boolean save(NoDebitData data) { throw new AssertionError("probe must not save wallet"); }
    }

    public static final class NoDebitData {
        public static final int SLOT_COUNT = 27;
        public static final int STACK_SIZE = 64;
        public NoDebitData copy() { throw new AssertionError("probe must not copy wallet"); }
        public long balance(UUID player, String currency) { throw new AssertionError("probe must not read wallet"); }
        public Map<String, Long> balances(UUID player) { throw new AssertionError("probe must not read wallet"); }
        public void credit(UUID player, String currency, long amount) { throw new AssertionError("probe must not credit"); }
    }
}

