# M2 implementation status

M2 now contains pure-core contracts and a journal-backed claim service. It is
not wired to gameplay, inventory delivery or AVECOINS, so it does not change
server balances or grant items yet.

- `EconomyPort` exposes balance, credit and debit with a stable operation ID and
  distinct confirmed, rejected and uncertain mutation results.
- `RewardClaim` gives a composite claim a stable ID and unique component IDs.
  Currency and item definitions are the initial reward types.
- `RewardDeliveryPort` works per component so the future claim journal can
  record partial delivery without replaying confirmed components. Each component
  receives a stable operation ID derived from the claim and component IDs.
- Canonical capture and evolution events carry species identity and generations
  resolved by an adapter. The quest core does not depend on Cobblemon classes.
- `RewardClaimService` persists a claim before delivery, persists `IN_FLIGHT`
  before each component, and records the provider result. Duplicate requests
  skip confirmed components. `IN_FLIGHT` after a crash and `UNCERTAIN` require
  recovery and are never automatically delivered again.
- `FileRewardClaimStore` writes one claim per file through atomic replacement.
  It is intended for one server process with one shared store instance.

Before enabling currency or item rewards on a server, add the provider adapters,
item overflow policy, an admin recovery path and server lifecycle wiring.
Recovery must verify external provider state before changing an `IN_FLIGHT` or
`UNCERTAIN` component. File-store locking covers threads sharing one store
instance, not multiple running server processes.

The next functional slice is the AVECOINS adapter and a controlled recovery
workflow, followed by item delivery and quest progress persistence.

The first NeoForge integration gate is read-only: `/zian reward status` checks
the installed AVECOINS version and the inspected wallet method/layout contract
without reading or changing player balances. `/zian reward inspect <claimId>`
reads a saved claim journal. Both commands require operator level 2; no claim
or economy mutation command is registered yet. See `M2_YOUER_PROBE.md` for the
test-server procedure.

The next controlled test build (`alpha.3`) includes a reflective AVECOINS 2.3
adapter and a currency reward bridge. Normal quest rewards remain disabled.
`/zian reward balance` reads the operator's coppercoin balance. A single
`/zian reward testcredit` command is available only with the startup property
`-Dzianutilities.rewardTestCreditEnabled=true` and operator level 3. It credits
one coppercoin to the executing operator using a stable per-player claim ID;
repeating it, including after restart, must not issue another credit. The
claim journal is written before calling AVECOINS, and uncertain results block
automatic replay. See `M2_CURRENCY_PROBE.md` before enabling the test command.

