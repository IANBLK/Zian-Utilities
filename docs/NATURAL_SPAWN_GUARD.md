# Natural spawn guard

Status: M1-06 implementation notes.

## Controlled path

The guard subscribes to CobblemonEvents.ENTITY_SPAWN and only evaluates events where the entity is a PokemonEntity and the spawner is a PlayerSpawner.

Runtime observer evidence previously recorded natural/player spawns with PlayerSpawner. Party send-out was observed through a separate path and is not classified here.

Decision flow:

ENTITY_SPAWN -> PokemonEntity -> PlayerSpawner -> CobblemonGenerationResolver -> current persisted GenerationState -> GenerationPolicy -> ALLOW or cancel.

## Cache safety

The guard reads the persisted generation state for every spawn decision. It does not copy enabled generations into an existing PlayerSpawner instance.

This specifically addresses NAT-03: a PlayerSpawner may already exist before an administrator disables a generation, but the next spawn decision reloads the current GenerationState and must still deny that generation.

The Species-to-Generation resolver may cache stable species mapping. The runtime enabled/disabled generation state is deliberately not cached by the guard.

## Unknown species

Until generation TOML wiring is implemented, the Milestone 1 default is UnknownSpeciesPolicy = DENY, matching CONFIG_SPEC.md.

## Safety boundary

The guard does not globally cancel PokemonEntity creation. It only controls PlayerSpawner events. Party send-out, PC withdrawal, direct admin-created Pokemon, battle entities, breeding, GTS delivery, fishing, Poke Snack and Habitat-specific routes are not intentionally controlled by M1-06.

Fishing and Poke Snack get their own source-specific guards in M1-07 and M1-08.

## Diagnostics

Denied natural spawns are logged only at DEBUG level with source, species, resolved generations and reason. Normal logs are not spammed.

## Runtime acceptance

Compilation and unit tests do not by themselves close NAT-01 through NAT-03. Runtime validation must verify:

- NAT-01: an enabled generation can spawn naturally.
- NAT-02: a disabled generation is denied.
- NAT-03: disable a generation after the player's PlayerSpawner has already been active; subsequent spawns from that generation remain denied without relog or restart.

Start on clean NeoForge 1.21.1 + Cobblemon 1.8.1, then repeat the accepted subset on Youer.