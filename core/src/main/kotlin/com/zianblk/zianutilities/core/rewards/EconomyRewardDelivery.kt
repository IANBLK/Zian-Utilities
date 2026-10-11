package com.zianblk.zianutilities.core.rewards

import com.zianblk.zianutilities.core.economy.EconomyMutationResult
import com.zianblk.zianutilities.core.economy.EconomyOperation
import com.zianblk.zianutilities.core.economy.EconomyPort
import java.util.UUID

/** Currency-only bridge. Item rewards stay unavailable until their overflow policy is implemented. */
class EconomyRewardDelivery(private val economy: EconomyPort) : RewardDeliveryPort {
    override fun deliver(
        claim: RewardClaim,
        component: RewardComponent,
        operationId: UUID,
    ): RewardDeliveryResult = when (val reward = component.reward) {
        is Reward.Currency -> when (val result = economy.credit(
            EconomyOperation(operationId, claim.playerId, reward.currencyId, reward.amount)
        )) {
            EconomyMutationResult.Applied -> RewardDeliveryResult.Applied
            is EconomyMutationResult.Rejected -> RewardDeliveryResult.Rejected(result.reason)
            is EconomyMutationResult.Uncertain -> RewardDeliveryResult.Uncertain(result.reason)
        }
        is Reward.Item -> RewardDeliveryResult.Rejected("item_delivery_not_available")
    }
}

