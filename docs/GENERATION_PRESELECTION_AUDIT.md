# PR #33 technical audit

Date: 2026-09-25
Scope: early generation filtering for Fishing and Poke Snack.

## Result

No data-integrity blocker was found. The approach uses Cobblemon's public spawning influence extension points and keeps the existing PRE guards as final enforcement.

The PR remains draft until runtime validation.

## Upstream API checks

Reviewed Cobblemon spawning APIs used by the implementation:

- FishingSpawnerFactory.positionInfluenceBuilders is explicitly intended for extenders to add per-cast influences.
- PokeSnackSpawnerFactory.influenceBuilders is explicitly intended to add influences to newly-created Poke Snack FixedAreaSpawners.
- SpawningInfluence.affectSpawnable returns whether a SpawnDetail is allowed under that influence.
- Spawner attaches its influences to the SpawnablePosition before selector selection.
- PokemonSpawnDetail exposes PokemonProperties before SpawnAction creation.

Therefore the filter runs before final SpawnAction creation and does not need to reroll or spawn anything itself.

## Weight and rarity behavior

GenerationPreselectionFilter only implements affectSpawnable.

It does not implement:

- affectWeight
- affectBucketWeights
- injectSpawns
- affectAction
- affectSpawn

It therefore does not directly rewrite Cobblemon weights, bait effects, rarity buckets, shiny chance, level ranges or spawn actions.

Fishing's existing PlayerLevelRangeInfluence, BucketNormalizingInfluence and Habitat influences remain registered.
Poke Snack's BucketNormalizingInfluence, SpawnBaitInfluence and SpawnRules remain registered.

## Empty pool behavior

If all PokemonSpawnDetail candidates for the selected conditions are rejected, Zian Utilities does not retry, substitute a species, select another generation, or create an entity.

The intended result is no valid Pokemon SpawnAction for that selection.

This must still be runtime-tested because the exact user-visible fishing/snack lifecycle is owned by Cobblemon.

## State freshness

The filter stores only the MinecraftServer reference.

GenerationState is loaded inside affectSpawnable for each candidate evaluation. It does not snapshot enabled generations when the influence is created.

This is especially important for Poke Snacks because their spawner influence can outlive a generation enable/disable command.

## Compatibility

The installation appends builders to Cobblemon's existing mutable builder lists.

It does not:

- replace spawnPool
- replace the builder lists
- clear another mod's influences
- mutate SpawnDetail
- mutate SpawnablePosition
- create/discard Pokemon entities
- modify natural PlayerSpawner behavior

Non-Pokemon SpawnDetail values pass through unchanged.

## Defense in depth

FishingSpawnGuard and PokeSnackSpawnGuard remain installed.

They still validate the concrete PokemonSpawnAction at PRE and deny a blocked generation if an addon or future Cobblemon path bypasses early filtering.

Fishing's explicit bobber cleanup therefore remains fallback behavior, not the expected normal filtering path.

## Diagnostic addition

Runtime-only diagnostics were added behind:

-Dzianutilities.runtimeTestPreselection=true

They log ALLOW/DENY candidate decisions with species, resolved generations and current active generations.

This flag is intended only for runtime validation because candidate-level logging can be noisy.

## Remaining runtime gate

Before merge:

1. Start with a mixed-generation fishing pool and only one generation enabled.
2. Confirm PRESELECTION DENY appears for blocked candidates while actual encounters remain enabled-generation Pokemon.
3. Confirm normal successful fishing no longer relies on repeated FishingSpawnGuard DENY/bobber cleanup.
4. Test Poke Snack with the same generation restriction.
5. Disable the active generation while an existing snack/spawner is present and confirm subsequent candidate decisions use the new state.
6. Test an empty allowed pool and confirm no blocked Pokemon, no recursion and no incorrect snack/bobber lifecycle.
7. Keep the explicit forced PRE fallback test for fishing cleanup.

## Audit decision

Ready for CI and runtime validation.

Not ready to merge to main until the runtime gate above passes.
