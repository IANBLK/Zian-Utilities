package com.zianblk.zianutilities.neoforge.economy;

import com.zianblk.zianutilities.core.economy.EconomyBalanceResult;
import com.zianblk.zianutilities.core.economy.EconomyMutationResult;
import com.zianblk.zianutilities.core.economy.EconomyOperation;
import com.zianblk.zianutilities.core.economy.EconomyPort;

import java.util.Set;
import java.util.UUID;

/** AVECOINS 2.3 boundary. The provider does not honor operation IDs; the claim journal does. */
public final class AvecoinsEconomyPort implements EconomyPort {
    private final WalletAccess wallet;

    public AvecoinsEconomyPort() {
        this(new ReflectiveAvecoinsWallet());
    }

    AvecoinsEconomyPort(WalletAccess wallet) {
        this.wallet = wallet;
    }

    @Override
    public String getProviderId() {
        return "avecoins";
    }

    @Override
    public EconomyBalanceResult balance(UUID playerId, String currencyId) {
        if (!wallet.currencies().contains(currencyId)) {
            return new EconomyBalanceResult.Unavailable("unsupported_currency");
        }
        try {
            return new EconomyBalanceResult.Available(wallet.balance(playerId, currencyId));
        } catch (Exception error) {
            return new EconomyBalanceResult.Unavailable("wallet_read_failed");
        }
    }

    @Override
    public EconomyMutationResult credit(EconomyOperation operation) {
        if (!wallet.currencies().contains(operation.getCurrencyId())) {
            return new EconomyMutationResult.Rejected("unsupported_currency");
        }
        try {
            return switch (wallet.credit(operation.getPlayerId(), operation.getCurrencyId(), operation.getAmount())) {
                case APPLIED -> EconomyMutationResult.Applied.INSTANCE;
                case WALLET_FULL -> new EconomyMutationResult.Rejected("wallet_full");
                case INSUFFICIENT_FUNDS -> new EconomyMutationResult.Uncertain("unexpected_credit_result");
            };
        } catch (Exception error) {
            // A failed save can occur after the mutation was applied. Never claim rejection.
            return new EconomyMutationResult.Uncertain("avecoins_credit_unconfirmed");
        }
    }

    @Override
    public EconomyMutationResult debit(EconomyOperation operation) {
        if (!wallet.currencies().contains(operation.getCurrencyId())) {
            return new EconomyMutationResult.Rejected("unsupported_currency");
        }
        try {
            return switch (wallet.debit(operation.getPlayerId(), operation.getCurrencyId(), operation.getAmount())) {
                case APPLIED -> EconomyMutationResult.Applied.INSTANCE;
                case INSUFFICIENT_FUNDS -> new EconomyMutationResult.Rejected("insufficient_funds");
                case WALLET_FULL -> new EconomyMutationResult.Uncertain("unexpected_debit_result");
            };
        } catch (Exception error) {
            return new EconomyMutationResult.Uncertain("avecoins_debit_unconfirmed");
        }
    }

    interface WalletAccess {
        Set<String> currencies();
        long balance(UUID playerId, String currencyId) throws Exception;
        Mutation credit(UUID playerId, String currencyId, long amount) throws Exception;
        Mutation debit(UUID playerId, String currencyId, long amount) throws Exception;
    }

    enum Mutation { APPLIED, WALLET_FULL, INSUFFICIENT_FUNDS }
}

