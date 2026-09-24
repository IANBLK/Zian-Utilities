# Milestone 1 architecture decisions

Status: accepted decisions to apply during production bootstrap.

## Unknown species policy

Decision: **Option A**.

`UnknownSpeciesPolicy` applies even when all nine canonical generations are enabled.

There is no special "all generations means transparent filter" exception.

Examples:

```text
9 generations enabled + unknown + ALLOW -> allow
9 generations enabled + unknown + DENY  -> deny
```

This keeps the policy explicit and deterministic.

## Milestone 1 controlled sources

Required:

```text
NATURAL
FISHING
POKE_SNACK
```

Habitat remains present only behind a disabled-by-default feature flag and is not part of the initial Definition of Done until end-to-end runtime verification is complete.

## Final barrier classification

`POKEMON_ENTITY_SPAWN` is not itself proof that the spawn should be generation-controlled.

The final barrier must classify the source first.

Unknown/admin/addon/unclassified sources are allowed through without consulting `GenerationPolicy`.

### Classifier input

Do not define the production classifier as:

```kotlin
classify(spawner: Spawner)
```

Fishing static + runtime evidence shows its final spawner is the generic `BasicSpawner`.

Instead pass the complete spawn event or both cause and spawner:

```kotlin
fun interface SpawnSourceClassifier {
    fun classify(event: SpawnEvent<PokemonEntity>): ControlledSource?
}
```

The implementation may use:

- concrete spawner type/identity where that is proven safe;
- concrete `SpawnCause` type where it is the stronger discriminator;
- feature flags for sources not yet accepted.

### Fishing

Fishing is identified by:

```text
cause instanceof FishingSpawnCause
```

Runtime evidence independently observed:

```text
spawner = BasicSpawner
cause   = FishingSpawnCause
```

Therefore `instanceof BasicSpawner -> FISHING` is explicitly forbidden.

## Core boundary

`core` knows only canonical:

```text
gen1 ... gen9
```

Cobblemon-specific aliases stay in `CobblemonGenerationResolver`:

```text
gen7b -> GEN_7
gen8a -> GEN_8
```

## Package

```text
com.zianblk.zianutilities
```

## Build baseline

```text
Minecraft 1.21.1
Java 21
NeoForge 21.1.x
Cobblemon 1.8.1
Gradle 9.2.1
```

Cobblemon Maven:

```text
https://artefacts.cobblemon.com/releases/
com.cobblemon:neoforge:1.8.1+1.21.1
```

## Configuration

Use NeoForge `ModConfigSpec` / TOML for module/operator configuration.

Future data-driven definitions such as quests, rewards and Gacha pools may use JSON.

## CI baseline

Pin the runner and toolchain instead of using floating defaults.

Current baseline:

```text
ubuntu-24.04
Java 21
Gradle 9.2.1
```

Action major versions should be verified at the time the production workflow is created rather than copied blindly from an older architecture draft.
