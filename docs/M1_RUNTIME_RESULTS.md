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
| Diagnostics | PASS | PASS | DIAG-01 passed on Youer and a NeoForge integrated-server run with debug off; DIAG-02 closed with accepted candidate/source evidence and 70,665/70,665 server-thread evaluations. |
| Performance sanity | OBSERVED OK | PARTIAL PASS | PERF-01 PASS on Youer with two players and 20 TPS across supplied spark snapshots; PERF-02 allocation remains unmeasured. |
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

Functional recovery after a forced process termination has been observed: the subsequent launch recovered the expected Gen2 + Gen7 state. The operator also reports successful normal-restart and forced-close recovery on both server and local client. These additional observations are operator reports, not independent proof of the stop mode. The archived pre-restart `.log.gz` could not be parsed in the available evidence path. The later Youer and NeoForge logs supplied for DIAG-01 both end with normal world saves, so neither can establish the earlier forced termination.

Status: `PERSIST-03 FUNCTIONAL PASS / FORMAL EVIDENCE PENDING`.

The operator confirmed that the old pre-restart log is unavailable. To close the formal gate, perform one controlled abnormal-stop check in a disposable test copy: record active generations, terminate the process without Minecraft's normal `stop` command, retain the pre-restart log or hosting-panel kill event before the next launch, restart, verify the same state and that it remains writable, then retain the post-restart log. This repetition is only to document the stop mode and recovery; the earlier functional recovery observation remains accepted.

### PERSIST-03 controlled attempt on Youer

The operator supplied two consecutive Youer `latest.log` files from a Stop-then-Kill panel attempt. The first run (`latest (1).log`, SHA-256 `263B5364E3A551016630444757B87D764211CD7597C5DDFAE6C161A2C084DABB`) records `Stopping the server` / `Stopping server` at 05:01:43 and `Saving worlds` at 05:01:44. The second run (`latest (2).log`, SHA-256 `54522D8E383B45E6B98BBE3883456E97655BB4CFD719858758C7B19A9DEBF6FB`) starts at 05:02:21, runs normally, and itself records `Stopping server` / `Saving worlds` at 05:04:07. Both starts loaded the world and Zian GTS without a Zian persistence error.

Outcome: the panel's initial Stop completed the normal save sequence before Kill could establish an abrupt stop. These logs support normal restart health, but do **not** close PERSIST-03's abnormal-stop proof. Do not repeat the same Stop-then-Kill sequence merely to produce another normal shutdown. Formal closure needs a direct process-termination method on a disposable test copy, plus the pre/post logs.

## Diagnostic gate audit

The runtime matrix defines:

- `DIAG-01`: blocked spawn with debug off -> no spam.
- `DIAG-02`: debug on -> useful source/species/reason data.

### DIAG-02

Prior diagnostic runs already produced useful candidate/source/species/reason evidence. [Generation preselection audit](GENERATION_PRESELECTION_AUDIT.md#runtime-validation-status) records the Youer candidate ALLOW/DENY examples and 70,665/70,665 preselection evaluations with `sameServerThread=true` (zero `false`). The temporary thread sampler was removed after collecting that evidence. Together these accepted records are sufficient for DIAG-02; repeating an equivalent high-volume diagnostic run is unnecessary unless a later code change affects diagnostics or preselection.

Status: `DIAG-02 PASS — EVIDENCE SUFFICIENT; documentary closure recorded`.

Evidence provenance: the accepted diagnostic runs summarized above, preserved in `main` after PR #38, and the persistence/diagnostic audit recorded in PR #39. This reconciliation records existing runtime evidence; it does not claim a new runtime execution.

### DIAG-01

Youer 1.21.1 / NeoForge 21.1.251, Cobblemon 1.8.1, Java 21. The operator provided a server launch command without any `-Dzianutilities.runtimeTest*` flags and the run's `latest.log`. This is the Youer result.

Observed sequence in the supplied log:

1. At 04:09:00, `/zian generation disable gen2` succeeded. The one `[ZIAN-AUDIT]` line records that explicit command.
2. At 04:09:04 and 04:09:58, the operator checked `/zian generation active`; the log records the commands but does not include their chat responses. The operator reported that while Gen2 was enabled only Gen2 Pokémon were seen, and during the Gen7-only state only Gen7 Pokémon were seen. This is player observation, not a spawn count extracted from the log.
3. At 04:09:22, the player teleported to another area. From then until disconnect at 04:13:47 (about four minutes 25 seconds), the log contains no repeated Zian candidate/denial diagnostics or Zian errors. Other mods emitted isolated warnings; they are outside this logging gate.
4. At 04:13:53, the server stopped normally and saved worlds.

Result: `DIAG-01 PASS on Youer (operator observation plus supplied log)`. Normal command audit output is allowed.

The operator also supplied `latest.log` from a NeoForge 21.1.251 / Minecraft 1.21.1 local client with an integrated server, Cobblemon 1.8.1 and Zian Utilities 0.1.0-alpha.1. This instance contains additional mods and is not a minimal two-mod installation; it does not use Youer. The log does not list JVM diagnostic properties, so the debug-off conclusion rests on normal INFO logging and the absence of opt-in Zian diagnostic output.

1. At 23:21:56 the active-generation response was `ninguna`; at 23:22:13 Gen2 was enabled and at 23:22:14 the response was `gen2`.
2. At 23:23:47 Gen2 was disabled; at 23:23:49 the response was `ninguna`; at 23:23:53 Gen7 was enabled. The operator reported that newly appearing Pokémon matched whichever generation was active in each interval. The log confirms command state, while the spawn observation comes from the operator.
3. During the Gen2 and Gen7 observation windows, no repeated Zian candidate/denial diagnostic lines or Zian errors appeared. The three `[ZIAN-AUDIT]` lines correspond to the explicit enable/disable commands. Warnings from other mods are outside this gate.
4. The integrated server saved normally and stopped at 23:28:04.

Result: `DIAG-01 PASS on NeoForge integrated server (operator observation plus supplied log)`. Together with the Youer result, the debug-off/no-spam gate is closed for the tested M1 implementation baseline.

## PERF-01 Youer runtime evidence

Environment: Youer 1.21.1 / NeoForge 21.1.251, Java 21, Cobblemon 1.8.1, with spark on the test server. The operator reported natural spawning in two separate player areas and generation-matched Pokémon.

In `latest (1).log`, player IANBLK joined at 04:50:32 and ZIANBLK at 04:52:11; both remained connected until the 05:01:43 shutdown, about nine minutes 32 seconds with two players. Multiple operator-supplied spark screenshots during this session show 20.0 TPS across the displayed 5-second, 10-second, 1-minute, 5-minute and 15-minute windows. The displayed 95th-percentile tick durations remained below 50 ms in the sampled 10-second and 1-minute windows. Some isolated maximum tick spikes occurred, but no sustained TPS drop, Zian exception or recursive spawn behavior was observed. The log also contains repeated `minecraft:` empty-pool warnings from a world-generation worker; these are not attributed to Zian and were not used as performance evidence.

Result: `PERF-01 PASS on Youer` for the matrix's two-player natural-spawn sanity criterion. This does not establish PERF-02 allocation behavior or a corresponding NeoForge multi-player performance result.

## PERF-02 status

The operator supplied a Gen7-only screenshot from the same two-player session, showing 20.0 TPS during the blocked-generation configuration. This is favorable tick evidence, but no allocation profile or memory/GC trend was supplied. The prior 70,665/70,665 server-thread evaluations establish thread affinity rather than allocation rate.

Status: `PERF-02 TICK SANITY OBSERVED / ALLOCATION EVIDENCE PENDING`. A short spark allocation profile during repeated denied candidates, or equivalent memory/GC evidence, is still required for formal closure.

## Remaining evidence audit

This audit separates runtime behavior already observed from the specific proof required to close each matrix ID. It adds no new runtime acceptance.

| ID | Evidence already available | Formal gap |
|---|---|---|
| PERSIST-03 | Correct Gen2 + Gen7 recovery was previously observed. The new Stop-then-Kill attempt also restarted cleanly. | Both supplied logs show normal saves. Stop-then-Kill did not establish an abnormal termination; direct termination evidence is still missing. |
| PERF-01 | PASS on Youer: two players in separate natural-spawn areas for about nine minutes 32 seconds; supplied spark snapshots show 20.0 TPS and no sustained tick degradation. | No Youer rerun required for this matrix case unless the relevant runtime path changes. |
| PERF-02 | Gen7-only spark snapshot retained 20.0 TPS; 70,665/70,665 preselection evaluations stayed on the server thread. | Neither tick rate nor thread affinity measures allocation. A short allocation profile or equivalent memory/GC trend under denied candidates remains required. |
| YOUER-01 | Enable/disable/active/status/list and live mutation were exercised on Youer; the supplied log also shows a successful Gen2 disable and active-state commands. | Map the accepted command observations and responses to the formal case once the NeoForge baseline is accepted. The supplied server log does not contain chat responses for `active`. |
| YOUER-02 | Youer natural generation filtering was operator-observed; Fishing and Poké Snack both have accepted functional observations, including their simultaneous use. | Map the accepted natural, Fishing and Poké Snack results to the formal Youer case after NeoForge acceptance; rerun only a path whose evidence cannot be recovered or whose implementation changed. |
| YOUER-03 | Existing Pokémon and Zian GTS receive were previously exercised without Generation Control interference. | Preserve or reference the accepted GTS transaction result alongside active generation state for formal case closure. A new transaction is needed only if that evidence cannot be recovered. |

## Remaining required gates before M1 release-candidate acceptance

1. For `PERSIST-03`, obtain direct abnormal-termination evidence on a disposable test copy. The available panel Stop-then-Kill path produced normal saves; do not repeat that path as proof.
2. For `PERF-02`, capture a short allocation profile or equivalent memory/GC trend under repeated denied candidates. `PERF-01` is closed on Youer.
3. After NeoForge acceptance, map the already observed Youer command, spawn and GTS behavior to `YOUER-01` through `YOUER-03`; rerun only an unproven path.

Do not repeat already accepted natural-spawn, Fishing, Poké Snack or PERSIST-02 tests unless a later code change touches their relevant paths.

Do not promote M1 to release-candidate status until the remaining gates above are satisfied.
