package com.zianblk.zianutilities.core.rewards

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.channels.FileChannel
import java.util.Properties
import java.util.UUID

/** One server-process store. Atomic replacement keeps an interrupted write from erasing the prior state. */
class FileRewardClaimStore(private val directory: Path) : RewardClaimStore {
    private val locks = Array(256) { Any() }

    override fun <T> withClaimLock(claimId: UUID, action: () -> T): T =
        synchronized(locks[(claimId.hashCode() and Int.MAX_VALUE) % locks.size]) { action() }

    override fun load(claimId: UUID): RewardClaimRecord? {
        val path = pathFor(claimId)
        if (!Files.exists(path)) return null
        val properties = Properties()
        Files.newBufferedReader(path, StandardCharsets.UTF_8).use(properties::load)
        require(properties.required("version") == "1") { "unsupported claim journal version" }
        require(properties.required("claimId") == claimId.toString()) { "claim journal ID mismatch" }

        val playerId = UUID.fromString(properties.required("playerId"))
        val count = properties.required("count").toInt()
        require(count in 1..1000) { "invalid reward component count" }
        val rewards = (0 until count).map { index ->
            val prefix = "component.$index."
            val reward = when (properties.required(prefix + "type")) {
                "currency" -> Reward.Currency(
                    properties.required(prefix + "rewardId"),
                    properties.required(prefix + "amount").toLong(),
                )
                "item" -> Reward.Item(
                    properties.required(prefix + "rewardId"),
                    properties.required(prefix + "amount").toInt(),
                )
                else -> error("unknown reward type in claim journal")
            }
            RewardComponent(properties.required(prefix + "id"), reward)
        }
        val claim = RewardClaim(
            claimId, playerId, properties.required("sourceModule"),
            properties.required("sourceId"), rewards,
        )
        val states = rewards.mapIndexed { index, component ->
            val prefix = "component.$index."
            component.id to ComponentRecord(
                UUID.fromString(properties.required(prefix + "operationId")),
                ComponentStatus.valueOf(properties.required(prefix + "status")),
                properties.getProperty(prefix + "reason"),
            )
        }.toMap()
        return RewardClaimRecord(claim, states)
    }

    override fun save(record: RewardClaimRecord) {
        Files.createDirectories(directory)
        val properties = Properties().apply {
            setProperty("version", "1")
            setProperty("claimId", record.claim.claimId.toString())
            setProperty("playerId", record.claim.playerId.toString())
            setProperty("sourceModule", record.claim.sourceModule)
            setProperty("sourceId", record.claim.sourceId)
            setProperty("count", record.claim.components.size.toString())
            record.claim.components.forEachIndexed { index, component ->
                val prefix = "component.$index."
                val step = requireNotNull(record.components[component.id])
                setProperty(prefix + "id", component.id)
                setProperty(prefix + "operationId", step.operationId.toString())
                setProperty(prefix + "status", step.status.name)
                step.reason?.let { setProperty(prefix + "reason", it) }
                when (val reward = component.reward) {
                    is Reward.Currency -> {
                        setProperty(prefix + "type", "currency")
                        setProperty(prefix + "rewardId", reward.currencyId)
                        setProperty(prefix + "amount", reward.amount.toString())
                    }
                    is Reward.Item -> {
                        setProperty(prefix + "type", "item")
                        setProperty(prefix + "rewardId", reward.itemId)
                        setProperty(prefix + "amount", reward.count.toString())
                    }
                }
            }
        }
        val temp = Files.createTempFile(directory, "claim-", ".tmp")
        try {
            Files.newBufferedWriter(temp, StandardCharsets.UTF_8).use {
                properties.store(it, "Zian Utilities reward claim")
            }
            FileChannel.open(temp, StandardOpenOption.WRITE).use { it.force(true) }
            Files.move(
                temp, pathFor(record.claim.claimId),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    private fun pathFor(claimId: UUID): Path = directory.resolve("$claimId.properties")

    private fun Properties.required(key: String): String =
        requireNotNull(getProperty(key)) { "missing claim journal property: $key" }
}

