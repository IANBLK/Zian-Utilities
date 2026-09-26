# M1 runtime results

Status: IN PROGRESS — FINAL VALIDATION BASELINE

Current implementation baseline: `95c64f42f96b0afc2adf7db747344ac90f4c0c72`
Validation source: `main` after PR #38 (`83c973b6994b336e655d559ee2f4066ba39c62ed`)

This file is evidence-only. Compilation success is not runtime acceptance.

Runtime evidence has already been collected on the Aventura 2 Youer 1.21.1 test server with Java 21 and Cobblemon 1.8.1, plus focused pure-NeoForge validation for generation filtering, Fishing and Poké Snack. M1 is not yet release-candidate accepted because the remaining formal gates below must still be recorded.

| Group | NeoForge | Youer | Notes |
|---|---|---|---|
| Generation state/commands | PARTIAL PASS | PARTIAL PASS | Enable/disable/status/list and hot generation mutation have been exercised. Do not repeat broad exploratory testing; final baseline only needs enough command/state coverage to support the remaining gates. |
| Persistence | PARTIAL PASS | PARTIAL PASS | PERSIST-02 PASS: state survived a normal restart and remained writable afterward. PERSIST-03 functional recovery was observed after a forced stop, but formal abrupt-stop evidence remains pending. |
| Natural spawning | PASS | PARTIAL PASS | NAT-03 passed on the clean NeoForge baseline: generation disabled after PlayerSpawner activity remained denied for new natural spawns while another enabled generation continued spawning. |
| Fishing | PASS | PASS | PR #35 focused NeoForge runtime validation passed. Encounter frequency improved without guaranteeing every cast; generation filtering remained correct. |
| Poké Snack | PASS | PASS | PR #35 focused NeoForge runtime validation passed, including simultaneous Fishing + Poké Snack operation and generation filtering. |
| Safety exclusions | PARTIAL PASS | PARTIAL PASS | Existing Pokémon, party send, PC withdraw, /givepokemon, battle, evolution and Zian GTS receive were exercised without Generation Control interference. Breeding remains N/A when no breeding system is installed. |
| Diagnostics | PARTIAL PASS | PASS | DIAG-02 PASS (documentary closure): useful candidate/source evidence and 70,665/70,665 server-thread evaluations. DIAG-01 PASS on Youer with debug off; the clean-NeoForge DIAG-01 check remains pending. |
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

## PERSIST-02 runtime evidence

Environment: clean NeoForge 21.1.251, Minecraft 1.21.1, Java 21, Cobblemon 1.8.1, Zian Utilities 0.1.0-alpha.1.

Observed sequence:

1. Before the normal restart, the persisted generation state was Gen2 + Gen7.
2. After restarting and re-entering the same world, Generation Control reported `2/9` generations active and `/zian generation active` reported exactly `gen2, gen7`.
3. Gen3 was then enabled successfully and the state immediately became `gen2, gen3, gen7`, proving the restored state remained writable rather than merely readable.
4. The subsequent shutdown followed Minecraft's normal save path: player disconnect, server stop, player/world saves, and all dimensions saved.
5. No generation-state corruption or Zian Utilities persistence exception was observed.

Result: `PERSIST-02 PASS`.

## PERSIST-03 status

Functional recovery after a forced process termination has been observed: the subsequent launch recovered the expected Gen2 + Gen7 state. However, the archived pre-restart `.log.gz` could not be parsed in the available evidence path, so the abrupt termination itself is not yet formally demonstrated by log evidence.

Status: `PERSIST-03 FUNCTIONAL PASS / FORMAL EVIDENCE PENDING`.

Do not repeat the functional recovery test unless needed. To close the formal gate, preserve readable evidence showing that the pre-restart log terminates without Minecraft's normal `Stopping server` / `Saving worlds` sequence, paired with the already observed correct post-restart state.

## Diagnostic gate audit

The runtime matrix defines:

- `DIAG-01`: blocked spawn with debug off -> no spam.
- `DIAG-02`: debug on -> useful source/species/reason data.

### DIAG-02

Prior diagnostic runs already produced useful candidate/source/species/reason evidence. [Generation preselection audit](GENERATION_PRESELECTION_AUDIT.md#runtime-validation-status) records the Youer candidate ALLOW/DENY examples and 70,665/70,665 preselection evaluations with `sameServerThread=true` (zero `false`). The temporary thread sampler was removed after collecting that evidence. Together these accepted records are sufficient for DIAG-02; repeating an equivalent high-volume diagnostic run is unnecessary unless a later code change affects diagnostics or preselection.

Status: `DIAG-02 PASS — EVIDENCE SUFFICIENT; documentary closure recorded`.

Evidence provenance: the accepted diagnostic runs summarized above, preserved in `main` after PR #38, and the persistence/diagnostic audit recorded in PR #39. This reconciliation records existing runtime evidence; it does not claim a new runtime execution.

### DIAG-01

Youer 1.21.1 / NeoForge 21.1.251, Cobblemon 1.8.1, Java 21. The operator provided a server launch command without any `-Dzianutilities.runtimeTest*` flags and the run's `latest.log`. This is a Youer result; the clean-NeoForge DIAG-01 check remains a separate gate.

Observed sequence in the supplied log:

1. At 04:09:00, `/zian generation disable gen2` succeeded. The one `[ZIAN-AUDIT]` line records that explicit command.
2. At 04:09:04 and 04:09:58, the operator checked `/zian generation active`; the log records the commands but does not include their chat responses. The operator reported that while Gen2 was enabled only Gen2 Pokémon were seen, and during the Gen7-only state only Gen7 Pokémon were seen. This is player observation, not a spawn count extracted from the log.
3. At 04:09:22, the player teleported to another area. From then until disconnect at 04:13:47 (about four minutes 25 seconds), the log contains no repeated Zian candidate/denial diagnostics or Zian errors. Other mods emitted isolated warnings; they are outside this logging gate.
4. At 04:13:53, the server stopped normally and saved worlds.

Result: `DIAG-01 PASS on Youer (operator observation plus supplied log)`. Normal command audit output is allowed. For clean NeoForge, repeat the short check with diagnostic flags and Zian DEBUG/TRACE logging off, at least one generation blocked, visible natural spawning from an enabled generation, and no repeated per-candidate/per-denial Zian log lines.

## Remaining required gates before M1 release-candidate acceptance

1. Close the formal evidence side of `PERSIST-03`.
2. Execute the small debug-off/no-spam check for `DIAG-01` on clean NeoForge. The Youer result is recorded above; `DIAG-02` is closed using accepted evidence.
3. Formally close `PERF-01` and `PERF-02` against the matrix criteria, reusing existing runtime evidence where equivalent rather than repeating unnecessary stress work.
4. After NeoForge acceptance, execute `YOUER-01` through `YOUER-03`.

Do not repeat already accepted natural-spawn, Fishing, Poké Snack or PERSIST-02 tests unless a later code change touches their relevant paths.

Do not promote M1 to release-candidate status until the remaining gates above are satisfied.
