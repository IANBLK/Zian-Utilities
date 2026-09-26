# M1 runtime results

Status: IN PROGRESS — FINAL VALIDATION BASELINE

Current implementation baseline: `95c64f42f96b0afc2adf7db747344ac90f4c0c72`
Validation source: `main` after PR #35

This file is evidence-only. Compilation success is not runtime acceptance.

Runtime evidence has already been collected on the Aventura 2 Youer 1.21.1 test server with Java 21 and Cobblemon 1.8.1, plus focused pure-NeoForge validation for generation filtering, Fishing and Poké Snack. M1 is not yet release-candidate accepted because the remaining formal gates below must still be recorded.

| Group | NeoForge | Youer | Notes |
|---|---|---|---|
| Generation state/commands | PARTIAL PASS | PARTIAL PASS | Enable/disable/status/list and hot generation mutation have been exercised. Do not repeat broad exploratory testing; final baseline only needs enough command/state coverage to support the remaining gates. |
| Persistence | PARTIAL PASS | PARTIAL PASS | Normal stop/start preserved generation state. PERSIST-03 forced abnormal stop and formal PERSIST-02 scheduled restart evidence remain. |
| Natural spawning | PARTIAL PASS | PARTIAL PASS | Allowed/blocked behavior and live generation changes were observed. NAT-03 remains an explicit formal case. |
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

## Remaining required gates before M1 release-candidate acceptance

Run these against the current `main` implementation baseline unless a later code change supersedes it:

1. `NAT-03` — explicitly record the required natural-spawn edge case from `M1_RUNTIME_PROTOCOL.md` / scenario documentation.
2. `PERSIST-03` — forced abnormal stop, then restart and verify generation-state integrity.
3. `PERSIST-02` — record one production-like scheduled `stop -> wait -> start` restart and verify generation-state integrity.
4. `DIAG-01` and `DIAG-02` — formal diagnostic evidence only; prior thread-affinity evidence may be referenced where applicable instead of needlessly repeating equivalent stress work.
5. `PERF-01` and `PERF-02` — formal performance sanity evidence; stop on recursion, runaway logging or meaningful tick degradation attributable to Zian Utilities.
6. After NeoForge acceptance, execute the required Youer subset as `YOUER-01` through `YOUER-03`.

Do not repeat already accepted Fishing/Poké Snack exploratory tests unless a later code change touches their spawn-selection path.

Do not promote M1 to release-candidate status until the remaining gates above are satisfied.
