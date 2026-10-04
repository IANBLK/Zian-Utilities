package com.zianblk.zianutilities.neoforge.gacha;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class GachaDeliveryQueueTest {
    @Test void deliveryWaitsUntilOneSecondAfterTheReelAndIsDequeuedOnlyOnce() {
        var queue = new GachaDeliveryQueue();
        UUID player = UUID.randomUUID(), operation = UUID.randomUUID();
        long start = 123L;
        long deadline = start + GachaTiming.DELIVERY_MILLIS * 1_000_000L;
        queue.schedule(player, operation, start);
        assertEquals(GachaTiming.REEL_MILLIS + 1000, GachaTiming.DELIVERY_MILLIS);
        assertTrue(queue.due(deadline - 1).isEmpty());
        assertEquals(new GachaDeliveryQueue.Delivery(player, operation), queue.due(deadline).getFirst());
        assertTrue(queue.due(deadline + 1).isEmpty());
    }

    @Test void duplicateSchedulingDoesNotCreateAnotherDeliveryOrExtendTheDeadline() {
        var queue = new GachaDeliveryQueue();
        UUID player = UUID.randomUUID(), operation = UUID.randomUUID();
        queue.schedule(player, operation, 0);
        queue.schedule(player, operation, 1_000_000_000L);
        assertEquals(1, queue.due(GachaTiming.DELIVERY_MILLIS * 1_000_000L).size());
        assertTrue(queue.due(Long.MAX_VALUE).isEmpty());
    }

    @Test void logoutAndServerStopDiscardOnlyTheVolatileDeliveryTask() {
        var queue = new GachaDeliveryQueue();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        queue.schedule(first, UUID.randomUUID(), 0);
        queue.schedule(second, UUID.randomUUID(), 0);
        queue.removePlayer(first);
        var due = queue.due(GachaTiming.DELIVERY_MILLIS * 1_000_000L);
        assertEquals(1, due.size());
        assertEquals(second, due.getFirst().player());
        queue.schedule(second, UUID.randomUUID(), 0);
        queue.clear();
        assertTrue(queue.due(GachaTiming.DELIVERY_MILLIS * 1_000_000L).isEmpty());
    }
}
