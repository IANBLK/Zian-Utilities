# Runtime observer test protocol

Use this protocol with `docs/RUNTIME_TEST_MATRIX.md`.

## Natural spawning

Start NeoForge + Cobblemon + observer, wait for normal Pokémon spawning and capture all `[ZIAN-OBS]` lines. Record spawner and cause classes.

## Fishing

Correlate:

```text
FISHING_PRE
POKEMON_ENTITY_SPAWN
FISHING_POST
```

using SpawnAction identity and the final Pokémon UUID.

## Poké Snack

Correlate:

```text
POKE_SNACK_PRE
POKEMON_ENTITY_SPAWN
POKE_SNACK_POST
```

## Activated Habitat

Trigger repeated activated Habitat spawns and record `HABITAT_ACTIVATED` plus subsequent final Pokémon spawn events. Do not infer causation from a single run.

## Natural Habitat influence

Compare spawner/cause/final event patterns inside and outside the Habitat influence area.

## Party send-out

Send out a party Pokémon and inspect `POKEMON_SENT_POST finalSpawnSeen=...`. This directly tests whether party entities share the final spawn event.

## Existing entity load

Persist Pokémon entities, restart, then compare `POKEMON_ENTITY_LOAD` with any final spawn records.

## Youer

Repeat only after NeoForge baseline results are recorded.

## Report fields

```text
test id
date
Minecraft
NeoForge/Youer
Cobblemon
observer commit
other mods/addons
steps
relevant [ZIAN-OBS] lines
result
interpretation
needs repeat
```
