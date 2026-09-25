# Generation pre-selection filtering research

Status: design validated against Cobblemon 1.8.x spawning APIs; production implementation intentionally not added yet.

## Problem

The current FishingSpawnGuard and PokeSnackSpawnGuard run on PRE events after Cobblemon has already selected a SpawnAction.

That is safe as a final enforcement barrier, but it can create poor gameplay when only a small number of generations are enabled:

1. Cobblemon selects a species from a disabled generation.
2. Zian Utilities cancels the PRE event.
3. No blocked Pokemon appears.
4. For fishing, the bobber is explicitly cleaned up.
5. The player must try again and wait for Cobblemon RNG to select an allowed species.

The same general issue can affect Poke Snack selection.

## Cobblemon API finding

Cobblemon 1.8 introduced public extension points specifically for this stage:

- FishingSpawnerFactory.positionInfluenceBuilders
- PokeSnackSpawnerFactory.influenceBuilders
- SpawningInfluence.affectSpawnable(SpawnDetail, SpawnablePosition)

SpawningInfluence.affectSpawnable is evaluated on SpawnDetail candidates before the final SpawnAction is created. This is the correct layer for Generation Control to remove disabled generations from the candidate set without rerolling after a denied PRE event.

PokemonSpawnDetail exposes PokemonProperties before SpawnAction creation, including the species identifier needed by CobblemonGenerationResolver.

## Recommended architecture

Use two enforcement layers.

### Layer 1: candidate filtering

Register a Zian generation SpawningInfluence with FishingSpawnerFactory and PokeSnackSpawnerFactory.

For each PokemonSpawnDetail candidate:

1. Read the species identifier from PokemonSpawnDetail.
2. Resolve species -> Generation using the shared CobblemonGenerationResolver.
3. Load the current GenerationState dynamically.
4. Apply GenerationPolicy.
5. Return true only when the candidate is allowed.

Non-Pokemon SpawnDetail values must return true.

This preserves Cobblemon's normal selector and weights among the remaining valid candidates. Zian Utilities must not perform its own retry/reroll loop.

### Layer 2: existing PRE guards

Keep FishingSpawnGuard and PokeSnackSpawnGuard.

They remain a fail-safe in case:

- another addon injects or mutates a SpawnAction after candidate filtering;
- a future Cobblemon change bypasses the influence path;
- an unknown/unclassified path reaches PRE.

The fishing bobber cleanup remains necessary for a denied PRE fallback.

## Dynamic state requirement

The influence must not snapshot enabled generations when registered.

GenerationState must be read when affectSpawnable is evaluated. This preserves the existing hot enable/disable guarantee and avoids the cached-spawner problem covered by NAT-03.

## Probability semantics

Do not set blocked candidate weights to zero unless Cobblemon explicitly guarantees zero-weight candidates are removed safely.

Prefer affectSpawnable(...)=false for blocked SpawnDetail candidates.

This lets Cobblemon perform its own weighted selection only over valid candidates. Relative weights and bait/rarity influences should continue to be handled by Cobblemon.

## Empty allowed pool

If the current biome/conditions contain no candidate from an enabled generation, the selection should produce no Pokemon.

Zian Utilities must not substitute a Pokemon from another biome, bucket, rarity, or generation and must not create a manual retry loop.

This is intentional: Generation Control restricts the existing Cobblemon pool; it does not invent new encounters.

## Fishing

FishingSpawnerFactory has a per-cast positionInfluenceBuilders extension point. Add the generation influence there.

This is preferable to replacing FishingSpawnerFactory.spawnPool or the shared fishing spawner because replacing global Cobblemon state would be more invasive and more likely to conflict with Habitat/addons.

Expected flow:

cast
-> Cobblemon builds FishingSpawnablePosition influences
-> generation influence removes disabled PokemonSpawnDetail candidates
-> Cobblemon selects normally from remaining candidates
-> BobberSpawnPokemonEvent.Pre
-> existing FishingSpawnGuard verifies final selected species
-> spawn

## Poke Snack

PokeSnackSpawnerFactory has influenceBuilders for newly-created FixedAreaSpawner instances. Add the same generation filtering concept there.

Expected flow:

Poke Snack spawner
-> generation influence filters disabled PokemonSpawnDetail candidates
-> Cobblemon selects normally from remaining candidates
-> PokeSnackSpawnPokemonEvent.Pre
-> existing PokeSnackSpawnGuard verifies final selected species
-> spawn
-> normal snack lifecycle

This should also make SNACK-02 easier to reason about: blocked species should normally never reach PRE after early filtering. PRE denial then becomes fallback-barrier validation rather than the primary gameplay path.

## Natural spawning

Do not change NaturalSpawnGuard as part of this patch.

PlayerSpawner also supports influences, but natural spawning already has different performance and compatibility considerations. Mixing that change into the fishing/snack UX fix would expand M1 risk unnecessarily.

Researching an early natural filter can be a separate optimization after M1 acceptance.

## Compatibility rules

The implementation must:

- append to Cobblemon factory influence builder lists; never replace them;
- never replace Cobblemon spawn pools;
- never clear existing influences;
- allow non-Pokemon SpawnDetail candidates;
- read GenerationState dynamically;
- reuse the shared species -> generation resolver and GenerationPolicy;
- retain PRE guards as final safety barriers;
- avoid retry loops;
- avoid spawning entities itself;
- avoid modifying bait, rarity, shiny, level, Habitat, or bucket weighting.

## Required tests before merge

1. Fishing with only one generation enabled and a mixed-generation fishing pool.
   Confirm repeated Pokemon encounters are from enabled generations without repeated denied PRE cleanup during normal operation.
2. Fishing fallback guard test.
   Confirm a deliberately forced blocked SpawnAction is still denied and bobber cleanup works.
3. Poke Snack with one generation enabled and a mixed-generation pool.
   Confirm bites/spawns are from enabled generations and snack lifecycle remains normal.
4. Poke Snack with no valid enabled candidate.
   Confirm no blocked Pokemon, no false bite/consumption, and no recursive retry.
5. Hot disable while fishing/snack systems already exist.
   Confirm the next selection uses the new state without restart.
6. ZianOBS trace.
   Confirm allowed normal paths still converge to the expected PRE -> ENTITY_SPAWN -> final event sequence.

## Decision

Early filtering is technically supported by Cobblemon's public spawning influence extension points and is preferable to repeated post-selection cancellation.

Proceed with a small implementation that appends a generation SpawningInfluence to FishingSpawnerFactory and PokeSnackSpawnerFactory while retaining the current PRE guards.

Before merging to main, compile/CI and runtime-test it on NeoForge, then Youer.
