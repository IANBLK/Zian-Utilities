# Changelog

## 0.1.0-alpha.24

- Misiones y Gachas, incluidos sus pagos, se activan sin parámetros especiales de Java. Al iniciar por primera vez se crea `config/zianutilities-features.properties` con cuatro interruptores editables.
- Los interruptores de pagos solo funcionan si su módulo está habilitado; una configuración incompleta o inválida desactiva las funciones en lugar de activar pagos inesperadamente.
- `/zian quest test rotate` continúa siendo una acción de prueba y no queda disponible sin su antiguo parámetro de prueba.
- Los parámetros antiguos de prueba ya no son necesarios para las funciones normales. La selección de objetivos y las recompensas de una ventana de misiones ya iniciada conservan sus reglas hasta el siguiente reinicio programado.

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
- Fishing spawn guard on `BOBBER_SPAWN_POKEMON_PRE`, using planned species generation and current persisted state without broad `BasicSpawner` classification.

