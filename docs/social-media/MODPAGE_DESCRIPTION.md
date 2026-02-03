# Living Lands Reloaded

<p align="center">
  <a href="https://discord.gg/8jgMj9GPsq"><img src="https://img.shields.io/badge/DISCORD-JOIN%20SERVER-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Discord"></a>
  <a href="https://ko-fi.com/moshpitplays"><img src="https://img.shields.io/badge/BUY%20ME%20A%20COFFEE-SUPPORT-FFDD00?style=for-the-badge&logo=buy-me-a-coffee&logoColor=black" alt="Buy Me A Coffee"></a>
  <a href="https://github.com/MoshPitCodes/living-lands-reloaded"><img src="https://img.shields.io/badge/DOCS-READ%20MORE-4A90E2?style=for-the-badge&logo=github&logoColor=white" alt="Documentation"></a>
</p>

---

## IMPORTANT (Compatibility)

Living Lands Reloaded is **NOT COMPATIBLE** with:

- Mods using CustomUI (HUD/UI conflicts)
- MultipleHUD
- Mods that manipulate player stats or add separate XP systems

Living Lands Reloaded makes survival feel personal: travel, combat, and work drain your needs, while professions turn your playstyle into long-term perks.

## Player Experience

### RPG Survival Needs

- HUD-tracked needs: **Hunger**, **Thirst**, **Energy**
- Activity-based drain (travel, combat, effort)
- Clear consequences and rewards (low needs hurt, high energy feels great)

### Professions That Reward Normal Play

- Earn XP by playing: Combat, Mining, Logging, Building, Gathering
- Tier 1 (Lv 15): XP bonus
- Tier 2 (Lv 45): big survival boosts
- Tier 3 (Lv 100): powerful perks (mobility/combat triggers, resource bonuses, building efficiency)

Death penalty: XP loss on your **2 highest professions** (10% base, +3% per death, capped at 35%) with decay/mercy.

### Modded Consumables (v1.4.3)

- Auto-discovers food/drinks/potions from installed mods on first startup
- Groups items by mod namespace for easy management
- Re-scan after adding mods: `/ll scan consumables --save`

Performance: ~200ms auto-scan for ~250 consumables (201 detected with valid effects).

## Admin Experience

- Drop-in setup: install, boot once, configs generate automatically
- Hot reload: `/ll reload [module]`
- Optional per-world metabolism overrides
- Announcements: MOTD, welcome messages, recurring announcements, operator broadcast

## Commands (Quick Reference)

Players:

- `/ll stats`
- `/ll buffs`, `/ll debuffs`
- `/ll professions`, `/ll progress`

Admins:

- `/ll reload [module]`
- `/ll broadcast <message>`
- `/ll prof set/add/reset/show`
- `/ll scan consumables [--save] [--section <name>]`

## Stability & Performance

- Auto-save every 5 minutes to reduce data loss risk
- Fast startup consumables scan (see metric above)
- Responsive HUD updates (changes show immediately)

## Compatibility (Important)

Not compatible with:

- Mods using CustomUI (HUD/UI conflicts)
- MultipleHUD
- Mods that manipulate player stats or add separate XP systems

If you hit a compatibility issue, report it on GitHub.

## Compatible Mods (Tested)

This is a non-exhaustive list of mods confirmed compatible in live server testing.

Quality of life:

- AdvancedItemInfo (v1.0.5)
- BetterMap (v1.2.7)
- WhereThisAt (v1.0.6)
- Simply-Trash (v1.0.0)
- BetterWardrobes (v1.0.2)

Gameplay / utility:

- BloodMoon (v1.2.1)
- Eldritch Tales (v0.0.1)
- Books and Papers (v1.1.0)
- Vein Mining (v1.3.7)
- Hybrid (v1.7 - 2026.01.17)
- Overstacked (v2026.1.28)

Content expansion (highlights):

- Hidden's Harvest Delights (v0.0.3)
- NoCube Bakehouse/Culinary/Tavern/Orchard/Cultivation (v0.0.2)
- More Potions (v2.0.0)
- Aures Horses (v01.02.2026 - Evil Update)
- Aures Livestock (v01.02.2026)
- NoCube Undead Warriors (v0.0.3)
- Skeleton Banging Shield (v0.2)
- NoCube Bags/Resource Bags (v0.0.2)
- Outlanders Armor Pack (v1.2)
- Thorium Chests (v1.0.0)
- Thorium Furnaces (v1.0.2)
- Artisan's Palette (v1.0.1)
- Violet's Furnishings (v1.0.1)
- Violet's Wardrobe

## Installation

Players: join a server running Living Lands Reloaded (HUD appears automatically).

Server owners:

1) Download `livinglands-reloaded-1.4.3.jar`
2) Place it in `AppData/Roaming/Hytale/UserData/Mods/`
3) Start the server (configs generate under `Saves/{SAVE_NAME}/mods/MPC_LivingLandsReloaded/config/`)

Built by **MoshPitCodes**.
