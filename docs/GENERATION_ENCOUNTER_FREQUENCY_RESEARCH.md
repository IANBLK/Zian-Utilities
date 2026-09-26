# Generation-filter encounter frequency research

Status: IMPLEMENTED AND RUNTIME VALIDATED on the focused NeoForge baseline; pending final PR review/merge.

## Reported symptom

With generation filtering enabled, some generations (notably Gen2 in the observed NeoForge test) could produce many fishing attempts ending in Cobblemon's no-bite result, while another enabled generation such as Gen7 produced encounters much more often in the same workflow. Poké Snack could suffer the same class of imbalance.

Aventura 2 also plans to reduce normal world Pokémon spawning by about 60%. Generation Control must not compensate for that world-spawn tuning. Fishing and Poké Snack are deliberate player interactions and should not receive an accidental second penalty purely because disabled generations were removed.

## Root cause

Cobblemon chooses among rarity buckets while Zian's generation filter removes disabled-generation candidates from the concrete local candidate set. A bucket that originally had candidates can therefore become empty for the exact fishing/snack position after generation filtering.

That creates this outcome:

1. Cobblemon prepares bucket weights for the interaction.
2. The exact SpawnablePosition supplies biome/time/weather/fishing/snack context.
3. Zian filters disabled-generation candidates.
4. Some buckets can have zero locally valid enabled-generation candidates.
5. If an empty bucket remains eligible for the roll, selection can produce no spawn action.
6. Fishing reports the normal no-bite result; Poké Snack can likewise produce no Pokémon.

Different generations distribute their locally valid species differently across buckets, so the extra no-encounter probability can be strongly generation-dependent.

## Rejected approaches

A blind species-weight multiplier was rejected because it cannot repair an empty selected bucket and can distort relative Pokémon rarity.

Increasing natural spawning is out of scope because Aventura 2 intentionally plans a lower global Pokémon spawn rate.

Unbounded rerolls were rejected because they can distort probability, create recursion/performance risk, and make addon compatibility harder to reason about.

An earlier global-bucket prototype was also rejected before runtime testing because it could only determine whether an enabled generation existed somewhere in a bucket, not whether an enabled candidate was valid for the exact SpawnablePosition.

## Implemented solution

`GenerationPreselectionFilter.GenerationInfluence` now keeps the exact `SpawnablePosition` supplied by Cobblemon through `affectSpawnablePosition(...)`.

Before Cobblemon rolls the bucket, `affectBucketWeights(...)` evaluates each positive-weight bucket with Cobblemon's own `getMatchingSpawns(bucket, position)` for that exact position and then applies the existing generation `affectSpawnable` decision.

Buckets with no locally valid enabled-generation candidate receive weight 0. Remaining positive bucket weights are normalized while preserving their relative proportions.

Conceptually:

```text
Cobblemon conditions + exact SpawnablePosition
                    |
                    v
          original bucket weights
                    |
                    v
       getMatchingSpawns(bucket, position)
                    |
                    v
          generation eligibility filter
                    |
          +---------+---------+
          |                   |
       empty                valid
      weight 0          keep old weight
          |                   |
          +---------+---------+
                    |
                    v
        normalize remaining buckets
                    |
                    v
        Cobblemon normal selection
```

This does not modify individual Pokémon weights, shiny odds, level ranges, bait/rod conditions, or natural-spawn frequency. Existing PRE guards remain defense-in-depth.

## CI evidence

The corrected local-position implementation compiled successfully against the project's Cobblemon 1.8.1 / NeoForge baseline in CI #179 at commit `2b0c8000dc2c170b6f7bb6240da5ae16bfe072b3`.

The repository's Cobblemon API evidence workflow was also extended during this investigation to inspect the exact resolved 1.8.1 artifact for `Spawner`, `FlatSpawnablePositionWeightedSelector`, `SpawnablePosition`, `PokeRodFishingBobberEntity`, `BucketNormalizingInfluence`, `BucketMultiplyingInfluence`, Fishing/Poké Snack factories, and `SpawningInfluence`.

## Runtime evidence

Focused runtime validation was performed on the clean NeoForge baseline using Cobblemon 1.8.1 and the CI #179 Zian Utilities build.

Observed results:

- Gen2 fishing encounter frequency improved substantially compared with the pre-fix run.
- Fishing still produced legitimate no-bite outcomes; the patch does not guarantee an encounter on every cast.
- Gen2 -> Gen7 generation filtering continued to block disabled generations correctly.
- Gen1 was also exercised successfully afterward.
- Poké Snack was active near the player during fishing and continued to produce Pokémon while the fishing tests ran.
- Poké Snack respected the active-generation filtering in the observed run.
- Fishing and Poké Snack operated simultaneously without observed interference.
- No stuck fishing bobber, recursion, Zian-attributed exception, crash, or obvious duplication was observed.
- Normal world-spawn tuning was not changed by this patch.

## Acceptance decision

Focused Fishing and Poké Snack validation: PASS.

The implementation solves the observed generation-dependent empty-bucket penalty without turning every cast/snack cycle into a guaranteed encounter and without intentionally changing individual species rarity.

Before release promotion, retain the normal M1 persistence/diagnostic/performance gates and the broader Youer compatibility pass. This focused result closes the encounter-frequency regression itself; it does not replace those unrelated M1 gates.
