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

/** Separate v1 cycle files leave the original capture mission data untouched. */
class FileCaptureCycleStore(private val directory: Path) : CaptureCycleStore {
    private val locks = Array(256) { Any() }

    override fun <T> withPlayerLock(playerId: UUID, action: () -> T): T =
        synchronized(locks[(playerId.hashCode() and Int.MAX_VALUE) % locks.size]) { action() }

    override fun load(playerId: UUID): CaptureCycle? {
        val path = currentPath(playerId)
        if (!Files.exists(path)) return null
        val p = Properties()
        Files.newBufferedReader(path, StandardCharsets.UTF_8).use(p::load)
        require(p.required("schemaVersion") == "1") { "unsupported cycle schema" }
        require(p.required("definitionId") == CaptureCycle.DEFINITION_ID)
        require(p.required("definitionVersion") == CaptureCycle.DEFINITION_VERSION.toString())
        require(p.required("playerId") == playerId.toString()) { "cycle player mismatch" }
        val count = p.required("captureCount").toInt()
        require(count in 0..CaptureCycle.GOAL)
        val captures = (0 until count).map { index ->
            CycleCapture(
                UUID.fromString(p.required("capture.$index.pokemonId")),
                p.required("capture.$index.speciesId"),
                requireNotNull(Generation.fromCanonicalId(p.required("capture.$index.generation"))),
                p.required("capture.$index.atEpochMs").toLong(),
            )
        }
        return CaptureCycle(
            UUID.fromString(p.required("cycleId")),
            playerId,
            p.required("assignedAtEpochMs").toLong(),
            captures,
        )
    }

    override fun save(cycle: CaptureCycle) = write(currentPath(cycle.playerId), cycle)

    override fun rotate(previous: CaptureCycle, next: CaptureCycle) {
        require(previous.playerId == next.playerId)
        val archive = directory.resolve("history")
            .resolve(previous.playerId.toString()).resolve("${previous.cycleId}.properties")
        if (!Files.exists(archive)) write(archive, previous)
        // A crash after archiving but before replacement leaves the old current file.
        // Retrying is safe because the archive is immutable and written first.
        save(next)
    }

    private fun write(path: Path, cycle: CaptureCycle) {
        val parent = path.parent
        Files.createDirectories(parent)
        val p = Properties().apply {
            setProperty("schemaVersion", "1")
            setProperty("definitionId", CaptureCycle.DEFINITION_ID)
            setProperty("definitionVersion", CaptureCycle.DEFINITION_VERSION.toString())
            setProperty("cycleId", cycle.cycleId.toString())
            setProperty("playerId", cycle.playerId.toString())
            setProperty("assignedAtEpochMs", cycle.assignedAtEpochMs.toString())
            setProperty("captureCount", cycle.captures.size.toString())
            cycle.captures.forEachIndexed { i, capture ->
                setProperty("capture.$i.pokemonId", capture.pokemonId.toString())
                setProperty("capture.$i.speciesId", capture.speciesId)
                setProperty("capture.$i.generation", capture.generation.id)
                setProperty("capture.$i.atEpochMs", capture.atEpochMs.toString())
            }
        }
        val temp = Files.createTempFile(parent, "capture-cycle-", ".tmp")
        try {
            Files.newBufferedWriter(temp, StandardCharsets.UTF_8).use {
                p.store(it, "Zian Utilities capture cycle")
            }
            FileChannel.open(temp, StandardOpenOption.WRITE).use { it.force(true) }
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    private fun currentPath(playerId: UUID) = directory.resolve("$playerId.properties")

    private fun Properties.required(key: String): String =
        requireNotNull(getProperty(key)) { "missing capture cycle property: $key" }
}

