# Final spawn validation and diagnostics

Status: M1-09 implementation.

## Safety rule

The final ENTITY_SPAWN listener is diagnostic only. It does not cancel events.

Generation enforcement remains in the source-specific guards:

- NATURAL -> NaturalSpawnGuard
- FISHING -> FishingSpawnGuard
- POKE_SNACK -> PokeSnackSpawnGuard

This prevents a generic final listener from blocking party, PC, battle, admin,
breeding, evolution, GTS or addon flows merely because a PokemonEntity appears.

## Source classifier

The classifier receives the complete SpawnEvent and uses the strongest verified
signal available.

Fishing is classified by FishingSpawnCause. BasicSpawner is deliberately not a
fishing discriminator.

Natural spawning is classified by PlayerSpawner.

Unknown or unclassified sources return null and are never generation-filtered
by the final diagnostic layer.

Poké Snack is enforced at its source-specific PRE event. It is not inferred
from a generic final spawn event.

## Runtime diagnostics

Enable:

```text
-Dzianutilities.runtimeTestFinalBarrier=true
```

The final listener records source, canceled state, species, cause class and
spawner class. With the property disabled it emits only debug-level records.

Expected examples:

```text
[ZIAN-RUNTIME] stage=FINAL source=NATURAL ...
[ZIAN-RUNTIME] stage=FINAL source=FISHING ...
[ZIAN-RUNTIME] stage=FINAL source=UNCONTROLLED ...
```

## Acceptance

M1-09 runtime acceptance requires SAFE-01 through SAFE-08 plus DIAG-01 and
DIAG-02 from RUNTIME_TEST_MATRIX.md.

In particular, party, PC, admin commands, battle, evolution, breeding and GTS
must remain unaffected.
