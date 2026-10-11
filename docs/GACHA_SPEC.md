# Zian Utilities Gacha - functional specification

Status: design in progress; ticket payment and administrator-created pools confirmed.

This document describes desired behavior only. Gacha is not part of Milestone 1.

## Goals

Let administrators create custom Gacha pools. A roll consumes an AVECOINS ticket balance, while AVECOINS coins remain reserved for quest rewards. Preserve transaction safety and recoverability.

## Decisions confirmed for the first playable version

- Pools are created and controlled by server administrators, not generated per player.
- A roll is paid with an AVECOINS ticket, not with an AVECOINS coin.
- AVECOINS 2.3 exposes `avecoins:goldticket`, `avecoins:diamondticket`, and `avecoins:netheriteticket` as managed wallet currencies on the tested Youer server. These are wallet balances for this design, not inventory item stacks.
- Each pool chooses which of the supported AVECOINS ticket currencies it accepts and how many tickets a roll costs.
- The first version awards inventory items only; Pokémon, currency, commands, and other reward types are later extensions.
- Administrators create and edit pools through an in-game interface. Only authorized administrators can publish, disable, or change them.
- A custom pool definition must freeze its ticket type, cost, prize entries, and weights for each accepted roll. Later administrator edits affect new rolls only.
- Administrators add a prize by copying an item stack from their inventory into the editor. The source item remains in the administrator's inventory; the published prize retains its count and item data.
- If the winner's inventory has no safe space for the complete prize, the prize becomes pending in a durable claim list. It is never dropped on the ground.
- The player sees the exact odds for each prize before spending a ticket. The server derives displayed percentages from the same published weights used by the roll.
- Every paid roll awards exactly one configured item prize. A published pool cannot contain an empty outcome.
- There is no pity/guaranteed-rarity counter in the first version. It may be added later only with explicit display of how it changes each player's odds.
- The client may preview a pool and request a roll, but the server checks the ticket balance, chooses the outcome, and delivers the prize.

Ticket prices and prize weights are set by the administrator; no fixed economic values are baked into the mod.

## Administrator and player flow

1. An authorized administrator opens the Gachas editor from `/zian`, creates a pool, names it, selects its AVECOINS ticket type and ticket cost, and copies prize stacks from their inventory.
2. The editor assigns each prize a positive weight and shows the resulting percentage. An empty or invalid pool cannot be published. Publishing produces a new immutable revision; disabling a pool blocks new rolls without erasing operation history or pending prizes.
3. A player opens Gachas from `/zian`, views the published pools, ticket cost, prizes, and odds, then confirms one roll. The client sends a single request; the server determines the result.
4. The server records the operation before deducting tickets, records the deduction outcome, selects and persists the prize once, and then attempts delivery. An interrupted or uncertain operation is inspected/recovered by an administrator, never charged or rolled again blindly.
5. A prize that does not fit remains claimable from the Gachas screen. A successful claim removes it from pending storage exactly once. The player's history shows ticket cost, prize, and outcome.

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

First version:

```text
AVECOINS managed ticket balance (goldticket, diamondticket, or netheriteticket)
```

Future possible methods:

```text
AVECOINS coins
physical item tickets
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

Future support only; omitted from the first version:

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

First slice after Quests/Rewards are stable:

```text
administrator-created custom pools through an in-game interface
one chosen AVECOINS ticket currency and configurable ticket cost per pool
exactly one item reward per roll, always
no pity counter in the first version
server-side published pool revisions and disclosed odds
operation journal
history
recovery states
unit tests
runtime crash/restart tests
```

Do not begin with every planned reward type or elaborate UI.
