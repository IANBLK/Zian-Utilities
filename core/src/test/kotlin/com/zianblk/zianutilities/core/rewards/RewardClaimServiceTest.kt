package com.zianblk.zianutilities.core.rewards

import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.io.TempDir
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class RewardClaimServiceTest {
    @TempDir lateinit var directory: Path

    private val claim = RewardClaim(
        UUID.randomUUID(), UUID.randomUUID(), "quests", "daily:2026-09-26:1",
        listOf(
            RewardComponent("coins", Reward.Currency("avecoins", 500)),
            RewardComponent("item", Reward.Item("minecraft:diamond", 1)),
        ),
    )

    @Test
    fun `completed claim survives restart and duplicate request delivers nothing`() {
        val calls = mutableListOf<UUID>()
        val delivery = RewardDeliveryPort { _, _, operationId ->
            calls += operationId
            RewardDeliveryResult.Applied
        }
        val first = RewardClaimService(FileRewardClaimStore(directory), delivery).claim(claim)
        val second = RewardClaimService(FileRewardClaimStore(directory), delivery).claim(claim)

        assertEquals(ClaimStatus.CLAIMED, first.status())
        assertEquals(first, second)
        assertEquals(2, calls.size)
        assertNotEquals(calls[0], calls[1])
    }

    @Test
    fun `uncertain second component preserves first and blocks replay after restart`() {
        val calls = mutableListOf<String>()
        val delivery = RewardDeliveryPort { _, component, _ ->
            calls += component.id
            if (component.id == "item") RewardDeliveryResult.Uncertain("provider timeout")
            else RewardDeliveryResult.Applied
        }
        val first = RewardClaimService(FileRewardClaimStore(directory), delivery).claim(claim)
        val second = RewardClaimService(FileRewardClaimStore(directory), delivery).claim(claim)

        assertEquals(ClaimStatus.RECOVERY_REQUIRED, first.status())
        assertEquals(ComponentStatus.APPLIED, second.components.getValue("coins").status)
        assertEquals(ComponentStatus.UNCERTAIN, second.components.getValue("item").status)
        assertEquals(listOf("coins", "item"), calls)
    }

    @Test
    fun `persisted in-flight intent blocks delivery after simulated crash`() {
        val store = FileRewardClaimStore(directory)
        val inFlight = RewardClaimRecord.pending(claim)
            .withComponent("coins", ComponentStatus.IN_FLIGHT)
        store.save(inFlight)
        val calls = AtomicInteger()
        val delivery = RewardDeliveryPort { _, _, _ ->
            calls.incrementAndGet()
            RewardDeliveryResult.Applied
        }

        val result = RewardClaimService(FileRewardClaimStore(directory), delivery).claim(claim)
        assertEquals(ClaimStatus.RECOVERY_REQUIRED, result.status())
        assertEquals(0, calls.get())
    }

    @Test
    fun `same claim ID cannot be reused for another reward definition`() {
        val service = RewardClaimService(FileRewardClaimStore(directory)) { _, _, _ ->
            RewardDeliveryResult.Rejected("inventory full")
        }
        assertEquals(ClaimStatus.REJECTED, service.claim(claim).status())
        val changed = claim.copy(sourceId = "daily:2026-09-26:2")
        assertFailsWith<IllegalArgumentException> { service.claim(changed) }
    }

    @Test
    fun `provider exception becomes uncertain and blocks another attempt`() {
        val calls = AtomicInteger()
        val service = RewardClaimService(FileRewardClaimStore(directory)) { _, _, _ ->
            calls.incrementAndGet()
            throw IllegalStateException("provider lost connection")
        }
        val first = service.claim(claim)
        val second = service.claim(claim)

        assertEquals(ClaimStatus.RECOVERY_REQUIRED, first.status())
        assertEquals(first, second)
        assertEquals(1, calls.get())
    }

    @Test
    fun `failed intent persistence prevents external delivery`() {
        val failingStore = object : RewardClaimStore {
            var saves = 0
            private var record: RewardClaimRecord? = null

            override fun <T> withClaimLock(claimId: UUID, action: () -> T): T = action()
            override fun load(claimId: UUID): RewardClaimRecord? = record
            override fun save(record: RewardClaimRecord) {
                saves++
                if (saves == 2) throw java.io.IOException("disk unavailable")
                this.record = record
            }
        }
        val calls = AtomicInteger()
        val service = RewardClaimService(failingStore) { _, _, _ ->
            calls.incrementAndGet()
            RewardDeliveryResult.Applied
        }

        assertFailsWith<java.io.IOException> { service.claim(claim) }
        assertEquals(0, calls.get())
    }

    @Test
    fun `failed result persistence leaves in-flight state and prevents duplicate credit`() {
        val failingStore = object : RewardClaimStore {
            var saves = 0
            var record: RewardClaimRecord? = null

            override fun <T> withClaimLock(claimId: UUID, action: () -> T): T = action()
            override fun load(claimId: UUID): RewardClaimRecord? = record
            override fun save(record: RewardClaimRecord) {
                saves++
                if (saves == 3) throw java.io.IOException("disk unavailable after delivery")
                this.record = record
            }
        }
        val calls = AtomicInteger()
        val service = RewardClaimService(failingStore) { _, _, _ ->
            calls.incrementAndGet()
            RewardDeliveryResult.Applied
        }

        assertFailsWith<java.io.IOException> { service.claim(claim) }
        assertEquals(ComponentStatus.IN_FLIGHT, failingStore.record?.components?.get("coins")?.status)
        assertEquals(ClaimStatus.RECOVERY_REQUIRED, service.claim(claim).status())
        assertEquals(1, calls.get())
    }

    @Test
    fun `concurrent duplicate requests through one store deliver each component once`() {
        val store = FileRewardClaimStore(directory)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        val delivery = RewardDeliveryPort { _, _, _ ->
            if (calls.incrementAndGet() == 1) {
                started.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
            RewardDeliveryResult.Applied
        }
        val firstService = RewardClaimService(store, delivery)
        val secondService = RewardClaimService(store, delivery)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit<RewardClaimRecord> { firstService.claim(claim) }
            check(started.await(5, TimeUnit.SECONDS))
            val second = executor.submit<RewardClaimRecord> { secondService.claim(claim) }
            release.countDown()
            assertEquals(ClaimStatus.CLAIMED, first.get(5, TimeUnit.SECONDS).status())
            assertEquals(ClaimStatus.CLAIMED, second.get(5, TimeUnit.SECONDS).status())
            assertEquals(2, calls.get())
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }
}

