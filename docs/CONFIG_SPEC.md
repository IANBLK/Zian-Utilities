# Zian Utilities configuration specification

Status: product/configuration requirements. This does not lock the final Java/Kotlin implementation.

## Goals

Configuration should be:

- understandable by server administrators;
- modular;
- reloadable where safe;
- explicit about restart-required settings;
- versioned where schema evolution matters;
- separated by feature so one large file does not become unmaintainable.

## Suggested configuration layout

```text
config/zianutilities/
├── core.toml
├── generation.toml
├── quests.toml
├── gacha.toml
├── economy.toml
├── diagnostics.toml
└── data/
    ├── quests/
    │   ├── campaigns/
    │   ├── daily/
    │   └── weekly/
    ├── gacha/
    │   └── pools/
    └── rewards/
```

Exact file names/formats may change after architecture review.

## Core configuration

Potential settings:

```toml
schemaVersion = 1

[modules]
generation = true
quests = false
gacha = false
equipment = false
enchantments = false

[server]
timezone = "America/Guayaquil"
```

Module enable/disable state should be evaluated at startup unless the implementation can prove hot-disable is safe.

## Generation configuration

Conceptual fields:

```toml
schemaVersion = 1

unknownSpeciesPolicy = "DENY"

[generationMapping]
gen7b = "GEN_7"
gen8a = "GEN_8"

[manualOverrides]
# "namespace:species" = "GEN_3"

[diagnostics]
logDeniedSpawns = false
logUnknownSpecies = true
```

Generation state itself should not live only in config if it must be changed at runtime and persisted independently.

Config defines policy/defaults.
Persistent state defines current enabled/disabled generations.

## Quests configuration

Global behavior:

```toml
schemaVersion = 1

[daily]
enabled = true
rotation = "PERSONAL"
questCount = 3
resetTime = "00:00"

[weekly]
enabled = true
rotation = "PERSONAL"
questCount = 3
resetDay = "MONDAY"
resetTime = "00:00"

[selection]
respectEnabledGenerations = true
avoidImpossibleTargets = true
```

Quest definitions should live in data files, not in the main TOML.

## Quest definition example

Conceptual only:

```json
{
  "schemaVersion": 1,
  "id": "daily_capture_water",
  "category": "daily",
  "weight": 20,
  "requirements": {
    "enabledGenerations": "ANY"
  },
  "objectives": [
    {
      "type": "capture",
      "amount": 5,
      "filters": {
        "type": "water",
        "generation": "ENABLED"
      }
    }
  ],
  "rewards": [
    {
      "type": "currency",
      "currency": "avecoins",
      "amount": 300
    }
  ]
}
```

## Gacha configuration

Global behavior:

```toml
schemaVersion = 1

enabled = false
historyLimit = 100
allowCurrencyPayment = true
allowTicketPayment = true
```

Pools should live in separate data files.

Conceptual pool:

```json
{
  "schemaVersion": 1,
  "id": "standard",
  "enabled": true,
  "cost": {
    "type": "currency",
    "currency": "avecoins",
    "amount": 1000
  },
  "pity": {
    "hard": 50
  },
  "entries": [
    {
      "id": "iron_bundle",
      "weight": 100,
      "rewards": [
        {
          "type": "item",
          "item": "minecraft:iron_ingot",
          "amount": 8
        }
      ]
    }
  ]
}
```

## Economy configuration

Potential settings:

```toml
schemaVersion = 1

provider = "avecoins"
requiredVersion = "2.3"
defaultCurrency = "avecoins"
failClosed = true
```

Do not place secrets in config.

## Diagnostics configuration

Potential settings:

```toml
schemaVersion = 1

enabled = false
logSpawnDecisions = false
logQuestEvents = false
logRewardClaims = true
logEconomyMutations = true
logGachaOperations = true
includePlayerNames = false
```

Prefer UUIDs in durable logs/audit records.

## Reload policy

Classify settings:

```text
HOT_RELOAD_SAFE
RELOAD_WITH_VALIDATION
RESTART_REQUIRED
```

Examples:

- quest definition weights: potentially reloadable;
- module enable/disable: likely restart-required initially;
- generation mapping: reload only with validation;
- economy provider: restart-required;
- diagnostics flags: hot-reload safe.

## Validation

Invalid config should fail clearly.

Do not silently coerce:

- negative reward amounts;
- unknown generation names;
- duplicate IDs;
- impossible reset times;
- invalid currency IDs;
- malformed pool weights.

Validation errors should include file path + logical ID + field.
