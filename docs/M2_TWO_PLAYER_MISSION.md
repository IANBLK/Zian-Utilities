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

