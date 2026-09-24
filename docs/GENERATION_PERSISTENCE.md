# Generation state persistence

Status: Milestone 1 implementation contract.

## Storage scope

Generation Control state is global to the server, not dimension-specific.

NeoForge persistence therefore attaches the SavedData instance to the Overworld data storage:

```text
MinecraftServer
  -> overworld()
  -> getDataStorage()
  -> computeIfAbsent(...)
```

This matches NeoForge 1.21.1 guidance for SavedData that is not level-specific.

## File identity

```text
zianutilities_generation_state.dat
```

under the world's Overworld `data/` directory.

## Schema

Current schema:

```text
schema_version = 1
enabled_generations = ["gen1", ..., "gen9"]
```

The schema version is independent from the mod version.

### Version 0 migration

A missing `schema_version` is interpreted as legacy schema 0.

Schema 0 uses the same `enabled_generations` list and is migrated in memory through an explicit migration path before being written back as schema 1.

## Forward compatibility

Unknown generation IDs are not silently discarded.

They are preserved and written back unchanged while the current runtime ignores them for policy evaluation.

Example:

```text
["gen1", "gen10", "addon_custom_gen"]
```

loads as:

```text
known state: GEN_1
preserved unknown IDs: gen10, addon_custom_gen
```

A schema version newer than the implementation supports fails explicitly rather than pretending the data is understood.

## Dirty tracking

Mutating the state calls `SavedData#setDirty()`.

Saving an identical state is a no-op and does not dirty the dataset unnecessarily.

## Restart behavior

Normal server stop/start and panel-driven scheduled restarts rely on Minecraft/NeoForge SavedData flushing.

Runtime acceptance still requires explicit stop/start testing on NeoForge, followed by Youer validation before claiming Youer persistence compatibility.
