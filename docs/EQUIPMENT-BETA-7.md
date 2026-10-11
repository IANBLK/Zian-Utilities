# Equipment in 0.1.0-beta.7

Prismatic equipment keeps its existing identifiers and statistics. Dark Reaper
adds a helmet, chestplate, leggings, boots, sword, axe, pickaxe, shovel and hoe
using the same enhanced netherite statistics. Both sets are enchantable and
fire resistant. Armor has +1 defense, +1 toughness and +0.05 knockback resistance
per piece compared with netherite. Tools keep the enhanced netherite tier.

Four bows (Curve, Regular, Long, Harp) use Chiaki Haruma's idle and three drawing
textures. All four have identical statistics: 2032 durability, enchantability 16,
fire resistance and +1 arrow base damage. Bow combat otherwise uses Minecraft's
normal ammunition, charging and enchantment behavior. There is no netherite bow
in vanilla Minecraft; these are custom bows with the equipment tier's durability.

All pieces appear under **Zian Utilities | Equipment**. There are no recipes or
new natural loot sources. No ItemsAdder, Oraxen or separate resource pack is
required. Mining 3x3 remains command-only and works with the Dark Reaper pickaxe.
Install beta.7 on both server and clients to load the new item identifiers.

## Test commands (operator; omit the slash in the server console)

`/give IANBLK zianutilities:dark_reaper_helmet`

Replace `helmet` with `chestplate`, `leggings`, `boots`, `sword`, `axe`,
`pickaxe`, `shovel` or `hoe` for the remaining pieces.

`/give IANBLK zianutilities:curve_bow`

Other bows: `regular_bow`, `long_bow`, `harp_bow`.

With the appropriate item in hand:

`/enchant IANBLK minecraft:power 5`

`/enchant IANBLK minecraft:unbreaking 3`

With the Dark Reaper pickaxe in hand:

`/enchant IANBLK zianutilities:mineria_3x3 1`

Check all four bow drawing states, armor on the player, enchantments, ordinary
survival firing and durability. Confirm the Prismatic items remain intact.

## Asset attribution

Dark Reaper textures: MonGen's Cave. Bow Bundle textures: Chiaki Haruma.
The project owner confirmed written authorization from both creators for public
CurseForge and GitHub distribution. Their textures are not covered by the MIT
license on this project's code. See `META-INF/EQUIPMENT_CREDITS.txt` in the JAR.
