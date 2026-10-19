# Zian Utilities 0.1.2 — English guide

**Language notice:** The current in-game interface, preset dialogues and administrator messages are primarily Spanish. English localization is incomplete. The English guide explains the controls and includes their Spanish button names. Changing Minecraft to English does not translate administrator-written NPC names, dialogues or configuration text.

## Players: hub and quests

Open `/ZianUtilities`, then **Misiones** (Quests), **Gachas** or **Crianza** (Breeding). Accept an available quest, inspect its objective/progress, complete the activity and claim when eligible. Global, weekly and generation campaigns retain individual progress; availability depends on server configuration. Installing the mod does not invent a missing server catalog.

## Players: breed Pokémon

1. Keep compatible parents in your party. Open **Crianza**, `/ZianUtilities crianza` or `/crianza` when available.
2. Choose both parents; inspect compatibility and offspring preview. Start only an eligible pair.
3. New default jobs take **12 hours**. Existing jobs and administrator-customized durations are preserved. Waiting is free by default.
4. Acceleration is optional. The paginated selector offers eight configured coin/ticket choices. Inspect cost, balance and time reduction before payment.
5. Claim the finished offspring to the PC. A full PC/interrupted delivery may leave a pending result; keep its reference instead of repeatedly claiming to force a second offspring.

Breeding eligibility is independent of generation spawn restrictions. The server decides compatibility and results.

## Players: Gachas

Choose a named catalog, inspect rewards/probabilities and use the configured ticket. The reserved result survives interruption without a free reroll. Catalogs, tickets and rewards are administrator-defined. AVECOINS is optional for supported currency actions, not for every non-economic feature.

## Administrators: generations and mount policy

`/ZianUtilities generation active` inspects active generations; `generation enable gen1` is an enabling example (prefix with `/ZianUtilities`). Restrictions control natural, fishing and Poké Snack spawning, without erasing owned Pokémon.

`/ZianUtilities vuelo ver` inspects the mount policy. `vuelo velocidad 65` sets speed to 65%; `vuelo estamina 60` caps maximum stamina at 60%, not its spending rate. Disabling the policy restores ordinary riding; it does not prohibit flight or change `/fly` permissions. Use administrative command completion for configuration; do not grant players an admin wildcard.

## Equipment and saved work

The catalog includes themed weapons and bows. `zianutilities:mineria_3x3` is a command-only pickaxe enchantment, not normal enchanting/loot; it respects break permissions, durability and drops. Retired Reforged armor converts to vanilla equivalents with components retained; weapons remain. Current breeding defaults migrate without rewriting custom durations. Preserve jobs, reserved rewards and receipts. Cobbreeding's MIT attribution and separate artwork notices remain included.

## Installation, updating and recovery

Use Minecraft **1.21.1**, **Java 21** and **NeoForge**, with the separate required libraries listed in the README. Stop the server and back up the entire world, configuration and mod list before replacing a version. Install the same current JAR on the server and every client. Remove the previous JAR, keep the required libraries on both sides, start and inspect the log, then verify your existing configuration and progress. For rollback, stop and restore the complete matching backup and mod versions together. Do not edit schema numbers to force migration.

Youer has been tested; this does not guarantee every hybrid server/plugin combination. Command-whitelist plugins may separately block public commands. Allow the needed public roots while keeping administrative operations restricted. LuckPerms is optional; administrator configuration remains protected.

Keep world data and configuration together. Optional SQLite maintenance is explicit, not required automatically by an update. Never edit a live database or delete a journal to clear an error. For an uncertain payment/delivery, keep its reference, reconcile the recorded outcome with inventory/PC/wallet state and use the administrator review flow. Confirmation records a verified result; it must never be used to guess or generate a second reward.

Report the mod/library versions, reproduction steps and sanitized logs through this repository's Issues. Do not upload credentials, tokens, player databases or private world backups.
