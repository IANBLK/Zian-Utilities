# Cobblemon 1.8.1 research status

This document records the evidence status relevant to Milestone 1.

## Confirmed directly from the Cobblemon 1.8.1 JAR

The exact inspected artifact is:

`Cobblemon-neoforge-1.8.1+1.21.1(1).jar`

Confirmed by class listing and/or `javap`:

- `CobblemonEvents.ENTITY_SPAWN`
- `CobblemonEvents.POKEMON_ENTITY_SPAWN`
- `SpawnEvent<T>`
- `PlayerSpawnerFactory`
- `PlayerSpawnerFactory.influenceBuilders`
- `SpawningInfluence` and its public influence methods
- `SingleEntitySpawnAction.run()`

The bytecode of `SingleEntitySpawnAction.run()` confirms this sequence for that pipeline:

```text
createEntity
→ position entity
→ ENTITY_SPAWN
→ cancellation check
→ ServerLevel.addFreshEntity
```

Therefore the Cobblemon entity-spawn event is a confirmed cancellable point before world insertion for entities created through that action.

## Confirmed as present in the JAR

The following Cobblemon components are present and relevant:

- `FishingSpawnerFactory`
- `PokeSnackSpawnerFactory`
- `PokeRodFishingBobberEntity`
- `PokeSnackBlockEntity`
- `HabitatBlock`
- `HabitatBlockEntity`
- `ActivatedHabitatSpawning`
- `NaturalHabitatSpawning`
- `HabitatSpawnActivatedEvent`
- `SpawnCause`

Before implementation of source-specific guards, raw signature/bytecode evidence should be generated for the exact classes involved.

## Generation labels confirmed in Cobblemon 1.8.1

Official labels found in the JAR include:

```text
gen1
gen2
gen3
gen4
gen5
gen6
gen7
gen7b
gen8
gen8a
gen9
```

The planned product mapping is:

- `gen7b` → Generation 7
- `gen8a` → Generation 8

This mapping is a Zian Utilities product decision, not a claim that Cobblemon itself aliases those labels.

## Runtime verification still required

Do not assume that one hook covers every source.

Runtime testing is still required for:

- natural spawn lifecycle and cached player spawners
- fishing end-to-end
- Poké Snack end-to-end
- Habitat Block activated spawning
- Habitat Block natural spawning
- special/addon spawn paths
- NeoForge pure runtime
- Youer 1.21.1 runtime

Habitat is not a blocking acceptance requirement for the first Milestone 1 candidate until its path is validated end-to-end.

## Design rule

The production architecture must keep the generation policy/state independent from source-specific Cobblemon hooks so new spawn sources can be added without rewriting the core.
