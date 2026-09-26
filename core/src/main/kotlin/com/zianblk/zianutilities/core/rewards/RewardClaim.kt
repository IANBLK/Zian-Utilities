package com.zianblk.zianutilities.core.rewards

import java.util.UUID

sealed interface Reward {
    data class Currency(val currencyId: String, val amount: Long) : Reward {
        init {
            require(currencyId.isNotBlank()) { "currencyId must not be blank" }
            require(amount > 0) { "amount must be positive" }
        }
    }

    data class Item(val itemId: String, val count: Int) : Reward {
        init {
            require(itemId.isNotBlank()) { "itemId must not be blank" }
            require(count > 0) { "count must be positive" }
        }
    }
}

/** Component IDs must remain stable when an unfinished claim is recovered. */
data class RewardComponent(val id: String, val reward: Reward) {
    init {
        require(id.isNotBlank()) { "component id must not be blank" }
    }
}

data class RewardClaim(
    val claimId: UUID,
    val playerId: UUID,
    val sourceModule: String,
    val sourceId: String,
    val components: List<RewardComponent>,
) {
    init {
        require(sourceModule.isNotBlank()) { "sourceModule must not be blank" }
        require(sourceId.isNotBlank()) { "sourceId must not be blank" }
        require(components.isNotEmpty()) { "claim must contain a reward" }
        require(components.map { it.id }.distinct().size == components.size) {
            "component ids must be unique within a claim"
        }
    }
}

sealed interface RewardDeliveryResult {
    data object Applied : RewardDeliveryResult
    data class Rejected(val reason: String) : RewardDeliveryResult
    data class Uncertain(val reason: String) : RewardDeliveryResult
}

/** A delivery attempt is one journaled component, never an entire composite claim. */
interface RewardDeliveryPort {
    fun deliver(claim: RewardClaim, component: RewardComponent): RewardDeliveryResult
}

