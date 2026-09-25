# M1 runtime results

Status: IN PROGRESS

Baseline: Minecraft 1.21.1, Java 21, NeoForge 21.1.251, Cobblemon 1.8.1.
Integrated preselection commit on main: f01434a3c5115c93d1627f2612ca52c63c4c1f4d

This file records observed runtime evidence only. Compilation success is not runtime acceptance. The pure-NeoForge baseline required by M1_RUNTIME_PROTOCOL.md is still pending, so M1 is not yet release-candidate accepted.

| Group | NeoForge | Youer | Notes |
|---|---|---|---|
| Generation state/commands | NOT RUN | PARTIAL PASS | Enable/disable/status/list exercised; hot Gen3 -> Gen4 mutation observed without restart. Full GEN-01..10 evidence set still needs formal recording. |
| Persistence | NOT RUN | PARTIAL PASS | Normal stop/start preserved generation state. Scheduled restart and forced abnormal-stop evidence remain formal gates. |
| Natural spawning | NOT RUN | PARTIAL PASS | Allowed/blocked behavior observed and hot generation change reflected immediately. NAT-03 remains an explicit formal case. |
| Fishing | NOT RUN | PASS | Allowed fishing, blocked-generation fallback/bobber cleanup, empty pool and preselection were exercised. |
| Poke Snack | NOT RUN | PASS | Allowed spawn, live state change on an existing snack, empty pool and preselection were exercised. |
| Safety exclusions | NOT RUN | PARTIAL PASS | Existing Pokemon, party, PC, admin give, battle, evolution and GTS receive were exercised. Breeding is unavailable in the current environment. |
| Diagnostics | NOT RUN | PARTIAL PASS | Candidate diagnostics were useful. Thread-affinity sampling recorded 70,665/70,665 preselection evaluations on the server thread. Formal DIAG-01/02 remains pending. |
| Performance sanity | NOT RUN | OBSERVED OK | No obvious recursion or runtime instability observed; formal PERF-01/02 remains pending. |
| Habitat research | NOT RUN | NOT RUN | Research-only and non-blocking for initial M1. |
| Youer compatibility | N/A | PARTIAL PASS | Core paths coexist successfully on the tested Youer environment. Formal YOUER-01..03 record remains pending after NeoForge acceptance. |

## Merged PR #33 evidence

The generation preselection work passed CI and focused Youer runtime validation before being merged to main.

Observed:
1. An existing Poke Snack followed a live Gen3 -> Gen4 state change without restart.
2. Natural spawning and fishing also followed the new state.
3. With no active generations, fresh controlled natural spawning, fishing and Poke Snack produced no new Pokemon.
4. Candidate-level tracing showed both ALLOW and DENY decisions with no observed denied-generation final spawn.
5. Thread-affinity tracing recorded 70,665 preselection evaluations; all reported sameServerThread=true and none reported false.
6. No Zian-attributed crash, duplication, recursion or stuck fishing bobber was observed.

The temporary PRESELECTION_THREAD logger was removed after evidence collection. Candidate ALLOW/DENY diagnostics remain opt-in only.

## Still required before M1 release-candidate acceptance

- Complete the reduced pure-NeoForge 21.1.251 baseline in M1_NEOFORGE_BASELINE_CHECKLIST.md.
- Execute/record NAT-03 explicitly.
- Execute/record PERSIST-03 forced abnormal stop on a disposable test world.
- Record the production-like scheduled restart as PERSIST-02.
- Finish formal DIAG-01/02 and PERF-01/02 evidence.
- After NeoForge acceptance, record the required Youer subset as YOUER-01..03.

Do not promote M1 to release-candidate status until those gates are satisfied.
