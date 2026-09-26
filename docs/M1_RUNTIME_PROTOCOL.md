# M1-10 runtime compatibility protocol

This document is the execution sheet for issue #10. Automated compilation is not
runtime acceptance. The matrix must be executed on a real game/server runtime.

## Current final-validation baseline

```text
Minecraft: 1.21.1
Java: 21
NeoForge baseline: 21.1.251
Cobblemon baseline: 1.8.1+1.21.1
Zian Utilities implementation baseline: 95c64f42f96b0afc2adf7db747344ac90f4c0c72
Validation tracking baseline: main after PR #38
```

The implementation baseline is the PR #35 merge that contains the validated
Fishing/Poké Snack bucket fix. PR #36 refreshed validation documentation;
PR #38 added accepted NAT-03 runtime evidence without changing runtime behavior.

Deployment target: the operator's Minecraft 1.21.1 Youer server. Prioritize
observed behavior and acceptance on that environment. Reuse the focused clean
NeoForge results as a regression reference, but do not require a duplicate
pure-NeoForge abnormal-stop test merely to accept the Youer deployment when the
same persistence case has direct Youer Kill/recovery evidence. Keep the broader
cross-loader M1 release-candidate gate separate from Youer deployment acceptance.

## Diagnostic launch flags

For the validation build, enable only the flags required by the scenario being
recorded. For the remaining natural-spawn/diagnostic validation, use:

```text
-Dzianutilities.runtimeTestNatural=true
-Dzianutilities.runtimeTestFinalBarrier=true
```

Fishing and Poké Snack focused runtime validation already passed on the current
implementation baseline. Do not repeat those noisy diagnostics unless a later
code change touches their spawn-selection path.

Remove diagnostic flags after acceptance testing. They are intentionally noisy.

## Cross-loader M1 matrix order

This is the broader matrix sequence. For the intended Youer deployment, use the
accepted Youer results and its remaining focused evidence gaps directly.

1. GEN-01 through GEN-10.
2. PERSIST-01 and PERSIST-03. PERSIST-02 is also required on the production-like scheduled-restart environment.
3. NAT-01 through NAT-03.
4. FISH-01 and FISH-02.
5. SNACK-01 and SNACK-02.
6. SAFE-01 through SAFE-08.
7. DIAG-01 and DIAG-02.
8. PERF-01 and PERF-02.
9. Repeat the accepted core subset on Youer as YOUER-01 through YOUER-03.

Previously accepted scenarios do not need to be repeated unless a later code
change affects their path. Habitat remains research-only for this milestone.

## NAT-03: player-spawner cache timing

`RUNTIME_TEST_MATRIX.md` defines NAT-03 as:

> disable after player spawner already active -> blocked generation remains denied

Purpose: prove that disabling a generation takes effect for **new natural spawn
attempts even when Cobblemon's PlayerSpawner for the player is already active**.
The test must not rely on restarting the world/server, moving to a fresh world,
or recreating the player spawner after the disable command.

### Preconditions

- Environment: clean NeoForge 21.1.251 baseline.
- Cobblemon: 1.8.1+1.21.1.
- Zian Utilities: current final-validation build from `main`.
- Start with one test generation enabled. Prefer a generation that produces
  natural spawns reliably in the chosen biome.
- Keep the player in one suitable loaded test area long enough to observe at
  least one natural Pokémon from that enabled generation. This establishes that
  the player's natural spawning pipeline is already active.
- Enable `-Dzianutilities.runtimeTestNatural=true` for evidence. The final
  barrier flag may also remain enabled for source classification evidence.

### Actions

1. Run `/zian generation active` and record the enabled generation.
2. Stay in the same loaded test area until at least one **natural** spawn from
   that generation is observed. Do not use `/givepokemon`, Fishing or Poké Snack
   as the establishing spawn.
3. Without restarting, leaving the world, changing dimension or deliberately
   moving to fresh chunks, run `/zian generation disable <generation>`.
4. Run `/zian generation active` again and confirm that generation is inactive.
5. Remain in the same loaded area for a normal observation window. Existing
   Pokémon that spawned before the disable are allowed to remain; NAT-03 judges
   only new natural spawn attempts after the state change.
6. Preserve `latest.log` covering the establishing natural spawn, disable
   command, post-disable active-state output and post-disable natural-spawn
   diagnostics.

### Expected result

- The generation state changes immediately without restart.
- Pokémon that already existed before the disable are not removed.
- No **new natural spawn** from the disabled generation succeeds after the
  disable command.
- Natural candidates from the disabled generation are denied by Generation
  Control even though the player's Cobblemon PlayerSpawner was already active.
- No recursive spawning, repeated-action loop, exception or server crash is
  attributable to Zian Utilities.

### PASS criteria

Record NAT-03 as PASS only when all of the following are true:

1. A natural spawn from the generation was observed before disabling it,
   establishing an active PlayerSpawner path.
2. The generation was disabled in-place with no restart/dimension reset.
3. `/zian generation active` confirmed the new state.
4. No post-disable natural spawn from that generation succeeded during the
   observation window.
5. The log contains enough natural-spawn evidence to distinguish post-disable
   denied candidates from unrelated/admin/fishing/snack flows.
6. No stop condition from this protocol occurred.

If no useful natural spawn/candidate activity occurs after the disable, record
`BLOCKED` rather than PASS and repeat in a more suitable biome/time. Absence of
all spawn activity is not proof that the cache-timing guard worked.

## Result record

Copy this block for every scenario:

```text
ID:
Environment: NeoForge | Youer
Minecraft:
Loader/build:
Cobblemon:
Zian Utilities commit:
Active generations:
Actions:
Expected:
Actual:
Relevant log lines:
Result: PASS | FAIL | BLOCKED
Notes:
```

A compile-only result must never be recorded as PASS for a runtime scenario.

## Stop conditions

Stop the matrix and preserve logs/world before continuing if any test shows:

- Pokémon duplication;
- item or currency duplication;
- generation state corruption;
- party/PC/admin/battle/evolution/breeding/GTS interference;
- repeated or recursive spawn actions;
- server crash attributable to Zian Utilities.

## Acceptance gates

For the intended Youer server deployment, evaluate the Youer results directly:
commands and live generation state, natural/Fishing/Poké Snack filtering, GTS
coexistence, persistence including direct Kill/recovery, debug-off logging, and
performance sanity. Preserve existing accepted evidence and rerun only an
unproven or changed path. The Youer direct-Kill recovery closes that environment's
PERSIST-03 case; the older pure-NeoForge stop-mode archive is not a blocker for
this deployment decision.

For a broader cross-loader M1 release-candidate claim, retain the original
NeoForge-first baseline requirement and record any remaining NeoForge evidence
separately. Neither track is accepted automatically by compilation alone.
