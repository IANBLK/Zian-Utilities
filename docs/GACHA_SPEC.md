# Zian Utilities Gacha - functional specification

Status: future module specification.

This document describes desired behavior only. Gacha is not part of Milestone 1.

## Goals

Provide configurable Gacha pools that can use AVECOINS and/or tickets while preserving transaction safety and recoverability.

## Non-goal

The implementation must not be:

```text
withdraw
→ random
→ give reward
```

with no durable operation state.

## Pool definition

A pool should eventually support:

```text
id
display metadata
enabled/disabled
entry cost
currency or ticket requirement
reward entries
weights
rarity/tier
pity rules
availability window
optional prerequisites
```

Definitions should be data-driven.

## Payment methods

Planned methods:

```text
AVECOINS
Gacha Ticket
future custom payment adapter
```

A pool may choose one or more supported payment methods if product design allows it.

## Operation identity

Every roll should receive a stable operation ID before irreversible work.

Conceptual lifecycle:

```text
CREATED
VALIDATED
PAYMENT_PENDING
PAYMENT_APPLIED
REWARD_RESOLVED
DELIVERY_PENDING
DELIVERED
PITY_UPDATED
COMPLETED
RECOVERY_REQUIRED
REJECTED
```

Exact state names remain open.

## Required ordering

The design must make the reward outcome recoverable.

A safe conceptual flow is:

1. create/persist operation;
2. validate pool/player/payment;
3. attempt payment;
4. persist confirmed payment or uncertain state;
5. resolve reward exactly once;
6. persist resolved reward;
7. deliver through RewardService;
8. persist delivery result;
9. update pity using durable state;
10. mark completed.

A crash must not cause a new random result for an already-resolved operation.

## Economy semantics

AVECOINS interactions use EconomyPort.

```text
Applied
Rejected
Uncertain
```

If payment is `Uncertain`, the roll enters recovery. Do not debit again automatically.

## Randomness

The implementation should use an appropriate server-side RNG.

For recovery, the selected reward should be persisted after resolution.

Do not rely on replaying RNG after restart to reconstruct a result.

## Pity

Planned support:

```text
hard pity
optional soft pity
guaranteed tier/reward
per-player state
per-pool state
reset rules
```

Pity changes should occur exactly once for a completed/resolved operation according to the chosen design.

## History

Keep a bounded or configurable history with:

```text
operation ID
player
pool
payment method
cost
reward
result
timestamp
pity before/after if useful
```

## Admin controls

Future administration should include:

```text
reload pools
enable/disable pool
inspect player pity
inspect operation
recover/reconcile stuck operation
grant ticket
simulate pool probabilities without awarding
```

Simulation must never mutate player state.

## Reward integration

All Gacha outputs go through RewardService.

This allows the same pool format to award:

```text
items
currency
tickets
Pokémon
commands
future custom rewards
```

## Failure rules

Examples:

- insufficient funds -> REJECTED, no roll;
- full wallet when reward is currency -> reward claim/recovery policy applies;
- payment uncertain -> RECOVERY_REQUIRED;
- reward delivery uncertain -> do not reroll;
- server crash after reward resolved -> reload the persisted same result;
- duplicate client/admin request -> identify same operation or reject duplicate according to API design.

## First Gacha milestone

Recommended first slice after Quests/Rewards are stable:

```text
one configurable pool
AVECOINS payment
item rewards
hard pity
operation journal
history
recovery states
unit tests
runtime crash/restart tests
```

Do not begin with every planned reward type or elaborate UI.
