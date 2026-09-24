# Development rules

## New project boundary

Zian Utilities is a new project.

Do not copy implementation code from the old Generation Spawns project into the production source tree.

The old repository may be consulted only as historical context for observed bugs, test cases and migration history. New implementation decisions should be based on:

- verified Cobblemon / NeoForge APIs
- observable runtime behavior
- Zian Utilities functional requirements
- new tests written for this repository

## Current implementation target

Milestone 1 is:

```text
Core
+ Cobblemon integration foundation
+ GenerationResolver
+ Generation state
+ Persistence
+ Commands
+ Generation Control
+ Tests
```

Do not implement Quests, Gacha, Equipment or Enchantments during this milestone.

## Future integration rule

External systems should be isolated behind ports/adapters.

Examples:

```text
Feature → EconomyPort → AVECOINS adapter
Feature → RewardService → reward implementations
Core → GenerationResolver contract → Cobblemon resolver
```

## AVECOINS reference

The historical Zian GTS integration is preserved under:

`docs/reference/avecoins/`

Those files are reference material only until a new Zian Utilities economy integration is designed and tested.
