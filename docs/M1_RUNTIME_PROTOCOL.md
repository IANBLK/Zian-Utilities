# M1-10 runtime compatibility protocol

This document is the execution sheet for issue #10. Automated compilation is not
runtime acceptance. The matrix must be executed on a real game/server runtime.

## Frozen baseline

```text
Minecraft: 1.21.1
Java: 21
NeoForge baseline: 21.1.251
Cobblemon baseline: 1.8.1+1.21.1
Zian Utilities baseline commit: acb47642c9e23c99aff98e75575e328f03d6f928
```

Run NeoForge first. Do not mark Youer accepted until the NeoForge baseline has
passed the required M1 scenarios.

## Diagnostic launch flags

For the validation build, enable:

```text
-Dzianutilities.runtimeTestNatural=true
-Dzianutilities.runtimeTestFishing=true
-Dzianutilities.runtimeTestPokeSnack=true
-Dzianutilities.runtimeTestFinalBarrier=true
```

Remove these flags after acceptance testing. They are intentionally noisy.

## Required order

1. GEN-01 through GEN-10.
2. PERSIST-01 and PERSIST-03. PERSIST-02 is also required on the production-like scheduled-restart environment.
3. NAT-01 through NAT-03.
4. FISH-01 and FISH-02.
5. SNACK-01 and SNACK-02.
6. SAFE-01 through SAFE-08.
7. DIAG-01 and DIAG-02.
8. PERF-01 and PERF-02.
9. Repeat the accepted core subset on Youer as YOUER-01 through YOUER-03.

Habitat remains research-only for this milestone.

## Result record

Copy this block for every scenario:

```text
ID:
Environment: NeoForge | Youer
Minecraft:
Loader/build:
Cobblemon:
Zian Utilities commit:
Active generations:
Actions:
Expected:
Actual:
Relevant log lines:
Result: PASS | FAIL | BLOCKED
Notes:
```

A compile-only result must never be recorded as PASS for a runtime scenario.

## Stop conditions

Stop the matrix and preserve logs/world before continuing if any test shows:

- Pokémon duplication;
- item or currency duplication;
- generation state corruption;
- party/PC/admin/battle/evolution/breeding/GTS interference;
- repeated or recursive spawn actions;
- server crash attributable to Zian Utilities.

## M1 release-candidate gate

M1 may move to release-candidate status only after the required NeoForge baseline
passes and the Youer core subset shows no compatibility regression.
