# M1-06 runtime validation protocol

Target: NAT-01, NAT-02 and NAT-03 for the Natural Spawn Guard.

## Environment

Use a clean NeoForge 1.21.1 test instance first.

Required mods:

- Cobblemon 1.8.1
- KotlinForForge required by Cobblemon
- Zian Utilities build from the runtime-validation branch

Enable the temporary runtime logger with this JVM property:

```text
-Dzianutilities.runtimeTestNatural=true
```

When enabled, every controlled natural PlayerSpawner decision logs either:

```text
[ZIAN-RUNTIME] source=NATURAL decision=ALLOW ...
[ZIAN-RUNTIME] source=NATURAL decision=DENY ...
```

This logger is opt-in. Normal production startup without the property does not emit these INFO lines.

## Recommended location

Use a vanilla Plains or Sunflower Plains biome.

A prior clean-runtime observer session already produced multiple ordinary PlayerSpawner events for common species including Rookidee/Corvisquire (Gen 8), Tandemaus (Gen 9), Yungoos (Gen 7) and Rattata (Gen 1). For the most repeatable initial check, use Gen 8 as the primary target and remain in the same area for the whole test.

Do not use /spawnpokemon, /givepokemon, party send-out, breeding, fishing, Poke Snack or other direct creation routes for NAT-01 to NAT-03. Those are different sources by design.

## Preparation

1. Start the world and join normally.
2. Stand in an open Plains/Sunflower Plains area with normal natural spawning conditions.
3. Run:

```text
/zian generation active
```

4. Disable every generation, then enable only Gen 8:

```text
/zian generation disable gen1
/zian generation disable gen2
/zian generation disable gen3
/zian generation disable gen4
/zian generation disable gen5
/zian generation disable gen6
/zian generation disable gen7
/zian generation enable gen8
/zian generation disable gen9
```

5. Confirm:

```text
/zian generation active
```

Expected state:

```text
gen8
```

Wait long enough for the player's natural PlayerSpawner to be unquestionably active. Move around normally rather than standing on one block. The important part of NAT-03 is that the spawner already existed before Gen 8 is disabled.

## NAT-01: allowed generation

With only Gen 8 active, continue moving around the same natural-spawn area.

Expected log evidence:

```text
[ZIAN-RUNTIME] source=NATURAL decision=ALLOW species=... generations=[GEN_8] active=[GEN_8]
```

Rookidee or Corvisquire are convenient examples if they appear, but any species resolved to Gen 8 is valid.

Pass criteria:

- at least one Gen 8 PlayerSpawner decision is logged as ALLOW;
- the corresponding Pokemon can appear naturally;
- no restart or relog is required.

## NAT-02: blocked generation

Do not leave the test world. Keep the same player/session.

Run:

```text
/zian generation disable gen8
```

Confirm:

```text
/zian generation active
```

Expected state:

```text
ninguna
```

Continue moving through the same area.

Expected evidence:

```text
[ZIAN-RUNTIME] source=NATURAL decision=DENY species=... generations=[GEN_8] active=[] reason=generation_disabled
```

Pass criteria:

- Gen 8 candidates are observed as DENY;
- denied candidates do not enter the world;
- the server/client remains stable.

## NAT-03: cached PlayerSpawner safety

This is intentionally performed immediately after NAT-01 without relogging, restarting or changing dimensions.

The PlayerSpawner was already active while Gen 8 was enabled. Gen 8 was then disabled in the same session.

Continue for several natural spawn attempts.

Pass criteria:

- DENY lines continue after the command change;
- the log shows active=[] (or otherwise the new persisted state) on post-disable decisions;
- no Gen 8 natural spawn slips through because an older PlayerSpawner cached the previous state;
- no relog or restart was necessary.

## Optional second-generation cross-check

After NAT-03, enable Gen 1 without restarting:

```text
/zian generation enable gen1
```

Continue in Plains/nearby Forest terrain. Caterpie is a useful common Gen 1 target in Plains/Forest conditions.

Expected evidence:

```text
[ZIAN-RUNTIME] source=NATURAL decision=ALLOW species=cobblemon:caterpie generations=[GEN_1] active=[GEN_1]
```

This is not required to pass NAT-01 through NAT-03, but it is a useful sanity check that policy changes work both directions.

## Evidence to return

After the test, provide `latest.log` from the instance.

The useful lines are:

```text
[ZIAN-RUNTIME]
[ZIAN-AUDIT]
Generaciones activas:
```

Do not trim the log before sending it. Full context helps distinguish a real guard failure from an unrelated Cobblemon/server event.

## Result classification

Only mark issue #6 runtime-complete when NAT-01, NAT-02 and NAT-03 all have positive log evidence.

Compilation alone is not runtime acceptance, because apparently software enjoys making that distinction expensive.