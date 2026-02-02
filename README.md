<!-- DO NOT TOUCH THIS SECTION#1: START -->
<h1 align="center">
   <br>
   <img src="./.github/assets/logo/living-lands-reloaded-logo.png" width="400px" /><br>
      Living Lands Reloaded | Hytale RPG Survival Mod
   <br>
   <img src="./.github/assets/pallet/pallet-0.png" width="800px" /> <br>

   <div align="center">
      <p></p>
      <div align="center">
         <a href="https://github.com/MoshPitCodes/living-lands-reloaded/stargazers">
            <img src="https://img.shields.io/github/stars/MoshPitCodes/living-lands-reloaded?color=FABD2F&labelColor=282828&style=for-the-badge&logo=starship&logoColor=FABD2F">
         </a>
         <a href="https://github.com/MoshPitCodes/living-lands-reloaded/">
            <img src="https://img.shields.io/github/repo-size/MoshPitCodes/living-lands-reloaded?color=B16286&labelColor=282828&style=for-the-badge&logo=github&logoColor=B16286">
         </a>
         <a href="https://hytale.com">
            <img src="https://img.shields.io/badge/Hytale-Server%20Mod-blue.svg?style=for-the-badge&labelColor=282828&logo=data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCI+PHBhdGggZmlsbD0iIzQ1ODU4OCIgZD0iTTEyIDJMMiA3bDEwIDVsMTAtNUwxMiAyeiIvPjwvc3ZnPg==&logoColor=458588&color=458588">
         </a>
         <a href="https://github.com/MoshPitCodes/living-lands-reloaded/blob/main/LICENSE">
            <img src="https://img.shields.io/static/v1.svg?style=for-the-badge&label=License&message=Apache-2.0&colorA=282828&colorB=98971A&logo=apache&logoColor=98971A&"/>
         </a>
      </div>
      <br>
   </div>
</h1>

<br/>
<!-- DO NOT TOUCH THIS SECTION#1: END -->

# Overview

**Living Lands Reloaded** is a modular RPG survival mod for Hytale featuring metabolism tracking and profession leveling. Built with a modern, scalable architecture using Kotlin and SQLite.

**Current Status:** **v1.4.3 (Tested)** - Auto-scan consumables! Zero-configuration modded food support with automatic discovery and namespace detection. Tested with 29 compatible mods in production.

---

## Recent Updates

**v1.4.3 Changes (AUTO-SCAN CONSUMABLES):**
- 🎉 **NEW: Automatic Consumables Discovery!** - Zero-configuration setup for modded food/drinks
  - Auto-scans Item registry on first startup (~200ms for 250+ items)
  - Smart namespace detection using `AssetMap.getAssetPack()` API
  - Organizes items by mod in separate config file (`metabolism_consumables.yml`)
- 🔍 **Manual Scan Command:** `/ll scan consumables [--save] [--section <name>]`
  - Preview discovered items or save to config
  - Custom section names for organization
- 🧹 **Logging Cleanup:** Production-ready console output (essential messages only)
- 📝 **Config Migration:** v5→v6 removes old nested modded consumables structure

---

## Features

### Metabolism System (Complete)
- **Three Core Stats** - Hunger, thirst, and energy (0-100 base, up to 135 with abilities)
- **Activity-Based Depletion** - Stats drain faster when sprinting, swimming, or in combat
- **Buffs & Debuffs** - Speed penalties at low energy, bonuses at high stats (10-point hysteresis)
- **Food Consumption** - Eating restores stats based on food type
- **Modded Consumables** - Automatic discovery of all modded food/drinks (T1-T7 tiers, namespace detection)
- **Global Persistence** - Stats follow players across worlds
- **Thread-Safe** - Async database operations with proper synchronization
- **Auto-Save** - 5-minute periodic saves for crash protection

### Professions System (Complete)
- **5 Professions** - Combat, Mining, Logging, Building, Gathering
- **100 Levels Each** - Exponential XP curve with configurable rates
- **XP From Activities** - Kill mobs, mine ores, chop logs, place blocks, gather items
- **Tier 1 Abilities (Level 15)** - +15% XP boost for each profession
- **Tier 2 Abilities (Level 45)** - Permanent max stat increases
  - Iron Stomach (Combat) - +35 max hunger (100 → 135)
  - Desert Nomad (Mining) - +35 max thirst (100 → 135)
  - Tireless Woodsman (Logging) - +35 max energy (100 → 135)
  - Enduring Builder (Building) - +15 max stamina
  - Hearty Gatherer (Gathering) - +4 hunger/thirst per food pickup
- **Tier 3 Abilities (Level 100)** - Powerful active/passive effects
  - Survivalist (Combat) - -15% metabolism depletion
  - Adrenaline Rush (Combat) - +10% speed for 5s on kill
  - Ore Sense (Mining) - 10% chance bonus ore
  - Timber! (Logging) - 25% chance extra log
  - Efficient Architect (Building) - 12% chance block refund
- **Level-Up Notifications** - Chat messages when leveling up
- **Death Penalty** - Lose XP on death (progressive 10-35% on 2 highest professions)
- **Global Persistence** - Stats survive server restarts and world switches
- **Auto-Save** - 5-minute periodic saves for crash protection

### Announcer System (Complete)
- **MOTD (Message of the Day)** - Display welcome message on player join
- **Welcome Messages** - Different messages for first-time vs returning players
- **Recurring Announcements** - Automated tips/info with configurable intervals (5m, 10m, etc.)
- **Broadcast Commands** - Admins can send server-wide messages
- **Placeholder Support** - Dynamic values like `{player_name}`, `{server_name}`, `{join_count}`
- **Per-World Overrides** - Customize messages per world
- **Color Codes** - Minecraft-style color codes (`&a` = green, `&6` = gold, etc.)
- **Hot-Reload** - Update configs without restarting via `/ll reload announcer`

### Commands

**Player Commands:**
- `/ll stats` - Toggle metabolism HUD
- `/ll buffs` - Toggle buffs display
- `/ll debuffs` - Toggle debuffs display
- `/ll professions` - Toggle detailed professions panel
- `/ll progress` - Toggle compact progress view

**Admin Commands (Operator Only):**
- `/ll reload [module]` - Hot-reload configuration
- `/ll broadcast <message>` - Broadcast message to all players
- `/ll scan consumables [--save] [--section <name>]` - Scan for modded consumables
- `/ll prof set <player> <profession> <level>` - Set profession level
- `/ll prof add <player> <profession> <xp>` - Add XP to profession
- `/ll prof reset <player> [profession]` - Reset profession(s)
- `/ll prof show <player>` - Show player profession stats

---

## Implementation Status

| Module | Component | Status | Notes |
|--------|-----------|--------|-------|
| **Core** | Service Registry | ✅ Complete | Type-safe service locator |
| | World Management | ✅ Complete | Per-world contexts |
| | Configuration | ✅ Complete | YAML hot-reload with migrations |
| | Persistence | ✅ Complete | Global + per-world SQLite |
| **Metabolism** | Stat Tracking | ✅ Complete | Hunger/thirst/energy |
| | Depletion System | ✅ Complete | Activity-based rates |
| | Food Consumption | ✅ Complete | Effect-based detection |
| | Buffs/Debuffs | ✅ Complete | Hysteresis-based states |
| **Professions** | XP Systems | ✅ Complete | All 5 professions award XP |
| | Tier 1 Abilities | ✅ Complete | +15% XP boosts work |
| | Tier 2 Abilities | ✅ Complete | +35 max stat bonuses (v1.4.1) |
| | Tier 3 Abilities | ✅ Complete | All 5 abilities functional (v1.4.0) |
| | Death Penalty | ✅ Complete | Progressive 10-35% XP loss |
| | Admin Commands | ✅ Complete | `/ll prof set/add/reset/show` |
| | Modded Consumables | ✅ Complete | Auto-scan with namespace detection (v1.4.3) |
| **Announcer** | MOTD/Welcome | ✅ Complete | Join messages with placeholders |
| | Recurring Announcements | ✅ Complete | Interval-based automation |
| | Broadcast Commands | ✅ Complete | Admin messaging |
| | Per-World Overrides | ✅ Complete | Custom messages per world |

---

## Installation

### Requirements

| Requirement | Version |
|-------------|---------|
| **Java** | 25+ |
| **Hytale Server** | Latest build |

### Quick Start

1. Download `livinglands-reloaded-1.4.3.jar` from [Releases](https://github.com/MoshPitCodes/living-lands-reloaded/releases)
2. Place in Hytale global mods directory: `AppData/Roaming/Hytale/UserData/Mods/`
3. Start server - configs auto-generated in `Saves/{SAVE_NAME}/mods/MPC_LivingLandsReloaded/config/`

### Build from Source

```bash
git clone https://github.com/MoshPitCodes/living-lands-reloaded.git
cd living-lands-reloaded
./gradlew build
# JAR: build/libs/livinglands-reloaded-1.4.3.jar
```

---

## Configuration

Config files are auto-generated on first run:

```
Saves/{SAVE_NAME}/mods/MPC_LivingLandsReloaded/
├── config/
│   ├── core.yml                    # Core settings
│   ├── metabolism.yml              # Depletion rates
│   ├── metabolism_consumables.yml  # Auto-scanned modded items (v1.4.3)
│   ├── professions.yml             # XP rates and abilities
│   └── announcer.yml               # Server messages and announcements
└── data/
    ├── global/livinglands.db        # Global player stats (metabolism, professions)
    └── {world-uuid}/livinglands.db  # Per-world data (future: claims)
```

### Hot Reload

```bash
/ll reload           # Reload all configs
/ll reload metabolism # Reload specific module
/ll reload announcer  # Reload announcer config
```

---

## Architecture

### Module System

```kotlin
// Register a service
CoreModule.services.register<MyService>(instance)

// Get a service
val service = CoreModule.services.get<MyService>()
```

### Key Patterns

- **Global Player Stats** - Metabolism/profession stats follow players across worlds
- **Per-World Configs** - Different depletion rates per world (optional)
- **Thread Safety** - `ConcurrentHashMap`, synchronized DB access, `world.execute { }`
- **Async Operations** - Database I/O uses Kotlin coroutines with `Dispatchers.IO`

### Project Structure

```
src/main/kotlin/com/livinglands/
├── LivingLandsPlugin.kt         # Entry point
├── core/
│   ├── CoreModule.kt            # Central hub
│   ├── ServiceRegistry.kt       # Type-safe services
│   ├── config/ConfigManager.kt  # YAML system
│   └── persistence/             # SQLite layer
└── modules/
    ├── metabolism/              # ✅ Complete
    │   ├── MetabolismModule.kt
    │   ├── MetabolismService.kt
    │   ├── MetabolismTickSystem.kt
    │   └── modded/             # Modded consumables support (v1.4.0)
    ├── professions/            # ✅ Complete (v1.4.0)
    │   ├── ProfessionsModule.kt
    │   ├── ProfessionsService.kt
    │   ├── abilities/          # All 15 abilities functional
    │   └── systems/            # All 5 XP systems work
    └── announcer/              # ✅ Complete (v1.3.0)
```

---

## Development

### Building

```bash
./gradlew build              # Full build
./gradlew build -x test      # Skip tests
./gradlew shadowJar          # Fat JAR with deps
```

### Testing

```bash
# Deploy to test server
./scripts/deploy_windows.sh

# Watch logs
./scripts/watch_windows_logs.sh
```

### Guidelines

- Use `synchronized(connection)` for DB access
- Wrap ECS calls in `world.execute { }`
- Catch exceptions and degrade gracefully
- Log with appropriate levels (FINE debug, INFO events)

---

## Roadmap

### v1.4.3 (Current - Tested ✅)
- ✅ **Auto-Scan Consumables** - Zero-configuration modded food support
- ✅ **Automatic Discovery** - Scans Item registry on first startup (~200ms)
- ✅ **Namespace Detection** - Smart grouping by mod using AssetMap API
- ✅ **Manual Scan Command** - `/ll scan consumables [--save] [--section <name>]`
- ✅ **Separate Config** - `metabolism_consumables.yml` (organized by mod)
- ✅ **HUD Code Quality** - DRY fixes, world switch handler, configurable limits
- ✅ **Production Tested** - Verified with 29 compatible mods on live server
- ✅ **All Core Modules Complete** (Metabolism, Professions, Announcer)

### Future Modules
- [ ] **Claims** (land protection, world-specific)
- [ ] **Economy** (currency, trading, shops)
- [ ] **Groups** (clans, parties, shared progression)
- [ ] **Random Encounters** (dynamic events, world bosses)

See [`docs/ROADMAP.md`](docs/ROADMAP.md) for detailed feature plans and timeline.

---

## Documentation

- [`docs/TECHNICAL_DESIGN.md`](docs/TECHNICAL_DESIGN.md) - Complete technical architecture
- [`docs/ROADMAP.md`](docs/ROADMAP.md) - Public roadmap and feature plans
- [`docs/PROFESSIONS_ABILITIES.md`](docs/PROFESSIONS_ABILITIES.md) - Professions system reference
- [`docs/LOGGING.md`](docs/LOGGING.md) - Logging system guide
- [`docs/MODULE_LIFECYCLE.md`](docs/MODULE_LIFECYCLE.md) - Module lifecycle and event handling

---

## Contributing

1. Fork the repository
2. Create feature branch (`git checkout -b feature/name`)
3. Follow Kotlin conventions
4. Ensure `./gradlew build` compiles without warnings
5. Test on Hytale server
6. Open Pull Request

---

## License

Apache License 2.0 - see [LICENSE](LICENSE)

```
Copyright 2026 MoshPitCodes
```

<!-- DO NOT TOUCH THIS SECTION#2: START -->
<br/>
<p align="center"><img src="https://raw.githubusercontent.com/catppuccin/catppuccin/main/assets/footers/gray0_ctp_on_line.svg?sanitize=true" /></p>
<div align="right">
  <a href="#readme">Back to the Top</a>
</div>
<!-- DO NOT TOUCH THIS SECTION#2: END -->
