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

/** Atomic per-player files for one running server process. */
class FileCaptureTrialStore(private val directory: Path) : CaptureTrialStore {
    private val locks = Array(256) { Any() }

    override fun <T> withPlayerLock(playerId: UUID, action: () -> T): T =
        synchronized(locks[(playerId.hashCode() and Int.MAX_VALUE) % locks.size]) { action() }

    override fun load(playerId: UUID): CaptureTrial? {
        val path = pathFor(playerId)
        if (!Files.exists(path)) return null
        val properties = Properties()
        Files.newBufferedReader(path, StandardCharsets.UTF_8).use(properties::load)
        require(properties.required("version") == "1") { "unsupported capture trial version" }
        require(properties.required("playerId") == playerId.toString()) { "capture trial player mismatch" }
        val ids = properties.required("targetGenerations").split(",")
        val targets = ids.map { id ->
            requireNotNull(Generation.fromCanonicalId(id)) {
                "unknown capture trial generation: $id"
            }
        }.toSet()
        require(targets.isNotEmpty()) { "capture trial has no target generations" }
        return CaptureTrial(
            UUID.fromString(properties.required("assignmentId")),
            playerId,
            targets,
            properties.getProperty("capturedSpeciesId"),
        )
    }

    override fun save(trial: CaptureTrial) {
        Files.createDirectories(directory)
        val properties = Properties().apply {
            setProperty("version", "1")
            setProperty("assignmentId", trial.assignmentId.toString())
            setProperty("playerId", trial.playerId.toString())
            setProperty("targetGenerations", trial.targetGenerations
                .sortedBy { it.ordinal }.joinToString(",") { it.id })
            trial.capturedSpeciesId?.let { setProperty("capturedSpeciesId", it) }
        }
        val temp = Files.createTempFile(directory, "capture-trial-", ".tmp")
        try {
            Files.newBufferedWriter(temp, StandardCharsets.UTF_8).use {
                properties.store(it, "Zian Utilities capture trial")
            }
            FileChannel.open(temp, StandardOpenOption.WRITE).use { it.force(true) }
            Files.move(
                temp, pathFor(trial.playerId),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    private fun pathFor(playerId: UUID): Path = directory.resolve("$playerId.properties")

    private fun Properties.required(key: String): String =
        requireNotNull(getProperty(key)) { "missing capture trial property: $key" }
}

