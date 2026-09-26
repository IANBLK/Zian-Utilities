# M1 runtime results

Status: IN PROGRESS — FINAL VALIDATION BASELINE

Current implementation baseline: `95c64f42f96b0afc2adf7db747344ac90f4c0c72`
Validation source: `main` after PR #38 (`83c973b6994b336e655d559ee2f4066ba39c62ed`)

This file is evidence-only. Compilation success is not runtime acceptance.

The operator's Minecraft 1.21.1 Youer server is the intended deployment target. Runtime evidence has been collected there with Java 21 and Cobblemon 1.8.1, plus focused clean-NeoForge regression validation for generation filtering, Fishing and Poké Snack. Evaluate Youer deployment readiness directly from its accepted behavior; track the broader cross-loader M1 release-candidate gate separately. Neither gate is automatically accepted by this document.

| Group | NeoForge | Youer | Notes |
|---|---|---|---|
| Generation state/commands | PARTIAL PASS | PARTIAL PASS | Enable/disable/status/list and hot generation mutation have been exercised. Do not repeat broad exploratory testing; final baseline only needs enough command/state coverage to support the remaining gates. |
| Persistence | PARTIAL PASS | PARTIAL PASS | PERSIST-02 PASS after normal restart. PERSIST-03 PASS on Youer after direct Kill and Gen7 recovery; the earlier NeoForge forced-close observation remains functional only without archived abrupt-stop proof. |
| Natural spawning | PASS | PARTIAL PASS | NAT-03 passed on the clean NeoForge baseline: generation disabled after PlayerSpawner activity remained denied for new natural spawns while another enabled generation continued spawning. |
| Fishing | PASS | PASS | PR #35 focused NeoForge runtime validation passed. Encounter frequency improved without guaranteeing every cast; generation filtering remained correct. |
| Poké Snack | PASS | PASS | PR #35 focused NeoForge runtime validation passed, including simultaneous Fishing + Poké Snack operation and generation filtering. |
| Safety exclusions | PARTIAL PASS | PARTIAL PASS | Existing Pokémon, party send, PC withdraw, /givepokemon, battle and evolution were exercised without Generation Control interference. Breeding remains N/A when no breeding system is installed; external Zian GTS is outside this milestone's acceptance scope. |
| Diagnostics | PASS | PASS | DIAG-01 passed on Youer and a NeoForge integrated-server run with debug off; DIAG-02 closed with accepted candidate/source evidence and 70,665/70,665 server-thread evaluations. |
| Performance sanity | OBSERVED OK | PARTIAL PASS | PERF-01 and PERF-02 PASS on Youer: two-player 20 TPS sanity plus a three-minute allocation profile with only 0.01% of sampled allocations directly attributed to Zian Utilities. Pure-NeoForge multi-player performance was not measured. |
| Habitat research | NOT REQUIRED | NOT REQUIRED | Research-only and non-blocking for M1. |
| Youer compatibility | N/A | PARTIAL PASS | Commands, natural spawning, Fishing and Poké Snack have been exercised on the intended server. YOUER-01/02 are the relevant subset; YOUER-03 (external GTS) is N/A. |

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

Status before the direct Youer Kill: `PERSIST-03 FUNCTIONAL PASS / FORMAL EVIDENCE PENDING`.

The operator confirmed that the older pre-restart log is unavailable. A subsequent controlled Youer test now supplies a readable abrupt-stop log paired with the recovered state below. The original NeoForge forced-close observation remains functional evidence, without independent archival proof of its stop mode.

### PERSIST-03 controlled attempt on Youer

The operator supplied two consecutive Youer `latest.log` files from a Stop-then-Kill panel attempt. The first run (`latest (1).log`, SHA-256 `263B5364E3A551016630444757B87D764211CD7597C5DDFAE6C161A2C084DABB`) records `Stopping the server` / `Stopping server` at 05:01:43 and `Saving worlds` at 05:01:44. The second run (`latest (2).log`, SHA-256 `54522D8E383B45E6B98BBE3883456E97655BB4CFD719858758C7B19A9DEBF6FB`) starts at 05:02:21, runs normally, and itself records `Stopping server` / `Saving worlds` at 05:04:07. Both starts loaded the world without a Zian Utilities persistence error.

Outcome: the panel's initial Stop completed the normal save sequence before Kill could establish an abrupt stop. These logs support normal restart health, but do **not** prove abnormal termination. The direct-Kill test below supplies that missing Youer evidence; do not repeat Stop-then-Kill.

### PERSIST-03 direct Kill and recovery on Youer

The operator confirmed that only Gen7 was active and that the Pterodactyl panel showed this test server offline after an administrator invoked the direct `kill` power action for its verified numeric server ID, without sending Minecraft `stop` first. The action output itself was not archived; the stop mode is supported by the operator report and the paired logs.

- Pre-restart `latest (4).log` (SHA-256 `ECF7C569A2C926D80EE685A8A614B87D81E2652A15FB16F22DB6BAE517B26F17`) starts Youer 1.21.1 / NeoForge 21.1.251 and ends immediately after `Done (35.008s)!` at 05:41:29. It contains no `Stopping server`, `Saving worlds`, or all-dimensions-saved sequence.
- Post-restart `latest (5).log` (SHA-256 `9ADD2D372930570BFD035EB9791FD8BE896617FC77035961DEC8531CDFC9300C`) starts at 05:45, reaches `Done (34.949s)!` at 05:45:42, and records `Generation Control: 1/9 generaciones activas` at 05:45:45 and `Generaciones activas: gen7` at 05:45:51. The operator supplied a matching console screenshot. This second run shuts down normally at 05:46:16 and saves all dimensions. No Zian state-corruption error appears.

Result: `PERSIST-03 PASS on Youer` for the matrix's forced-abnormal-stop/state-integrity criterion. The restored Gen7 state matches the operator's pre-Kill state. A post-restart write mutation was not performed; the matrix does not require one for this case. The pure-NeoForge baseline remains separately subject to the protocol's NeoForge-first acceptance order.

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

## PERF-02 Youer allocation evidence

The earlier Gen7-only snapshot showed 20.0 TPS. The operator then supplied the [spark allocation profile](https://spark.lucko.me/oizamoD3ge) and `latest (3).log` (SHA-256 `18E1CEE33299BDF23A9F5157D86100F1152B74A97CC3FD45B539094C5D2773AF`). The operator confirmed that only Gen7 was active and natural Pokemon appeared during this run. This configuration exercised generation filtering; the server log itself does not record active-generation chat responses or a count of denied candidates.

The profiler ran from 05:15:10 to 05:18:10 (viewer: 3m 1s / 3,600 ticks) with two connected players. It was an async allocation profile sampled at 512 KB. The viewer reported 20.00 TPS, 32.2 ms 95th-percentile MSPT, 2.9 GB / 4 GB process memory at completion, three G1 Young collections during the profile averaging 44.7 ms, two G1 Concurrent collections averaging 10.5 ms, and zero G1 Old collections. In the spark Mods view, `zianutilities` accounted for 0.01% of sampled allocations (one 512 KB sample on the server thread), under `NaturalSpawnGuard.onEntitySpawn` and a debug logging formatting path. No sustained TPS loss or Zian exception appeared in the log. The Java-agent warning at profile completion came from spark's instrumentation and is not a Zian error.

Result: `PERF-02 PASS on Youer` for the short generation-denial allocation-sanity gate. This sampled profile is evidence against a visible allocation hotspot in the tested window; it does not prove zero allocations, quantify all indirect allocations, or establish long-duration memory stability. The earlier 70,665/70,665 server-thread audit is separate thread-affinity evidence, not an allocation measurement.

## Remaining evidence audit

This audit separates runtime behavior already observed from the specific proof required to close each matrix ID. It adds no new runtime acceptance.

| ID | Evidence already available | Formal gap |
|---|---|---|
| PERSIST-03 | PASS on Youer: operator-reported direct Kill, pre-restart log ending without normal save, and post-restart status/active response restoring Gen7. | The older NeoForge forced-close observation lacks archived stop-mode proof; this Youer result does not alone satisfy the protocol's NeoForge-first release gate. |
| PERF-01 | PASS on Youer: two players in separate natural-spawn areas for about nine minutes 32 seconds; supplied spark snapshots show 20.0 TPS and no sustained tick degradation. | No Youer rerun required for this matrix case unless the relevant runtime path changes. |
| PERF-02 | PASS on Youer: three-minute spark allocation profile with only Gen7 active, natural appearances, two players, 20 TPS and 0.01% of sampled allocations directly attributed to Zian Utilities. | No Youer rerun required for this short allocation-sanity case unless the relevant runtime path changes. This is not a long-duration leak test. |
| YOUER-01 | Functional behavior observed on Youer: enable/disable/active/status/list and live mutation; logs confirm a Gen2 disable and active-state commands. | Server logs do not include chat responses. Preserve an existing screenshot or operator record of the responses for a fully archived case; no broad rerun needed. |
| YOUER-02 | Functional PASS for the Youer deployment: natural generation filtering was operator-observed; Fishing and Poké Snack have accepted observations, including simultaneous use. | Preserve references to the already accepted cases. Repeat only a path whose evidence is unavailable or whose implementation changed. |
| YOUER-03 | N/A: Zian GTS is a separate mod and is not part of Zian Utilities M1. | No GTS transaction or additional GTS test is required for this mod's acceptance. |

## Youer compatibility mapping against the runtime matrix

`RUNTIME_TEST_MATRIX.md` defines `YOUER-01` as the core command subset and `YOUER-02` as natural/Fishing/Poké Snack parity. `YOUER-03` is N/A because Zian GTS is a separate mod. Youer is the intended deployment target. The revised `M1_RUNTIME_PROTOCOL.md` evaluates its deployment readiness from Youer evidence while retaining a separate NeoForge-first gate for a broader cross-loader M1 release-candidate claim.

- `YOUER-01 — functional behavior observed; archival responses incomplete`: the accepted Youer observations include enable/disable/active/status/list and live mutation. The supplied Youer `latest.log` records a successful Gen2 disable at 04:09:00 and `active` commands at 04:09:04 and 04:09:58; `latest (1).log` and `latest (2).log` record further `active` commands with two players. Server logs do not include the chat responses. The operator also observed that naturally appearing Pokemon matched the currently active generation. Accepted command behavior supports the Youer deployment assessment. Do not infer unlogged chat responses from command issuance alone; preserve any existing screenshot or operator record for formal traceability.
- `YOUER-02 — functional PASS for Youer deployment`: the operator observed natural spawns restricted to the active generation in separate areas, including Gen2-only and Gen7-only states. The already accepted Fishing and Poké Snack Youer observations include both mechanisms operating together under generation filtering. This covers the three required paths for the intended server. Preserve the accepted case references; no automatic gameplay replay is needed.
- `YOUER-03 — N/A`: Zian GTS belongs to a separate mod. Earlier coexistence observations remain historical context only; no transaction proof or focused GTS test is required for Zian Utilities.

## Remaining acceptance work

For the intended Youer server deployment, `PERSIST-03`, `PERF-01`, `PERF-02`, DIAG-01 and DIAG-02 have direct accepted Youer evidence. `YOUER-02` has accepted functional evidence. Preserve existing command-response evidence for `YOUER-01` if available. `SAFE-08` and `YOUER-03` are N/A because they concern the separate GTS mod. Do not repeat the direct Kill or broad spawn/Fishing/Poké Snack tests.

For a broader cross-loader M1 release-candidate claim, the original pure-NeoForge baseline still has separately tracked gaps, including the missing archive proving the stop mode of its older PERSIST-03 observation. That archive is not required to decide whether the intended Youer server works correctly. Do not mark the broader release candidate accepted until its own matrix gates are resolved.
