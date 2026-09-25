# M1 pure NeoForge baseline checklist

Purpose: close the formal M1 release-candidate gate without mixing Youer-specific behavior into the reference-loader result.

## Frozen environment

```text
Minecraft: 1.21.1
Java: 21
NeoForge: 21.1.251
Cobblemon: 1.8.1
Zian Utilities: use the final audited M1 candidate build
```

Use a disposable test world/server. Do not run abnormal-stop testing against the production world.

## Diagnostic JVM flags

Enable only for the relevant diagnostic pass:

```text
-Dzianutilities.runtimeTestNatural=true
-Dzianutilities.runtimeTestFishing=true
-Dzianutilities.runtimeTestPokeSnack=true
-Dzianutilities.runtimeTestFinalBarrier=true
-Dzianutilities.runtimeTestPreselection=true
```

Remove them after acceptance testing. Candidate-level preselection logging is intentionally very noisy.

## Reduced execution sequence

### A. Generation state

1. Start with no active generations and confirm `/zian generation active`.
2. Enable Gen3 and confirm status.
3. Observe an allowed Gen3 natural spawn.
4. Disable Gen3 and enable Gen4 without restart.
5. Confirm new controlled natural spawns follow Gen4.
6. Enable a second generation and confirm both active generations are reported.
7. Return to the desired clean state.

### B. Fishing

1. With one generation active, fish until at least one valid encounter completes.
2. Confirm PRE/POST diagnostics and final species generation.
3. Disable that generation without restart and enable another.
4. Fish again and confirm the new state is used.
5. Disable all generations and confirm fishing does not leak a blocked Pokémon.
6. Confirm no stuck bobber after denied/empty selection.

### C. Poké Snack

1. Place a snack with one generation active and confirm a valid encounter.
2. Place another snack/spawner, change active generation without restart, and let the existing snack continue.
3. Confirm its eventual spawn follows the current generation state.
4. Disable all generations and confirm no blocked Pokémon is produced.

### D. Safety smoke test

Exercise the available paths:
- existing Pokémon remain usable;
- party send/receive;
- PC deposit/withdraw;
- admin `/givepokemon`;
- battle;
- evolution;
- breeding if a breeding system is installed;
- GTS receive if Zian GTS is installed in this baseline.

A missing optional subsystem is recorded N/A/BLOCKED, never PASS.

### E. Persistence

1. Enable a distinctive generation set, for example Gen2 + Gen7.
2. Perform a normal stop/start and confirm the set persists.
3. For PERSIST-03 only, use a disposable world/server: change state, allow the state mutation to complete, terminate the server abnormally, restart, and verify the saved state is coherent.
4. Preserve the log and world if state corruption appears.

The production-like scheduled restart (PERSIST-02) belongs on Youer/Pterodactyl using its normal stop -> wait -> start schedule.

### F. Diagnostics and sanity

1. Verify diagnostic-off startup does not emit candidate spam.
2. Enable diagnostics and verify useful source/candidate records appear.
3. Exercise repeated fishing/snack denies and several players if available.
4. Watch for recursion, repeated actions, duplication, runaway allocations or Zian-attributed exceptions.

## Immediate stop conditions

Stop testing and preserve logs/world on:
- Pokémon, item or currency duplication;
- generation state corruption;
- party/PC/admin/battle/evolution/breeding/GTS interference;
- recursive/repeated spawn actions;
- Zian Utilities attributable crash.

## Evidence to keep

For every failed or important boundary scenario, retain:
- exact Zian Utilities commit/JAR;
- loader and Cobblemon versions;
- active generation set;
- command/action sequence;
- relevant log lines;
- expected vs actual result.

Do not declare M1 RC from compilation or Youer-only evidence. The reference NeoForge baseline is the remaining formal loader gate.
