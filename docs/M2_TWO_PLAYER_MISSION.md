# M2: two-player capture mission acceptance (alpha.8)

This is a Youer 1.21.1 test of assignment isolation, not an automatic mission rollout. Use alpha.8.1 or later: alpha.8's default Brigadier root requirement is rewritten by Youer to a Bukkit permission, hiding `/zian quest capture status` from non-operators. Alpha.8.1 explicitly opens only the shared `/zian` root; protected subcommands keep their own permission checks. The bridge remains disabled unless the startup command contains `-Dzianutilities.captureMissionTestEnabled=true` before `-jar`. No capture mission pays AVECOINS or other rewards. The existing alpha.7 assignment files remain in place and completed assignments cannot be restarted with this command.

## Changes

- An operator can assign the capture mission to an **online** player with `/zian quest capture assign <player>`. Repeating it returns that player's existing assignment ID and state.
- Each player, including a non-operator, can inspect only their own mission with `/zian quest capture status` while the test flag is enabled. The original operator-only `/zian quest capture accept` remains available for self-assignment.
- All mission progress is still keyed by player UUID. Only the capturing player's assigned mission can complete. It follows the server's currently active generations.

## Controlled Youer test

1. Back up the world and keep only the alpha.8.1 Zian Utilities JAR in `mods`. Start Youer with the test flag. Keep reward and older capture trial flags off. Ensure Gen7 is active and at least two players are online; player B should **not** have operator permissions.
2. Player A (operator) runs `/zian quest capture status` and notes the existing alpha.7 completed assignment, if present. Player B runs the same command and should see `no asignada`. A records `/zian reward balance`; the reward command remains operator-only in this slice.
3. A runs `/zian quest capture assign <B>` twice. Both responses must show the same new ID for B and `0/1 ACTIVA generaciones=gen7`. B's own `/zian quest capture status` must show that ID. B must not see the operator-only `accept` or `assign` subcommands.
4. A captures a Gen7 Pokémon. B's status must remain 0/1. A's completed alpha.7 assignment, if present, must remain unchanged.
5. B captures a naturally spawned Gen7 Pokémon. B's status must become 1/1 with B's captured species and Gen7; A's assignment must remain unchanged. The console should show one completion line with B's UUID. A's coppercoin balance must not change; the mission has no reward path for either player.
6. Restart with the same JAR and flag. Both players inspect their own status, and A checks the reward balance; IDs, completion details, and balance must persist. Repeating `assign <B>` must not create a second assignment.
7. Remove the flag and restart. `quest capture` must disappear for both players while generation and reward balance commands continue to work.

Stop and retain the console log if a non-operator can assign a mission, if one player's capture advances the other's mission, if an assignment disappears, if any balance changes, or if the server fails to start. Do not delete an existing assignment file to retry: use another test player or a backed-up test world.

## Youer runtime evidence (2026-09-27)

User-supplied screenshots after installing the corrected build show ZIANBLK consulting its existing completed Yungoos/Gen7 assignment `52d22b97-34cd-44d2-8e15-89a0a65ae094` without the prior unknown-command error. The operator IANBLK used `assign ZIANBLK`; it correctly returned that existing completed assignment rather than creating another. IANBLK's own initial status was `no asignada`, and its subsequent self-assignment returned a separate stable ID at 0/1 twice. This supports command visibility and per-player identity separation. The existing completed ZIANBLK assignment was left intact.

The supplied pre-restart Pterodactyl console confirms `capture_mission=enabled rewards=disabled`, both player UUIDs, and two distinct assignment IDs. At 01:00:49 `assign ZIANBLK` returned `52d22b97-34cd-44d2-8e15-89a0a65ae094 completed=true` for UUID `5e53d178-9226-4173-be7d-745344580c35`. At 01:01:28 `assign IANBLK` created `a8b7d536-98ec-4c24-b741-e527aaeac795 completed=false` for UUID `eeedb8a6-0064-4783-bfb1-628ec63df235`. At 01:03:11 a Cobblemon Minior capture logged `result=COMPLETED` for IANBLK and that new ID. IANBLK's in-game status then showed 1/1 Minior/Gen7 with no reward. This confirms correct event attribution for IANBLK. The log does not contain a ZIANBLK capture while IANBLK was still 0/1, so that specific cross-player event check is not claimed.

Post-restart screenshots show ZIANBLK still at 1/1 Yungoos/Gen7 with its original ID and IANBLK still at 1/1 Minior/Gen7 with its separate ID. IANBLK's AVECOINS coppercoin balance is 0. The earlier 1-coppercoin test credit belonged to ZIANBLK, so expecting 1 for IANBLK was incorrect; the mission did not grant IANBLK a reward.

The operator then removed `-Dzianutilities.captureMissionTestEnabled=true` and restarted with the same JAR. The operator reports that `/zian quest capture` was absent for **both** accounts, while `/zian generation active` worked for **both** accounts. This opt-out result is user-confirmed; no post-opt-out console transcript was supplied. The controlled two-account Youer acceptance is complete within the stated limit: a ZIANBLK capture while IANBLK was still 0/1 was not observed. Core tests cover per-player isolation and concurrent captures; do not claim that specific negative case was observed in Youer.

