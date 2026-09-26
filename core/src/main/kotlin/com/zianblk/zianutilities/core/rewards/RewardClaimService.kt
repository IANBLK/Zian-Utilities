package com.zianblk.zianutilities.core.rewards

import java.util.UUID

/** Sequential delivery with persisted intent and fail-closed recovery. */
class RewardClaimService(
    private val store: RewardClaimStore,
    private val delivery: RewardDeliveryPort,
) {
    fun claim(request: RewardClaim): RewardClaimRecord = store.withClaimLock(request.claimId) {
        var record = store.load(request.claimId) ?: RewardClaimRecord.pending(request).also(store::save)
        require(record.claim == request) { "claim ID already belongs to a different reward definition" }

        // Existing rejected/uncertain/in-flight outcomes must be resolved explicitly, not retried.
        if (record.status() == ClaimStatus.REJECTED || record.status() == ClaimStatus.RECOVERY_REQUIRED) {
            return@withClaimLock record
        }

        for (component in request.components) {
            val step = requireNotNull(record.components[component.id])
            if (step.status == ComponentStatus.APPLIED) continue

            // A previous crash after persisting IN_FLIGHT may have delivered the reward.
            if (step.status != ComponentStatus.PENDING) return@withClaimLock record

            record = record.withComponent(component.id, ComponentStatus.IN_FLIGHT)
            store.save(record)

            val result = try {
                delivery.deliver(request, component, step.operationId)
            } catch (failure: Exception) {
                RewardDeliveryResult.Uncertain(failure.message ?: failure.javaClass.simpleName)
            }

            record = when (result) {
                RewardDeliveryResult.Applied -> record.withComponent(component.id, ComponentStatus.APPLIED)
                is RewardDeliveryResult.Rejected -> record.withComponent(
                    component.id, ComponentStatus.REJECTED, result.reason
                )
                is RewardDeliveryResult.Uncertain -> record.withComponent(
                    component.id, ComponentStatus.UNCERTAIN, result.reason
                )
            }
            // If this save fails after delivery, disk retains IN_FLIGHT and replay stays blocked.
            store.save(record)
            if (record.status() != ClaimStatus.PENDING && record.status() != ClaimStatus.CLAIMED) {
                return@withClaimLock record
            }
        }
        record
    }

    fun inspect(claimId: UUID): RewardClaimRecord? = store.withClaimLock(claimId) {
        store.load(claimId)
    }
}

