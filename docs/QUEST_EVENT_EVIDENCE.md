# Cobblemon 1.8.1 quest event evidence

Exact inspected artifact:

`Cobblemon-neoforge-1.8.1+1.21.1(1).jar`

Inspection method:

- `jar tf`
- `javap -p`
- selected signature inspection of `CobblemonEvents` and event classes

This document is research evidence for the future Quests module. It is not yet production architecture.

## Confirmed event observables

`CobblemonEvents` exposes the following relevant observables in Cobblemon 1.8.1:

```text
POKEMON_CAPTURED
BATTLE_VICTORY
BATTLE_FAINTED
POKEMON_FAINTED
EVOLUTION_COMPLETE
LEVEL_UP_EVENT
EXPERIENCE_GAINED_EVENT_PRE
EXPERIENCE_GAINED_EVENT_POST
POKEMON_SEEN
POKEDEX_DATA_CHANGED_PRE
POKEDEX_DATA_CHANGED_POST
COLLECT_EGG
HATCH_EGG_PRE
HATCH_EGG_POST
TRADE_EVENT_PRE
TRADE_EVENT_POST
BOBBER_SPAWN_POKEMON_PRE
BOBBER_SPAWN_POKEMON_MODIFY
BOBBER_SPAWN_POKEMON_POST
```

## Capture

Confirmed class:

`PokemonCapturedEvent`

Confirmed accessors:

```text
getPokemon() -> Pokemon
getPlayer() -> ServerPlayer
getPokeBallEntity() -> EmptyPokeBallEntity
getContext()
```

This is a strong direct candidate for capture quests because both player and captured Pokémon are provided explicitly.

Potential filters supported by the event payload plus Pokémon API:

- species
- generation via shared GenerationResolver
- shiny
- level
- primary/secondary type
- future labels/features if intentionally supported

## Evolution

Confirmed class:

`EvolutionCompleteEvent`

Confirmed accessors:

```text
getPokemon()
getSourcePokemon()
getEvolution()
getContext()
```

The event does not expose a ServerPlayer directly.

`Pokemon` exposes:

```text
getOwnerPlayer()
getOwnerUUID()
getOwnerEntity()
getSpecies()
getForm()
getLevel()
getTypes()
getShiny()
```

Therefore owner resolution appears possible for normal player-owned Pokémon, but this must be runtime-tested before it becomes a quest acceptance requirement.

## Level up

Confirmed class:

`LevelUpEvent`

Confirmed accessors:

```text
getPokemon()
getOldLevel()
getNewLevel()
getContext()
```

The Pokémon owner can potentially be resolved through `Pokemon.getOwnerPlayer()/getOwnerUUID()`.

Runtime verification is required for storage/offline ownership edge cases.

## Battle victory

Confirmed class:

`BattleVictoryEvent`

Confirmed accessors:

```text
getBattle()
getWinners()
getLosers()
getWasWildCapture()
getContext()
```

`BattleActor` exposes:

```text
getUuid()
getPlayerUUIDs()
isForPlayer(ServerPlayer)
getPokemonList()
getType()
```

This provides a viable basis for "win battles" objectives.

It does not by itself prove how to classify every battle as wild, trainer, NPC, PvP, raid or addon-specific. Battle type classification requires focused research/runtime verification.

## Battle fainted / defeat candidate

Confirmed class:

`BattleFaintedEvent`

Confirmed accessors:

```text
getBattle()
getKilled() -> BattlePokemon
getContext() -> BattleContext
getPnx()
getStructContext()
```

This is a likely source for objectives such as "defeat Pokémon", but attribution to the correct player and classification of the defeated target must be verified carefully.

Do not increment defeat quests from the generic `PokemonFaintedEvent` without proving battle/player attribution, because that event only exposes the Pokémon and fainted timer.

## Generic Pokémon fainted

Confirmed class:

`PokemonFaintedEvent`

Confirmed accessors:

```text
getPokemon()
getFaintedTimer()
getContext()
```

Useful as a Pokémon lifecycle event, but insufficient by itself for a safe "player defeated Pokémon" quest.

## Eggs / breeding

Confirmed observables:

```text
COLLECT_EGG
HATCH_EGG_PRE
HATCH_EGG_POST
```

`CollectEggEvent` exposes:

```text
getEgg() -> PokemonProperties
getMaleParent()
getFemaleParent()
getPlayer()
```

`HatchEggEvent.Post` exposes:

```text
getPlayer()
getPokemon()
```

These are strong candidates for future breeding/hatching objectives.

## Pokédex / seen

Confirmed observables:

```text
POKEMON_SEEN
POKEDEX_DATA_CHANGED_PRE
POKEDEX_DATA_CHANGED_POST
```

These make Pokédex-oriented quests technically plausible.

The exact semantics of data-change categories and whether they are preferable to direct capture/seen events should be researched before implementation.

## Fishing

The spawn pipeline research already confirms:

`BOBBER_SPAWN_POKEMON_PRE/MODIFY/POST`

These events describe Pokémon spawning from fishing, not necessarily successful player capture.

A quest such as "fish up Pokémon encounters" and a quest such as "capture Pokémon obtained through fishing" are different objectives and should not be conflated.

Tracking a successful capture that originated from fishing may require temporary correlation/context. That is **NEEDS RUNTIME VERIFICATION**.

## Trade

Confirmed:

```text
TRADE_EVENT_PRE
TRADE_EVENT_POST
```

Trade quests are technically possible, but anti-abuse rules would be required before exposing them as repeatable Daily/Weekly objectives.

## Strong candidates for first Quests implementation

Based on event evidence, the lowest-risk initial objectives are:

```text
CAPTURE
CAPTURE_SPECIES
CAPTURE_GENERATION
CAPTURE_TYPE
CAPTURE_SHINY
EVOLVE_OWNED_POKEMON
LEVEL_UP_OWNED_POKEMON
WIN_BATTLE
HATCH_EGG
```

"Defeat Pokémon" remains desirable but should wait until battle attribution is explicitly validated.

## Runtime verification checklist

Before production Quest listeners are accepted:

- Capture fires once per successful capture.
- EvolutionComplete resolves the expected player owner.
- LevelUp resolves ownership for party and PC Pokémon.
- BattleVictory maps player winners correctly in singles and doubles.
- BattleFainted attribution is tested in wild, NPC and PvP battles.
- Hatching fires once and identifies the player.
- Fishing-origin correlation is tested before adding a fishing-capture objective.
- Events are checked under Youer after NeoForge baseline.
