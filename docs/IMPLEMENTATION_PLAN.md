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

## Phase 0: Project Setup (1-2 days)

**Goal:** Buildable Kotlin project that loads as a Hytale plugin

### Tasks

- [ ] **0.1** Initialize Gradle project with Kotlin DSL
  - Kotlin 2.0+ with Java 25 target
  - Configure HytaleServer.jar as compileOnly dependency
  - Setup source sets: `src/main/kotlin`, `src/main/resources`

- [ ] **0.2** Create plugin manifest
  - `plugin.json` with plugin metadata
  - Entry point class reference

- [ ] **0.3** Create minimal plugin entry point
  ```kotlin
  class LivingLandsPlugin(init: JavaPluginInit) : JavaPlugin(init) {
      override fun setup() { logger.info("Living Lands loading...") }
      override fun start() { logger.info("Living Lands started") }
      override fun shutdown() { logger.info("Living Lands shutdown") }
  }
  ```

- [ ] **0.4** Verify plugin loads on server
  - Build JAR
  - Place in server plugins folder
  - Confirm log messages appear

### Deliverable
Plugin JAR that loads, logs messages, and doesn't crash the server.

---

## Phase 1: Core Infrastructure (3-4 days)

**Goal:** CoreModule with service registry, player tracking, and basic world awareness

### Tasks

- [ ] **1.1** Create CoreModule singleton
  ```kotlin
  object CoreModule {
      lateinit var services: ServiceRegistry
      lateinit var players: PlayerRegistry
      lateinit var worlds: WorldRegistry
  }
  ```

- [ ] **1.2** Implement ServiceRegistry
  - Generic `register<T>()` and `get<T>()` methods
  - Thread-safe ConcurrentHashMap backing

- [ ] **1.3** Implement PlayerRegistry
  - Track PlayerSession (playerId, entityRef, store, world)
  - Handle PlayerReadyEvent / PlayerDisconnectEvent
  - Provide `getSession(playerId)` lookup

- [ ] **1.4** Implement WorldRegistry (basic)
  - Track worlds by UUID on AddWorldEvent / RemoveWorldEvent
  - Create WorldContext with worldId and worldName
  - Defer persistence to Phase 2

- [ ] **1.5** Wire up event listeners in plugin
  - Register player events
  - Register world events
  - Verify tracking works via debug logging

### Deliverable
Core infrastructure that tracks players and worlds. Verify with `/ll debug` command showing active players/worlds.

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
Player join/leave persisted to SQLite. Verify data survives server restart.

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

## Phase 3.5: Configuration Migration System (1-2 days)

**Goal:** Automatic config versioning and migration for breaking changes

### Rationale
Config structure changes during development (especially in beta) require migration to preserve user customizations. Without migration, users must manually update or delete configs after plugin updates, leading to poor UX and potential data loss.

### Tasks

- [ ] **3.5.1** Add version tracking to config base interface
  ```kotlin
  interface VersionedConfig {
      val configVersion: Int
  }
  ```

- [ ] **3.5.2** Extend ConfigManager with migration support
  - Add `loadWithMigration<T>()` method
  - Track config version in YAML files
  - Create backup before migration (`.yml.backup`)
  - Log migration actions for transparency

- [ ] **3.5.3** Implement migration registry
  ```kotlin
  class ConfigMigration<T>(
      val fromVersion: Int,
      val toVersion: Int,
      val migrate: (Map<String, Any>) -> Map<String, Any>
  )
  ```

- [ ] **3.5.4** Update MetabolismConfig with version
  - Add `configVersion = 2` field
  - Create migration from v1 (old rates) to v2 (new balanced rates)
  - Example: 480s → 1440s for hunger baseDepletionRateSeconds

- [ ] **3.5.5** Implement automatic migration flow
  - On load, check `configVersion` field
  - If version < current, apply sequential migrations
  - Save migrated config with new version
  - Log: "Migrated metabolism config from v1 to v2"

- [ ] **3.5.6** Add validation and fallback
  - Validate migrated config structure
  - Fall back to defaults if migration fails
  - Preserve backup for manual recovery

- [ ] **3.5.7** Document migration creation for developers
  - Add migration guide to `docs/TECHNICAL_DESIGN.md`
  - Example migrations for reference
  - Best practices for preserving user settings

### Deliverable
Config files automatically upgrade when plugin updates, preserving customizations where possible.

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

## Phase 4: Module System (2-3 days)

**Goal:** Module interface with lifecycle management

### Tasks

- [ ] **4.1** Define Module sealed interface
  - id, name, version, dependencies
  - setup(), start(), shutdown()

- [ ] **4.2** Implement AbstractModule base class
  - Common functionality: logging, event registration helpers
  - Dependency resolution helpers

- [ ] **4.3** Implement module lifecycle in CoreModule
  - Topological sort for dependency order
  - Setup → Start → Shutdown phases
  - Error handling (don't crash on module failure)

- [ ] **4.4** Create stub MetabolismModule
  - Registers with CoreModule
  - Logs lifecycle events
  - No actual functionality yet

- [ ] **4.5** Verify module loading order and lifecycle

### Deliverable
Module system that loads MetabolismModule stub in correct order.

---

## Phase 5: Metabolism Core (3-4 days)

**Goal:** Working hunger/thirst/energy with depletion

### Tasks

- [ ] **5.1** Create MetabolismConfig
  ```kotlin
  data class MetabolismConfig(
      val hunger: StatConfig = StatConfig(enabled = true, depletionRate = 480.0),
      val thirst: StatConfig = StatConfig(enabled = true, depletionRate = 360.0),
      val energy: StatConfig = StatConfig(enabled = true, depletionRate = 600.0)
  )
  ```

- [ ] **5.2** Create MetabolismRepository
  - `metabolism_stats` table schema
  - CRUD operations for MetabolismStats

- [ ] **5.3** Create MetabolismService
  - In-memory state per player per world
  - `initializePlayer()` loads from DB or creates default
  - `processTick()` depletes stats based on time

- [ ] **5.4** Create MetabolismTickSystem (ECS)
  - Query players, call service.processTick()
  - Run every 1 second (not every tick)

- [ ] **5.5** Implement activity detection
  - Read MovementStatesComponent
  - Map to ActivityState enum
  - Apply activity multipliers to depletion

- [ ] **5.6** Save on disconnect and shutdown
  - Persist current stats to DB

- [ ] **5.7** Create `/ll stats` command
  - Show current hunger/thirst/energy values

### Deliverable
Stats deplete over time, faster when sprinting. Values persist across sessions.

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
Players see metabolism bars on screen that update as stats change.

---

## Phase 7: Debuffs & Buffs (2-3 days)

**Goal:** Penalties for low stats, bonuses for high stats

### Tasks

- [ ] **7.1** Implement HysteresisController
  - Generic enter/exit threshold logic
  - Prevents state flickering

- [ ] **7.2** Implement DebuffsSystem
  - Starvation (hunger = 0): damage over time
  - Dehydration (thirst < 20): movement slow
  - Exhaustion (energy < 15): stamina debuff

- [ ] **7.3** Implement SpeedManager
  - Centralized speed modification
  - Track original speed, apply multipliers
  - Handle multiple debuffs stacking

- [ ] **7.4** Implement BuffsSystem (basic)
  - Well-fed bonus when all stats > 90
  - Speed boost, disabled if any debuff active

- [ ] **7.5** Integrate with metabolism tick
  - Check thresholds, apply/remove effects

### Deliverable
Low stats cause penalties, high stats give bonuses. No flickering.

---

## Phase 8: Food Consumption (2-3 days)

**Goal:** Eating food restores stats

### Tasks

- [ ] **8.1** Implement FoodEffectDetector
  - Monitor EffectControllerComponent
  - Detect new food effects by pattern matching

- [ ] **8.2** Create ConsumableRegistry
  - Map effect patterns to restoration values
  - Configurable in metabolism.yml

- [ ] **8.3** Create FoodConsumptionProcessor
  - On detected food effect, restore stats
  - Apply appropriate amounts based on food type

- [ ] **8.4** Run detection at high frequency (50ms)
  - Separate from main metabolism tick
  - Catch instant effects

- [ ] **8.5** Test with various food items

### Deliverable
Eating food restores hunger/thirst/energy appropriately.

---

## Phase 9: Polish & Optimization (2-3 days)

**Goal:** Production-ready performance and stability

### Tasks

- [ ] **9.1** Profile tick systems
  - Ensure no frame drops
  - Optimize hot paths

- [ ] **9.2** Add error handling throughout
  - Catch exceptions, log, continue
  - No crashes from edge cases

- [ ] **9.3** Reduce logging verbosity
  - Debug logs only when config.debug = true
  - INFO level for important events only

- [ ] **9.4** Test with multiple players
  - Verify per-player isolation
  - Check for race conditions

- [ ] **9.5** Test world transitions
  - Player moves between worlds
  - Data correctly isolated per world

- [ ] **9.6** Documentation pass
  - Update README with setup instructions
  - Document all config options

### Deliverable
Stable, performant build ready for wider testing.

---

## Future Phases (Post-MVP)

These are deferred until core metabolism is solid:

### Phase 10: Leveling Module
- Profession system (Mining, Logging, Combat, etc.)
- XP gain from activities
- Level-up rewards

### Phase 11: Claims Module
- Plot claiming system
- Trust management
- Protection from other players

### Phase 12: Poison System
- Poisonous consumables
- Multiple poison effect types
- Native poison integration

### Phase 13: Native Effect Integration
- Detect Hytale's burn, stun, freeze effects
- Apply metabolism drain during debuffs

### Phase 14: Advanced HUD
- Customizable HUD positioning
- Settings UI for players
- Notification system for level-ups, etc.

---

## Timeline Summary

| Phase | Duration | Cumulative |
|-------|----------|------------|
| 0: Project Setup | 1-2 days | 1-2 days |
| 1: Core Infrastructure | 3-4 days | 4-6 days |
| 2: Persistence Layer | 2-3 days | 6-9 days |
| 3: Configuration System | 1-2 days | 7-11 days |
| 3.5: Config Migration | 1-2 days | 8-13 days |
| 4: Module System | 2-3 days | 10-16 days |
| 5: Metabolism Core | 3-4 days | 13-20 days |
| 6: MultiHUD System | 2-3 days | 15-23 days |
| 7: Debuffs & Buffs | 2-3 days | 17-26 days |
| 8: Food Consumption | 2-3 days | 19-29 days |
| 9: Polish & Optimization | 2-3 days | 21-32 days |

**Estimated MVP:** 4-6.5 weeks

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
