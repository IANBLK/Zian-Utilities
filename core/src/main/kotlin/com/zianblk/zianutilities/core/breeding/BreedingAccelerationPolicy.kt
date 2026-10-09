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
    val maxCoins: Int = 12,
    val maxTickets: Int = 6,
    val defaultConcurrentLimit: Int = 1,
    val vipConcurrentLimit: Int = 2
) {
    init {
        require(baseDurationMinutes > minimumDurationMinutes && minimumDurationMinutes > 0)
        require(coinReductionMinutes > 0 && ticketReductionMinutes > coinReductionMinutes)
        require(maxCoins >= 0 && maxTickets >= 0)
        require(defaultConcurrentLimit > 0 && vipConcurrentLimit >= defaultConcurrentLimit)
    }

    fun durationMinutes(coins: Int, tickets: Int): Long {
        require(coins in 0..maxCoins) { "Coin limit exceeded" }
        require(tickets in 0..maxTickets) { "Ticket limit exceeded" }
        val reduction = Math.addExact(
            Math.multiplyExact(coins.toLong(), coinReductionMinutes),
            Math.multiplyExact(tickets.toLong(), ticketReductionMinutes)
        )
        return (baseDurationMinutes - reduction).coerceAtLeast(minimumDurationMinutes)
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
        if (coins !in 0..maxCoins || tickets !in 0..maxTickets) return false
        if (addCoins < 0 || addTickets < 0 || (addCoins == 0 && addTickets == 0)) return false
        if (addCoins > maxCoins - coins || addTickets > maxTickets - tickets) return false
        return durationMinutes(coins + addCoins, tickets + addTickets) < durationMinutes(coins, tickets)
    }
}
