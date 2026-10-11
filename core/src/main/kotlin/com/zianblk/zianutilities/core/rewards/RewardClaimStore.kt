package com.zianblk.zianutilities.core.rewards

import java.util.UUID

/** Saves must return only after the claim state is durable enough to survive a restart. */
interface RewardClaimStore {
    fun <T> withClaimLock(claimId: UUID, action: () -> T): T

    fun load(claimId: UUID): RewardClaimRecord?

    fun save(record: RewardClaimRecord)
}

