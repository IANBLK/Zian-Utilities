package com.zianblk.zianutilities.core.breeding

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BreedingOperationTest {
    private fun sample() = BreedingRequest(
        operationId = UUID.randomUUID(),
        ownerId = UUID.randomUUID(),
        motherId = UUID.randomUUID(),
        fatherId = UUID.randomUUID(),
        childId = UUID.randomUUID(),
        currencyId = "netheriteticket",
        price = 2,
        createdAtMillis = 1000L,
        readyAtMillis = 61000L
    )

    @Test fun happyPathRetainsStableChildId() {
        val initial = sample()
        val paid = BreedingTransitions.recordPayment(
            BreedingTransitions.startPayment(initial), ExternalMutationResult.APPLIED
        )
        val waiting = BreedingTransitions.startWaiting(paid)
        assertFailsWith<IllegalArgumentException> { BreedingTransitions.markReady(waiting, 60999L) }
        val ready = BreedingTransitions.markReady(waiting, 61000L)
        val delivered = BreedingTransitions.recordDelivery(
            BreedingTransitions.startDelivery(ready), ExternalMutationResult.APPLIED
        )
        val complete = BreedingTransitions.complete(delivered)
        assertEquals(BreedingStage.COMPLETED, complete.stage)
        assertEquals(initial.childId, complete.childId)
        assertEquals(initial.operationId, complete.operationId)
    }

    @Test fun uncertainPaymentMustBlock() {
        val blocked = BreedingTransitions.recordPayment(
            BreedingTransitions.startPayment(sample()), ExternalMutationResult.UNCERTAIN
        )
        assertEquals(BreedingStage.RECOVERY_REQUIRED, blocked.stage)
        assertFailsWith<IllegalArgumentException> { BreedingTransitions.startWaiting(blocked) }
    }

    @Test fun uncertainDeliveryMustBlock() {
        val initial = sample()
        val ready = BreedingTransitions.markReady(
            BreedingTransitions.startWaiting(
                BreedingTransitions.recordPayment(
                    BreedingTransitions.startPayment(initial), ExternalMutationResult.APPLIED
                )
            ), initial.readyAtMillis
        )
        val blocked = BreedingTransitions.recordDelivery(
            BreedingTransitions.startDelivery(ready), ExternalMutationResult.UNCERTAIN
        )
        assertEquals(BreedingStage.RECOVERY_REQUIRED, blocked.stage)
        assertFailsWith<IllegalArgumentException> { BreedingTransitions.startDelivery(blocked) }
    }

    @Test fun rejectedDeliveryCanBeRetriedOnlyWhenKnownNotApplied() {
        val initial = sample()
        val ready = BreedingTransitions.markReady(
            BreedingTransitions.startWaiting(
                BreedingTransitions.recordPayment(
                    BreedingTransitions.startPayment(initial), ExternalMutationResult.APPLIED
                )
            ), initial.readyAtMillis
        )
        assertEquals(
            BreedingStage.READY,
            BreedingTransitions.recordDelivery(
                BreedingTransitions.startDelivery(ready), ExternalMutationResult.REJECTED
            ).stage
        )
    }

    @Test fun invalidInputsRejected() {
        val original = sample()
        assertFailsWith<IllegalArgumentException> { original.copy(motherId = original.fatherId) }
        assertFailsWith<IllegalArgumentException> { original.copy(price = 0) }
        assertFailsWith<IllegalArgumentException> { original.copy(readyAtMillis = original.createdAtMillis) }
    }
}
