# Transaction safety and anti-dupe rules

Status: cross-module safety requirement.

This document defines rules for any feature that performs irreversible or externally visible mutations.

## Core principle

Never assume:

```text
exception == nothing happened
```

External systems can fail after applying a mutation but before confirming success.

## Result classes

Use explicit outcomes where external mutation is involved:

```text
APPLIED
REJECTED
UNCERTAIN
```

### APPLIED

Confirmed applied.

### REJECTED

Confirmed not applied.

### UNCERTAIN

The system cannot prove whether it was applied.

An uncertain result must never trigger an automatic blind retry.

## Stable operation IDs

Every logical operation that can survive/retry/recover must use a stable ID.

Examples:

```text
reward claim
Gacha roll
economy debit
economy credit
admin recovery action
```

Do not create a new ID merely because the server restarted.

## Persist-before-mutate

When a flow contains an irreversible external action:

```text
persist operation intent/state
→ perform external mutation
→ persist confirmed result
```

The exact sequence depends on feature semantics, but durable state must exist before recovery becomes necessary.

## No fake atomicity

Do not pretend these are one database transaction if they span different systems:

```text
AVECOINS
Minecraft inventory
Cobblemon storage
saved data
third-party mod APIs
```

Instead:

- journal component steps;
- track partial completion;
- compensate only when compensation is actually safe;
- otherwise enter recovery.

## Quest reward claims

Requirements:

- one stable claim ID;
- claim state persisted;
- duplicate claim request detects existing state;
- currency reward uses same operation ID across recovery;
- if one composite reward component is uncertain, do not redeliver already confirmed components.

## Gacha

Requirements:

- persist Gacha operation before debit;
- payment result stored;
- reward selected once;
- selected reward persisted;
- delivery journaled;
- pity update exactly once;
- duplicate requests cannot create multiple rolls for the same logical action;
- uncertain payment never triggers a new debit automatically.

## Tickets/items used as payment

If virtual tickets are used:

- use durable balance/state;
- consume exactly once;
- journal the consumption.

If physical item tickets are used:

- define inventory race rules;
- validate quantity immediately before mutation;
- perform removal server-side;
- persist operation state around the irreversible point.

## Concurrency

Sensitive per-player/per-operation mutations should use appropriate synchronization/serialization.

Important cases:

- simultaneous Gacha requests;
- simultaneous reward claims;
- two commands editing the same generation state;
- multiple economy operations for one player;
- reconnect while a claim is recovering.

Do not use a global lock when a narrower lock is sufficient.

## Restart/crash recovery

On startup or player interaction:

- detect non-terminal operations;
- classify recoverable vs uncertain;
- never silently discard;
- expose diagnostics/admin recovery where automatic recovery is unsafe.

## Idempotency

Prefer providers that support:

```text
idempotency key
transaction ID
status lookup
```

If AVECOINS later exposes these, preserve operation IDs and use them.

## Audit

Record enough to reconstruct what happened:

```text
operationId
player UUID
feature
operation type
source ID
state transitions
provider result
timestamps
error/reason
recovery action
```

Avoid logging secrets.

## Anti-dupe test cases

Mandatory future tests:

- double-click/double command submission;
- two simultaneous requests;
- server stop after payment before reward;
- crash after reward selection before delivery;
- crash after delivery before completion marker;
- uncertain provider response;
- reconnect during recovery;
- repeated admin recovery command;
- full inventory/wallet edge cases;
- stale client request replay.

## Fail-safe preference

For economy/reward operations:

```text
uncertain + recoverable
```

is preferable to:

```text
guess + duplicate/loss
```
