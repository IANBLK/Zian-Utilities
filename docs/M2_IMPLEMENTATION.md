# M2 implementation status

The first M2 slice adds pure-core contracts only. No reward is delivered and no
AVECOINS balance is changed by this slice.

- `EconomyPort` exposes balance, credit and debit with a stable operation ID and
  distinct confirmed, rejected and uncertain mutation results.
- `RewardClaim` gives a composite claim a stable ID and unique component IDs.
  Currency and item definitions are the initial reward types.
- `RewardDeliveryPort` works per component so the future claim journal can
  record partial delivery without replaying confirmed components.
- Canonical capture and evolution events carry species identity and generations
  resolved by an adapter. The quest core does not depend on Cobblemon classes.

Before enabling currency or item rewards on a server, implement and test the
durable claim journal, provider adapter, item overflow policy and admin recovery
path. Persist intent before each external mutation. Treat uncertain results as
recovery-required; never automatically replay them. A separate operation ID is
needed for each external mutation within a composite claim, and it must remain
stable across recovery.

The next functional slice is a journal-backed reward service with fake provider
tests for duplicate requests, partial completion and uncertain results. After
that, add the AVECOINS adapter and quest progress persistence.

