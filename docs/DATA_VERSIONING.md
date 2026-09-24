# Persistence and data versioning principles

Status: cross-module design requirement.

## Goal

Saved worlds must remain recoverable as Zian Utilities evolves.

## Every durable dataset should have a schema version

Examples:

```text
generation state
quest player state
quest definitions/revisions
gacha pity
gacha operations
reward claims
```

Do not rely on the mod version alone to infer data schema.

## Migration rules

- migrations should be explicit;
- migrations should be deterministic;
- do not silently discard unknown/corrupt fields if recovery is possible;
- log migration start/result;
- keep backups/recovery guidance for destructive changes;
- test migration using fixtures from older schema versions.

## Crash safety

For important state transitions:

```text
persist intent/state
→ perform irreversible external action
→ persist result
```

when the feature requires recoverability.

The exact storage mechanism can differ by module.

## Definitions vs player state

Data-driven definitions can change independently from saved player state.

Active quests/gacha operations therefore need enough persisted identity/revision information to remain interpretable after a configuration update.

## Unknown/new fields

Where serialization technology permits, prefer forward-compatible reading rather than failing merely because a newer harmless field exists.

## Recovery states

External mutations may require durable states such as:

```text
PENDING
APPLIED
REJECTED
UNCERTAIN
RECOVERY_REQUIRED
```

A server restart must not convert an uncertain operation into a fresh operation with a new ID.
