# Living Lands Reloaded

<p align="center">
  <a href="https://discord.gg/8jgMj9GPsq"><img src="https://img.shields.io/badge/DISCORD-JOIN%20SERVER-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Discord"></a>
  <a href="https://ko-fi.com/moshpitplays"><img src="https://img.shields.io/badge/KO--FI-SUPPORT-FF5E5B?style=for-the-badge&logo=ko-fi&logoColor=white" alt="Ko-fi"></a>
  <a href="https://ko-fi.com/moshpitplays"><img src="https://img.shields.io/badge/BUY%20ME%20A%20COFFEE-SUPPORT-FFDD00?style=for-the-badge&logo=buy-me-a-coffee&logoColor=black" alt="Buy Me A Coffee"></a>
  <a href="https://github.com/MoshPitCodes/living-lands-reloaded"><img src="https://img.shields.io/badge/DOCS-READ%20MORE-4A90E2?style=for-the-badge&logo=github&logoColor=white" alt="Documentation"></a>
</p>

---

Living Lands Reloaded makes survival feel personal.

Every trip has a cost, every fight drains you, and every run home becomes a decision. As you play, your craft turns into identity: you grow into a fighter, miner, builder, logger, or gatherer, unlocking lasting perks along the way.

***

***

**🚨 IMPORTANT 🚨**

**This mod is __NOT COMPATIBLE__ with other mods that have a CustomUI.**

**This mod is __NOT COMPATIBLE__ with MultipleHUD mod.**

*   Until Hypixel has figured out how UIs should work in the game, I will not invest any time into this.

**This mod is __NOT COMPATIBLE__ with mods that manipulate player stats or introduce XP systems.**

*   These introduce conflicting mechanics with individual implementations that are not meant to work together.

***

***

## 🧭 RPG Survival: Needs That Create Stakes

Three stats tracked and shown on the HUD:

*   **Hunger** - food pressure (includes damage at low hunger)
*   **Thirst** - travel pressure (stamina pressure at low thirst)
*   **Energy** - effort pressure (speed debuffs at low energy, speed buff at high energy)

Drain is **activity-based** (sprinting/combat/travel), and foods restore different needs.

**🎉 v1.4.3 - AUTOMATIC CONSUMABLES DISCOVERY! 🎉**

**Zero-configuration modded consumables - just install mods and start your server!**

- ✨ **Auto-Scan System:** Automatically discovers all food/drink/potion items on first startup
- 🏷️ **Smart Grouping:** Organizes items by mod namespace (Hytale, NoCube, ChampionsVandal)
- ⚡ **Fast Performance:** ~200ms scan time for 162 consumable items
- 🔍 **Manual Scan:** `/ll scan consumables --save` to discover new items after installing mods
- 📁 **Separate Config:** Clean organization in `metabolism_consumables.yml`

### 🍖 Modded Consumables Support (v1.4.3 - AUTO-DISCOVERY!)

**NEW IN v1.4.3:** Living Lands automatically discovers and configures consumables from installed mods!

No more manual configuration - just install your favorite food/drink mods and Living Lands handles the rest.

*   **Automatic Discovery** - Scans Item registry for all consumable items
    *   114 consumables with valid effects found across 3 mods
    *   73 vanilla Hytale items (foods, potions, drinks)
    *   22 NoCube Bakehouse items (breads, pastries)
    *   19 ChampionsVandal More Potions items (stamina/regen/resistance)
*   **Namespace Detection** - Smart grouping by mod for easy management
    *   Uses Hytale's `AssetMap.getAssetPack()` API for accurate detection
    *   Creates organized sections: `AutoScan_2026-02-02_Hytale`, `AutoScan_2026-02-02_NoCube`
*   **Extended Tier System** - Support for T1-T7 items (vanilla was T1-T3)
    *   T6 Exquisite Feast: 53 hunger (69 with MEAT multiplier!)
    *   T7 Legendary Feast: 65 hunger (84.5 with MEAT multiplier!)
*   **Manual Scan Command** - Need to discover new items after installing more mods?
    *   `/ll scan consumables` - Preview discovered items
    *   `/ll scan consumables --save` - Save to config with namespace grouping
*   **Hot-Reload Support** - Edit config, run `/ll reload`, changes apply instantly

***

## 🏅 Professions: Your Character Sheet In Motion

Gain XP from normal play:

*   **Combat** (kills), **Mining** (ores), **Logging** (logs), **Building** (placing blocks), **Gathering** (item pickups)

Abilities unlock at:

*   **Level 15 (Tier 1):** +15% XP in that profession
*   **Level 45 (Tier 2):** **MASSIVE survival boosts!** Combat +35 max hunger (135 total), Mining +35 max thirst (135 total), Logging +35 max energy (135 total), Building +15 max stamina, Gathering +4 hunger/thirst on food pickup ✅
*   **Level 100 (Tier 3):** ✨ **ALL FUNCTIONAL IN 1.4.0!** ✨
    *   **Survivalist (Combat):** -15% metabolism depletion
    *   **Adrenaline Rush (Combat):** +10% speed for 5 seconds on kill
    *   **Ore Sense (Mining):** 10% chance bonus ore drop
    *   **Timber! (Logging):** 25% chance extra log
    *   **Efficient Architect (Building):** 12% chance block refund

Death penalty: progressive XP loss on your **2 highest professions** (base **10%**, +**3%** per death, capped at **35%**), with decay and mercy.

***

## 🛠️ Built For Server Owners (Not Just Players)

*   **Drop-in setup** - install the jar, boot the server, configs generate automatically
*   **Hot reload** - `/ll reload [module]`
*   **Per-world metabolism rules** - optional world overrides for metabolism config
*   **Configurable** - XP rates, drain rates, penalties, announcements

***

## 📢 Server Announcements

*   **MOTD** + **welcome messages** (first-time vs returning)
*   **Recurring announcements** (configurable intervals)
*   **Admin broadcast** (`/ll broadcast <message>`)
*   Placeholders: `{player_name}`, `{server_name}`, `{join_count}`
*   Color codes: `&a`, `&6`, etc.

Note: per-world overrides are supported in config; world-specific routing is being finished.

***

## ⌨️ Commands

**Player Commands:**

*   `/ll stats` - toggle metabolism HUD panel
*   `/ll buffs` - toggle buffs display
*   `/ll debuffs` - toggle debuffs display
*   `/ll professions` - toggle professions panel
*   `/ll progress` - toggle compact professions progress panel

**Admin Commands:**

*   `/ll reload [module]` - reload configuration files (operator only)
*   `/ll broadcast <message>` - broadcast message to all players (operator only)
*   `/ll prof set/add/reset/show` - manage player professions (operator only)
*   `/ll scan consumables [--save] [--section <name>]` - discover consumable items (operator only)

***

## 🛡️ Stability & Performance (v1.4.3)

Living Lands Reloaded has undergone comprehensive algorithm auditing to ensure rock-solid reliability:

*   **Auto-Discovery System (v1.4.3)** - Automatic consumables detection with ~200ms scan time for 162 items
*   **Smart Namespace Grouping (v1.4.3)** - Efficient mod detection using Hytale's AssetMap API
*   **Auto-Save System** - 5-minute periodic saves prevent data loss on server crashes
*   **Race Condition Protection** - Coroutine Mutex prevents corruption when admin commands and gameplay events collide
*   **Memory Leak Prevention** - Guaranteed cleanup on player disconnect, even when errors occur
*   **Database Verification** - All writes verified with row count checks and failure warnings
*   **Instant Feedback** - Buffs/debuffs and HUD update the moment you eat food (no delay)
*   **Wider Hysteresis Gaps** - 10-point gaps eliminate debuff flickering

## 🗺️ What's Coming Next

**Next Updates:**

*   **Land Claims** (planned) - protect builds, trust friends, per-world management
*   **Advanced mechanics** (planned) - high-stat buffs, seasonal variation, custom food effects, poison and dangerous consumables

***

## 📦 Installation

Players: join a server running Living Lands Reloaded. The HUD appears automatically.

Server owners:

1.  Download `livinglands-reloaded-1.4.2.jar` (**v1.4.0/v1.4.1 are broken - use v1.4.2!**)
2.  Place it in your Hytale global mods folder: `AppData/Roaming/Hytale/UserData/Mods/`
3.  Start the server
4.  Configs generate in `Saves/{SAVE_NAME}/mods/MPC_LivingLandsReloaded/config/`

***

## 💬 Community

*   Bugs and issues: [GitHub](https://github.com/MoshPitCodes/living-lands-reloaded)
*   Suggestions and discussion: [Discord](https://discord.gg/8jgMj9GPsq)
*   If you like the mod: reviews help more than you think

Built by **MoshPitCodes**.

**Current Version:** 1.4.2 **License:** Apache 2.0 **Source Code:** Available on GitHub

***

☕ Support Development

Enjoying Living Lands Reloaded? Consider supporting development: [Ko-fi](https://ko-fi.com/moshpitplays)