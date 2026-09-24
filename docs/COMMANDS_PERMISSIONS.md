# Commands and permissions specification

Status: product requirement, not final Brigadier implementation.

## Command root

Recommended root:

`/zian`

Avoid scattering unrelated top-level commands unless there is a strong usability reason.

## Generation commands

Conceptual:

```text
/zian generation enable <gen>
/zian generation disable <gen>
/zian generation active
/zian generation status <gen>
/zian generation reload
/zian generation inspect <species>
```

Suggested access:

```text
active/status -> player-safe read access
enable/disable/reload/inspect -> admin
```

## Quest commands

Player:

```text
/zian quests
/zian quests daily
/zian quests weekly
/zian quests campaign
/zian quests claim <quest>
```

Admin:

```text
/zian quests inspect <player>
/zian quests complete <player> <quest>
/zian quests reset <player> <scope>
/zian quests reload
```

Dangerous mutation commands should emit audit records.

## Gacha commands

Player:

```text
/zian gacha
/zian gacha pools
/zian gacha roll <pool>
/zian gacha history
```

Admin:

```text
/zian gacha inspect <operationId>
/zian gacha pity <player> <pool>
/zian gacha recover <operationId>
/zian gacha simulate <pool> <rolls>
/zian gacha reload
```

Simulation must never charge, deliver or mutate pity.

## Diagnostics

```text
/zian debug status
/zian debug generation
/zian debug quests <player>
/zian debug economy
/zian debug gacha <operationId>
```

Debug output should avoid exposing sensitive infrastructure information.

## Permission model

Use a small capability-oriented permission model.

Conceptual nodes:

```text
zian.use
zian.generation.view
zian.generation.admin
zian.quests.use
zian.quests.admin
zian.gacha.use
zian.gacha.admin
zian.debug
zian.reload
```

NeoForge-native permission handling should be researched before final implementation.

Do not make production behavior depend on Bukkit/Paper permission APIs.

## Operator fallback

If no external permission integration is present, admin actions may fall back to an appropriate operator level.

The chosen OP level should be documented and consistent.

## Autocomplete

Autocomplete should be available for:

- gen1-gen9;
- known quest IDs for admins;
- known Gacha pools;
- player names where online resolution is safe;
- operation IDs only where bounded/filtered enough to avoid huge completions.

## Error handling

Commands should distinguish:

```text
invalid syntax
not authorized
unknown generation
unknown quest
unknown pool
operation not found
invalid state transition
provider unavailable
operation uncertain
```

Do not collapse all failures into a generic "something went wrong".

## Audit requirement

The following commands should create audit entries:

- generation enable/disable;
- quest reset/complete;
- Gacha recover;
- pity modifications;
- module reloads that change behavior;
- manual economy-affecting actions, if added later.

Audit should record UUID, command/action, target, result and timestamp.
