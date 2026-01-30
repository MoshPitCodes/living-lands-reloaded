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

**Current Status:** **v1.2.2** - Metabolism system complete. Professions system complete with Tier 1 & Tier 2 abilities. Code cleanup and logging improvements.

---

## Recent Updates

**v1.2.2 Changes:**
- ✅ Logging system improvements (aligned with Java log levels)
- ✅ Codebase cleanup (removed 770 lines of stub code)
- ✅ Fixed player disconnect race condition
- ✅ All INFO logs changed to FINE for cleaner output
- ✅ Added safety guards to prevent incomplete module enablement

---

## Features

### Metabolism System (Complete)
- **Three Core Stats** - Hunger, thirst, and energy (0-100 scale)
- **Activity-Based Depletion** - Stats drain faster when sprinting, swimming, or in combat
- **Buffs & Debuffs** - Speed penalties at low energy, bonuses at high stats
- **Food Consumption** - Eating restores stats based on food type
- **Global Persistence** - Stats follow players across worlds
- **Thread-Safe** - Async database operations with proper synchronization

### Professions System (Partial)
- **5 Professions** - Combat, Mining, Logging, Building, Gathering
- **100 Levels Each** - Exponential XP curve with configurable rates
- **XP From Activities** - Kill mobs, mine ores, chop logs, place blocks, gather items
- **Tier 1 Abilities** - +15% XP boost at level 15 (working)
- **Level-Up Notifications** - Chat messages when leveling up
- **Death Penalty** - Lose XP on death (progressive 10-35% on 2 highest professions)
- **Global Persistence** - Stats survive server restarts and world switches

### Commands

**Player Commands:**
- `/ll stats` - Toggle metabolism HUD
- `/ll buffs` - Toggle buffs display
- `/ll debuffs` - Toggle debuffs display
- `/ll professions` - Toggle detailed professions panel
- `/ll progress` - Toggle compact progress view

**Admin Commands (Operator Only):**
- `/ll reload [module]` - Hot-reload configuration
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
| | Tier 2 Abilities | ✅ Complete | Max stat bonuses integrated |
| | Death Penalty | ✅ Complete | Progressive 10-35% XP loss |
| | Admin Commands | ✅ Complete | `/ll prof set/add/reset/show` |
| | Tier 3 Abilities | 🚧 Planned | Passive effects (future) |

---

## Installation

### Requirements

| Requirement | Version |
|-------------|---------|
| **Java** | 25+ |
| **Hytale Server** | Latest build |

### Quick Start

1. Download `livinglands-reloaded-1.2.2.jar` from [Releases](https://github.com/MoshPitCodes/living-lands-reloaded/releases)
2. Place in Hytale server's `plugins/` directory
3. Start server - configs auto-generated in `LivingLandsReloaded/config/`

### Build from Source

```bash
git clone https://github.com/MoshPitCodes/living-lands-reloaded.git
cd living-lands-reloaded
./gradlew build
# JAR: build/libs/livinglands-reloaded-1.2.2.jar
```

---

## Configuration

Config files are auto-generated on first run:

```
LivingLandsReloaded/
├── config/
│   ├── core.yml           # Core settings
│   ├── metabolism.yml     # Depletion rates
│   └── professions.yml    # XP rates and abilities
└── data/
    ├── global/livinglands.db        # Global player stats
    └── {world-uuid}/livinglands.db  # Per-world data
```

### Hot Reload

```bash
/ll reload           # Reload all configs
/ll reload metabolism # Reload specific module
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
    │   └── MetabolismTickSystem.kt
    └── professions/             # 🚧 40% complete
        ├── ProfessionsModule.kt
        ├── ProfessionsService.kt
        ├── abilities/
        │   ├── Ability.kt              # 15 ability classes
        │   └── AbilityRegistry.kt      # Tier 1 works, Tier 2/3 stubs
        └── systems/                    # All 5 XP systems work
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

### v1.2.2 (Current)
- ✅ Metabolism system complete
- ✅ Core infrastructure stable
- ✅ Professions XP systems complete
- ✅ Tier 1 & Tier 2 abilities fully integrated
- ✅ Death penalty system
- ✅ Admin commands (`/ll prof set/add/reset/show`)
- ✅ Thread safety improvements
- ✅ Logging system aligned with Java standards
- ✅ Codebase cleanup (removed stub modules)
- ✅ Player disconnect race condition fixed

### Next Sprint (v1.3.0)
- [ ] Tier 3 ability triggers (passive effects at level 100)
- [ ] World switch handling for ability persistence
- [ ] Performance optimizations

### Future
- [ ] Claims module (land protection)
- [ ] Economy module (currency/trading)
- [ ] Groups module (clans/parties)

See [`docs/IMPLEMENTATION_PLAN_DETAILED.md`](docs/IMPLEMENTATION_PLAN_DETAILED.md) for detailed timeline.

---

## Documentation

- [`docs/TECHNICAL_DESIGN.md`](docs/TECHNICAL_DESIGN.md) - Architecture deep dive
- [`docs/IMPLEMENTATION_PLAN_DETAILED.md`](docs/IMPLEMENTATION_PLAN_DETAILED.md) - Development roadmap & timeline
- [`CHANGELOG.md`](CHANGELOG.md) - Version history

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
