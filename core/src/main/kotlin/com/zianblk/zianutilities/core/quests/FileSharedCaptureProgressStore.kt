package com.zianblk.zianutilities.core.quests

import com.zianblk.zianutilities.core.generation.Generation
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.Properties
import java.util.UUID

/** One current and at most one previous acceptance record per player. */
class FileSharedCaptureProgressStore(private val directory: Path) : SharedCaptureProgressStore {
    private val locks = Array(256) { Any() }

    override fun <T> withPlayerLock(playerId: UUID, action: () -> T): T =
        synchronized(locks[(playerId.hashCode() and Int.MAX_VALUE) % locks.size]) { action() }

    override fun load(playerId: UUID): SharedCaptureProgress? {
        val path = currentPath(playerId)
        if (!Files.exists(path)) return null
        val p = Properties()
        Files.newBufferedReader(path, StandardCharsets.UTF_8).use(p::load)
        require(p.required("schemaVersion") == "1") { "unsupported shared mission schema" }
        require(p.required("definitionVersion") == SharedCaptureMission.DEFINITION_VERSION.toString())
        require(p.required("playerId") == playerId.toString()) { "shared mission player mismatch" }
        val count = p.required("captureCount").toInt()
        require(count in 0..SharedCaptureMission.GOAL)
        val captures = (0 until count).map { i ->
            CycleCapture(
                UUID.fromString(p.required("capture.$i.pokemonId")),
                p.required("capture.$i.speciesId"),
                requireNotNull(Generation.fromCanonicalId(p.required("capture.$i.generation"))),
                p.required("capture.$i.atEpochMs").toLong(),
            )
        }
        return SharedCaptureProgress(
            UUID.fromString(p.required("missionId")),
            playerId,
            p.required("windowStartEpochMs").toLong(),
            p.required("acceptedAtEpochMs").toLong(),
            captures,
        )
    }

    override fun save(progress: SharedCaptureProgress) = write(currentPath(progress.playerId), progress)

    override fun archiveAndClear(progress: SharedCaptureProgress) {
        write(directory.resolve("previous").resolve("${progress.playerId}.properties"), progress)
        // Retrying after a crash is safe: previous is replaced before current is removed.
        Files.delete(currentPath(progress.playerId))
    }

    private fun write(path: Path, progress: SharedCaptureProgress) {
        val parent = path.parent
        Files.createDirectories(parent)
        val p = Properties().apply {
            setProperty("schemaVersion", "1")
            setProperty("definitionVersion", SharedCaptureMission.DEFINITION_VERSION.toString())
            setProperty("missionId", progress.missionId.toString())
            setProperty("playerId", progress.playerId.toString())
            setProperty("windowStartEpochMs", progress.windowStartEpochMs.toString())
            setProperty("acceptedAtEpochMs", progress.acceptedAtEpochMs.toString())
            setProperty("captureCount", progress.captures.size.toString())
            progress.captures.forEachIndexed { i, capture ->
                setProperty("capture.$i.pokemonId", capture.pokemonId.toString())
                setProperty("capture.$i.speciesId", capture.speciesId)
                setProperty("capture.$i.generation", capture.generation.id)
                setProperty("capture.$i.atEpochMs", capture.atEpochMs.toString())
            }
        }
        val temp = Files.createTempFile(parent, "shared-capture-", ".tmp")
        try {
            Files.newBufferedWriter(temp, StandardCharsets.UTF_8).use {
                p.store(it, "Zian Utilities shared capture progress")
            }
            FileChannel.open(temp, StandardOpenOption.WRITE).use { it.force(true) }
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    private fun currentPath(playerId: UUID) = directory.resolve("$playerId.properties")

    private fun Properties.required(key: String): String =
        requireNotNull(getProperty(key)) { "missing shared mission property: $key" }
}

