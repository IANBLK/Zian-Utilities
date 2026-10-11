# Alpha.5 one-capture trial for Youer 1.21.1

This is a deliberately limited server-side quest progress test. It accepts one
capture for one operator, saves progress immediately in the world at
`data/zianutilities/capture_trials/<player UUID>.properties`, and never pays a
reward. Normal campaign, daily, and weekly quests remain disabled. The trial
does not reset or create a second assignment for the same player.

The code is off by default. It is active only when the Java startup includes
`-Dzianutilities.questTrialEnabled=true` before `-jar`. The old capture
probe and coppercoin credit flags must stay absent.

## Controlled test

1. Back up the Youer test world and stop the server normally. Replace alpha.4
   with the alpha.5 Zian Utilities JAR. Keep just one Zian Utilities JAR.
2. Add the trial flag to the startup command and start the server. Confirm
   `[ZIAN-QUEST-TRIAL] capture=enabled rewards=disabled` appears once.
3. As the operator in-game, run `/zian generation active`. With gen7 active,
   run `/zian quest trial start`, then `/zian quest trial status`. Expect
   the same assignment ID and `0/1 ACTIVO generaciones=gen7; sin recompensa`.
   Starting again must return the same ID, without resetting progress.
4. Capture one naturally encountered Gen7 Pokémon. The console should have
   one `[ZIAN-QUEST-TRIAL] event=capture ... result=COMPLETED` line. Run
   `/zian quest trial status`; expect `1/1 COMPLETADO` and the captured
   species. Check that AVECOINS balance has not changed.
5. Stop and restart normally with the same alpha.5 JAR and trial flag. Run
   `/zian quest trial status` again; the assignment ID, `1/1`, and species
   must persist. Another capture must leave the trial at `1/1`.
6. Remove the trial flag and restart. The trial command should no longer be
   available. Keep the saved trial file; do not delete it to rerun this test.

If startup fails, the assignment cannot be saved, the wrong player/species is
recorded, progress disappears after restart, or any reward/balance changes,
stop the test and send the relevant console lines and command outputs. This
trial is one capture only; it does not establish repeated quest rotation,
automatic mission assignment, abrupt-crash durability, or reward delivery.

The assignment snapshots the generations active when it starts. A capture
counts only when its resolved generation is in that snapshot **and** is still
enabled when the event is processed. Unknown-generation captures do not count.
The capture event does not prove whether a Pokémon was naturally spawned, so
the operator must use a naturally encountered Pokémon for this test.

## First Youer runtime observation — PASS for capture and normal restart

The operator supplied in-game captures and a Pterodactyl console transcript
from Youer 1.21.1 with the alpha.5 trial flag enabled:

- `/zian generation active` showed gen7. Starting the trial returned
  assignment `7e125e5f-6f97-45ef-829a-1291eb7ce09f` at `0/1 ACTIVO`;
  the repeated start/status output retained the same assignment and progress.
- At 22:34:36, the console recorded
  `event=capture species=cobblemon:komala result=COMPLETED`. The in-game
  status then showed the same assignment at `1/1 COMPLETADO`, gen7,
  `species=cobblemon:komala`, and `sin recompensa`.
- Pterodactyl performed a normal stop at 22:37:18 with player and world saves.
  After Youer restarted, the operator's in-game status still showed the same
  assignment ID, `1/1 COMPLETADO`, and Komala.

The supplied evidence supports a single successful capture and persistence
across a normal restart. It does not show a separate AVECOINS balance check,
a second capture after completion, an abrupt kill, or the trial flag removed.
Player network addresses from the console are omitted here.

## Second capture and unchanged wallet — PASS (operator report)

After the normal restart, the operator reported capturing a second Pokémon,
Mudbray. In-game output before and after that capture showed the same trial
assignment `7e125e5f-6f97-45ef-829a-1291eb7ce09f` at `1/1 COMPLETADO`
with the original `cobblemon:komala` species. The visible
`avecoins:coppercoin` balance was **1** on repeated reads. The console
transcript records the status and balance commands; it does not log this
second capture because the trial listener logs only the transition to
`COMPLETED`. The player identified Mudbray as the second captured Pokémon.

This supports no additional trial progress and no wallet credit from the
second capture on the Youer test server. Disabling the startup flag and
confirming the trial command is unavailable after restart is the final
operational cleanup check.

## Trial flag removal — PASS (operator report)

The operator removed `-Dzianutilities.questTrialEnabled=true` and restarted
the same alpha.5 JAR. The supplied in-game capture showed gen7 still active
and `avecoins:coppercoin` balance still **1**. The operator reported that only
the generation and balance commands remained available, consistent with the
trial command being gated off. No screenshot of an attempted trial command or
post-restart startup line was supplied; command unavailability is therefore
recorded as an operator report. The completed trial file was retained.

The controlled Youer test now covers explicit start, one matching capture,
normal-restart persistence, no extra progress or coin from a second capture,
and opt-out cleanup. It does not validate abrupt-kill durability or automatic
production quest/reward behavior.
