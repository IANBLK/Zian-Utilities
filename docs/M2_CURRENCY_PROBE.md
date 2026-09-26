# M2 controlled coppercoin test on Youer

This test is for the Youer 1.21.1 **test server only** after the read-only
AVECOINS probe has passed. `alpha.3` keeps normal rewards disabled. The gated
command may add exactly one `avecoins:coppercoin` to the executing operator.

1. Back up the test server's world and AVECOINS wallet data. Stop it normally.
2. Replace the previous Zian Utilities JAR with
   `Zian-Utilities-0.1.0-alpha.3-NeoForge-1.21.1.jar`; keep only one Zian
   Utilities JAR in `mods/`. Do not change the other mods.
3. Start without the new flag. Confirm `zian reward status` reports the credit
   test **disabled** and `zian generation active` still shows the expected
   generation. An operator can run `/zian reward balance` in-game to record
   their current `avecoins:coppercoin` balance. The console cannot use this
   player-specific command.
4. Stop the server. Add `-Dzianutilities.rewardTestCreditEnabled=true` to the
   Java startup arguments **before `-jar`**, then restart. Confirm `zian reward
   status` reports the credit test **enabled**.
5. As an operator with level 3 or higher, run `/zian reward testcredit` once
   in-game. Record the claim UUID from its response. Check `/zian reward
   balance`: it should be exactly **one coppercoin higher**.
6. Run `/zian reward testcredit` a second time and check the balance again.
   It must not increase. Run `/zian reward inspect <claim UUID>`; expected
   `CLAIMED [copper=APPLIED]`.
7. Restart the server once and repeat `/zian reward testcredit` and `/zian
   reward balance`. The balance must remain unchanged after the restart.
8. Remove the startup flag when finished and restart. Keep the claim journal
   under the world `data/zianutilities/reward_claims/` directory; do not delete
   it to rerun the test.

If any command reports `RECOVERY_REQUIRED`, an unexpected balance, or a save
error, **stop the test**. Do not use a new claim ID or attempt another payment.
Send the command output, claim UUID, and `latest.log` for analysis. No debit,
item reward, or quest reward is exercised by this test.

