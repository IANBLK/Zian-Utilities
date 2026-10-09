package com.zianblk.zianutilities.core.breeding

/**
 * Pure policy for a free, 24h breeding request and optional paid acceleration.
 * Values in this policy are proposal defaults, not real AVECOINS transactions.
 */
data class BreedingAccelerationPolicy(
    val baseDurationMinutes: Long = 24 * 60,
    val minimumDurationMinutes: Long = 2 * 60,
    val coinReductionMinutes: Long = 60,
    val ticketReductionMinutes: Long = 220,
    val defaultConcurrentLimit: Int = 1,
    val vipConcurrentLimit: Int = 2
) {
    init {
        require(baseDurationMinutes > minimumDurationMinutes && minimumDurationMinutes > 0)
        require(coinReductionMinutes > 0 && ticketReductionMinutes > coinReductionMinutes)
        require(defaultConcurrentLimit > 0 && vipConcurrentLimit >= defaultConcurrentLimit)
    }

    fun durationMinutes(coins: Int, tickets: Int): Long {
        require(coins >= 0 && tickets >= 0) { "Amounts cannot be negative" }
        val maxReduction = baseDurationMinutes - minimumDurationMinutes
        // Saturating calculation: even Int.MAX_VALUE items cannot overflow or reduce past the floor.
        val coinReduction = coins.toLong() * coinReductionMinutes
        val ticketReduction = tickets.toLong() * ticketReductionMinutes
        val reduction = if (coinReduction >= maxReduction || ticketReduction >= maxReduction ||
            coinReduction >= maxReduction - ticketReduction
        ) maxReduction else coinReduction + ticketReduction
        return baseDurationMinutes - reduction
    }

    fun effectiveReadyAtMillis(startMillis: Long, coins: Int, tickets: Int): Long =
        Math.addExact(startMillis, Math.multiplyExact(durationMinutes(coins, tickets), 60_000L))

    /**
     * This is only a duration policy: it does NOT debit wallets, apply purchases
     * or grant capacity. Callers must journal purchase before any AVECOINS mutation.
     */
    fun permittedAdditional(
        coins: Int, tickets: Int, addCoins: Int, addTickets: Int
    ): Boolean {
        if (coins < 0 || tickets < 0 || addCoins < 0 || addTickets < 0 || (addCoins == 0 && addTickets == 0)) return false
        val current = durationMinutes(coins, tickets)
        if (current == minimumDurationMinutes) return false
        // Counts use Int in this first slice; reject arithmetic overflow, not high valid counts.
        if (coins > Int.MAX_VALUE - addCoins || tickets > Int.MAX_VALUE - addTickets) return false
        return durationMinutes(coins + addCoins, tickets + addTickets) < current
    }
}
