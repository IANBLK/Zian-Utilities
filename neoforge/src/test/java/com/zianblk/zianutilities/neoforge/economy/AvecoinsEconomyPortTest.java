package com.zianblk.zianutilities.neoforge.economy;

import com.zianblk.zianutilities.core.economy.EconomyBalanceResult;
import com.zianblk.zianutilities.core.economy.EconomyMutationResult;
import com.zianblk.zianutilities.core.economy.EconomyOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class AvecoinsEconomyPortTest {
    private static final String COPPER = "avecoins:coppercoin";
    private final UUID player = UUID.randomUUID();

    @BeforeEach
    void reset() {
        Store.persisted = new Data();
        Store.saveReturnsTrue = true;
    }

    @Test
    void creditSavesCopiedWalletAndReportsBalance() {
        var port = port();
        var result = port.credit(operation(COPPER, 1));
        assertInstanceOf(EconomyMutationResult.Applied.class, result);
        assertEquals(1L, ((EconomyBalanceResult.Available) port.balance(player, COPPER)).getAmount());
    }

    @Test
    void fullWalletRejectsBeforeSave() {
        Store.persisted.credit(player, COPPER, 1728);
        var result = port().credit(operation(COPPER, 1));
        assertEquals(new EconomyMutationResult.Rejected("wallet_full"), result);
        assertEquals(1728, Store.persisted.balance(player, COPPER));
    }

    @Test
    void failedSaveIsUncertainAndNeverReportedAsRejection() {
        Store.saveReturnsTrue = false;
        var result = port().credit(operation(COPPER, 1));
        assertEquals(new EconomyMutationResult.Uncertain("avecoins_credit_unconfirmed"), result);
    }

    @Test
    void unsupportedCurrencyAndInsufficientDebitAreKnownRejections() {
        var port = port();
        assertEquals(
            new EconomyMutationResult.Rejected("unsupported_currency"),
            port.credit(operation("other:coin", 1))
        );
        assertEquals(
            new EconomyMutationResult.Rejected("insufficient_funds"),
            port.debit(operation(COPPER, 1))
        );
    }

    private AvecoinsEconomyPort port() {
        return new AvecoinsEconomyPort(new ReflectiveAvecoinsWallet(Crafting.class, Store.class, Data.class));
    }

    private EconomyOperation operation(String currency, long amount) {
        return new EconomyOperation(UUID.randomUUID(), player, currency, amount);
    }

    public static final class Crafting {
        public static final Set<String> MANAGED_RESULTS = Set.of(COPPER);
    }

    public static final class Store {
        static Data persisted;
        static boolean saveReturnsTrue;

        public static Data get() { return persisted; }
        public static boolean save(Data candidate) {
            if (!saveReturnsTrue) return false;
            persisted = candidate;
            return true;
        }
    }

    public static final class Data {
        public static final int SLOT_COUNT = 27;
        public static final int STACK_SIZE = 64;
        private final Map<UUID, Map<String, Long>> wallets = new HashMap<>();

        public Data copy() {
            Data copy = new Data();
            wallets.forEach((id, values) -> copy.wallets.put(id, new HashMap<>(values)));
            return copy;
        }

        public Map<String, Long> balances(UUID id) {
            return Map.copyOf(wallets.getOrDefault(id, Map.of()));
        }

        public long balance(UUID id, String currency) {
            return wallets.getOrDefault(id, Map.of()).getOrDefault(currency, 0L);
        }

        public void credit(UUID id, String currency, long amount) {
            wallets.computeIfAbsent(id, ignored -> new HashMap<>())
                .merge(currency, amount, Long::sum);
        }

        public boolean debit(UUID id, String currency, long amount) {
            if (balance(id, currency) < amount) return false;
            wallets.get(id).merge(currency, -amount, Long::sum);
            return true;
        }
    }
}

