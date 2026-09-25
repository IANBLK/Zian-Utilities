# PR #33 technical audit

Date: 2026-09-25
Scope: early generation filtering for Fishing and Poke Snack.

## Result

No data-integrity blocker was found. The approach uses Cobblemon's public spawning influence extension points and keeps the existing PRE guards as final enforcement.

Runtime validation on Youer has passed for mixed-generation filtering and live generation-state freshness. The PR remains open while the remaining empty-pool/diagnostic checks and formal M1 baseline bookkeeping are completed.

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

## Runtime validation status

Validated on Youer 1.21.1 with Cobblemon 1.8.1:

- mixed-generation fishing with one enabled generation produced enabled-generation encounters;
- normal fishing completed without a stuck bobber;
- Poke Snack produced enabled-generation encounters;
- an already-placed Poke Snack respected a live Gen3 -> Gen4 switch without restart;
- natural spawning and fishing also reflected the live switch;
- no post-switch Gen3 leak was observed in the tested paths;
- no crash, duplication or recursive spawn behavior was observed.

Still required before merge:

1. Candidate-level diagnostics: PASS on Youer with `-Dzianutilities.runtimeTestPreselection=true`. With active `[GEN_2, GEN_7]`, allowed candidates included Corsola/GEN_2, Wimpod/GEN_7, Golisopod/GEN_7, Wooper/GEN_2 and Qwilfish/GEN_2. Blocked candidates included Magikarp/GEN_1, Barbaracle/GEN_6, Staryu/GEN_1, Relicanth/GEN_3, Grapploct/GEN_8, Inkay/GEN_6, Starmie/GEN_1, Veluza/GEN_9 and Dratini/GEN_1.
2. Empty allowed pool: PASS on Youer. With no active generations, no new natural Pokémon appeared in the fresh area, Poké Snack produced no Pokémon, and fishing produced no Pokémon. No crash or recursive spawn behavior was observed. The log confirmed `Generaciones activas: ninguna`; previously loaded entities were reported only as entity loads.
3. Keep the explicit forced PRE fallback test for fishing cleanup as regression coverage.

## Audit decision

CI and the main Youer runtime behavior have passed.

The focused preselection runtime gate is now satisfied. Keep the existing PRE fishing cleanup regression evidence attached to the M1 record. PR #33 is ready for final review/merge; formal M1 release-candidate acceptance remains separately gated by the pure-NeoForge baseline in `M1_RUNTIME_PROTOCOL.md`. Formal M1 release-candidate acceptance also remains gated by the pure-NeoForge baseline in `M1_RUNTIME_PROTOCOL.md`.
