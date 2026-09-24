# Zian Utilities Rewards - functional specification

Status: future shared service specification.

Rewards should be reusable by Quests, Gacha and future modules.

## Goals

- one reward model for multiple features;
- explicit result handling;
- persistent claim identity;
- safe interaction with external systems;
- extensible reward types;
- no direct AVECOINS calls from feature modules.

## Planned reward types

```text
ITEM
CURRENCY
EXPERIENCE
COMMAND
POKEMON
GACHA_TICKET
CUSTOM
```

Not every type needs to ship in the first implementation.

## Reward result semantics

The reward layer should distinguish at least:

```text
APPLIED
REJECTED
UNCERTAIN
```

Meaning:

### APPLIED
Delivery is confirmed.

### REJECTED
Delivery definitely did not occur and a known reason is available.

### UNCERTAIN
The system cannot safely determine whether delivery happened.

An uncertain external reward must not be blindly retried.

## Reward claim identity

Every claimable reward operation should have a stable identifier.

Conceptually:

`claimOperationId: UUID`

The same logical claim must not generate a new operation ID on every retry/restart.

This supports eventual idempotency if external APIs later expose it.

## Composite rewards

A quest or Gacha result may contain multiple rewards:

```text
500 AVECOINS
+ 1 Gacha Ticket
+ 5 Poké Balls
```

Composite delivery needs an explicit policy.

Architecture must decide whether to:

- deliver sequentially and journal each component;
- reserve/validate all possible rewards first;
- compensate on partial failure when possible;
- mark the claim for recovery on an uncertain component.

Do not pretend multiple external systems form one atomic transaction if they do not.

## Currency rewards

Currency delivery must use:

```text
RewardService
↓
CurrencyReward handler
↓
EconomyPort
↓
AVECOINS adapter
```

The semantics documented in `docs/AVECOINS_INTEGRATION.md` apply.

## Item rewards

Item rewards need a defined overflow policy.

Possible policies:

```text
inventory only
inventory then drop
mail/pending storage
reject if insufficient space
```

The final product choice should be explicit and testable.

## Pokémon rewards

Pokémon rewards require careful ownership/storage behavior.

Before implementation, define:

- party vs PC fallback;
- full storage behavior;
- species/form/properties definition format;
- whether generated Pokémon can count toward quests;
- audit/logging.

## Command rewards

Command rewards are powerful and should be treated as trusted configuration only.

The system should define:

- execution context;
- placeholder rules;
- permission/trust boundary;
- failure result semantics.

## Gacha ticket rewards

Prefer a durable virtual balance or explicit item strategy, but do not mix both accidentally.

The Gacha design should state exactly what a ticket is and how consumption is persisted.

## Claim lifecycle

Suggested conceptual states:

```text
AVAILABLE
CLAIMING
PARTIALLY_APPLIED
CLAIMED
REJECTED
RECOVERY_REQUIRED
```

Exact states are an architecture decision.

## Audit

Important reward operations should be auditable with:

```text
operation ID
player UUID
source module
source ID
reward definition
attempt time
result
external reason
recovery state
```

## Initial implementation recommendation

When Rewards is first implemented:

```text
ItemReward
CurrencyReward
simple composite rewards
claim journal
Applied/Rejected/Uncertain
tests
```

Add Pokémon/command/ticket rewards only after their edge cases are explicitly defined.
