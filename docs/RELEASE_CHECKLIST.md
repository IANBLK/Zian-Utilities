# Release checklist

## Alpha

An alpha may be published when:

- project compiles on Java 21;
- automated tests pass;
- the specific feature under test starts on NeoForge 1.21.1;
- known limitations are documented;
- artifact is clearly marked alpha;
- no claim of Youer compatibility is made until tested.

## Beta

A beta requires:

- Milestone acceptance tests passed on NeoForge;
- targeted Youer tests passed;
- normal restart persistence passed;
- scheduled restart behavior passed;
- no known duplication/external-operation replay bug;
- upgrade/migration path tested from previous public version where relevant;
- changelog complete;
- debug logging is bounded/disableable.

## Stable

Stable requires:

- all advertised features have runtime acceptance evidence;
- Youer target build validated if advertised;
- no open critical data-loss, duplication or economy-consistency bug;
- crash/recovery tests for transactional modules;
- migration tests;
- permissions/admin commands reviewed;
- documentation matches actual commands/config;
- downloadable artifact hash produced;
- release notes list supported Minecraft/NeoForge/Cobblemon/AVECOINS versions.

## Never infer stability from compilation alone

A successful Gradle build proves the project compiled.

It does not prove:

- runtime startup;
- event coverage;
- persistence correctness;
- Youer compatibility;
- economy transaction safety;
- absence of duplication.
