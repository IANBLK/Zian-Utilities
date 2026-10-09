# Zian Breeding - Work Local implementation and test gates

Status: implementation plan, not yet a playable mod. Date: 2026-10-09.
PR: https://github.com/IANBLK/Zian-Utilities/pull/70

## Locked user choices

- Command: `/zianutilities crianza`, with a NeoForge GUI selecting compatible party parents.
- Breeding starts free, default 24 hours; speedups may reduce elapsed completion target no further than 2 hours from original start.
- No limit on *quantities* of accepted AVECOINS currencies/tickets; a purchase that has no effective time reduction is rejected before debit.
- Multiple denominations: the real set of managed AVECOINS results, not a hardcoded enumeration. Reduction minutes configurable separately per item ID.
- Current example denomination rates are *illustrative and unconfirmed*, not final default values.
- Default: one active breeding, VIP: two through server-side permission checks.
- No physical eggs or runtime Cobbreeding/Incubator JAR dependencies.
- One immutable child identity/outcome per operation, direct Cobblemon PC delivery.
- Minecraft 1.21.1, NeoForge, Java 21, Cobblemon 1.8.1, Youer smoke tests.

## Known blockers before a playable beta

1. Legacy `BreedingOperation.kt` still models a mandatory fee. Refactor into a FREE start and separate journaled optional acceleration purchases.
2. Old `BreedingAccelerationPolicy.kt` models just generic coin/ticket and obsolete rates. Retire, rewrite or delegate to `CurrencyAccelerationRules` so there is ONE production rule engine.
3. Implement real JSON/TOML config loader and validated reload, source of active AVECOINS currency IDs, permissions, Brigadier command, NeoForge menu/network/GUI, party lookup, genetics, PC delivery, persistent operations and journal.
4. Ensure player cannot swap parent identities between GUI selection and confirmation. Store immutable snapshot of both parents; decide parent lock or snapshot policy.
5. Define cancellation and refund rules before coding the UI. Never automatically refund an uncertain debit.
6. Verify the PC mutation's persistence/recovery semantics with hard-kill tests.
7. Verify third-party MIT attribution and inspect any copied assets for additional licenses.

## Suggested release scope

- First beta: free breeding, party compatibility, persistence, direct PC delivery, admin status and crash tests.
- Second beta: configurable AVECOINS acceleration, permissions, UI, audit records.
- Later: previews, notifications, statistics, breeding history, extended options.
- Keep `enabled=false` until runtime and hard-kill gates pass.

## Minimum test matrix

- No compatible parents, same parent twice, removed/swapped parents, Ditto and special offspring rules.
- Gen-restricted/unbreedable species and custom forms.
- Default 1 active request, VIP 2, VIP downgrade with in-flight requests.
- Free 24h request; 2h minimum; assorted currencies; invalid ID, negative/huge quantities; wallet insufficient.
- Config changes must not retroactively alter already purchased reductions.
- Two parallel submissions/purchases; race with logout/stop/restart and clock-boundary completion.
- PC online/full/offline; final child UUID remains unchanged after delivery rejection or restart.
- Graceful panel 6h restarts and kill -9 after every WAL/payment/delivery stage; no duplicates or double charge.
- SavedData schema versioning and migration, WAL replay/corruption, quarantine and admin reconciliation.
- Full build/tests on exact SHA, final NeoForge JAR smoke test on Youer without Cobbreeding/Incubator.

## Git workflow for Work Local

Use branch `feat/zian-breeding-core`, fetch latest from remote. Keep commits small,
push every completed unit to GitHub, inspect GitHub Actions and correct failures
before proceeding. Do not merge into main or call a milestone complete without
a successful CI and applicable server tests. Do not represent this documentation
as a completed runtime integration.
