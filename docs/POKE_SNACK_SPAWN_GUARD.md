# Poké Snack spawn guard

Status: M1-08 implementation notes.

## Controlled path

The guard subscribes to:

```text
CobblemonEvents.POKE_SNACK_SPAWN_POKEMON_PRE
```

and evaluates only planned `PokemonSpawnAction` values with a concrete species ID.

This is the source-specific cancellation point confirmed by the Cobblemon 1.8.1
bytecode research already recorded in `COBBLEMON_SPAWN_EVIDENCE_EXTENDED.md`.

## Decision flow

```text
POKE_SNACK_SPAWN_POKEMON_PRE
    ↓
PokemonSpawnAction?
    ↓
planned species ID
    ↓
CobblemonGenerationResolver
    ↓
current persisted GenerationState
    ↓
GenerationPolicy
    ↓
ALLOW or event.cancel()
```

Unknown or non-Pokémon actions are left untouched.

## State freshness

The guard reads the current persisted generation state for every Poké Snack
attempt. Enabled/disabled state is not cached in the guard.

Until TOML policy wiring lands, M1 continues to use:

```text
UnknownSpeciesPolicy = DENY
```

## Runtime diagnostics

Enable temporary validation logging with:

```text
-Dzianutilities.runtimeTestPokeSnack=true
```

Expected lines include:

```text
[ZIAN-RUNTIME] source=POKE_SNACK decision=ALLOW ...
[ZIAN-RUNTIME] source=POKE_SNACK decision=DENY ...
```

## Runtime acceptance

M1-08 remains open until clean NeoForge runtime testing demonstrates:

- SNACK-01: an enabled-generation Pokémon spawns normally;
- SNACK-02: a disabled-generation Pokémon is canceled cleanly;
- cancellation does not create a Pokémon or emit an unexpected successful POST;
- repeated cancellation does not break the Poké Snack block lifecycle.

After clean NeoForge acceptance, repeat the accepted subset on Youer 1.21.1.
