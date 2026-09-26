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

