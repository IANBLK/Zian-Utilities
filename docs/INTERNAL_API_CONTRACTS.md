# Internal API contracts

Status: conceptual service boundaries for architecture review.

These are behavioral contracts, not final Java/Kotlin signatures.

## Design goal

Feature modules should depend on stable capabilities, not concrete storage/event implementations.

## GenerationService

Responsibilities:

```text
query enabled generations
enable/disable generation
evaluate generation policy
expose immutable/read-only state snapshots
notify interested modules of state changes if needed
```

Should not know how Cobblemon emits spawn events.

## GenerationResolver

Responsibilities:

```text
resolve a species identity to a generation
handle gen7b/gen8a product mapping
apply manual overrides
return UNKNOWN explicitly
```

Cobblemon-specific implementation may depend on Cobblemon Species.

The core contract should avoid forcing every consumer to depend on Cobblemon internals where practical.

## SpawnGuard integration

Responsibilities:

```text
receive source-specific spawn context
resolve species generation
consult generation policy
allow/deny
emit diagnostics
```

Source adapters:

```text
natural
fishing
poke snack
habitat
future/addon
```

The generation domain must not depend on those adapters.

## QuestService

Future responsibilities:

```text
get active assignments
apply canonical quest events
read progress
claim rewards
resolve period/rotation
admin inspection/reset
```

Quest listeners should convert Cobblemon events into internal canonical events before modifying player progress.

Example conceptual canonical events:

```text
PokemonCaptured
PokemonEvolved
PokemonLeveled
BattleWon
EggHatched
```

This prevents the quest core from subscribing directly to every external event bus.

## RewardService

Responsibilities:

```text
validate reward definitions
create/continue durable claim
deliver reward components
return explicit result
expose recovery state
audit delivery
```

Consumers should not directly manipulate AVECOINS or reward-specific storage.

## EconomyPort

Responsibilities:

```text
affordability/balance query
withdraw
deposit
currency/provider identification
explicit Applied/Rejected/Uncertain result
stable operation ID support
```

AVECOINS is one adapter.

## GachaService

Future responsibilities:

```text
list available pools
validate roll
create durable operation
process payment
resolve reward once
deliver through RewardService
update pity
expose operation/history/recovery state
```

Gacha should not contain AVECOINS-specific implementation logic.

## AuditService

Potential shared responsibility:

```text
record important administrative/transactional events
search by operation/player/module
bounded retention/configuration
```

Whether this becomes a dedicated service or structured logging layer is an architecture decision.

## Clock / TimeProvider

Quests and scheduled logic benefit from an injectable clock abstraction.

Responsibilities:

```text
current Instant
configured server zone
derive daily period key
derive weekly period key
```

This makes Daily/Weekly logic unit-testable without waiting for Monday like medieval peasants.

## Persistence boundaries

Prefer module-owned repositories/stores behind interfaces rather than one giant "DatabaseManager".

Conceptual:

```text
GenerationStateStore
QuestStateStore
RewardClaimStore
GachaOperationStore
GachaPityStore
```

Actual implementation may share storage infrastructure, but ownership and schemas remain explicit.

## Dependency direction

Desired direction:

```text
external integrations
        ↓
adapters
        ↓
service contracts / domain
        ↑
feature modules
```

Avoid:

```text
QuestCore -> Cobblemon event bus
GachaCore -> AVECOINS WalletStore
Generation domain -> NeoForge event classes
```

## Public API policy

Do not expose a public third-party API merely because internal interfaces exist.

First stabilize internal contracts.

A documented external API can be introduced later once compatibility/versioning policy exists.
