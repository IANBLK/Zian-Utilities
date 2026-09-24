# Project conventions

These conventions are safe to adopt before the final production architecture is approved.

## Identity

```text
Project: Zian Utilities
Repository: IANBLK/Zian-Utilities
Mod ID: zianutilities
Base package (provisional): com.zianblk.zianutilities
```

The package layout below the base package remains pending architecture review.

## Baseline

```text
Minecraft 1.21.1
Java 21
NeoForge 1.21.1
Cobblemon 1.8.1 initial baseline
Youer 1.21.1 final server validation
```

## Repository rules

- UTF-8 text.
- No generated build output committed.
- No runtime worlds/logs committed.
- Do not commit third-party JARs unless licensing and redistribution explicitly allow it.
- Do not copy implementation code from archived Generation Spawns into production sources.
- Preserve external integration samples under `docs/reference/` until intentionally promoted into new code.

## Branches

Suggested workflow:

```text
main
feature/<short-name>
fix/<short-name>
research/<short-name>
docs/<short-name>
```

Use short-lived branches once production coding begins.

## Commits

Suggested prefixes:

```text
feat:
fix:
test:
docs:
refactor:
build:
ci:
chore:
research:
```

## Version channels

Plan releases with explicit stability:

```text
alpha
beta
stable
```

Do not call a build stable until runtime testing passes on the intended server platform.

## Evidence language

Technical documents should distinguish:

```text
CONFIRMED IN JAR
INFERRED FROM BYTECODE
RUNTIME VERIFIED
NEEDS RUNTIME VERIFICATION
PRODUCT DECISION
```

Do not silently promote an inference into a verified runtime fact.
