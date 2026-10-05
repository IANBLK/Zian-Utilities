**✨ ZIAN UTILITIES — MORE PROGRESSION FOR YOUR COBBLEMON SERVER**

Bring together Pokémon generation control, recurring quests, customizable gachas, and custom equipment in one in-game hub.

Designed for **Minecraft 1.21.1**, **NeoForge 21.1.x**, and **Cobblemon 1.8.1**, with testing on **Youer 1.21.1**.

**🌍 CONTROL POKÉMON GENERATIONS**

Administrators choose which generations are enabled as their server progresses.

Generation control filters supported natural spawns, fishing encounters, and Poké Snack spawns. Pokémon that players already own or that already exist in the world are retained.

**📜 QUESTS THAT KEEP YOUR SERVER MOVING**

**Every three hours:** shared capture and wild-battle objectives, with individual acceptance, progress, and rewards. Eligible Pokémon targets rotate with the quest window.

**Weekly challenges:** capture **25 eligible Pokémon** and win **50 battles against wild Pokémon**. Each objective has its own reward, and the weekly cycle resets on Monday.

**Generation campaigns:** register distinct eligible species and progress through three persistent stages. The campaign target is fixed when accepted and is based on half of the generation’s naturally spawning species.

Players can inspect their registered species and retain campaign progress while more generations are enabled. Quest progress persists across server restarts.

Quest rewards integrate with **AVECOINS**. Objectives completed while rewards are disabled are recorded as completed without payment; re-enabling rewards does not automatically backpay those objectives.

**🎰 CREATE YOUR OWN GACHAS**

Administrators can create and publish prize pools through the in-game editor, with item rewards, visible probabilities, and an AVECOINS ticket cost.

The animated reel reveals the reward before delivery. The server schedules delivery with an additional one-second wait after the animation’s intended end; network latency may slightly affect the visible timing.

Hover over prize icons or names to inspect Minecraft’s normal item information, including configured enchantments and attributes. Tooltips are also available in the editor and for pending prizes and ticket icons.

If the inventory is full, the reserved reward remains available to claim. Disconnects or restarts retain reserved prizes for later manual claim.

**⚔️ CUSTOM EQUIPMENT**

Discover the **Prismatic** and **Dark Reaper** equipment sets, each with a helmet, chestplate, leggings, boots, sword, axe, pickaxe, shovel, and hoe.

Both sets are enchantable and fire resistant. Each armor piece provides **+1 armor, +1 toughness, and +0.05 knockback resistance** compared with its netherite counterpart. Tools use an enhanced netherite tier.

Four custom bows — **Curve, Regular, Long, and Harp** — include drawing animations, **2032 durability**, and **+1 arrow base damage**.

Equipment appears in its own creative tab. There are no crafting recipes or added natural loot sources. Textures are bundled in the mod; ItemsAdder, Oraxen, and a separate resource pack are not required.

Equipment visuals may be refined during development. Included third-party artwork retains its creators’ rights; see the [asset credits](https://github.com/IANBLK/Zian-Utilities/blob/feat/brand-command-root/THIRD_PARTY_ASSETS.md).

**⛏️ COMMAND-ONLY 3×3 MINING**

The **zianutilities:mineria_3x3** enchantment allows compatible pickaxes to mine a 3×3×1 area.

It is available only through commands and does not appear in enchanting tables, villager trades, or generated loot.

With a compatible pickaxe held, an administrator can use **/enchant <player> zianutilities:mineria_3x3 1**.

Additional blocks use normal player block-breaking checks, drops, and durability. Area mining skips unsupported blocks and does not run if the original break is cancelled.

**⌨️ MAIN COMMANDS**

**/ZianUtilities** — Open the main hub.

**/ZianUtilities menu** — Open the main hub.

**/ZianUtilities quest** — Open quests.

**/ZianUtilities gacha** — Open gachas.

**/ZianUtilities generation active** — View enabled generations.

**/ZianUtilities generation status [generation]** — View generation information.

**/ZianUtilities generation enable gen1** — Enable Generation 1; administrators can replace gen1 with another supported generation ID.

**/ZianUtilities generation disable gen1** — Disable the specified generation.

**/ZianUtilities gacha review <player>** — Administrative inspection of pending or uncertain rolls.

**/ZianUtilities gacha confirm-delivered <player> <operationId>** — Confirm an interrupted delivery only after verifying that the reward was received.

The old **/zian** root has been removed. Update scripts and menu buttons to use **/ZianUtilities**, preserving capitalization. Administrative generation and recovery actions remain protected by their server-side permission checks.

Additional debit-resolution and compensation commands are documented in the [recovery guide](https://github.com/IANBLK/Zian-Utilities/blob/feat/brand-command-root/docs/BETA6_RECOVERY.md). These record verified administrative decisions and do not determine transaction outcomes automatically.

**⚙️ CONFIGURATION**

The server creates **config/zianutilities-features.properties** with four feature switches: **quests.enabled**, **quests.rewards.enabled**, **gachas.enabled**, and **gachas.payments.enabled**.

These switches are enabled by default. Change them with the server stopped and restart to apply them. Normal features do not require special Java startup flags.

**📦 INSTALLATION**

Requires **Minecraft 1.21.1**, **Java 21**, **NeoForge 21.1.251+**, **Cobblemon 1.8.1**, and its required dependencies, including **Kotlin for Forge 5.10+**.

Install the same Zian Utilities version on the server and every client. Use **AVECOINS 2.3 or 2.4** for quest payments and paid gacha rolls. Future economy versions are not accepted automatically.

**🚧 BETA — ACTIVE DEVELOPMENT**

Zian Utilities is in active development. Features, balance, and equipment textures may change during the beta period.

Back up your world before updating. Older retired equipment families and elemental swords are no longer registered; worlds containing those items require attention before migration.

Report issues with your versions, server platform, reproduction steps, and relevant logs through [GitHub Issues](https://github.com/IANBLK/Zian-Utilities/issues).

**Created by IANBLK • Minecraft 1.21.1 • NeoForge • Cobblemon • Youer**
