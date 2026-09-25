# M1 runtime results

Status: IN PROGRESS

Baseline under validation: Minecraft 1.21.1, Java 21, NeoForge 21.1.251, Cobblemon 1.8.1.

This file is evidence-only. Compilation success is not runtime acceptance. Results below record only scenarios actually exercised. The formal pure-NeoForge baseline is still pending and therefore M1 is not yet release-candidate accepted.

## Summary

| Group | NeoForge | Youer | Evidence / remaining work |
|---|---|---|---|
| Generation state/commands | PENDING | PASS (partial matrix) | enable/disable/status, multi-generation and hot mutation exercised |
| Persistence | PENDING | PASS normal restart | manual stop/start passed; scheduled restart and abnormal-stop scenarios remain formal gates |
| Natural spawning | PENDING | PASS | allowed/blocked behavior and live generation switch observed |
| Fishing | PENDING | PASS | allowed encounters, blocked/empty pool behavior, cleanup regression and preselection exercised |
| Poké Snack | PENDING | PASS | allowed encounters, empty pool, live state switch on an already placed snack, and preselection exercised |
| Safety exclusions | PENDING | PASS for exercised paths | existing Pokémon, party, PC, admin give, battle, evolution and GTS exercised; breeding unavailable in this environment |
| Diagnostics | PENDING | PASS for exercised flags | runtime diagnostics produced useful source/candidate evidence; flags must be disabled after testing |
| Performance sanity | PENDING | NOT FORMALLY RUN | no runtime instability observed, but PERF-01/02 are not recorded as formal passes |
| Habitat research | N/A | N/A | research-only, non-blocking for initial M1 |
| Youer compatibility | N/A | PASS for exercised core subset | Youer 1.21.1 + Cobblemon 1.8.1; no observed Zian crash/dupe/regression |

## Recorded Youer evidence

### Generation control and natural spawning

- Generation enable/disable/status commands were exercised successfully.
- Multiple active generations were exercised.
- A live Gen3 -> Gen4 change was performed without restarting the server.
- Natural PlayerSpawner output followed the new active generation state after the live change.
- With no generations active and after moving to a fresh area, no new controlled natural Pokémon were observed.

### Fishing

- Gen3-only fishing produced Gen3 encounters including Azurill, Barboach, Wailmer, Relicanth and Carvanha.
- After the live Gen3 -> Gen4 change, subsequent fishing produced Gen4 Buizel encounters without restart.
- With no active generations, fishing produced no Pokémon.
- The blocked-PRE cleanup regression was tested after the fishing bobber cleanup fix; no stuck bobber, duplication or Zian-attributed crash was observed.
- Candidate preselection diagnostics were exercised on the PR #33 validation build. With active Gen2 + Gen7, both allowed and denied candidates were observed before final encounter selection, and no denied-generation candidate was observed becoming the final spawn.

### Poké Snack

- Gen3-only Poké Snack flow produced Gen3 Pokémon including Castform, Torchic and Taillow.
- An already placed snack/spawner remained live across a Gen3 -> Gen4 state change and later produced Gen4 Electivire without restart.
- With no active generations, the snack produced no Pokémon.
- Candidate preselection diagnostics were exercised with Gen2 + Gen7 active; allowed and denied candidates were observed and no denied candidate was observed as a final snack spawn.

### Safety

The exercised runtime paths showed no interference with:
- already existing Pokémon;
- party send/receive;
- PC withdrawal;
- admin `/givepokemon`;
- battles;
- evolution;
- GTS receive.

Breeding is not available in the current test environment and is therefore not recorded as PASS.

### Diagnostics

The focused preselection validation used:

```text
-Dzianutilities.runtimeTestPreselection=true
```

The diagnostic run produced thousands of PRESELECTION lines, as expected from candidate-level tracing. This flag is test-only and must be removed/disabled for normal production use. The generation filter itself remains active when the diagnostic flag is off.

## Formal gaps before M1 release-candidate acceptance

1. Run the required reduced baseline on pure NeoForge 21.1.251 with Cobblemon 1.8.1.
2. Record PERSIST-02 using the production-like scheduled stop -> wait -> start path.
3. Record PERSIST-03 using an abnormal stop in a disposable test world and verify generation state recovery after restart.
4. Record the remaining formal DIAG/PERF scenarios instead of inferring them from ordinary play.
5. Mark unavailable scenarios explicitly BLOCKED/N/A where the protocol permits it; do not silently convert them to PASS.
6. Repeat/confirm the required Youer core subset after the accepted NeoForge baseline if necessary.

## PR #33 note

Generation preselection work is isolated in PR #33 at reviewed head `417a4c7fa7b23ce6f4052f6e511dd962f4be733c`. It passed CI and focused Youer runtime validation. It remains separate from this documentation branch while awaiting independent audit.

## Acceptance rule

M1 may move to release-candidate status only when the pure-NeoForge required baseline is recorded as passing and the required Youer subset shows no compatibility regression.
