# Zian Utilities Quests - functional specification

Status: three-hour global quests implemented; first weekly and campaign version implemented in alpha.26.

This document defines product behavior, not the final implementation architecture.

## Purpose

Provide Cobblemon-focused quests that can coexist with Generation Control and use shared services instead of duplicating Cobblemon rules.

Quest families:

```text
Campaign
Weekly
Global three-hour rotation (already implemented)
```

There is no separate daily quest family: the three-hour global rotation already
fills that role. Generation availability grows cumulatively. During the first
15 days only Gen 1 is expected to be active; later generations are added, not
substituted. A campaign becomes available when its generation is enabled and
remains available as more generations unlock.

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

## Weekly quests

Weekly quests use a weekly period key, a server-wide definition, and separate
acceptance, progress and claims for each player. The initial objectives are:

```text
Capture 25 Pokemon eligible under the currently active generations.
Win 50 battles against wild Pokemon (PvP never counts).
```

Only captures after acceptance count. A captured Pokemon UUID and battle UUID
may count once per objective. Capture species must belong to an enabled
generation and be present in Cobblemon's natural world-spawn pool. Progress
survives restarts, resets at the weekly boundary, and pays each objective at
most once per week. The existing three-hour quest can progress from the same
gameplay event; its claim remains independent.

Reward currency and amount must be editable in a server config file for each
weekly objective and campaign chapter, without changing JVM startup arguments
or rebuilding the mod. The three-hour mission already exposes
`capture.currency`, `capture.amount`, `battle.currency`, and `battle.amount` in
the world's `data/zianutilities/global_quest_v1/config.properties`. Weekly
rewards are frozen on the first request of a weekly period; campaign chapter rewards
are frozen when that chapter opens for the player. Editing config never changes
a previously completed or pending claim. Operators can disable quest payments
globally with `quests.rewards.enabled=false` in
`config/zianutilities-features.properties` followed by a server restart.

Example:

`2026-W39`

Configurable behavior should eventually support:

```text
weekly reset day and time (alpha.26 uses Monday 00:00 America/Guayaquil)
reward amount for each objective
```

Campaigns are permanent per player and per generation. The Gen 1 campaign is
available as soon as Gen 1 is enabled. Each later generation campaign unlocks
when that generation becomes active. Campaign capture objectives draw only
from naturally spawning Pokemon in their own generation. No campaign progress
resets when a weekly or three-hour period ends. The first campaign design has
three sequential chapters per generation: capture 3, then 8, then 15 distinct
species of that generation. A species counts once within its chapter. Captures
before accepting the campaign or before a chapter unlocks do not count. Each chapter
has its own durable, one-time reward claim. The next chapter opens automatically
after the previous chapter's reward is resolved.

In alpha.26, the server creates
`world/data/zianutilities/progression_quests_v1/config.properties` with separate
currency and amount keys for both weekly objectives and all three campaign
chapters. Default rewards are 5/10 copper coins weekly and 5/10/15 copper coins
for campaign chapters. Weekly values are frozen in that week's `offer.properties`
on first access. A campaign freezes each chapter's values when that chapter
opens. Edit the config between periods/chapters; restarting the server is not
required for the next new period/chapter to see the saved file.

On a test server started with `-Dzianutilities.globalQuestTestEnabled=true`,
`/zian quest test weekly rotate` starts a new no-payment weekly rehearsal without
moving the real Monday reset. It clears the visible weekly progress for all
players by changing the test period key; prior reward claims are untouched.
The next real week ignores the test key and pays according to its normal rules.

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
three-hour global period
item reward
currency reward through EconomyPort
manual admin inspection
tests
```

Weekly and campaign objectives can follow once the base is proven.
