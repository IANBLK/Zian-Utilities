package com.zianblk.zianutilities.core.breeding

/**
 * Configurable reductions for any AVECOINS managed currency ID, not an enum.
 * The runtime must validate entries against AVECOINS managed results and debit
 * the exact ID through a journaled, recoverable transaction.
 */
data class CurrencyAccelerationRules(
    val baseMinutes: Long = 1440L,
    val minimumMinutes: Long = 120L,
    val reductionMinutesByCurrency: Map<String, Long>
) {
    init {
        require(baseMinutes > minimumMinutes && minimumMinutes > 0)
        require(reductionMinutesByCurrency.isNotEmpty())
        require(reductionMinutesByCurrency.keys.all { it.isNotBlank() && it == it.trim() })
        require(reductionMinutesByCurrency.values.all { it > 0 })
    }

    fun minutesFor(currencyId: String): Long =
        requireNotNull(reductionMinutesByCurrency[currencyId]) { "Unknown or disabled currency: $currencyId" }

    fun remainingMinutes(used: Map<String, Long>): Long {
        var reduction = 0L
        val maximum = baseMinutes - minimumMinutes
        for ((id, amount) in used) {
            require(amount >= 0) { "Negative quantity for $id" }
            val minutes = minutesFor(id)
            if (reduction == maximum) continue
            val needed = maximum - reduction
            // Avoid overflow and clamp safely for arbitrarily large quantities.
            val requiredUnits = needed / minutes + if (needed % minutes == 0L) 0L else 1L
            val contribution = if (amount >= requiredUnits) needed else amount * minutes
            reduction += contribution
        }
        return baseMinutes - reduction
    }

    /**
     * Returns whether the requested purchase can reduce the effective duration.
     * This is not a payment authorization; purchases MUST be journaled.
     */
    fun canAccelerate(used: Map<String, Long>, currencyId: String, quantity: Long): Boolean {
        if (quantity <= 0 || currencyId !in reductionMinutesByCurrency) return false
        val before = remainingMinutes(used)
        if (before <= minimumMinutes) return false
        val currentCount = used[currencyId] ?: 0L
        if (quantity > Long.MAX_VALUE - currentCount) return false
        return remainingMinutes(used + (currencyId to currentCount + quantity)) < before
    }

    fun completionTimeMillis(startMillis: Long, used: Map<String, Long>): Long =
        Math.addExact(startMillis, Math.multiplyExact(remainingMinutes(used), 60_000L))
}
