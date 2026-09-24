# Fishing spawn guard

Status: M1-07 implementation notes.

## Controlled path

The guard subscribes to:

```text
CobblemonEvents.BOBBER_SPAWN_POKEMON_PRE
```

and evaluates only planned `PokemonSpawnAction` values with a concrete species ID.

This is the early, source-specific fishing hook. It avoids treating every later
`BasicSpawner` event as fishing.

## Why PRE is used

Static bytecode evidence and prior runtime observation established the fishing path:

```text
BOBBER_SPAWN_POKEMON_PRE
    ↓
SpawnAction.complete()
    ↓
ENTITY_SPAWN
    ↓
POKEMON_ENTITY_SPAWN
    ↓
BOBBER_SPAWN_POKEMON_POST
```

The final common events expose:

```text
spawner = BasicSpawner
cause   = FishingSpawnCause
```

Therefore `BasicSpawner` is not a safe fishing discriminator.

M1-07 uses the dedicated PRE event for source-specific cancellation. The later
M1-09 final barrier will still classify fishing by `FishingSpawnCause` as a
defense-in-depth check.

## Decision flow

```text
BOBBER_SPAWN_POKEMON_PRE
    ↓
PokemonSpawnAction?
    ↓
planned species ID
    ↓
CobblemonGenerationResolver
    ↓
current persisted GenerationState
    ↓
GenerationPolicy
    ↓
ALLOW or event.cancel()
```

Unknown or non-Pokemon spawn actions are left untouched rather than being
guessed at.

## State freshness

Like the natural guard, FishingSpawnGuard does not cache enabled/disabled
generation state.

Every fishing attempt reads the current `GenerationStateSavedData` state.

## Unknown species

Until TOML policy wiring lands, M1 uses:

```text
UnknownSpeciesPolicy = DENY
```

## Runtime diagnostics

Temporary validation logging can be enabled with:

```text
-Dzianutilities.runtimeTestFishing=true
```

Expected evidence:

```text
[ZIAN-RUNTIME] source=FISHING decision=ALLOW ...
[ZIAN-RUNTIME] source=FISHING decision=DENY ...
```

## Runtime acceptance

M1-07 should not be considered runtime-complete until a clean NeoForge test
shows both:

- a fishing Pokemon from an enabled generation is allowed and the fishing
  mechanic completes normally;
- a fishing Pokemon from a disabled generation is denied without breaking the
  rod/bobber flow or producing a duplicate/post event for a canceled spawn.

After clean NeoForge verification, repeat the accepted subset on Youer.
