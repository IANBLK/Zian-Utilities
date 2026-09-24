# Cobblemon 1.8.1 spawn evidence - extended

Source inspected directly:

`Cobblemon-neoforge-1.8.1+1.21.1(1).jar`

Method used:

- `jar tf`
- `javap -p`
- `javap -p -c`

This document records only findings observed in the exact JAR. Anything not demonstrated end-to-end remains a runtime verification item.

## FishingSpawnerFactory

Confirmed class:

`com.cobblemon.mod.common.api.spawning.spawner.FishingSpawnerFactory`

Confirmed public API includes:

```text
getSpawnPool()
setSpawnPool(SpawnPool)

getPositionInfluenceBuilders()
setPositionInfluenceBuilders(...)

createSharedSpawner()
findHabitatInfluences(ServerLevel, BlockPos, Spawner)
buildPositionInfluences(FishingSpawnerFactory.Context)
```

This confirms fishing has its own position influence builder mechanism.

## PokeSnackSpawnerFactory

Confirmed class:

`com.cobblemon.mod.common.api.spawning.spawner.PokeSnackSpawnerFactory`

Confirmed public API includes:

```text
getSpawnPool()
setSpawnPool(SpawnPool)

getInfluenceBuilders()
setInfluenceBuilders(...)

create(PokeSnackSpawnerFactory.Context)
```

The factory creates a `PokeSnackBlockEntity.Spawner`.

## Fishing event path

`PokeRodFishingBobberEntity` bytecode confirms this sequence for the Pokémon fishing path:

```text
planned SpawnAction
↓
CobblemonEvents.BOBBER_SPAWN_POKEMON_PRE
↓
emit event
↓
isCanceled()
↓
spawnPokemonFromFishing(...)
↓
SpawnAction.complete()
↓
SpawnAction.future.get()
↓
EntitySpawnResult
↓
CobblemonEvents.BOBBER_SPAWN_POKEMON_POST
```

The PRE event is therefore a confirmed source-specific cancellation point before `SpawnAction.complete()`.

The path also confirms fishing eventually completes a regular `SpawnAction`.

## Poké Snack event path

`PokeSnackBlockEntity` bytecode confirms:

```text
Spawner.calculateSpawnActionsForArea(...)
↓
first SpawnAction
↓
CobblemonEvents.POKE_SNACK_SPAWN_POKEMON_PRE
↓
emit event
↓
isCanceled()
↓
SpawnAction.complete()
↓
SpawnAction.future.get()
↓
EntitySpawnResult
↓
PokemonEntity
↓
CobblemonEvents.POKE_SNACK_SPAWN_POKEMON_POST
```

The PRE event is therefore a confirmed source-specific cancellation point before the action is completed.

## SpawnCause

Confirmed class:

`com.cobblemon.mod.common.api.spawning.SpawnCause`

It implements `SpawningInfluence`.

Confirmed data includes:

```text
Spawner
entity world id
entity id
entity UUID
entity type
entity lookup
```

Confirmed public methods include:

```text
getSpawner()
getEntityWorldId()
getEntityId()
getEntityUUID()
getEntityType()
getEntity()
isExpired()

affectSpawnablePosition(...)
affectSpawnable(...)
affectWeight(...)
affectAction(...)
affectSpawn(...)
affectBucketWeights(...)
normalizeBucketWeights(...)
isAllowedPosition(...)
injectSpawns(...)
```

This makes SpawnCause useful for classifying or carrying context about a spawning operation, but source classification must still be verified in runtime before it is treated as authoritative.

## Habitat activated spawning

Confirmed class:

`com.cobblemon.mod.common.api.habitats.spawningstyle.ActivatedHabitatSpawning`

Confirmed public API includes:

```text
getHabitatBlockEntity()
getChance()/setChance(...)
getTrigger()/setTrigger(...)
getCancelledNaturalSpawningRange()/set...
getSpawnRange()/set...
getMaxSpawns()/set...
getMaxSpawnsPerActivation()/set...
getSpawner()/setSpawner(...)
generateSpawnDetails(ServerLevel, BlockPos)
activate(ServerLevel, BlockPos)
```

The internal spawner type is `FixedAreaSpawner`.

### Activated Habitat event

Confirmed event:

`HabitatSpawnActivatedEvent extends Cancelable`

Confirmed fields/accessors include:

```text
HabitatBlockEntity
ActivatedHabitatSpawning
SpawnCause
maxSpawns
getSpawner() -> FixedAreaSpawner
```

Bytecode from `ActivatedHabitatSpawning` confirms creation and emission of:

`CobblemonEvents.HABITAT_SPAWN_ACTIVATED`

before the activation continues.

This confirms a Habitat-specific cancellation point exists.

It does **not** by itself prove every Habitat-generated Pokémon later passes through the same final Pokémon entity event. That remains runtime verification.

## Natural Habitat spawning

Confirmed class:

`com.cobblemon.mod.common.api.habitats.spawningstyle.NaturalHabitatSpawning`

Confirmed state/API includes:

```text
replaceSpawns
rangeOfInfluence
affectsFishing
affectsOverworld
generateSpawnDetails()
```

This demonstrates that Habitat Blocks can influence natural/fishing spawning behavior, but the exact end-to-end event sequence still needs runtime verification.

## BestSpawner

Confirmed class:

`com.cobblemon.mod.common.api.spawning.BestSpawner`

Confirmed relevant members include:

```text
defaultPokemonDespawner
fishingSpawner
init()
loadConfig()
reloadConfig()
onServerStarted(MinecraftServer)
```

This is useful context but should not become a dependency of the core generation policy unless implementation research proves it is necessary.

## Current evidence status

| Source | Source-specific hook | SpawnAction convergence | Runtime verification |
|---|---|---|---|
| Natural/player spawning | Influence builders confirmed | expected from researched pipeline | Required |
| Fishing | PRE confirmed | `SpawnAction.complete()` confirmed | Required |
| Poké Snack | PRE confirmed | `SpawnAction.complete()` confirmed | Required |
| Habitat activated | cancellable activation event confirmed | not proven end-to-end here | Required |
| Habitat natural influence | classes/state confirmed | not proven end-to-end here | Required |

## Design consequence

Milestone 1 should support an architecture where:

```text
Generation policy
       ↑
       │
source-specific early guard
       +
final Cobblemon spawn validation
```

but the source-specific adapters must remain replaceable.

Habitat remains non-blocking for the first acceptance candidate until runtime testing proves its end-to-end path.
