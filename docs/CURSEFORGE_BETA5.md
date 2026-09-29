# CurseForge copy — Zian Utilities 0.1.0 Beta 5

## Summary

Cobblemon server utilities with generation control, quests, ticket-based gachas, Prismatic equipment, and a command-only 3×3 mining enchantment.

## Description

# Zian Utilities

**Zian Utilities** brings progression and server tools to **Cobblemon on Minecraft 1.21.1**. Control which Pokémon generations appear, offer shared and personal quests, build ticket-based gachas, and add a Prismatic equipment set. The mod is **in active beta development**; please back up existing worlds before updating.

### Pokémon generation control

Server operators can enable generations as the server progresses. Enabled generations stay available while later generations are added. The control applies to natural spawns, fishing, and Poké Snack encounters; it does not remove Pokémon that players already own.

### Quests and campaigns

- **Every three hours:** the server offers shared capture and wild-battle objectives. Players accept objectives and track their own progress.
- **Weekly:** capture 25 Pokémon and win 50 battles against wild Pokémon. The weekly cycle resets on Monday.
- **Generation campaigns:** collect distinct eligible species from each generation across persistent stages. The campaign shows captured species and targets half of the eligible species for its final goal.

Quest rewards use AVECOINS. Server owners can adjust reward values in the generated configuration files before opening the next quest window.

### Ticket-based gachas

Operators can create, name, publish, deactivate, and delete prize pools. Each pool has a ticket type, cost, weighted prizes, and visible odds. A roll plays a roulette animation; the server validates the payment and prize. Prizes that do not fit in the inventory remain available to claim. Interrupted deliveries are kept in a journal for careful operator review.

### Prismatic equipment and mining

The dedicated **Zian Utilities | Equipment** creative tab contains Prismatic armor, a sword, axe, pickaxe, shovel, and hoe. The set has no crafting recipes and supports normal compatible enchantments. Its armor is based on netherite with increased defense, toughness, and knockback resistance. The equipment artwork is credited to **Inkless Studios** and may be updated during beta development.

The **3×3 Mining** enchantment works on pickaxes and is intentionally available **only through an operator command**. It does not appear in enchanting tables, villager trades, or world loot.

### Commands

| Command | Purpose |
| --- | --- |
| `/zian` or `/zian menu` | Open the main menu. |
| `/zian gacha` | Open the gacha screen. |
| `/zian generation active` | List enabled generations. |
| `/zian generation enable gen1` | Enable a generation; replace `gen1` as needed. |
| `/zian generation disable gen1` | Disable a generation if the server operator needs to revise the schedule. |
| `/zian gacha review <player>` | Operator: inspect unresolved rolls for an online player. |
| `/zian gacha confirm-delivered <player> <operationId>` | Operator: close an interrupted delivery **only after verifying that the player received the prize**. |
| `/enchant <player> zianutilities:mineria_3x3 1` | Operator: apply 3×3 Mining to a compatible pickaxe. |

Quest acceptance, progress, claiming, and gacha editing are available in the in-game menus.

### Requirements and setup

- **Minecraft:** 1.21.1
- **Java:** 21
- **Mod loader:** NeoForge 21.1.x
- **Cobblemon:** 1.8.1
- **AVECOINS:** 2.3 or 2.4 for quest payments and gacha tickets

Install the **same Zian Utilities JAR** on the server and every client, and remove older copies. Install Cobblemon and its required dependencies. On first launch, the mod creates `config/zianutilities-features.properties`; quests, rewards, gachas, and gacha payments can be enabled or disabled there with the server stopped. No special startup arguments are required.

The core progression, persistence, payments, and gachas have been tested on a **Youer 1.21.1** server. Beta 5 passes the automated build and tests; its new roulette layout and recovery commands still need in-game validation. AVECOINS versions newer than 2.4 are checked for compatibility rather than accepted automatically.

### Feedback

If you find a bug, include your Minecraft, NeoForge, Cobblemon, AVECOINS, and Zian Utilities versions, plus the relevant log excerpt. Please do not delete unresolved gacha journal files: they may be needed to determine whether a ticket was charged or a prize was delivered.

## Beta 5 changelog

- Fixed gacha roulette text overlapping the animation at GUI scale ×2 by displaying the spin in its own modal view.
- Removed all five elemental swords and their assets; only the Prismatic equipment set remains in the creative tab.
- Added `/zian gacha review <player>` to inspect unresolved rolls.
- Added `/zian gacha confirm-delivered <player> <operationId>` for operator-confirmed recovery of an interrupted prize delivery.
- Safely retires rolls interrupted before any ticket debit and reduces noisy stack traces for expected review states.
- Previous Captura, Explorador, Campeón, and Elemental Sword item IDs are no longer registered. Back up worlds containing these items before updating.

**Validation:** Gradle build and automated tests passed. The new GUI-scale layout and recovery flow await in-game beta testing.
