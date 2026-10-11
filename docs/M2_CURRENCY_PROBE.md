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

## Alpha.3 read-only baseline — PASS

On the Youer 1.21.1 test server, the operator's in-game output showed the
credit probe disabled, `AVECOINS 2.3 compatible`, the eight previously accepted
managed currencies, `Saldo de avecoins:coppercoin: 0`, and
`Generaciones activas: gen7`. No wallet mutation was requested. This is the
before-balance for the one-coppercoin test; a successful first credit should
show `1`, and duplicate/restart checks should remain `1`.

## Alpha.3 one-coppercoin test on Youer — PASS

Tested on the Youer 1.21.1 test server with the alpha.3 build and
`-Dzianutilities.rewardTestCreditEnabled=true`. Normal mission rewards stayed
disabled. The previously recorded coppercoin balance was **0**.

- At 21:27:43, the first `/zian reward testcredit` returned `CLAIMED` for
  claim `777299f6-db3b-3a76-89d6-3f828533b206`; the in-game balance was **1**.
- At 21:28:58, the second invocation used the same claim ID. The balance
  remained **1**, and `/zian reward inspect` showed
  `CLAIMED [copper=APPLIED]`.
- Pterodactyl stopped the server normally at 21:30:20, including player and
  world saves. Youer restarted with the same Java test flag. After login,
  `/zian reward balance` showed **1** before a third invocation at 21:31:31.
  The audit log again used the same claim ID and returned `CLAIMED`. The
  subsequent balance remained **1**, and inspect still showed
  `CLAIMED [copper=APPLIED]`.

Evidence: the operator supplied the console transcript covering both starts,
the normal stop, command timestamps, and audit entries, plus in-game captures
showing the balances and inspect result. Player network addresses are omitted
from this report. This proves one-credit delivery and duplicate suppression
across a normal restart on this Youer test server. It does not exercise an
abrupt process kill or the `RECOVERY_REQUIRED` path. After the test, remove
the Java test flag and restart; retain the claim journal.

## Test flag removal — PASS

After the controlled credit test, the operator removed
`-Dzianutilities.rewardTestCreditEnabled=true` and restarted the Youer test
server. An in-game status capture showed `prueba de crédito desactivada`,
`AVECOINS 2.3 compatible`, and missions still disabled. The status capture
does not include a post-removal balance check; the last observed balance of
**1** was from the prior restart test.
