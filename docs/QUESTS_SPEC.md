# Zian Utilities Quests - functional specification

Status: future module specification.

This document defines product behavior, not the final implementation architecture.

## Purpose

Provide Cobblemon-focused quests that can coexist with Generation Control and use shared services instead of duplicating Cobblemon rules.

Quest families:

```text
Campaign
Daily
Weekly
```

## Shared rules

- Quest definitions should be data-driven.
- Quests must use the shared Species -> Generation service.
- Repeatable quests must never select targets that are impossible under the currently enabled generations.
- Claim state must persist.
- A restart must not duplicate progress or rewards.
- A completed and claimed quest must not become claimable again in the same period.
- Future economy rewards must go through RewardService/EconomyPort, never direct AVECOINS calls.

## Campaign quests

Campaigns are persistent progression tracks.

Examples:

```text
Kanto campaign -> Generation 1
Johto campaign -> Generation 2
Hoenn campaign -> Generation 3
```

A campaign may declare a generation requirement.

Generation availability and quest availability remain separate concepts.

Generation Control answers:

`Is Gen N currently enabled?`

Quests answers:

`Is Campaign X unlocked/available for this player?`

## Daily quests

Daily quests are generated or selected from configured pools.

Configurable behavior should eventually support:

```text
rotation = GLOBAL | PERSONAL
quest count
daily reset time
timezone
completion bonus
reroll policy
```

A daily period should use an explicit period key, not player-login + 24 hours.

Example:

`2026-09-24`

## Weekly quests

Weekly quests use the same engine with a weekly period key.

Example:

`2026-W39`

Configurable behavior should eventually support:

```text
weekly reset day
weekly reset time
timezone
quest count
completion bonus
```

## Objective model

Initial objective candidates:

```text
capture
capture_species
capture_generation
capture_type
capture_shiny
evolve
level_up
win_battle
hatch_egg
```

Later objectives:

```text
defeat
defeat_generation
defeat_type
capture_legendary
pokedex_progress
fish_encounter
capture_from_fishing
trade
custom/addon objectives
```

Each objective should support only filters that make semantic sense.

Example conceptual definition:

```json
{
  "type": "capture",
  "amount": 10,
  "filters": {
    "generation": "enabled",
    "type": "water",
    "shiny": false
  }
}
```

The final schema remains an architecture decision.

## Generation-aware selection

For generated/repeatable quests, selectors must operate on currently valid content.

Examples:

```text
RANDOM_ENABLED_GENERATION
RANDOM_ENABLED_SPECIES
ANY_ENABLED_GENERATION
SPECIFIC_GENERATION
SPECIFIC_SPECIES
```

If no valid target exists, the generator must choose another template or fail generation clearly. It must not assign an impossible quest.

## Anti-abuse

Repeatable objectives need anti-abuse rules.

Examples:

- trades should not be infinitely farmable between the same players;
- admin-created Pokémon should not automatically count unless configured;
- repeated kill loops may need target/cooldown rules;
- quest progress should not increment twice from overlapping listeners;
- one gameplay action should map to one canonical quest event.

## Progress model

Conceptually each assigned quest needs:

```text
assignment ID
definition ID
definition version/hash
player UUID
period key, if repeatable
objective progress
status
reward claim status
timestamps
```

Suggested statuses:

```text
ACTIVE
COMPLETED
CLAIM_PENDING
CLAIMED
EXPIRED
RECOVERY_REQUIRED
```

Exact names remain open.

## Reset behavior

On login and on relevant scheduled checks:

1. determine current period key;
2. compare stored period;
3. if period changed, expire/close previous repeatable assignments according to policy;
4. generate/select the current period assignment;
5. persist before exposing rewards.

A scheduled server restart must not alter the logical period.

## Reward behavior

Quests submit rewards to RewardService.

Examples:

```text
AVECOINS
items
experience
commands
Pokémon
Gacha tickets
future custom rewards
```

Reward claiming must be recoverable if an external provider returns an uncertain result.

## Data-driven definitions

Definitions should be reloadable where safe, but active assignments must remain interpretable even if a definition is edited.

Therefore the persistence design should not rely solely on "load current JSON and hope it still means the same thing."

Possible strategies for architecture review:

- versioned definition IDs;
- immutable definition revisions;
- snapshot relevant assignment fields.

## Diagnostics

Admin diagnostics should eventually provide:

```text
player active quests
period keys
objective progress
claim state
last quest event
last rejected event reason
definition ID/version
recovery-required claims
```

## First Quests milestone

Do not implement all objectives at once.

Recommended first functional slice after Generation Control:

```text
Quest core
data loading
player progress persistence
capture objective
evolution objective
daily period
item reward
currency reward through EconomyPort
manual admin inspection
tests
```

Weekly and advanced battle objectives can follow once the base is proven.
