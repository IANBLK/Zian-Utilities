# M1 runtime results

Status: IN PROGRESS

Baseline implementation commit: `acb47642c9e23c99aff98e75575e328f03d6f928`
Current validation branch: `research/generation-preselection-filter`

This file is evidence-only. Compilation success is not runtime acceptance.

Runtime evidence collected on the Aventura 2 Youer 1.21.1 test server with Java 21 and Cobblemon 1.8.1. The formal pure-NeoForge baseline required by `M1_RUNTIME_PROTOCOL.md` is still pending, so M1 is not yet release-candidate accepted.

| Group | NeoForge | Youer | Notes |
|---|---|---|---|
| Generation state/commands | NOT RUN | PARTIAL PASS | Enable/disable/status/list exercised; hot Gen3 -> Gen4 mutation observed without restart. Full GEN-01..10 evidence set still needs formal recording. |
| Persistence | NOT RUN | PARTIAL PASS | Normal stop/start preserved generation state. Scheduled-restart and forced-abnormal-stop evidence remain to be formally completed. |
| Natural spawning | NOT RUN | PARTIAL PASS | Allowed/blocked behavior observed and hot generation change reflected immediately. NAT-03 remains an explicit formal case. |
| Fishing | NOT RUN | PASS | Allowed fishing completed normally; blocked-generation fallback/bobber cleanup was previously validated; preselection build produced only enabled-generation encounters in observed samples. |
| Poké Snack | NOT RUN | PARTIAL PASS | Allowed spawn works. An already-placed snack switched from Gen3 behavior to Gen4 after live generation-state change, proving state freshness. Empty-pool lifecycle remains part of the PR #33 gate. |
| Safety exclusions | NOT RUN | PARTIAL PASS | Existing Pokémon, party send, PC withdraw, /givepokemon, battle, evolution and Zian GTS receive were exercised without Generation Control interference. Breeding is N/A in the current environment because no breeding system is installed. |
| Diagnostics | NOT RUN | PARTIAL PASS | ZianOBS and runtime diagnostics produced useful source/event evidence. Candidate-level preselection diagnostics still require the dedicated JVM flag. |
| Performance sanity | NOT RUN | OBSERVED OK | No obvious tick degradation or recursive spawn behavior observed during current tests; formal PERF-01/02 run remains pending. |
| Habitat research | NOT RUN | NOT RUN | Non-blocking for initial M1. |
| Youer compatibility | N/A | PARTIAL PASS | Commands, natural spawning, fishing, Poké Snack and Zian GTS coexistence exercised successfully. Formal YOUER-01..03 record remains to be completed after NeoForge baseline. |

## PR #33 runtime evidence

Validation build: generation preselection branch after successful CI.

Observed sequence:

1. Gen3 was active and a placed Poké Snack produced Gen3 encounters normally.
2. Without restarting the server, Gen3 was disabled and Gen4 enabled.
3. Natural spawning changed to Gen4.
4. The Poké Snack that already existed before the command change subsequently produced a Gen4 encounter.
5. Fishing after the live change produced enabled-generation encounters in the observed sample (Buizel, Gen4).
6. No post-switch Gen3 leak was observed in the tested paths.
7. No crash, duplication, stuck fishing bobber or Zian Utilities runtime error was observed.

This is evidence that the preselection influence does not snapshot generation state when a Poké Snack spawner is created. The state is evaluated dynamically during candidate filtering.

## Still required before M1 release-candidate acceptance

- Complete the reduced pure-NeoForge 21.1.251 baseline required by `M1_RUNTIME_PROTOCOL.md`.
- Execute/record NAT-03 explicitly.
- Complete the remaining PR #33 empty-pool lifecycle check and candidate-level diagnostic check.
- Execute/record PERSIST-03 forced abnormal stop.
- Record the production-like scheduled restart as PERSIST-02.
- Finish formal DIAG-01/02 and PERF-01/02 evidence.
- After NeoForge acceptance, record the required Youer subset as YOUER-01..03.

Do not promote M1 to release-candidate status until those gates are satisfied.
