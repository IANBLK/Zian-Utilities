# Fishing javap evidence — Cobblemon 1.8.1

Date: 2026-09-24

Purpose: close the Milestone 1 source-classification question for fishing before production bootstrap.

## Artifact inspected

The reproducible GitHub Actions workflow resolved:

```text
com.cobblemon:neoforge:1.8.1+1.21.1
```

from:

```text
https://artefacts.cobblemon.com/releases/
```

Resolved Maven artifact SHA-256:

```text
c2c98de656b1169a9ef3f6a66d52663a8fe0e052b792a88d8d596d79de9834fd
```

This Maven artifact hash is not asserted to be identical to the previously inspected/runtime-distributed Cobblemon JAR. Findings below are implementation evidence for the published Maven artifact, cross-checked where noted against runtime observer output.

## Classes dumped

Relevant classes found directly in the outer Cobblemon Maven JAR:

```text
com.cobblemon.mod.common.api.spawning.fishing.FishingSpawnCause
com.cobblemon.mod.common.api.spawning.spawner.FishingSpawnerFactory
com.cobblemon.mod.common.api.spawning.spawner.FishingSpawnerFactory$Context
com.cobblemon.mod.common.entity.fishing.PokeRodFishingBobberEntity
```

## FishingSpawnerFactory

`FishingSpawnerFactory.createSharedSpawner()` returns:

```text
com.cobblemon.mod.common.api.spawning.spawner.BasicSpawner
```

Bytecode constructs that `BasicSpawner` with the string name:

```text
"fishing"
```

Therefore the fishing spawner is not represented by a unique Java/Kotlin subclass named `FishingSpawner`.

### Consequence

Do **not** classify every `BasicSpawner` as fishing.

That would be too broad and could classify unrelated present/future spawn paths incorrectly.

## PokeRodFishingBobberEntity planning path

The bytecode used to plan a fishing Pokémon spawn performs this sequence:

```text
BestSpawner.INSTANCE.getFishingSpawner()
    -> BasicSpawner

new FishingSpawnCause(
    fishingSpawner,
    ...
)

new FishingSpawnablePosition(
    fishingSpawnCause,
    ...
)

fishingSpawner.calculateSpawnActionForPosition(
    fishingSpawnCause,
    fishingSpawnablePosition
)
```

The exact method descriptor observed for `BestSpawner.getFishingSpawner()` is:

```text
()Lcom/cobblemon/mod/common/api/spawning/spawner/BasicSpawner;
```

The `FishingSpawnCause` constructor receives the spawner through the generic `Spawner` contract.

## Runtime cross-check

The runtime observer test from the clean NeoForge 21.1.251 + Cobblemon 1.8.1 environment recorded the same fishing spawn as:

```text
event=FISHING_PRE
action=1306324910

event=ENTITY_SPAWN
species=cobblemon:magikarp
spawner=com.cobblemon.mod.common.api.spawning.spawner.BasicSpawner
cause=com.cobblemon.mod.common.api.spawning.fishing.FishingSpawnCause

event=POKEMON_ENTITY_SPAWN
species=cobblemon:magikarp
spawner=com.cobblemon.mod.common.api.spawning.spawner.BasicSpawner
cause=com.cobblemon.mod.common.api.spawning.fishing.FishingSpawnCause

event=FISHING_POST
action=1306324910
sourceHint=FISHING
finalSpawnSeen=true
```

This cross-check confirms both:

1. fishing reaches the common final `POKEMON_ENTITY_SPAWN` barrier;
2. the final event exposes a generic `BasicSpawner`, while the cause is the specific `FishingSpawnCause`.

## Architecture decision

The Milestone 1 final-barrier classifier must **not** accept only `Spawner`.

Use source context that includes at least `SpawnCause`, or the whole `SpawnEvent<PokemonEntity>`.

Conceptual contract:

```kotlin
fun interface SpawnSourceClassifier {
    fun classify(event: SpawnEvent<PokemonEntity>): ControlledSource?
}
```

For fishing:

```kotlin
if (event.cause is FishingSpawnCause) {
    return ControlledSource.FISHING
}
```

A fallback identity comparison against `BestSpawner.INSTANCE.fishingSpawner` may be useful for diagnostics, but cause classification is more specific and avoids treating arbitrary `BasicSpawner` instances as fishing.

Unknown/unclassified sources remain `null` and the final barrier leaves them untouched.

## Status

Fishing source identity for Milestone 1:

```text
STATIC JAVAP: VERIFIED
RUNTIME FINAL EVENT: VERIFIED
FINAL CAUSE TYPE: FishingSpawnCause
FINAL SPAWNER TYPE: BasicSpawner
CLASSIFIER RULE: cause-based, not BasicSpawner instanceof
```
