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

**NEW in v1.4.1:** 
- 🎉 **Tier 2 Buff:** Max stat bonuses increased to +35 (was +15/+10) - 35% longer survival!
- ⚡ **Instant Response:** Eating food IMMEDIATELY updates buffs/debuffs and HUD (no more 2-second delay)
- 🔧 **Building Complete:** Enduring Builder (+15 stamina) now fully functional
- 🛡️ **Rock-Solid Stability:** 11 critical fixes from algorithm audit (auto-save, race condition protection, memory leak prevention)

### 🍖 Modded Consumables Support (v1.4.0+)

Living Lands now seamlessly integrates with food/drink mods! Your favorite gourmet meals from other mods properly restore hunger, thirst, and energy.

*   **92 Pre-configured Items** - Works out-of-the-box with popular food mods:
    *   Hidden's Harvest Delights (44 gourmet foods, T2-T7)
    *   NoCube's Bakehouse + Tavern + Orchard (48 items)
*   **Extended Tier System** - Support for T1-T7 items (vanilla was T1-T3)
    *   T6 Exquisite Feast: 53 hunger (69 with MEAT multiplier!)
    *   T7 Legendary Feast: 65 hunger (84.5 with MEAT multiplier!)
*   **Automatic Detection** - No manual config needed, just install and play
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

***

## 🛡️ Stability & Performance (v1.4.1)

Living Lands Reloaded has undergone comprehensive algorithm auditing to ensure rock-solid reliability:

*   **Auto-Save System** - 5-minute periodic saves prevent data loss on server crashes
*   **Race Condition Protection** - Thread-safe synchronization prevents corruption when admin commands and gameplay events collide
*   **Memory Leak Prevention** - Guaranteed cleanup on player disconnect, even when errors occur
*   **Database Verification** - All writes verified with row count checks and failure warnings
*   **Instant Feedback** - Buffs/debuffs and HUD update the moment you eat food (no delay)

## 🗺️ What's Coming Next

**Next Updates:**

*   **Land Claims** (planned) - protect builds, trust friends, per-world management
*   **Advanced mechanics** (planned) - high-stat buffs, seasonal variation, custom food effects, poison and dangerous consumables

***

## 📦 Installation

Players: join a server running Living Lands Reloaded. The HUD appears automatically.

Server owners:

1.  Download `livinglands-reloaded-1.4.1.jar`
2.  Place it in your server `plugins/` folder
3.  Start the server
4.  Configs generate in `LivingLandsReloaded/config/`

***

## 💬 Community

*   Bugs and issues: [GitHub](https://github.com/MoshPitCodes/living-lands-reloaded)
*   Suggestions and discussion: [Discord](https://discord.gg/8jgMj9GPsq)
*   If you like the mod: reviews help more than you think

Built by **MoshPitCodes**.

**Current Version:** 1.4.1 **License:** Apache 2.0 **Source Code:** Available on GitHub

***

☕ Support Development

Enjoying Living Lands Reloaded? Consider supporting development: [Ko-fi](https://ko-fi.com/moshpitplays)