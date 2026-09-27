package com.zianblk.zianutilities.core.quests

import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.Properties
import java.util.UUID

/** Keeps one current and one previous accepted quest per player. */
class FileGlobalQuestProgressStore(
    private val directory: Path,
    private val onEvict: (GlobalQuestProgress, GlobalQuestProgress) -> Unit = { _, _ -> },
) : GlobalQuestProgressStore {
    private val locks = Array(256) { Any() }

    override fun <T> withPlayerLock(playerId: UUID, action: () -> T): T =
        synchronized(locks[(playerId.hashCode() and Int.MAX_VALUE) % locks.size]) { action() }

    override fun load(playerId: UUID): GlobalQuestProgress? = read(directory.resolve("$playerId.properties"), playerId)

    override fun loadPrevious(playerId: UUID): GlobalQuestProgress? =
        read(directory.resolve("previous").resolve("$playerId.properties"), playerId)

    private fun read(path: Path, playerId: UUID): GlobalQuestProgress? {
        if (!Files.exists(path)) return null
        val p = Properties()
        Files.newBufferedReader(path, StandardCharsets.UTF_8).use(p::load)
        require(p.required("schema") == "1")
        require(p.required("playerId") == playerId.toString())
        val offer = GlobalQuestOffer(
            p.required("windowStart").toLong(),
            p.getProperty("targetSpecies")?.takeIf(String::isNotBlank),
            p.required("generationIds"),
            p.required("captureCurrency"), p.required("captureAmount").toLong(),
            p.required("battleCurrency"), p.required("battleAmount").toLong(),
            p.required("rewardsEnabled").toBooleanStrict(),
        )
        require(p.required("offerId") == offer.id.toString())
        val count = p.required("battleCount").toInt()
        require(count in 0..5)
        return GlobalQuestProgress(
            offer, playerId, p.required("acceptedAt").toLong(),
            p.getProperty("capturedPokemonId")?.takeIf(String::isNotBlank)?.let(UUID::fromString),
            (0 until count).map { UUID.fromString(p.required("battle.$it")) },
        )
    }

    override fun save(progress: GlobalQuestProgress) = write(directory.resolve("${progress.playerId}.properties"), progress)

    override fun archiveAndClear(progress: GlobalQuestProgress) {
        loadPrevious(progress.playerId)?.let { onEvict(it, progress) }
        write(directory.resolve("previous").resolve("${progress.playerId}.properties"), progress)
        Files.delete(directory.resolve("${progress.playerId}.properties"))
    }

    private fun write(path: Path, progress: GlobalQuestProgress) {
        Files.createDirectories(path.parent)
        val p = Properties().apply {
            setProperty("schema", "1")
            setProperty("playerId", progress.playerId.toString())
            setProperty("offerId", progress.offer.id.toString())
            setProperty("windowStart", progress.offer.windowStartEpochMs.toString())
            setProperty("targetSpecies", progress.offer.targetSpecies ?: "")
            setProperty("generationIds", progress.offer.generationIds)
            setProperty("captureCurrency", progress.offer.captureCurrency)
            setProperty("captureAmount", progress.offer.captureAmount.toString())
            setProperty("battleCurrency", progress.offer.battleCurrency)
            setProperty("battleAmount", progress.offer.battleAmount.toString())
            setProperty("rewardsEnabled", progress.offer.rewardsEnabled.toString())
            setProperty("acceptedAt", progress.acceptedAtEpochMs.toString())
            setProperty("capturedPokemonId", progress.capturedPokemonId?.toString() ?: "")
            setProperty("battleCount", progress.battleIds.size.toString())
            progress.battleIds.forEachIndexed { index, id -> setProperty("battle.$index", id.toString()) }
        }
        val temp = Files.createTempFile(path.parent, "global-quest-", ".tmp")
        try {
            Files.newBufferedWriter(temp, StandardCharsets.UTF_8).use { p.store(it, "Zian Utilities global quest") }
            FileChannel.open(temp, StandardOpenOption.WRITE).use { it.force(true) }
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    private fun Properties.required(key: String): String = requireNotNull(getProperty(key)) { "missing $key" }
}

