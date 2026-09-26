package com.zianblk.zianutilities.core.rewards

import java.nio.charset.StandardCharsets
import java.util.UUID

enum class ComponentStatus { PENDING, IN_FLIGHT, APPLIED, REJECTED, UNCERTAIN }

data class ComponentRecord(
    val operationId: UUID,
    val status: ComponentStatus,
    val reason: String? = null,
)

data class RewardClaimRecord(
    val claim: RewardClaim,
    val components: Map<String, ComponentRecord>,
) {
    init {
        require(components.keys == claim.components.map { it.id }.toSet()) {
            "component journal must match claim definition"
        }
    }

    fun status(): ClaimStatus = when {
        components.values.any { it.status == ComponentStatus.IN_FLIGHT || it.status == ComponentStatus.UNCERTAIN } ->
            ClaimStatus.RECOVERY_REQUIRED
        components.values.any { it.status == ComponentStatus.REJECTED } -> ClaimStatus.REJECTED
        components.values.all { it.status == ComponentStatus.APPLIED } -> ClaimStatus.CLAIMED
        else -> ClaimStatus.PENDING
    }

    fun withComponent(id: String, status: ComponentStatus, reason: String? = null): RewardClaimRecord {
        val current = requireNotNull(components[id]) { "unknown component: $id" }
        return copy(components = components + (id to current.copy(status = status, reason = reason)))
    }

    companion object {
        fun pending(claim: RewardClaim): RewardClaimRecord = RewardClaimRecord(
            claim,
            claim.components.associate { component ->
                val identity = "zianutilities:reward:${claim.claimId}:${component.id}"
                component.id to ComponentRecord(
                    UUID.nameUUIDFromBytes(identity.toByteArray(StandardCharsets.UTF_8)),
                    ComponentStatus.PENDING,
                )
            },
        )
    }
}

enum class ClaimStatus { PENDING, CLAIMED, REJECTED, RECOVERY_REQUIRED }

