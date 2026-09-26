package com.zianblk.zianutilities.core

import com.zianblk.zianutilities.core.economy.EconomyMutationResult
import com.zianblk.zianutilities.core.economy.EconomyOperation
import com.zianblk.zianutilities.core.generation.Generation
import com.zianblk.zianutilities.core.quests.QuestEvent
import com.zianblk.zianutilities.core.rewards.Reward
import com.zianblk.zianutilities.core.rewards.RewardClaim
import com.zianblk.zianutilities.core.rewards.RewardComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import java.util.UUID

class Milestone2ContractsTest {
    private val claimId = UUID.randomUUID()
    private val playerId = UUID.randomUUID()

    @Test
    fun `economy operation retains identity and rejects invalid amounts`() {
        val operation = EconomyOperation(claimId, playerId, "avecoins", 500)
        assertEquals(claimId, operation.operationId)
        assertFailsWith<IllegalArgumentException> {
            EconomyOperation(claimId, playerId, "avecoins", 0)
        }
        assertFailsWith<IllegalArgumentException> {
            EconomyOperation(claimId, playerId, " ", 500)
        }
    }

    @Test
    fun `uncertain mutation remains distinct from known rejection`() {
        val outcome: EconomyMutationResult = EconomyMutationResult.Uncertain("save_unconfirmed")
        assertIs<EconomyMutationResult.Uncertain>(outcome)
        assertEquals("save_unconfirmed", outcome.reason)
    }

    @Test
    fun `composite claim rejects duplicate component identities`() {
        val currency = RewardComponent("currency", Reward.Currency("avecoins", 500))
        val item = RewardComponent("item", Reward.Item("minecraft:diamond", 1))
        val claim = RewardClaim(claimId, playerId, "quests", "daily-1", listOf(currency, item))
        assertEquals(listOf("currency", "item"), claim.components.map { it.id })
        assertFailsWith<IllegalArgumentException> {
            RewardClaim(claimId, playerId, "quests", "daily-1", listOf(currency, currency))
        }
    }

    @Test
    fun `canonical capture event carries resolved generations without Cobblemon types`() {
        val event = QuestEvent.PokemonCaptured(
            UUID.randomUUID(), playerId, "cobblemon:popplio", setOf(Generation.GEN_7)
        )
        assertEquals(setOf(Generation.GEN_7), event.generations)
    }
}

