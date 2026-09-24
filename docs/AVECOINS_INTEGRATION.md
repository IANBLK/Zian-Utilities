# AVECOINS integration plan

Current inspected target:

`AVECOINS 2.3`

Historical reference implementation:

`docs/reference/avecoins/`

This document describes the behavior Zian Utilities should preserve conceptually. It does not make the historical Zian GTS classes production dependencies.

## Boundary

Desired shape:

```text
Feature
  ↓
EconomyPort
  ↓
AVECOINS adapter
  ↓
AVECOINS
```

Quests, Rewards and Gacha must not call AVECOINS implementation classes directly.

## Required operations

The economy boundary should support at least:

```text
balance / affordability check
withdraw/debit
deposit/credit
currency identification
clear success/error outcome
stable operation ID
```

## Result semantics

Three states are important:

### Applied

The mutation is confirmed.

### Rejected

The operation was not applied for a known reason.

Examples:

```text
invalid_amount
insufficient_funds
wallet_full
unsupported_currency
```

### Uncertain

The caller cannot safely determine whether the external mutation took effect.

An uncertain result must never be converted into an automatic blind retry.

## operationId

The historical port already carries:

`operationId: UUID`

AVECOINS 2.3 does not currently use that identifier for idempotency in the preserved adapter.

Keep the concept available so a future AVECOINS API can support:

- idempotency keys
- transaction lookup
- recovery after crash
- explicit transaction status

## Historical adapter observations

The preserved Zian GTS adapter:

- is reflective
- requires AVECOINS 2.3 exactly
- resolves managed currencies from `CraftingConfig.MANAGED_RESULTS`
- accesses `WalletStore` / `WalletData`
- copies wallet data before mutation
- saves the candidate state after debit/credit
- treats failed/uncertain save as an uncertain external mutation
- synchronizes around the wallet store
- checks wallet capacity before deposits

## Zian Utilities usage

### Quests / Rewards

Expected direction:

```text
Quest
↓
RewardService
↓
CurrencyReward
↓
EconomyPort
```

A quest reward claim should have its own durable claim identifier so a restart cannot turn a single reward into repeated deposits.

### Gacha

Expected direction:

```text
GachaOperation
↓
payment
↓
reward resolution
↓
delivery
↓
pity update
↓
complete
```

Gacha should persist operation state before irreversible external mutations.

An `Uncertain` payment result should enter a recovery state rather than retrying the debit.

## Future AVECOINS API preference

If AVECOINS later exposes a stable public API for:

- getBalance
- debit
- credit
- currency IDs
- transaction IDs
- idempotency
- transaction lookup

prefer that API over reflection while keeping the Zian Utilities `EconomyPort` stable.
