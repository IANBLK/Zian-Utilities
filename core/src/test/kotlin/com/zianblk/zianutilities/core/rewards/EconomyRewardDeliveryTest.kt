package com.zianblk.zianutilities.core.rewards

import com.zianblk.zianutilities.core.economy.EconomyBalanceResult
import com.zianblk.zianutilities.core.economy.EconomyMutationResult
import com.zianblk.zianutilities.core.economy.EconomyOperation
import com.zianblk.zianutilities.core.economy.EconomyPort
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class EconomyRewardDeliveryTest {
    @Test
    fun `currency delivery passes the journal operation identity to economy`() {
        val operationId = UUID.randomUUID()
        val playerId = UUID.randomUUID()
        var observed: EconomyOperation? = null
        val port = object : EconomyPort {
            override val providerId = "fake"
            override fun balance(playerId: UUID, currencyId: String) = EconomyBalanceResult.Available(0)
            override fun credit(operation: EconomyOperation): EconomyMutationResult {
                observed = operation
                return EconomyMutationResult.Uncertain("save_unconfirmed")
            }
            override fun debit(operation: EconomyOperation) = EconomyMutationResult.Rejected("unused")
        }
        val claim = RewardClaim(
            UUID.randomUUID(), playerId, "admin_probe", "one_credit",
            listOf(RewardComponent("coins", Reward.Currency("avecoins:coppercoin", 1))),
        )

        val result = EconomyRewardDelivery(port).deliver(claim, claim.components.single(), operationId)
        assertEquals(RewardDeliveryResult.Uncertain("save_unconfirmed"), result)
        assertEquals(EconomyOperation(operationId, playerId, "avecoins:coppercoin", 1), observed)
    }

    @Test
    fun `item reward is rejected without calling economy`() {
        val port = object : EconomyPort {
            override val providerId = "fake"
            override fun balance(playerId: UUID, currencyId: String) = EconomyBalanceResult.Available(0)
            override fun credit(operation: EconomyOperation): EconomyMutationResult =
                error("must not credit for item")
            override fun debit(operation: EconomyOperation) = EconomyMutationResult.Rejected("unused")
        }
        val claim = RewardClaim(
            UUID.randomUUID(), UUID.randomUUID(), "quests", "test",
            listOf(RewardComponent("item", Reward.Item("minecraft:diamond", 1))),
        )

        assertEquals(
            RewardDeliveryResult.Rejected("item_delivery_not_available"),
            EconomyRewardDelivery(port).deliver(claim, claim.components.single(), UUID.randomUUID()),
        )
    }
}

