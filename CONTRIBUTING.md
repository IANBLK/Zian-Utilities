# Contributing to Zian Utilities

## Baseline

- Java 21
- Minecraft 1.21.1
- NeoForge 21.1.x
- Cobblemon 1.8.1 baseline
- Gradle 9.2.1 in CI

## Architecture rules

- `core` must not import Minecraft, NeoForge or Cobblemon classes.
- Cobblemon-specific behavior belongs in the NeoForge integration layer.
- Do not copy production implementation from the archived Generation Spawns repository.
- Runtime claims must be backed by runtime evidence.
- Keep experimental observers and bytecode research outside production source sets.

## Commits

Use concise conventional prefixes such as `feat:`, `fix:`, `test:`, `docs:`, `build:`, `ci:` and `research:`.
