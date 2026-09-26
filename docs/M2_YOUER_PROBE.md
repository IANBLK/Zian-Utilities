# M2 read-only Youer probe

This gate verifies that the alpha.2 mod starts on the existing Youer 1.21.1
test server and can inspect AVECOINS 2.3 without changing a wallet. No reward
delivery is enabled by this build.

1. Back up the test server. Stop it normally.
2. Replace the previous Zian Utilities JAR with
   `Zian-Utilities-0.1.0-alpha.2-NeoForge-1.21.1.jar`. Keep only one Zian
   Utilities JAR in `mods/`. Leave Cobblemon, AVECOINS and the other test mods
   as they are.
3. Start the server and confirm it reaches `Done` without a Zian Utilities
   error. Do not change any generation settings for this probe.
4. From the server console or an operator account, run `zian reward status`
   (add `/` when typing in-game). Record all three output lines. Expected:
   `registro preparado; entrega desactivada`, `AVECOINS 2.3 compatible`, and
   the managed currency list. An incompatibility is a useful FAIL result;
   do not attempt any economy mutation.
5. Run `zian generation active` and confirm the generations shown match the
   state before this update. This checks that the new command registration did
   not disturb Generation Control.

Send the command output and the server `latest.log` if startup or the probe
fails. No player balance or item count needs to change during this test.

`/zian reward inspect <claimId>` is reserved for future claim diagnostics; it
will report “not found” until a reward-producing feature creates claims.

