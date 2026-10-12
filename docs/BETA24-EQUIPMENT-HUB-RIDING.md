# Beta.24: equipment retirement, bows, hub and riding administration

The 28 Reforged armor pieces are retired from the creative equipment catalog. Their registry IDs remain as compatibility aliases so older saves and unopened containers do not lose items. Existing pieces convert in place to diamond armor (six skins) or netherite armor (netherite skin) on login, inventory/menu processing or dropped-item loading. Count, component patches, enchantments, custom names, repair cost and wear are retained. Offline players convert when they return; unopened containers convert when accessed. Original Blockbench packs are kept outside the build for reference. Reforged custom geometry/textures and renderer are no longer shipped. Dark Lightning and the weapons remain.

Four bows now have distinct additional base-arrow damage: Classic 0, Curved 0.25, Harp 0.50, Long 0.75. Vanilla ammo, draw timing, enchantments and projectile behavior remain. The missing durability enchantment tag now admits Unbreaking, Mending and Curse of Vanishing in addition to Power, Punch, Flame and Infinity. Mending is obtained through the normal treasure/book paths, and the vanilla Infinity/Mending conflict remains. Bow, Prismatic and Dark Reaper names have theme colors and bold styling with framed flavor lore; existing IDs and set statistics remain.

The responsive hub has three aligned cards: Missions, Gachas and Breeding, each with icon, wrapped description, availability and its own action. It keeps the single canvas/input transform used at GUI scales 3 and 4. Actual client visual validation remains necessary.

Server administrators can configure global Pokémon mount limits:

- `/ZianUtilities vuelo ver`
- `/ZianUtilities vuelo velocidad <25–100>`
- `/ZianUtilities vuelo estamina <20–100>`
- `/ZianUtilities vuelo activar` or `desactivar`
- `/ZianUtilities vuelo recargar`

Default: enabled, speed 65%, maximum remaining stamina 60%. Settings are stored atomically in `config/zianutilities-riding.json` and mirrored to clients through a server-to-client-only payload. No player can submit settings through that channel. Commands require game-master administration and offer completion suggestions. Air-style Pokémon mounts have horizontal/ascent movement scaled; downward motion remains unchanged. Unridden Pokémon, land/water styles and player fly abilities/permissions are not modified. Native riding state is capped on server ticks and at the shared ride-bar call site. Starting an air-capable mount also applies the stamina cap. Disabling restores normal behavior without instantly refilling spent stamina.

Validation: normal unit/build checks, source review, packaged model/tag checks and two dedicated SQLite boots with Cobblemon 1.8.2. Native tests cover every retired armor alias with enchantments/name/wear/repair cost and idempotence, seven bow enchantments, the vanilla exclusion, distinct arrow damage, persisted riding settings, the actual stamina hook, absent-rider/disabled passthrough, unchanged fly abilities and denial of non-admin changes. Client movement/menu appearance still needs human gameplay confirmation.

Separate server integration fix: the twelve Legendary Monuments 8.2 battle-victory callbacks now have a native-NPC guard in a local datapack. The user repeated a real Novato victory and received its medal with no new console errors. No upstream RCT API implementation code is embedded in Utilities.
