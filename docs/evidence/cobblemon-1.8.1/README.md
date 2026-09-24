# Reproducible Cobblemon 1.8.1 evidence

Exact inspected file:

`Cobblemon-neoforge-1.8.1+1.21.1(1).jar`

SHA-256:

```text
5e6882d60d76f57c56dddbdfb9a66eafdb9f7b93103a305ca51f285b09d20c0d
```

Inspection tool:

```text
javap 21.0.11
```

## Reproduction commands

List relevant classes:

```sh
jar tf Cobblemon-neoforge-1.8.1+1.21.1.jar | grep -E 'Spawn|Fishing|PokeSnack|Habitat'
```

Public/private signatures:

```sh
javap -classpath Cobblemon-neoforge-1.8.1+1.21.1.jar -p <fully.qualified.ClassName>
```

Bytecode:

```sh
javap -classpath Cobblemon-neoforge-1.8.1+1.21.1.jar -p -c <fully.qualified.ClassName>
```

## Classes dumped during the current research pass

```text
com.cobblemon.mod.common.api.events.CobblemonEvents
com.cobblemon.mod.common.api.events.entity.SpawnEvent
com.cobblemon.mod.common.api.spawning.spawner.PlayerSpawnerFactory
com.cobblemon.mod.common.api.spawning.influence.SpawningInfluence
com.cobblemon.mod.common.api.spawning.detail.SingleEntitySpawnAction
com.cobblemon.mod.common.api.spawning.spawner.FishingSpawnerFactory
com.cobblemon.mod.common.api.spawning.spawner.PokeSnackSpawnerFactory
com.cobblemon.mod.common.entity.fishing.PokeRodFishingBobberEntity
com.cobblemon.mod.common.block.entity.PokeSnackBlockEntity
com.cobblemon.mod.common.block.entity.PokeSnackBlockEntity$Spawner
com.cobblemon.mod.common.api.events.habitats.HabitatSpawnActivatedEvent
com.cobblemon.mod.common.api.habitats.spawningstyle.ActivatedHabitatSpawning
com.cobblemon.mod.common.api.habitats.spawningstyle.NaturalHabitatSpawning
com.cobblemon.mod.common.block.habitat.HabitatBlock
com.cobblemon.mod.common.block.habitat.HabitatBlockEntity
com.cobblemon.mod.common.api.spawning.SpawnCause
com.cobblemon.mod.common.api.spawning.BestSpawner
com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent
com.cobblemon.mod.common.api.events.pokemon.LevelUpEvent
com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent
com.cobblemon.mod.common.api.events.battles.BattleFaintedEvent
com.cobblemon.mod.common.api.events.pokemon.PokemonFaintedEvent
com.cobblemon.mod.common.api.events.pokemon.CollectEggEvent
com.cobblemon.mod.common.api.events.pokemon.HatchEggEvent$Post
```

Evolution evidence uses:

`com.cobblemon.mod.common.api.events.pokemon.evolution.EvolutionCompleteEvent`

## Evidence policy

The raw `javap` output is considered static JAR evidence only.

It does not prove runtime event ordering across every source.

Runtime behavior must be recorded separately using the observer branch and `docs/RUNTIME_TEST_MATRIX.md`.

## Runtime observer

Research branch:

`research/runtime-spawn-observer`

The observer is intentionally read-only and logs with:

`[ZIAN-OBS]`

Do not merge the observer into production code. Its purpose is to close evidence gaps before the Generation Control implementation is finalized.
