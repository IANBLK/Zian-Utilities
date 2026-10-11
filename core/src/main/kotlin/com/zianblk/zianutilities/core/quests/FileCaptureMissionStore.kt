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

/** One file per player, immediately persisted by atomic replacement on one server process. */
class FileCaptureMissionStore(private val directory: Path) : CaptureMissionStore {
    private val locks = Array(256) { Any() }

    override fun <T> withPlayerLock(playerId: UUID, action: () -> T): T =
        synchronized(locks[(playerId.hashCode() and Int.MAX_VALUE) % locks.size]) { action() }

    override fun load(playerId: UUID): CaptureMission? {
        val path = pathFor(playerId)
        if (!Files.exists(path)) return null
        val properties = Properties()
        Files.newBufferedReader(path, StandardCharsets.UTF_8).use(properties::load)
        require(properties.required("schemaVersion") == "1") { "unsupported mission schema" }
        require(properties.required("definitionId") == CaptureMission.DEFINITION_ID) {
            "unknown capture mission definition"
        }
        require(properties.required("definitionVersion") == CaptureMission.DEFINITION_VERSION.toString()) {
            "unsupported capture mission definition version"
        }
        require(properties.required("playerId") == playerId.toString()) { "mission player mismatch" }
        val species = properties.getProperty("capturedSpeciesId")
        val generation = properties.getProperty("capturedGeneration")?.let {
            requireNotNull(Generation.fromCanonicalId(it)) { "unknown captured generation" }
        }
        val completedAt = properties.getProperty("completedAtEpochMs")?.toLong()
        return CaptureMission(
            UUID.fromString(properties.required("assignmentId")),
            playerId,
            properties.required("acceptedAtEpochMs").toLong(),
            species,
            generation,
            completedAt,
        )
    }

    override fun save(mission: CaptureMission) {
        Files.createDirectories(directory)
        val properties = Properties().apply {
            setProperty("schemaVersion", "1")
            setProperty("definitionId", CaptureMission.DEFINITION_ID)
            setProperty("definitionVersion", CaptureMission.DEFINITION_VERSION.toString())
            setProperty("assignmentId", mission.assignmentId.toString())
            setProperty("playerId", mission.playerId.toString())
            setProperty("acceptedAtEpochMs", mission.acceptedAtEpochMs.toString())
            mission.capturedSpeciesId?.let { setProperty("capturedSpeciesId", it) }
            mission.capturedGeneration?.let { setProperty("capturedGeneration", it.id) }
            mission.completedAtEpochMs?.let { setProperty("completedAtEpochMs", it.toString()) }
        }
        val temp = Files.createTempFile(directory, "capture-mission-", ".tmp")
        try {
            Files.newBufferedWriter(temp, StandardCharsets.UTF_8).use {
                properties.store(it, "Zian Utilities capture mission")
            }
            FileChannel.open(temp, StandardOpenOption.WRITE).use { it.force(true) }
            Files.move(
                temp, pathFor(mission.playerId),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    private fun pathFor(playerId: UUID): Path = directory.resolve("$playerId.properties")

    private fun Properties.required(key: String): String =
        requireNotNull(getProperty(key)) { "missing capture mission property: $key" }
}

