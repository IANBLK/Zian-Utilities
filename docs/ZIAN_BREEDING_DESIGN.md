# Zian Breeding: paid, party-based breeding without egg items

Status: design + pure JVM domain state machine only. NOT playable yet.
Target: Minecraft 1.21.1, NeoForge, Cobblemon 1.8.1, Youer validation required.

## Player experience

Command: `/zianutilities crianza` opens a server-authoritative GUI.

1. Show only Pokemon in the requesting player's **party**.
2. Pick two distinct compatible parents (female/male; allow Ditto exceptions according to the rules).
3. Show inherited traits and exact configurable AVECOINS item fee, plus time remaining.
4. On explicit confirmation, charge exactly once and create a persistent pending request.
5. Parents remain in the player's party, but the system must define whether they can participate in other requests while active (recommend one active breeding per parent).
6. When the server-side countdown ends, generate one preselected child and deliver directly to the requesting player's Cobblemon PC.
7. If PC is full, remain READY with the same child ID; **never create a new child** on retry.
8. No egg ItemStack, no pastures, no incubator blocks. The original Cobbreeding/Incubator mods are not required at runtime.

## Scope and inheritance

Use Cobbreeding's MIT-licensed breeding logic as a reference, adapted to Cobblemon 1.8.1 data.
Preserve copyright/license notices for any copied/adapted source. Audit assets separately.
Rules must include egg groups, Ditto, undiscovered/unbreedable species, form/offspring exceptions,
gender, nature, IVs, abilities, balls, egg moves, shiny odds and restricted generations.
The eligibility check MUST run again on the server when confirming GUI selection.
A GUI preview is not a security boundary. Validate that parents are still in party,
their identities have not changed and the player still owns them.

## Integration boundary

- Core: pure JVM request and transitions (in `core/.../breeding/`).
- NeoForge: GUI, commands, party lookup, compatibility, Cobblemon PC and event hooks.
- Economy: adapter to AVECOINS 2.3; production integration is **not yet present**
  in Zian Utilities. The existing `docs/reference/avecoins/` is reference-only.
- Persistence: SavedData/NBT for ordinary state plus durable WAL for money and PC mutation.
  Consider extracting journal/store from Zian GTS into generic contracts, not copying
  its trading-specific enums. No new WAL adapter has been integrated in this slice.

## Safety contract

Every breeding request persists a stable operation ID, parent identities, owner,
fee/currency, creation/ready timestamps and an immutable child UUID / outcome.
The selected child properties must be fixed durably BEFORE delivery attempt.
State written before external mutation: PREPARED -> BEFORE_PAYMENT -> PAYMENT_CONFIRMED
-> WAITING -> READY -> BEFORE_DELIVERY -> DELIVERY_CONFIRMED -> COMPLETED.
On UNCONFIRMED/UNCERTAIN payment or delivery: RECOVERY_REQUIRED and fail closed.
Only retry delivery after a proven REJECTED (not applied). Never re-charge blindly.
A bare `pc.add` followed by deleting an egg is insufficient: PC and world
storage are not one atomic transaction. On recovery search Cobblemon storage by
the stable child identity, with a carefully designed persistence boundary; do
not interpret absence in stale/unsaved storage as proof of no prior delivery.
Save ordering and idempotency claims require real hard-kill testing.

## Configuration proposed (not yet implemented)

- `enabled` (default false until fully tested)
- `currencyId` (validated AVECOINS managed item ID)
- `price` (> 0)
- `durationSeconds` (> 0)
- `maxActivePerPlayer`, `maxActivePerParent`
- `cooldownSeconds`, `blockedSpecies`, `allowDitto`
- `respectGenerationControl` (true)
- `shinyOddsMultiplier` (validated bounds)

Timers use persisted absolute timestamps; no fabricated offline progress and no
duplicate completion on startup. PC-full/offline conditions retain the pending
child in an explicit durable state. Support admin inspection/reconciliation.
Default permissions require authorization for GUI, submit and admin recovery.

## Verification gates before enabling gameplay

1. Core unit tests and NeoForge build green.
2. Confirm AVECOINS 2.3 cost with real wallet, low funds and uncertain save.
3. Cobblemon 1.8.1 party/PC: full/offline/reconnect, parents changed, Ditto,
   cross-species compatibility and generated species with generation restriction.
4. Kill the server after each journal stage, charge, child creation, PC insertion,
   PC persistence and completion. Verify no double charge or child duplication.
5. Youer 1.21.1 normal restart (including scheduled 6h stop/start) and hard kill.
6. Verify absence of external Incubator/Cobbreeding JARs at runtime and
   inspect third-party copyright notices for reused source.

Do NOT expose the command as a working feature until these gates pass.
