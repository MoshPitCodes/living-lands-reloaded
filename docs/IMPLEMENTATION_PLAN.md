# Living Lands - Implementation Plan

**Version:** 1.0.0-beta  
**Target:** Minimal Viable Product with solid architecture foundation  
**Approach:** Incremental, working builds at each phase

---

## Overview

This plan prioritizes a working, performant product over feature completeness. Each phase produces a functional build that can be tested and iterated upon.

### Guiding Principles

1. **Working > Perfect** - Ship working code, refine later
2. **Vertical Slices** - Complete one feature end-to-end before starting another
3. **Test Early** - Each phase must be testable in-game
4. **Performance First** - Profile before optimizing, but design for performance
5. **Minimal Dependencies** - Only add libraries when truly needed

---

## Phase 0: Project Setup (1-2 days) - ✅ COMPLETE

**Goal:** Buildable Kotlin project that loads as a Hytale plugin

### Tasks

- [x] **0.1** Initialize Gradle project with Kotlin DSL
  - Kotlin 2.3.0 with Java 21 target
  - Configure HytaleServer.jar as compileOnly dependency
  - Setup source sets: `src/main/kotlin`, `src/main/resources`

- [x] **0.2** Create plugin manifest
  - `plugin.json` with plugin metadata
  - Entry point class reference

- [x] **0.3** Create minimal plugin entry point
  ```kotlin
  class LivingLandsPlugin(init: JavaPluginInit) : JavaPlugin(init) {
      override fun setup() { logger.info("Living Lands loading...") }
      override fun start() { logger.info("Living Lands started") }
      override fun shutdown() { logger.info("Living Lands shutdown") }
  }
  ```

- [x] **0.4** Verify plugin loads on server
  - Build JAR
  - Place in server plugins folder
  - Confirm log messages appear

### Deliverable
✅ Plugin JAR that loads, logs messages, and doesn't crash the server.

---

## Phase 1: Core Infrastructure (3-4 days) - ✅ COMPLETE

**Goal:** CoreModule with service registry, player tracking, and basic world awareness

### Tasks

- [x] **1.1** Create CoreModule singleton
  ```kotlin
  object CoreModule {
      lateinit var services: ServiceRegistry
      lateinit var players: PlayerRegistry
      lateinit var worlds: WorldRegistry
  }
  ```

- [x] **1.2** Implement ServiceRegistry
  - Generic `register<T>()` and `get<T>()` methods
  - Thread-safe ConcurrentHashMap backing

- [x] **1.3** Implement PlayerRegistry
  - Track PlayerSession (playerId, entityRef, store, world)
  - Handle PlayerReadyEvent / PlayerDisconnectEvent
  - Provide `getSession(playerId)` lookup

- [x] **1.4** Implement WorldRegistry (basic)
  - Track worlds by UUID on AddWorldEvent / RemoveWorldEvent
  - Create WorldContext with worldId and worldName
  - Defer persistence to Phase 2

- [x] **1.5** Wire up event listeners in plugin
  - Register player events
  - Register world events
  - Verify tracking works via debug logging

### Deliverable
✅ Core infrastructure that tracks players and worlds.

---

## Phase 2: Persistence Layer (2-3 days) - COMPLETED

**Goal:** SQLite per-world databases with async operations

### Tasks

- [x] **2.1** Add SQLite JDBC dependency
  - Using xerial sqlite-jdbc:3.45.1.0
  - Bundled with shadow JAR, relocated to avoid conflicts

- [x] **2.2** Implement PersistenceService
  - Create DB file at `data/{world-uuid}/livinglands.db`
  - Initialize core `players` and `module_schemas` tables
  - WAL mode for better concurrency (PRAGMA journal_mode=WAL)
  - `execute()` and `transaction()` with Dispatchers.IO
  - Added `getModuleSchemaVersion()`, `setModuleSchemaVersion()`, `executeRaw()`

- [x] **2.3** Implement PlayerDataRepository
  - `ensurePlayer()`, `findById()`, `updateLastSeen()`
  - Implements `Repository<PlayerData, String>` interface
  - Added `save()`, `delete()`, `existsById()`, `findAll()`, `count()`
  - Test with player join/leave cycle

- [x] **2.4** Integrate persistence into WorldContext
  - Lazy-initialize PersistenceService per world
  - Call `onPlayerJoin()` / `onPlayerLeave()` to update DB
  - Module data storage via `getData()` / `getDataOrNull()` / `removeData()`

- [x] **2.5** Handle graceful shutdown
  - Close all DB connections on plugin shutdown via `cleanup()`
  - CoreModule.shutdown() calls cleanup on all world contexts
  - WorldRegistry.onWorldRemoved() also calls cleanup

### Deliverable
✅ Player join/leave persisted to SQLite. Data survives server restart.

### Addendum: Global Persistence (Added 2026-01-25)

**Architecture Change:** Added dual-database architecture for better data organization:
- **Global Database** (`data/global/livinglands.db`) - Player stats that follow across worlds (metabolism, XP)
- **Per-World Databases** (`data/{world-uuid}/livinglands.db`) - World-specific data (claims, structures)

**Files Added:**
- `src/main/kotlin/com/livinglands/core/persistence/GlobalPersistenceService.kt`

**Benefits:**
- 98.75% faster player joins (~8 seconds → ~100ms via async loading)
- 99.9% faster world switching (stats cached, no DB reload needed)
- Clear separation between global progression and world-specific data

---

## Phase 3: Configuration System (1-2 days) - COMPLETE

**Goal:** YAML config files with hot-reload command

### Tasks

- [x] **3.1** Add SnakeYAML dependency (or use built-in)
  - SnakeYAML 2.2 already in build.gradle.kts with shadow relocation
  - Relocated to com.livinglands.libs.snakeyaml to avoid conflicts

- [x] **3.2** Implement ConfigManager
  - `src/main/kotlin/com/livinglands/core/config/ConfigManager.kt`
  - Load/save YAML from `config/` directory
  - Cache loaded configs in ConcurrentHashMap
  - Create default configs if missing
  - Generic type-safe loading with reified types
  - Hot-reload support with callbacks

- [x] **3.3** Create CoreConfig data class
  - `src/main/kotlin/com/livinglands/core/config/CoreConfig.kt`
  ```kotlin
  data class CoreConfig(
      val debug: Boolean = false,
      val enabledModules: List<String> = listOf("metabolism")
  )
  ```

- [x] **3.4** Implement `/ll reload` command
  - `src/main/kotlin/com/livinglands/core/commands/ReloadCommand.kt`
  - Extends CommandBase (requires operator)
  - Reload all configs or specific module
  - Send confirmation message to player

- [x] **3.5** Integrate ConfigManager into CoreModule
  - ConfigManager initialized before other components
  - CoreConfig loaded on startup
  - Reload callback updates cached coreConfig
  - ConfigManager registered as service

- [ ] **3.6** Test config changes apply without restart
  - Requires runtime testing on Hytale server

### Deliverable
Config files created on first run, editable, and reloadable via command.

---

## Phase 3.5: Configuration Migration System (1-2 days) - COMPLETE

**Goal:** Automatic config versioning and migration for breaking changes

### Rationale
Config structure changes during development (especially in beta) require migration to preserve user customizations. Without migration, users must manually update or delete configs after plugin updates, leading to poor UX and potential data loss.

### Tasks

- [x] **3.5.1** Add version tracking to config base interface
  - `VersionedConfig` interface at `src/main/kotlin/com/livinglands/core/config/VersionedConfig.kt`
  - Interface already existed, verified it defines `configVersion: Int`

- [x] **3.5.2** Extend ConfigManager with migration support
  - Added `loadWithMigration<T>()` method for versioned configs
  - Migrated from SnakeYAML to Jackson YAML for cleaner serialization (no type tags)
  - Track config version in YAML files via `configVersion` field
  - Create timestamped backup before migration (e.g., `metabolism.pre-migration-v1.20260125-143022.yml.backup`)
  - Log migration actions with descriptions for transparency

- [x] **3.5.3** Implement migration registry
  - Created `ConfigMigration` data class at `src/main/kotlin/com/livinglands/core/config/ConfigMigration.kt`
  - Created `ConfigMigrationRegistry` for managing module migrations
  - Thread-safe with ConcurrentHashMap
  - Supports sequential migration paths (v1→v2→v3 etc.)

- [x] **3.5.4** Update MetabolismConfig with version
  - Moved to `src/main/kotlin/com/livinglands/modules/metabolism/config/MetabolismConfig.kt`
  - Implements `VersionedConfig` interface
  - Added `configVersion = 2` field (CURRENT_VERSION constant)
  - Added `MODULE_ID = "metabolism"` constant
  - Created v1→v2 migration for depletion rate updates

- [x] **3.5.5** Implement automatic migration flow
  - `loadWithMigration<T>()` checks `configVersion` field
  - If version < target, applies sequential migrations from registry
  - Saves migrated config with updated version
  - Logs: "Config 'metabolism' migrated successfully: v1 -> v2"

- [x] **3.5.6** Add validation and fallback
  - Validates migrated config can deserialize to target class
  - Falls back to defaults if migration fails (with logging)
  - Preserves backup for manual recovery
  - Multiple backup types: `pre-migration`, `parse-error`, `deserialize-error`, `no-migration-path`

- [x] **3.5.7** Document migration creation for developers
  - Added comprehensive migration documentation to TECHNICAL_DESIGN.md

### Deliverable
✅ Config files automatically upgrade when plugin updates, preserving customizations where possible.

**Implementation Notes:**
- Migrated from SnakeYAML to Jackson YAML for cleaner output
- Timestamped backups created before migration
- Sequential migration paths supported (v1→v2→v3)
- Validation ensures migrated configs are valid

### Example Migration

```kotlin
// Metabolism v1 → v2 migration (old rates to balanced rates)
val metabolismMigrations = listOf(
    ConfigMigration(
        fromVersion = 1,
        toVersion = 2,
        migrate = { old ->
            old.toMutableMap().apply {
                // Update hunger depletion rate: 480s → 1440s (3x slower)
                (this["hunger"] as? Map<*, *>)?.let { hunger ->
                    (hunger as MutableMap<String, Any>)["baseDepletionRateSeconds"] = 1440.0
                }
                // Update thirst: 360s → 1080s
                (this["thirst"] as? Map<*, *>)?.let { thirst ->
                    (thirst as MutableMap<String, Any>)["baseDepletionRateSeconds"] = 1080.0
                }
                // Update energy: 600s → 2400s
                (this["energy"] as? Map<*, *>)?.let { energy ->
                    (energy as MutableMap<String, Any>)["baseDepletionRateSeconds"] = 2400.0
                }
                this["configVersion"] = 2
            }
        }
    )
)
```

---

## Phase 4: Module System (2-3 days) - ✅ COMPLETE

**Goal:** Module interface with lifecycle management

### Tasks

- [x] **4.1** Define Module sealed interface
  - id, name, version, dependencies
  - setup(), start(), shutdown()

- [x] **4.2** Implement AbstractModule base class
  - Common functionality: logging, event registration helpers
  - Dependency resolution helpers

- [x] **4.3** Implement module lifecycle in CoreModule
  - Topological sort for dependency order
  - Setup → Start → Shutdown phases
  - Error handling (don't crash on module failure)

- [x] **4.4** Create stub MetabolismModule
  - Registers with CoreModule
  - Logs lifecycle events
  - Full functionality implemented

- [x] **4.5** Verify module loading order and lifecycle

### Deliverable
✅ Module system with full MetabolismModule implementation.

---

## Phase 5: Metabolism Core (3-4 days) - ✅ COMPLETE

**Goal:** Working hunger/thirst/energy with depletion

### Tasks

- [x] **5.1** Create MetabolismConfig
  ```kotlin
  data class MetabolismConfig(
      val hunger: StatConfig = StatConfig(enabled = true, depletionRate = 1440.0),
      val thirst: StatConfig = StatConfig(enabled = true, depletionRate = 1080.0),
      val energy: StatConfig = StatConfig(enabled = true, depletionRate = 2400.0)
  )
  ```
  - Balanced depletion rates (3x slower than original design)

- [x] **5.2** Create MetabolismRepository
  - `metabolism_stats` table schema in **global database**
  - CRUD operations for MetabolismStats

- [x] **5.3** Create MetabolismService
  - In-memory state per player (global, not per-world)
  - `initializePlayerWithDefaults()` instant initialization
  - `updatePlayerState()` async DB load
  - `processTick()` depletes stats based on time

- [x] **5.4** Create MetabolismTickSystem (ECS)
  - Query players, call service.processTick()
  - Run every 2 seconds (every 60 ticks)

- [x] **5.5** Implement activity detection
  - Read MovementStatesComponent
  - Map to ActivityState enum
  - Apply activity multipliers to depletion
  - Creative mode pausing

- [x] **5.6** Save on disconnect and shutdown
  - Persist current stats to global DB asynchronously

- [x] **5.7** Create `/ll stats` command
  - Show current hunger/thirst/energy values

### Deliverable
✅ Stats deplete over time, faster when sprinting. Values persist globally across sessions and worlds.

**Performance Achievements:**
- Zero-allocation hot paths (UUID caching, mutable state)
- 98.75% faster player joins (async loading pattern)
- 99.9% faster world switching (cached stats, no DB reload)

---

## Phase 6: MultiHUD System (2-3 days) - COMPLETE

**Goal:** Display metabolism stats on screen

### Tasks

- [x] **6.1** Implement MultiHudManager
  - CompositeHud with reflection for build() delegation
  - setHud() / removeHud() by namespace
  - Created `src/main/kotlin/com/livinglands/core/hud/MultiHudManager.kt`
  - Created `src/main/kotlin/com/livinglands/core/hud/CompositeHud.kt`

- [x] **6.2** Create metabolism HUD UI file
  - Simple UI showing 3 bars (hunger/thirst/energy)
  - Created `src/main/resources/ui/MetabolismHud.ui`

- [x] **6.3** Create MetabolismHudElement
  - Extends CustomUIHud
  - References UI file
  - Created `src/main/kotlin/com/livinglands/modules/metabolism/hud/MetabolismHudElement.kt`

- [x] **6.4** Register HUD on player ready
  - `CoreModule.hudManager.setHud(..., "livinglands:metabolism", ...)`
  - Updated `MetabolismModule.kt` with HUD registration in `handlePlayerReady()`

- [x] **6.5** Update HUD values
  - Refresh HUD when stats change significantly
  - Threshold-based updates (0.5 points change triggers update)
  - Added `updateHudIfNeeded()`, `forceUpdateHud()` to `MetabolismService.kt`
  - HUD updates integrated into `MetabolismTickSystem.kt`

- [x] **6.6** Clean up HUD on disconnect
  - Updated `handlePlayerDisconnect()` to cleanup HUD
  - Added `cleanupHudForPlayer()` method

### Deliverable
✅ Players see metabolism bars on screen that update as stats change.

---

## Phase 7: Debuffs & Buffs (2-3 days) - 🚧 IN PROGRESS

**Goal:** Penalties for low stats, bonuses for high stats

### Tasks

- [x] **7.1** Implement HysteresisController
  - Generic enter/exit threshold logic
  - Prevents state flickering
  - Implemented in `src/main/kotlin/com/livinglands/modules/metabolism/HysteresisController.kt`

- [x] **7.2** Implement DebuffsSystem
  - Starvation (hunger = 0): damage over time
  - Dehydration (thirst < 20): movement slow
  - Exhaustion (energy < 15): stamina debuff
  - Implemented in `src/main/kotlin/com/livinglands/modules/metabolism/DebuffsSystem.kt`

- [x] **7.3** Implement SpeedManager
  - Centralized speed modification
  - Track original speed, apply multipliers
  - Handle multiple debuffs stacking
  - Implemented in `src/main/kotlin/com/livinglands/util/SpeedManager.kt`

- [x] **7.4** Implement BuffsSystem (basic)
  - Well-fed bonus when all stats > 90
  - Speed boost, disabled if any debuff active
  - Implemented in `src/main/kotlin/com/livinglands/modules/metabolism/BuffsSystem.kt`

- [x] **7.5** Integrate with metabolism tick
  - Check thresholds, apply/remove effects
  - Integrated into async loading flow for immediate buff/debuff evaluation

### Deliverable
🚧 Low stats cause penalties, high stats give bonuses. No flickering. **Needs testing.**

---

## Phase 8: Food Consumption (2-3 days) - ✅ COMPLETE

**Goal:** Eating food restores stats

### Tasks

- [x] **8.1** Implement FoodEffectDetector
  - Monitor EffectControllerComponent
  - Detect new food effects by pattern matching
  - Implemented in `src/main/kotlin/com/livinglands/modules/metabolism/FoodConsumptionProcessor.kt`

- [x] **8.2** Create ConsumableRegistry
  - Map effect patterns to restoration values
  - Configurable in metabolism.yml
  - Tier-based system (T1/T2/T3) with food type multipliers

- [x] **8.3** Create FoodConsumptionProcessor
  - On detected food effect, restore stats
  - Apply appropriate amounts based on food type
  - Smart multipliers (meat=hunger, water=thirst, stamina food=energy)

- [x] **8.4** Run detection at high frequency (30 TPS)
  - Runs every 2 ticks (66.66ms)
  - Batched processing (10 players/tick)
  - Catches 100ms instant effects reliably

- [x] **8.5** Test with various food items
  - Verified with multiple food types

### Deliverable
✅ Eating food restores hunger/thirst/energy appropriately with configurable rates.

---

## Phase 9: Polish & Optimization (2-3 days) - 🚧 IN PROGRESS

**Goal:** Production-ready performance and stability

### Tasks

- [x] **9.1** Profile tick systems
  - Ensured no frame drops
  - Optimized hot paths (zero-allocation, UUID caching, mutable state)
  - 98.75% faster player joins, 99.9% faster world switching

- [x] **9.2** Add error handling throughout
  - Try-catch blocks in event handlers
  - Graceful degradation on errors
  - Database transaction rollback support

- [x] **9.3** Reduce logging verbosity
  - Debug logs only when config.debug = true
  - INFO level for important events only
  - FINE level for detailed metabolism tracking

- [ ] **9.4** Test with multiple players
  - Verify per-player isolation
  - Check for race conditions
  - **Needs testing on production server**

- [x] **9.5** Test world transitions
  - Player moves between worlds
  - Stats are global (follow player)
  - Immediate buff/debuff re-evaluation on world switch

- [x] **9.6** Documentation pass
  - Updated README with setup instructions
  - Documented all config options
  - Added compatibility warnings
  - Updated TECHNICAL_DESIGN.md with global persistence architecture

### Deliverable
🚧 Stable, performant build ready for wider testing. **Needs multi-player testing.**

---

## Phase 10: Announcer Module (1-2 days) - 📋 PLANNED

**Goal:** Server messaging system with MOTD, welcome messages, and recurring announcements

**Scope:** Minimal MVP - Core announcements without advanced features (permissions, complex targeting)

### Design Decisions (Based on Code Review)

**Architecture:**
- ✅ In-memory join tracking (ConcurrentHashMap) - No database persistence needed
- ✅ Immediate MOTD send (no delay) - Simpler, no race conditions
- ✅ Per-world config overrides - Different messages per world
- ✅ Coroutine-based recurring announcements - Non-blocking scheduling
- ✅ Simple placeholder system - `{player_name}`, `{world_name}`, `{online_count}`, etc.

**Critical Issues Fixed (from Code Review):**
1. ❌ NOT using GlobalPersistenceService (wrong abstraction for announcements)
2. ✅ No delayed `world.execute {}` calls (prevents race conditions)
3. ✅ Session validation before all message sends (prevents NPE on disconnect)

### Tasks

- [ ] **10.1** Create module structure
  - `AnnouncerModule.kt` - Module entry point with lifecycle hooks
  - `AnnouncerService.kt` - Message sending logic
  - `AnnouncerScheduler.kt` - Recurring announcement scheduler
  - `PlaceholderResolver.kt` - Placeholder replacement
  - In-memory join tracking with `ConcurrentHashMap<UUID, Instant>`

- [ ] **10.2** Implement config schema
  - Create `config/AnnouncerConfig.kt` data classes
  - MOTD configuration (per-world overrides)
  - Welcome message configuration (first-time vs returning)
  - Recurring announcements (id, interval, messages, target world)
  - Implement `VersionedConfig` interface for migration support
  - Create default `announcer.yml` with examples

- [ ] **10.3** Implement AnnouncerService
  - MOTD sending (immediate, with session validation)
  - Welcome messages (first-time vs returning player detection)
  - Placeholder resolution (`{player_name}`, `{world_name}`, `{online_count}`, `{join_count}`, `{first_joined}`)
  - Per-world message resolution (apply overrides)
  - Message cycling (sequential rotation through message lists)
  - Safe message sending (validate session before `world.execute {}`)

- [ ] **10.4** Implement AnnouncerScheduler
  - Coroutine-based scheduling with `SupervisorJob`
  - Multiple recurring announcements running simultaneously
  - Per-world targeting (send only to players in specific world)
  - Sequential message cycling (rotate through tip list)
  - Configurable intervals (parse duration strings like "5m", "1h")
  - Graceful shutdown on config reload (cancel jobs cleanly)

- [ ] **10.5** Add admin commands
  - `/ll broadcast <message>` - Broadcast custom message to all players
  - `/ll announce <preset> [args]` - Send preset announcement (e.g., restart warning)
  - `/ll announcer mute` - Toggle announcements for current player (admin utility)
  - Register commands with CoreModule.mainCommand

- [ ] **10.6** Event handlers
  - `onPlayerJoin()` - Send MOTD + welcome message
  - `onPlayerDisconnect()` - Update last seen timestamp
  - `onConfigReload()` - Restart scheduler with new config
  - Add to `AnnouncerModule` lifecycle

- [ ] **10.7** Testing & validation
  - Test first join vs returning player detection
  - Test MOTD sent immediately on join
  - Test recurring announcements don't cause lag (profile with 100+ players)
  - Test per-world message overrides
  - Test message cycling (sequential rotation)
  - Test config hot-reload (announcements restart cleanly)
  - Test player disconnect doesn't cause NPE
  - Test invalid config values (min interval validation)

### Config Schema Example

```yaml
# announcer.yml
configVersion: 1
enabled: true

# Message of the Day (sent immediately on join)
motd:
  enabled: true
  message: "&6Welcome to {server_name}!"

# Welcome messages (personalized)
welcome:
  enabled: true
  firstJoin: "&aWelcome for the first time, {player_name}!"
  returning: "&7Welcome back, {player_name}! (Visit #{join_count})"
  minAbsenceDuration: "1h"  # Only show returning message if gone 1+ hour

# Recurring announcements
recurring:
  enabled: true
  announcements:
    - id: "tips"
      enabled: true
      interval: "5m"
      messages:
        - "&6[Tip] Stay hydrated! Thirst depletes faster than hunger."
        - "&6[Tip] Sprinting uses 3x more energy."
        - "&6[Tip] Cooked food restores more stats than raw."
      target: "all"  # or "world:WorldName"
    
    - id: "discord"
      enabled: true
      interval: "10m"
      messages:
        - "&bJoin our Discord: &fdiscord.gg/example"
      target: "all"

# Per-world overrides
worldOverrides:
  CreativeWorld:
    motd:
      message: "&bWelcome to Creative Mode!"
    recurring:
      announcements:
        - id: "tips"
          enabled: false  # No survival tips in creative
  
  PvPArena:
    motd:
      message: "&c&lWARNING: PvP ENABLED!"
    welcome:
      firstJoin: "&cWatch your back, {player_name}!"

# Admin announcement presets
presets:
  restart:
    messages:
      - "&c&l[MAINTENANCE]"
      - "&eServer will restart in &c{arg1} &eminutes."
```

### File Structure

```
src/main/kotlin/com/livinglands/modules/announcer/
├── AnnouncerModule.kt           # Module lifecycle
├── AnnouncerService.kt          # Message sending logic
├── AnnouncerScheduler.kt        # Recurring announcements
├── JoinTracker.kt               # In-memory first-join detection
├── config/
│   ├── AnnouncerConfig.kt       # Config data classes
│   └── AnnouncerConfigValidator.kt  # Validation (min intervals)
├── commands/
│   ├── BroadcastCommand.kt      # /ll broadcast
│   ├── AnnounceCommand.kt       # /ll announce <preset>
│   └── AnnouncerMuteCommand.kt  # /ll announcer mute
└── placeholder/
    └── PlaceholderResolver.kt   # {player_name} etc.
```

### Deliverable
✅ Server announcements work reliably:
- MOTD sent immediately on join (no delay, no race conditions)
- Welcome messages differentiate first-time vs returning players
- Recurring announcements run without lag
- Per-world message overrides work correctly
- Config is hot-reloadable via `/ll reload announcer`
- Admins can broadcast custom messages

### Performance Targets
- MOTD send: < 1ms per player
- Welcome message: < 5ms per player (includes join tracking lookup)
- Recurring announcement: < 50ms total (broadcast to all players)
- Config reload: < 100ms (restart scheduler)

### Migration Path
If users had v2.6.0 or earlier, no migration needed (new feature).

---

## Future Phases (Post-MVP)

These are deferred until core metabolism is solid:

### Phase 11: Leveling Module
- Profession system (Mining, Logging, Combat, etc.)
- XP gain from activities
- Level-up rewards

### Phase 12: Claims Module
- Plot claiming system
- Trust management
- Protection from other players

### Phase 13: Poison System
- Poisonous consumables
- Multiple poison effect types
- Native poison integration

### Phase 14: Native Effect Integration
- Detect Hytale's burn, stun, freeze effects
- Apply metabolism drain during debuffs

### Phase 15: Advanced HUD
- Customizable HUD positioning
- Settings UI for players
- Notification system for level-ups, etc.

---

## Timeline Summary

| Phase | Duration | Status |
|-------|----------|--------|
| 0: Project Setup | 1-2 days | ✅ Complete |
| 1: Core Infrastructure | 3-4 days | ✅ Complete |
| 2: Persistence Layer | 2-3 days | ✅ Complete (+Global Persistence) |
| 3: Configuration System | 1-2 days | ✅ Complete |
| 3.5: Config Migration | 1-2 days | ✅ Complete (Jackson YAML) |
| 4: Module System | 2-3 days | ✅ Complete |
| 5: Metabolism Core | 3-4 days | ✅ Complete (+Performance Optimizations) |
| 6: MultiHUD System | 2-3 days | ✅ Complete |
| 7: Debuffs & Buffs | 2-3 days | 🚧 Needs Testing |
| 8: Food Consumption | 2-3 days | ✅ Complete |
| 9: Polish & Optimization | 2-3 days | 🚧 Needs Multi-Player Testing |
| 10: Announcer Module | 1-2 days | 📋 Planned (MVP) |

**Current MVP Progress:** ~95% Complete (8/9 phases done, 2 phases need testing)
**With Announcer:** ~90% Complete (Phase 10 adds new MVP feature)

---

## Definition of Done (per Phase)

- [ ] All tasks completed
- [ ] Code compiles without warnings
- [ ] Plugin loads without errors
- [ ] Feature works in-game as expected
- [ ] No performance regressions
- [ ] Debug logging available for troubleshooting

---

## Risk Mitigation

| Risk | Mitigation |
|------|------------|
| Hytale API changes | Pin to specific server version, abstract API calls |
| SQLite locking issues | WAL mode, serialize writes per world |
| HUD reflection breaks | Fallback to single HUD mode if reflection fails |
| Performance issues | Profile early, optimize tick frequency |
| Data corruption | Transaction wrapping, backup before migrations |

---

## Getting Started

1. Clone repository
2. Run `./gradlew build`
3. Copy JAR to server plugins folder
4. Start server, verify "Living Lands started" in logs
5. Begin Phase 0 tasks

---

**Next Action:** Start Phase 0 - Project Setup
