# Living Lands Reloaded (v1.4.8)

<p align="center">
  <a href="https://discord.gg/8jgMj9GPsq"><img src="https://img.shields.io/badge/DISCORD-JOIN%20SERVER-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Discord"></a>
  <a href="https://ko-fi.com/moshpitplays"><img src="https://img.shields.io/badge/BUY%20ME%20A%20COFFEE-SUPPORT-FFDD00?style=for-the-badge&logo=buy-me-a-coffee&logoColor=black" alt="Buy Me A Coffee"></a>
  <a href="https://github.com/MoshPitCodes/living-lands-reloaded"><img src="https://img.shields.io/badge/DOCS-READ%20MORE-4A90E2?style=for-the-badge&logo=github&logoColor=white" alt="Documentation"></a>
</p>

***

Living Lands Reloaded makes survival feel personal.

Every trip has a cost, every fight drains you, and every run home becomes a decision. As you play, your craft turns into identity: you grow into a fighter, miner, builder, logger, or gatherer, unlocking lasting perks along the way.

***

## ⚠️ Compatibility Notice

**NOT COMPATIBLE with:**
- Mods using CustomUI (HUD conflicts)
- MultipleHUD mod
- Mods that manipulate player stats or add XP systems

***

## 🧭 RPG Survival: Needs That Create Stakes

Three stats tracked on HUD:

*   **Hunger** - food pressure (damage at low levels)
*   **Thirst** - travel pressure (stamina penalties at low levels)
*   **Energy** - effort pressure (speed debuffs/buffs)

Activity-based drain (sprinting/combat/travel). **Auto-discovers modded food/drink/potions** with zero configuration (T1-T7 support, ~200ms scan). Use `/ll scan consumables --save` to re-scan after adding mods.

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

*   MOTD + welcome messages (first-time vs returning)
*   Recurring announcements with configurable intervals
*   Admin broadcast: `/ll broadcast <message>`
*   Placeholders: `{player_name}`, `{server_name}`, `{join_count}`
*   Color code support: `&a`, `&6`, etc.

***

## ⌨️ Commands

**Player:** `/ll stats`, `/ll buffs`, `/ll debuffs`, `/ll professions`, `/ll progress`

**Admin:** `/ll reload`, `/ll broadcast`, `/ll prof`, `/ll scan consumables`

***

## 🛡️ Stability & Performance

**Recent Improvements (v1.4.4-v1.4.8):**
*   ✅ Profession bonuses persist correctly across disconnect/reconnect (v1.4.8)
*   ✅ Thread-safe HUD updates prevent silent failures (v1.4.8)
*   ✅ Safe cross-module access prevents crashes when modules disabled (v1.4.4)
*   ✅ Graceful shutdown with timeout prevents data loss (v1.4.5)
*   ✅ Standardized logging across 643 calls in 43 files (v1.4.7)

**Core Reliability:**
*   Auto-save every 5 minutes prevents data loss
*   Race condition protection with Mutex synchronization
*   Memory leak prevention with guaranteed cleanup
*   Database write verification with row count checks
*   Instant HUD feedback when eating food
*   10-point hysteresis gaps prevent debuff flickering

## 🗺️ What's Coming Next

**Next Updates:**

*   **Land Claims** (planned) - protect builds, trust friends, per-world management
*   **Advanced mechanics** (planned) - high-stat buffs, seasonal variation, custom food effects, poison and dangerous consumables

***

## 📦 Installation

Players: join a server running Living Lands Reloaded. The HUD appears automatically.

Server owners:

1.  Download `livinglands-reloaded-1.4.8.jar`
2.  Place it in your Hytale global mods folder: `AppData/Roaming/Hytale/UserData/Mods/`
3.  Start the server
4.  Configs generate in `Saves/{SAVE_NAME}/mods/MPC_LivingLandsReloaded/config/`

***

## 💬 Community

*   Bugs and issues: [GitHub](https://github.com/MoshPitCodes/living-lands-reloaded)
*   Suggestions and discussion: [Discord](https://discord.gg/8jgMj9GPsq)
*   If you like the mod: reviews help more than you think

Built by **MoshPitCodes**.

**Current Version:** 1.4.8 **License:** Apache 2.0 **Source Code:** Available on GitHub

***

☕ Support Development

Enjoying Living Lands Reloaded? Consider supporting development: [Ko-fi](https://ko-fi.com/moshpitplays)

***

## ✅ Compatible Mods

Living Lands Reloaded has been tested and confirmed compatible with the following mods:

### Quality of Life Mods

*   **AdvancedItemInfo** (v1.0.5) - Enhanced item tooltips and information display
*   **BetterMap** (v1.2.7) - Improved minimap and navigation features
*   **WhereThisAt** (v1.0.6) - Coordinate display and waypoint management
*   **Simply-Trash** (v1.0.0) - Trash can and item deletion utilities
*   **BetterWardrobes** (v1.0.2) - Enhanced wardrobe management system

### Gameplay Enhancement Mods

*   **BloodMoon** (v1.2.1) - Challenging night events with increased difficulty
*   **Eldritch Tales** (v0.0.1) - Story and lore expansion content
*   **Books and Papers** (v1.1.0) - Enhanced reading and writing mechanics
*   **Vein Mining** (v1.3.7) - Mine entire ore veins at once (works with Living Lands Mining profession!)

### Utility Mods

*   **Hybrid** (v1.7 - 2026.01.17) - Server-side utility features
*   **Overstacked** (v2026.1.28) - Increased stack sizes for better inventory management

### Content Expansion Mods

#### Food & Consumables (Works seamlessly with Living Lands auto-scan!)

*   **Hidden's Harvest Delights** (v0.0.3) - Expanded food variety and recipes
*   **NoCube Bakehouse** (v0.0.2) - Bakery items and bread varieties
*   **NoCube Culinary** (v0.0.2) - Cooking expansion with new recipes
*   **NoCube Tavern** (v0.0.2) - Tavern-themed food and drinks
*   **NoCube Orchard** (v0.0.2) - Fruit trees and orchard mechanics
*   **NoCube Cultivation** (v0.0.2) - Advanced farming mechanics
*   **More Potions** (v2.0.0) - Additional potion types and effects

#### Creatures & NPCs

*   **Aures Horses** (v01.02.2026 - Evil Update) - Horse variants and mechanics
*   **Aures Livestock** (v01.02.2026) - Farm animal expansion
*   **NoCube Undead Warriors** (v0.0.3) - Undead enemy types
*   **Skeleton Banging Shield** (v0.2) - Enhanced skeleton AI

#### Items & Equipment

*   **NoCube Bags** (v0.0.2) - Inventory expansion bags
*   **NoCube Resource Bags** (v0.0.2) - Resource-specific storage bags
*   **Outlanders Armor Pack** (v1.2) - Additional armor sets
*   **Thorium Chests** (v1.0.0) - Enhanced chest storage
*   **Thorium Furnaces** (v1.0.2) - Advanced smelting options

#### Decoration & Building

*   **Artisan's Palette** (v1.0.1) - Decorative building blocks
*   **Violet's Furnishings** - Furniture and decorative items
*   **Violet's Wardrobe** - Clothing and cosmetic items

**Note:** This list represents mods that have been tested in a live server environment alongside Living Lands Reloaded. If you discover compatibility issues with any of these mods, please report them on [GitHub](https://github.com/MoshPitCodes/living-lands-reloaded/issues).

**Reminder:** Living Lands Reloaded is **NOT COMPATIBLE** with:

*   Mods that use CustomUI (conflicts with HUD system)
*   MultipleHUD mod (architectural incompatibility)
*   Mods that manipulate player stats or introduce XP systems (conflicts with Professions)