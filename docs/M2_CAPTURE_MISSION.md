# M2: first assignable capture mission (alpha.7)

This is an operator-only, explicitly enabled Youer 1.21.1 test. It does not issue rewards, replace the completed capture trial, or change generation filtering. Normal mission distribution remains disabled.

## Rules

- `/zian quest capture accept` creates one assignment per player only when at least one generation is active. Repeating it returns the same assignment ID, including after completion.
- `/zian quest capture status` shows `0/1 ACTIVA`, `0/1 PAUSADA`, or `1/1 COMPLETADA` with the recorded species and generation.
- A capture completes the mission only when Cobblemon resolves at least one generation currently enabled by the server. The mission follows the *current* generation setting, not the setting at acceptance. If none is enabled, progress pauses without deleting the assignment.
- Completion is written atomically to `world/data/zianutilities/quest_assignments/capture_any_active_v1/<player UUID>.properties` before success is reported. A bad or unsupported record fails closed. A second capture cannot complete it again.
- The bridge and commands are inactive by default. Enable only for the test with `-Dzianutilities.captureMissionTestEnabled=true` before `-jar`. Keep `zianutilities.captureTrialTestEnabled` and `zianutilities.captureProbeEnabled` unset unless testing them separately.

## Youer acceptance protocol

1. Back up the test world. Keep exactly one Zian Utilities JAR in `mods`, use alpha.7 on Youer 1.21.1 with Cobblemon 1.8.1, and start with the test flag above.
2. With only Gen7 active, run `/zian quest plan`, then `/zian quest capture status`: the plan offers capture from an active generation and the mission is not assigned yet. Record `/zian reward balance`.
3. Run `/zian quest capture accept` twice. Both responses must show the same ID and `0/1 ACTIVA generaciones=gen7`.
4. Capture one naturally spawned Gen7 Pokémon. `/zian quest capture status` must show the same ID and `1/1 COMPLETADA` with that species and `gen7`. The console should show one `[ZIAN-QUEST] event=capture ... result=COMPLETED`. Balance must be unchanged.
5. Capture another Pokémon and check status and balance. The ID, completed species, and balance must stay unchanged; there must be no second completion log.
6. Restart with the same JAR and flag. Status must retain the same completion and ID. Balance must still be unchanged.
7. Remove the flag and restart. The capture subcommands must be absent; generation control and reward balance must still work. The assignment file remains on disk for later testing.

Optional generation-switch check before step 4: accept under Gen7, then switch to Gen1/Gen2. A Gen7 capture must not complete; a Gen1 or Gen2 capture must complete. Restore the generation configuration afterward. Do not infer spawn eligibility from a loaded chunk; verify the species or its generation.

Stop the test and retain the world, console log, and assignment file if the server fails startup, the assignment disappears, an inactive-generation capture completes it, balance changes, or generation control changes unexpectedly. Do not remove or edit the record to retry on the same world.

