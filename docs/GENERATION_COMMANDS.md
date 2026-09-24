# Generation commands

Status: Milestone 1 implementation contract.

## Root

```text
/zian generation
```

## Read-only commands

Available without admin mutation permission:

```text
/zian generation active
/zian generation status
/zian generation status <gen>
```

Examples:

```text
/zian generation active
/zian generation status gen4
```

Autocomplete exposes only canonical IDs:

```text
gen1 ... gen9
```

Cobblemon-specific aliases such as `gen7b` and `gen8a` are intentionally not command generation IDs.

## Administrative commands

```text
/zian generation enable <gen>
/zian generation disable <gen>
```

The current fallback permission uses NeoForge/Minecraft's Game Master command level:

```text
Commands.LEVEL_GAMEMASTERS
```

This keeps production behavior independent from Bukkit/Paper permissions.

A future external permission adapter may expose the conceptual
`zian.generation.admin` capability without changing command behavior.

## Persistence

Mutation commands use the same `GenerationStateStore` backed by
`GenerationStateSavedData` that spawn guards will read.

Changes therefore mark the SavedData dirty immediately.

Repeated operations are idempotent:

```text
enable active generation   -> success/no-op, no redundant save
disable inactive generation -> success/no-op, no redundant save
```

## Audit

Successful mutation attempts emit a structured log entry:

```text
[ZIAN-AUDIT] action=generation_enable ...
[ZIAN-AUDIT] action=generation_disable ...
```

The log records actor, optional player UUID, target generation and whether the
operation changed state or was a no-op.

## Deferred commands

These remain outside M1-05:

```text
/zian generation reload
/zian generation inspect <species>
```

They will be added when configuration reload and diagnostic inspection have
real implementations rather than decorative commands.
