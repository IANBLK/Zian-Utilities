package com.zianblk.zianutilities.core.economy

import java.util.UUID

/** A stable identity for a single external balance mutation. Reuse it after a restart. */
data class EconomyOperation(
    val operationId: UUID,
    val playerId: UUID,
    val currencyId: String,
    val amount: Long,
) {
    init {
        require(currencyId.isNotBlank()) { "currencyId must not be blank" }
        require(amount > 0) { "amount must be positive" }
    }
}

sealed interface EconomyMutationResult {
    data object Applied : EconomyMutationResult
    data class Rejected(val reason: String) : EconomyMutationResult
    data class Uncertain(val reason: String) : EconomyMutationResult
}

sealed interface EconomyBalanceResult {
    data class Available(val amount: Long) : EconomyBalanceResult {
        init {
            require(amount >= 0) { "balance must not be negative" }
        }
    }

    data class Unavailable(val reason: String) : EconomyBalanceResult
}

/** Implementations adapt a provider such as AVECOINS; core never calls that provider directly. */
interface EconomyPort {
    val providerId: String

    fun balance(playerId: UUID, currencyId: String): EconomyBalanceResult

    fun credit(operation: EconomyOperation): EconomyMutationResult

    fun debit(operation: EconomyOperation): EconomyMutationResult
}

