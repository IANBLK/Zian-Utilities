# Changelog

All notable changes to Zian Utilities will be documented here.

## Unreleased

### Added

- Initial two-module Gradle bootstrap: `core` + `neoforge`.
- Java 21 / Minecraft 1.21.1 / NeoForge 21.1.251 baseline.
- Cobblemon 1.8.1 compile-time integration dependency.
- CI build and pure-core boundary check.
- Cobblemon Species → Generation resolver with canonical Gen 1-9 labels, `gen7b`/`gen8a` product mappings, caching, unknown-label handling and manual override precedence.
- Persistent global generation state via NeoForge SavedData, schema versioning, explicit legacy migration and forward-preserved unknown generation IDs.
- `/zian generation` administration commands with autocomplete, Game Master permission fallback, idempotent persistent mutations and structured audit logging.
- Natural/player spawn guard for Cobblemon `PlayerSpawner`, backed by live persisted generation state and conservative source classification.
