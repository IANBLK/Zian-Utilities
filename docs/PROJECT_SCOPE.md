# Zian Utilities - Project Scope

## Project scope

Zian Utilities is a modular Minecraft 1.21.1 project intended to host multiple server-side systems without coupling them into a single monolith.

Planned areas:

1. Core
2. Cobblemon integration
3. Generation Control
4. Quests
5. Rewards
6. Economy integration
7. Gacha
8. Equipment
9. Enchantments

## Current milestone

### Milestone 1: Core + Generation Control

The first milestone should cover only:

- core services and module boundaries
- Cobblemon integration foundation
- Species -> Generation resolution
- generation state
- persistence
- commands
- spawn filtering / guarding
- automated tests
- runtime verification plan

Habitat-related spawning remains subject to explicit runtime verification before becoming a mandatory acceptance criterion.

## Future milestones

### Milestone 2
- Reward system
- EconomyPort
- AVECOINS integration
- quest foundation

### Milestone 3
- Campaign Quests
- Daily Quests
- Weekly Quests

### Milestone 4
- Gacha
- pity
- recoverable transaction state
- AVECOINS and/or ticket payments

### Later
- weapons
- armor
- tools
- custom enchantments
- UI/UX improvements
- additional integrations

## Architectural constraints

- Core should not depend directly on AVECOINS.
- Core should avoid direct Cobblemon dependencies except through integration boundaries.
- Quests and Generation Control must not implement separate Species -> Generation logic.
- Quests and Gacha should share the same reward abstraction.
- Economy mutations must distinguish confirmed success, known rejection and uncertain outcome.
- Runtime verification is required for Cobblemon spawn paths that are not proven end-to-end.
