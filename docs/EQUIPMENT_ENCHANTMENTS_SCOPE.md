# Equipment and Enchantments - future scope

Status: future product scope only.

These systems are intentionally outside Milestone 1.

## Equipment

Planned categories:

```text
Weapons
Armor
Tools
```

The project should eventually support ordinary registered equipment plus optional custom behavior.

Potential future capabilities:

- custom attributes;
- durability;
- repair materials;
- special effects;
- cooldown abilities;
- set bonuses;
- model/resource-pack integration;
- recipe/datapack configuration where appropriate.

Do not make Quests or Gacha depend directly on equipment implementation classes. Equipment should be awardable through the generic item/reward layer.

## Enchantments

Custom enchantments are considered feasible for Minecraft 1.21.1, but their exact implementation must be designed specifically around the 1.21.1 enchantment/data model.

Potential categories:

- combat effects;
- utility/mining effects;
- Cobblemon-adjacent utility where technically safe;
- equipment-specific effects.

## Constraints

- avoid per-tick global scans when event-driven behavior is possible;
- keep server-authoritative effects server-side;
- define compatibility/conflict rules between custom enchantments;
- use data-driven configuration where Minecraft 1.21.1 supports it cleanly;
- do not couple Generation Control to Equipment/Enchantments.

## Before implementation

Research and decide:

- NeoForge 1.21.1 enchantment registration/data APIs;
- resource/data pack responsibilities;
- attribute modifier strategy;
- networking needs;
- Youer compatibility;
- whether custom visuals require an optional resource pack.

No production code should be added merely to reserve classes for these future systems.
