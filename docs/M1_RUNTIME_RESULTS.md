# M1 runtime results

Status: IN PROGRESS — FINAL VALIDATION BASELINE

Current implementation baseline: `95c64f42f96b0afc2adf7db747344ac90f4c0c72`
Validation source: `main` after PR #37

This file is evidence-only. Compilation success is not runtime acceptance.

Runtime evidence has already been collected on the Aventura 2 Youer 1.21.1 test server with Java 21 and Cobblemon 1.8.1, plus focused pure-NeoForge validation for generation filtering, Fishing and Poké Snack. M1 is not yet release-candidate accepted because the remaining formal gates below must still be recorded.

| Group | NeoForge | Youer | Notes |
|---|---|---|---|
| Generation state/commands | PARTIAL PASS | PARTIAL PASS | Enable/disable/status/list and hot generation mutation have been exercised. Do not repeat broad exploratory testing; final baseline only needs enough command/state coverage to support the remaining gates. |
| Persistence | PARTIAL PASS | PARTIAL PASS | Normal stop/start preserved generation state. PERSIST-03 forced abnormal stop and formal PERSIST-02 scheduled restart evidence remain. |
| Natural spawning | PASS | PARTIAL PASS | NAT-03 passed on the clean NeoForge baseline: generation disabled after PlayerSpawner activity remained denied for new natural spawns while another enabled generation continued spawning. |
| Fishing | PASS | PASS | PR #35 focused NeoForge runtime validation passed. Encounter frequency improved without guaranteeing every cast; generation filtering remained correct. |
| Poké Snack | PASS | PASS | PR #35 focused NeoForge runtime validation passed, including simultaneous Fishing + Poké Snack operation and generation filtering. |
| Safety exclusions | PARTIAL PASS | PARTIAL PASS | Existing Pokémon, party send, PC withdraw, /givepokemon, battle, evolution and Zian GTS receive were exercised without Generation Control interference. Breeding remains N/A when no breeding system is installed. |
| Diagnostics | PARTIAL PASS | PARTIAL PASS | Candidate/source diagnostics produced useful evidence. Thread-affinity sampling recorded 70,665/70,665 preselection evaluations on the server thread. DIAG-01/02 still need formal closure. |
| Performance sanity | OBSERVED OK | OBSERVED OK | No obvious tick degradation or recursive spawn behavior observed. PERF-01/02 still need formal closure. |
| Habitat research | NOT REQUIRED | NOT REQUIRED | Research-only and non-blocking for M1. |
| Youer compatibility | N/A | PARTIAL PASS | Commands, natural spawning, Fishing, Poké Snack and Zian GTS coexistence have been exercised. Formal YOUER-01..03 is the final compatibility subset after NeoForge acceptance. |

## PR #35 focused runtime evidence

Implementation merged to `main` as `95c64f42f96b0afc2adf7db747344ac90f4c0c72`.

Observed on the clean NeoForge 21.1.251 / Cobblemon 1.8.1 baseline:

1. Gen2 Fishing encounter frequency improved substantially compared with the pre-fix behavior.
2. Legitimate no-bite results remained; encounters are not forced on every cast.
3. Gen2 -> Gen7 filtering remained correct in the observed run.
4. Gen1 was also exercised successfully afterward.
5. Poké Snack operated near the player while Fishing was being tested.
6. Poké Snack continued to produce enabled-generation Pokémon.
7. Fishing and Poké Snack operated simultaneously without observed interference.
8. No stuck bobber, recursion, Zian-attributed exception, crash or obvious duplication was observed.

Focused Fishing/Poké Snack regression gate: PASS.

## NAT-03 runtime evidence

Environment: clean NeoForge 21.1.251 baseline with Cobblemon 1.8.1 and the current final-validation Zian Utilities implementation.

Observed sequence:

1. Gen2 was enabled and natural Gen2 activity was observed, establishing the already-active PlayerSpawner path.
2. Gen2 was disabled in-place without restarting the world/server or resetting the player spawning context.
3. Existing Pokémon were allowed to remain, as required by the protocol.
4. Gen7 was enabled afterward to prove that Cobblemon natural spawning remained active.
5. New Gen7 natural spawns continued to occur while no new Gen2 natural spawn was observed after Gen2 was disabled.
6. No recursive spawning, repeated-action loop, exception, crash or state corruption attributable to Zian Utilities was observed during the test.

Result: `NAT-03 PASS`.

This closes the NeoForge natural-spawning group for the current M1 implementation baseline. A later code change to generation enforcement or natural-spawn interception invalidates this acceptance and requires the affected case to be rerun.

## Remaining required gates before M1 release-candidate acceptance

Run these against the current `main` implementation baseline unless a later code change supersedes it:

1. `PERSIST-03` — forced abnormal stop, then restart and verify generation-state integrity.
2. `PERSIST-02` — record one production-like scheduled `stop -> wait -> start` restart and verify generation-state integrity.
3. `DIAG-01` and `DIAG-02` — formal diagnostic evidence only; prior thread-affinity evidence may be referenced where applicable instead of needlessly repeating equivalent stress work.
4. `PERF-01` and `PERF-02` — formal performance sanity evidence; stop on recursion, runaway logging or meaningful tick degradation attributable to Zian Utilities.
5. After NeoForge acceptance, execute the required Youer subset as `YOUER-01` through `YOUER-03`.

Do not repeat already accepted natural-spawn, Fishing or Poké Snack exploratory tests unless a later code change touches their relevant spawn-selection/enforcement path.

Do not promote M1 to release-candidate status until the remaining gates above are satisfied.
