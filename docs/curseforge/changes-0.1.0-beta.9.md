**✨ ZIAN UTILITIES — 0.1.0-beta.9**

**⌨️ UNIFIED COMMAND NAME**

Renamed the command root to **/ZianUtilities** across all existing sections. Removed the old **/zian** root and updated current help and recovery documentation.

Existing configuration, saved progress, feature switches, command permissions, and operator checks are preserved. Update scripts and GUI buttons that use the old root.

**🔎 RECENT IMPROVEMENTS INCLUDED — BETA 8**

Added native item tooltips when hovering over gacha reward icons or names, in both the player panel and editor. Configured enchantments, attributes, names, and descriptions are preserved.

Pending-prize and ticket icons can also be inspected. Registered bow client events directly to remove deprecated event-bus annotation warnings.

**⚔️ EQUIPMENT INCLUDED — BETA 7**

Added the nine-piece **Dark Reaper** set alongside the existing **Prismatic** equipment.

Added four enchantable bows with drawing animations, 2032 durability, and +1 arrow base damage.

Equipment textures are bundled with the mod, with creator authorization confirmed by the project owner. No external item plugin or resource pack is required.

**🎰 DELIVERY AND RECOVERY INCLUDED — BETA 6**

Gacha rewards are delivered after a server-managed delay that includes one additional second after the animation’s intended end.

Reserved prizes persist across disconnects and restarts. A full inventory leaves the prize pending instead of discarding it.

Separated uncertain charges from uncertain deliveries and added administrative resolution with recorded evidence, without repeating uncertain charges or automatically duplicating uncertain rewards.

Added safe retries for reward credits rejected specifically because the wallet was full, retaining the original claim identity and skipping components already paid.

Recorded quests completed with rewards disabled as completed without payment, preserving campaign progress without automatic backpay.

Limited compressed gacha NBT reads to 8 MiB and expanded recovery and reward-accounting regression coverage.

**📦 UPDATE NOTES**

Install **0.1.0-beta.9 on both the server and clients**. GitHub Actions successfully built this version.

The Prismatic and Dark Reaper sets remain available. Retired equipment families and elemental swords remain removed.

**This is a beta release. Equipment visuals and other features may be refined in future updates.**
