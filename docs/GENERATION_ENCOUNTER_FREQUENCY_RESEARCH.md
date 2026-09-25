# Generation-filter encounter frequency research

Status: root cause narrowed; unsafe global-bucket prototype rejected; no gameplay patch applied yet.

## Reported symptom

With generation filtering enabled, some generations (for example Gen2 in the observed NeoForge test) can produce many fishing attempts ending in Cobblemon's no-bite result, while another enabled generation such as Gen7 can produce encounters much more often in the same general workflow. Poké Snack frequency can show the same class of imbalance.

The server owner also intends to reduce normal world Pokémon spawning by about 60%. Generation Control must not compensate for that world-spawn tuning. Fishing and Poké Snack are deliberate player interactions and should not receive an accidental second penalty purely because disabled generations were removed.

## Current Zian behavior

`GenerationPreselectionFilter` contributes a `SpawningInfluence` to Fishing and Poké Snack and overrides only `affectSpawnable`.

It does not alter:

- spawn detail weights;
- bucket weights;
- bait effects;
- rarity;
- shiny odds;
- level ranges;
- spawn actions.

Blocked generations are therefore removed from candidate eligibility, while Cobblemon retains control of bucket selection and weighted selection.

## Root-cause hypothesis

Cobblemon chooses a rarity bucket independently from Zian's generation filter. Candidate filtering happens when the selected bucket is evaluated for the concrete spawnable position.

This creates a valid empty-bucket outcome:

1. Cobblemon rolls a bucket.
2. The current biome/time/fishing or snack conditions produce a local candidate set.
3. Zian removes candidates belonging to disabled generations.
4. The selected bucket can now contain zero eligible candidates.
5. Selection returns no spawn action.
6. Fishing reports the normal no-bite result; a Poké Snack pass can likewise produce no Pokémon.

Different generations have different species distributions across common/uncommon/rare/ultra-rare buckets and different local conditions, so the probability that a rolled bucket becomes empty is generation-dependent.

This explains why the generation restriction can be correct while encounter frequency still differs sharply between generations.

## Why a blind weight multiplier is rejected

Multiplying remaining Pokémon weights cannot repair an empty selected bucket. It also risks changing relative species rarity.

Increasing natural spawning is explicitly out of scope because Aventura 2 intentionally plans a lower global Pokémon spawn rate.

Retry loops are also rejected because they can distort bucket rarity, create recursion/performance problems, and make integration with Cobblemon/addons harder to reason about.

## Required solution semantics

A production fix should:

1. preserve the configured global natural-spawn reduction;
2. preserve biome, time, weather, lure/bait, rod and other Cobblemon conditions;
3. preserve relative Pokémon weights inside an eligible bucket;
4. preserve Cobblemon's relative rarity-bucket weights among buckets that actually contain an eligible enabled-generation candidate;
5. exclude buckets that are empty only because no enabled-generation candidate is valid for the concrete interaction;
6. return no encounter when every bucket is genuinely empty;
7. avoid manual spawn creation and unbounded rerolls;
8. keep the existing PRE guards as defense-in-depth.

The desired operation is therefore **conditional bucket renormalization**, not species-weight inflation.

Example:

```text
Cobblemon fishing bucket weights
common      83.25
uncommon    11.25
rare         4.125
ultra-rare   1.375

For this exact cast, after normal conditions + generation filtering:
common       empty
uncommon     valid
rare         valid
ultra-rare   empty

Desired roll:
renormalize only uncommon + rare using their existing relative weights.
Do not promote individual species and do not invent candidates.
```

## Exact-artifact evidence

The repository workflow `Cobblemon API evidence` is extended on this research branch to dump the exact resolved Cobblemon 1.8.1 NeoForge artifact for:

- `Spawner`;
- `FlatSpawnablePositionWeightedSelector`;
- `SpawnablePosition`;
- `PokeRodFishingBobberEntity`;
- `BucketNormalizingInfluence`;
- `BucketMultiplyingInfluence`;
- Fishing/Poké Snack factories and `SpawningInfluence`.

Do not implement a production hook until this exact 1.8.1 bytecode confirms a stable interception point that can renormalize eligible buckets without replacing Cobblemon's selector or spawn pools.

## Claude review question

If an independent review is requested, provide this document plus the exact-artifact javap output and ask:

> In Cobblemon 1.8.1, what is the narrowest stable extension point that lets a side-mod exclude rarity buckets with zero locally valid candidates after a generation `affectSpawnable` filter, then renormalize the remaining bucket weights, without replacing the spawn pool/selector, rerolling SpawnActions, or changing relative species weights? Please identify any thread-safety, recursion, compatibility, or probability-distribution risks.

## Decision

The observed frequency difference is plausible and technically explained by bucket selection preceding/being independent from generation-filtered local candidate availability. The correct direction is conditional bucket renormalization. Implementation remains blocked on confirming the exact Cobblemon 1.8.1 interception surface; no speculative gameplay change should be merged before that evidence is reviewed.


## Prototype result

An experimental `affectBucketWeights` implementation was compiled successfully in CI #171, but it was deliberately removed before runtime testing.

Reason: `affectBucketWeights` receives only the bucket-weight map. A naive implementation can determine whether an enabled generation exists somewhere in a bucket, but it cannot prove that the bucket has an enabled-generation candidate that is valid for the **exact local SpawnablePosition** (biome, time, weather, fishing context, bait, and other conditions). Keeping that prototype would therefore hide some globally empty buckets but would not solve the reported local empty-bucket case reliably.

The prototype was reverted rather than handing a misleading build to runtime testers.

### Refined requirement

The intervention point must have both:

- the mutable bucket weights before the bucket roll; and
- the exact local `SpawnablePosition` (or an equivalent context sufficient to run Cobblemon's own matching logic).

If Cobblemon 1.8.1 exposes no stable public extension point with both pieces, prefer a narrowly-scoped compatibility hook/mixin at the bucket-choice boundary over global spawn-pool mutation, species-weight inflation, or unbounded rerolls.
