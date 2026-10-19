# Zian Utilities

**Language notice:** The current in-game interface, preset dialogues and administrator messages are primarily Spanish. English localization is incomplete. The English guide explains the controls and includes their Spanish button names. Changing Minecraft to English does not translate administrator-written NPC names, dialogues or configuration text.

[English user and administrator guide](https://github.com/IANBLK/Zian-Utilities/blob/main/docs/GUIDE.md) · [Guía en español](https://github.com/IANBLK/Zian-Utilities/blob/main/docs/GUIA_ES.md)

Manage Pokémon generations, breed compatible party Pokémon, run recurring quests and offer configurable Gachas from a responsive in-game hub.

Breeding displays both parents and estimated offspring with hover details. Players can inspect jobs, accelerate waiting with configured currencies and claim completed offspring to their PC. Breeding eligibility is independent from generation-based natural spawn restrictions.

Gachas open as a named catalog with configurable icons and descriptions. Prize probabilities are visible before spending a ticket. The hub includes Missions, Gachas and Breeding cards, with layouts improved for GUI scales 3 and 4.

Administrators can limit flying Pokémon mount speed and maximum stamina without changing player fly permissions. Disabling these limits restores normal riding; it does not prohibit flight.

A command-only 3x3 mining enchantment lets compatible pickaxes mine an area while respecting player block-breaking permissions, drops and durability. It is not added to normal enchanting or loot.

Custom bows accept durability enchantments and have distinct arrow-damage bonuses. Problematic Reforged armor is retired; existing pieces convert to vanilla equivalents with item components preserved. Weapons remain.

Install on client and server: Minecraft 1.21.1, Java 21, NeoForge 21.1.x, Cobblemon 1.8.2 and Kotlin for Forge 5.10 or compatible newer 5.x. AVECOINS 2.3/2.4/2.5 is optional for supported currency features.

Durable progress supports optional SQLite migration and safe administrative maintenance. Back up the world before upgrades or migration.

## Español

Controle generaciones, críe Pokémon, configure misiones y Gachas y limite velocidad/estamina de monturas. La crianza no depende del bloqueo de generaciones. Instale en cliente y servidor con Cobblemon 1.8.2. Respalde antes de actualizar.

## Latest update — 0.1.2

New default breeding jobs take 12 hours. Eight coin/ticket options are available in a paginated selector. The cost, balance and waiting-time reduction are shown before acceleration. Only the previous default is migrated; existing jobs and custom settings are preserved. Added /crianza, readable operation references, and matching permission checks in commands and network handlers.

## Novedades — Español

Crianza nueva de 12 horas, ocho monedas/tickets y selector paginado. Se muestran coste, saldo y reducción antes de acelerar. Conserva trabajos existentes y configuraciones personalizadas. Alias /crianza y referencias de operaciones legibles.

Minecraft 1.21.1 · NeoForge · Java 21. Install the same mod version on client and server. Back up your world before upgrading; replace the old JAR instead of keeping both copies.
