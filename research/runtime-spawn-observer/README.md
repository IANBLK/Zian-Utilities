# Runtime Spawn Observer

Temporary research mod for Zian Utilities.

## Purpose

This observer records Cobblemon spawn-related events without cancelling or mutating them. It exists only to answer runtime questions before the production Generation Control implementation is designed.

### Questions to verify

- Does `POKEMON_ENTITY_SPAWN` fire for natural spawns?
- Does it fire for fishing?
- Does it fire for Poké Snack?
- Does it fire for activated Habitat spawning?
- Does it fire for natural Habitat-influenced spawning?
- Does a party send-out fire the final spawn event?
- Can source-specific events be correlated with the final Pokémon entity event?
- What spawner/cause classes are visible for each source?
- Does behavior remain the same on Youer 1.21.1?

## Safety

This mod is observer-only. It must never call `cancel()`, modify a SpawnAction, modify Pokémon/entities, remove entities or alter game state.

Logs use the prefix `[ZIAN-OBS]`.

## Target

Minecraft 1.21.1, NeoForge 1.21.1, Java 21, Cobblemon 1.8.1.

## Build note

Supply the exact Cobblemon NeoForge JAR with:

```text
-PcobblemonJar=/absolute/path/to/Cobblemon-neoforge-1.8.1+1.21.1.jar
```

or place it at:

```text
research/runtime-spawn-observer/libs/Cobblemon-neoforge-1.8.1+1.21.1.jar
```

Do not commit the Cobblemon JAR.

This research project intentionally lives outside the production source tree and does not define Zian Utilities production architecture.
