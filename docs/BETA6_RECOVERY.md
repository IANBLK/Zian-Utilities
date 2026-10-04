# Zian Utilities 0.1.0-beta.6 — recovery and audit decisions

## Scope

This candidate continues beta.5. Generation control, equipment, the mining enchantment and gacha animation retain their existing behavior. These changes address the external audit's recovery, reward accounting, bounded NBT reading and diagnostic findings.

## Gacha recovery

The reel lasts 5.3 seconds (3.4 seconds of spinning followed by the reveal). Automatic delivery waits 6.3 seconds on the server, including one additional second. Timing is server-owned, so network latency can slightly shift the visual interval.

The original chosen prize is persisted as READY immediately after a confirmed charge, together with its earliest claim time. Manual claims and additional rolls are blocked during this interval. No second ticket is charged for delivery. Logout or restart discards the volatile timer while retaining the journaled prize for later manual claim. A full inventory also leaves the prize READY. Closing the GUI does not reopen it when the timer finishes.

Use operator permission level 2 or the server console. The player must be online.

1. Inspect: `/ZianUtilities gacha review <player>`.
2. For a pending or uncertain debit, verify the original charge using transaction evidence, backups or AVECOINS records. A current balance alone cannot prove a past charge.
3. If the original charge occurred: `/ZianUtilities gacha resolve-debit <player> <operationId> charged <evidence>`. The existing prize becomes READY for the player to claim.
4. If the original charge did not occur: `/ZianUtilities gacha resolve-debit <player> <operationId> not-charged <evidence>`. The operation closes without changing the wallet.
5. For an uncertain delivery, verify whether the original prize was received. If verified, use `/ZianUtilities gacha confirm-delivered <player> <operationId>`.
6. If the player did not receive the whole prize, manually deliver only the verified missing amount or agreed compensation, then use `/ZianUtilities gacha confirm-compensated <player> <operationId> <evidence>`.

Evidence is a required reference of up to 160 characters for debit resolution and compensation. Administrative decisions are saved and logged. These commands do not prove the outcome themselves. If evidence is insufficient, leave the operation blocked.

New journals distinguish DEBIT_UNCERTAIN from DELIVERY_UNCERTAIN. DEBIT_PENDING and DELIVERING remain blocked after an interruption. Known old RECOVERY_REQUIRED debit reasons and old delivery records with no reason remain reviewable. Unknown legacy reasons remain blocked rather than guessed.

The proposed automatic closure of a missing delivery without compensation was rejected: it could leave a player with neither the ticket nor the prize. Delivery uncertainty is never automatically reopened as READY.

## Reward claims

Only a deterministic `wallet_full` rejection is retryable when the same claim is evaluated again. The provider rejected it before applying the credit. The claim uses the existing operation ID, persists intent before calling the wallet and skips components already applied. Unsupported-currency rejections, uncertain results and interrupted in-flight results remain blocked.

## Disabled quest rewards

This candidate preserves the existing no-automatic-backpay policy. Completion while rewards are disabled is now recorded as SKIPPED_REWARDS_DISABLED, rather than paid. Weekly test windows use SKIPPED_TEST_WINDOW. Campaign chapters retain their completed progress and store each new chapter's settlement, currency and amount. The UI shows skipped weekly payments and the count of campaign stages completed without payment.

Turning rewards back on does not automatically credit skipped objectives. Existing beta.5 records cannot reliably distinguish past disabled rewards from past payments, so this update does not invent or reconstruct that history.

## Validation limits

Regression tests cover disjoint recovery phases, known legacy classifications, terminal-state restrictions, retrying a full wallet without replaying successful components, persistence of skipped settlements and the compressed NBT read quota.

Full process-kill testing of the live Minecraft inventory and AVECOINS roll-to-claim boundary remains a future validation task (ZU-004). Passing unit tests is not evidence of every crash/restart scenario.

Before public release, test beta.6 on Youer with the same JAR on the server and client: normal gacha roll/claim, full inventory, restart with a pending prize, and a normal weekly/campaign payment. Reconciliation commands should only be tested against a disposable world with verified outcomes.
