# Milestone 1 runtime test matrix

Target environments:

1. NeoForge 1.21.1
2. Youer 1.21.1 after NeoForge validation

Cobblemon baseline:

`1.8.1`

## Rules

- Use a fresh test world for baseline testing.
- Record enabled generations before every scenario.
- Record server log around every test.
- Reproduce every failure at least twice before classifying it.
- Do not use a global EntityJoinLevelEvent cancellation as a shortcut during these tests.
- Verify both allowed and denied cases.
- After restart tests, verify state by command and by observed spawn behavior.

## Matrix

| ID | Area | Scenario | Expected |
|---|---|---|---|
| GEN-01 | Initial state | Fresh world, no generations enabled | no controlled generation spawns |
| GEN-02 | Commands | enable gen1 | Gen 1 becomes active |
| GEN-03 | Commands | disable gen1 | Gen 1 becomes inactive |
| GEN-04 | Commands | active/list | state reported correctly |
| GEN-05 | Runtime | enable generation without restart | new allowed spawns begin |
| GEN-06 | Runtime | disable generation without restart | new blocked spawns stop |
| GEN-07 | Combination | enable gen1 + gen3 only | only those generations allowed |
| GEN-08 | Special labels | gen7 behavior | gen7b follows configured Gen 7 mapping |
| GEN-09 | Special labels | gen8 behavior | gen8a follows configured Gen 8 mapping |
| GEN-10 | Unknown | species without known generation | configured unknown policy applied |
| PERSIST-01 | Restart | normal server stop/start | generation state survives |
| PERSIST-02 | Restart | scheduled restart | generation state survives |
| PERSIST-03 | Crash recovery | forced abnormal stop in test env | state not corrupted |
| DIM-01 | Dimensions | Overworld | global generation state applies |
| DIM-02 | Dimensions | Nether/custom dimension | same global state applies |
| NAT-01 | Natural | allowed generation natural spawn | spawn succeeds |
| NAT-02 | Natural | blocked generation natural spawn | spawn denied |
| NAT-03 | Cache timing | disable after player spawner already active | blocked generation remains denied |
| FISH-01 | Fishing | allowed generation | Pokémon can be fished |
| FISH-02 | Fishing | blocked generation | Pokémon spawn denied cleanly |
| SNACK-01 | Poké Snack | allowed generation | spawn succeeds |
| SNACK-02 | Poké Snack | blocked generation | spawn denied cleanly |
| HAB-01 | Habitat activated | activate habitat with allowed generation | observe full pipeline |
| HAB-02 | Habitat activated | blocked generation | verify safe cancellation |
| HAB-03 | Habitat natural | habitat influences natural spawn | trace source and final event |
| HAB-04 | Habitat fishing | habitat affects fishing | trace source and final event |
| SAFE-01 | Existing entity | Pokémon already in world when generation disabled | remains unaffected |
| SAFE-02 | Party | send Pokémon from party | unaffected |
| SAFE-03 | PC | withdraw Pokémon from PC | unaffected |
| SAFE-04 | Admin command | /givepokemon or equivalent | unaffected |
| SAFE-05 | Battle | battle Pokémon entities | unaffected |
| SAFE-06 | Evolution | evolve Pokémon | unaffected |
| SAFE-07 | Breeding | obtain Pokémon/egg through breeding | unaffected |
| SAFE-08 | GTS | receive Pokémon through Zian GTS | unaffected |
| DIAG-01 | Logging | blocked spawn with debug off | no spam |
| DIAG-02 | Logging | debug on | useful source/species/reason data |
| PERF-01 | Load | multiple players spawning naturally | no obvious tick degradation |
| PERF-02 | Load | repeated denied generations | resolver/filter does not allocate excessively |
| YOUER-01 | Youer | repeat core command tests | behavior matches NeoForge |
| YOUER-02 | Youer | repeat natural/fishing/snack tests | behavior matches NeoForge |
| YOUER-03 | Youer | GTS + Generation Control | no cross-system interference |

## Habitat acceptance

Habitat tests are currently research/compatibility tests.

Habitat does not block the first Milestone 1 release candidate until HAB-01 through HAB-04 establish its event path and safe interception strategy.

## Evidence to capture per scenario

Record:

```text
Minecraft version
NeoForge/Youer build
Cobblemon version
Zian Utilities commit/version
active generations
test command/actions
expected result
actual result
relevant log lines
pass/fail
notes
```

## Milestone 1 acceptance

A first usable Milestone 1 candidate should not be considered ready until:

- Generation state and commands pass.
- Persistence passes normal restart.
- Natural spawning passes allowed/denied tests.
- Fishing passes allowed/denied tests.
- Poké Snack passes allowed/denied tests.
- Safety tests confirm party/PC/admin/GTS/etc. are not blocked.
- No known duplication or repeated-action issue is observed.
- NeoForge baseline passes before Youer-specific approval.
