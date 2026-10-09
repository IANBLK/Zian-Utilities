package com.zianblk.zianutilities.core.breeding

import java.util.UUID

/**
 * Domain-only state of a paid breeding request. This is NOT a persistence adapter.
 *
 * A request owns one stable operationId and one preselected childId. These IDs must
 * survive restart and must never be regenerated when an operation is retried.
 */
enum class BreedingStage {
    PREPARED,
    BEFORE_PAYMENT,
    PAYMENT_CONFIRMED,
    WAITING,
    READY,
    BEFORE_DELIVERY,
    DELIVERY_CONFIRMED,
    COMPLETED,
    REJECTED,
    RECOVERY_REQUIRED
}

enum class ExternalMutationResult { APPLIED, REJECTED, UNCERTAIN }

data class BreedingRequest(
    val operationId: UUID,
    val ownerId: UUID,
    val motherId: UUID,
    val fatherId: UUID,
    val childId: UUID,
    val currencyId: String,
    val price: Long,
    val createdAtMillis: Long,
    val readyAtMillis: Long,
    val stage: BreedingStage = BreedingStage.PREPARED
) {
    init {
        require(ownerId != motherId && ownerId != fatherId) { "Player and Pokemon identities must differ" }
        require(motherId != fatherId) { "Two different parents required" }
        require(price > 0) { "Breeding fee must be positive" }
        require(currencyId.isNotBlank()) { "Currency identifier is mandatory" }
        require(readyAtMillis > createdAtMillis) { "Breeding duration must be positive" }
        require(operationId != childId && childId != motherId && childId != fatherId) {
            "Child and operation IDs must be distinct from parent IDs"
        }
    }
}

/**
 * Pure, deterministic transition rules. The runtime must persist the NEXT state
 * before calling an external payment/PC adapter at BEFORE_* boundaries.
 */
object BreedingTransitions {
    fun startPayment(request: BreedingRequest): BreedingRequest {
        require(request.stage == BreedingStage.PREPARED)
        return request.copy(stage = BreedingStage.BEFORE_PAYMENT)
    }

    fun recordPayment(request: BreedingRequest, outcome: ExternalMutationResult): BreedingRequest {
        require(request.stage == BreedingStage.BEFORE_PAYMENT)
        return request.copy(stage = when (outcome) {
            ExternalMutationResult.APPLIED -> BreedingStage.PAYMENT_CONFIRMED
            ExternalMutationResult.REJECTED -> BreedingStage.REJECTED
            ExternalMutationResult.UNCERTAIN -> BreedingStage.RECOVERY_REQUIRED
        })
    }

    fun startWaiting(request: BreedingRequest): BreedingRequest {
        require(request.stage == BreedingStage.PAYMENT_CONFIRMED)
        return request.copy(stage = BreedingStage.WAITING)
    }

    fun markReady(request: BreedingRequest, nowMillis: Long): BreedingRequest {
        require(request.stage == BreedingStage.WAITING)
        require(nowMillis >= request.readyAtMillis) { "Breeding is not finished" }
        return request.copy(stage = BreedingStage.READY)
    }

    fun startDelivery(request: BreedingRequest): BreedingRequest {
        require(request.stage == BreedingStage.READY)
        return request.copy(stage = BreedingStage.BEFORE_DELIVERY)
    }

    fun recordDelivery(request: BreedingRequest, outcome: ExternalMutationResult): BreedingRequest {
        require(request.stage == BreedingStage.BEFORE_DELIVERY)
        return request.copy(stage = when (outcome) {
            ExternalMutationResult.APPLIED -> BreedingStage.DELIVERY_CONFIRMED
            ExternalMutationResult.REJECTED -> BreedingStage.READY
            ExternalMutationResult.UNCERTAIN -> BreedingStage.RECOVERY_REQUIRED
        })
    }

    fun complete(request: BreedingRequest): BreedingRequest {
        require(request.stage == BreedingStage.DELIVERY_CONFIRMED)
        return request.copy(stage = BreedingStage.COMPLETED)
    }
}
