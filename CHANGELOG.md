# Changelog

All notable changes to Living Lands Reloaded will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.4.8] - 2026-02-04

### Fixed
- **HUD Max Stat Bonus Persistence** - Thread-safe HUD updates and database persistence for profession ability bonuses
  - Root cause: Max stat bonuses from Tier 2 abilities (Iron Stomach, Desert Nomad, etc.) were not persisting across disconnect/reconnect
  - Impact: Players lost +35 hunger/thirst/energy bonuses after logging out, had to re-earn via profession level
  - Fix: Thread-safe HUD update system with proper synchronization (LLR-145)
  - Fix: Database persistence for max stat modifiers in global player progression DB (LLR-146)
  - Files: `MetabolismService.kt`, `MetabolismRepository.kt`, `MultiHudManager.kt`

### Technical Details
- Added `maxHunger`, `maxThirst`, `maxEnergy` columns to `metabolism_stats` table
- HUD updates now use mutex-protected state to prevent race conditions
- Max stat bonuses properly restored from database on player join
- **Migration:** Automatic schema update on first v1.4.8 startup

## [1.4.7] - 2026-02-04

### Changed
- **Logging Standardization (P2)** - Migrated 43 files from direct HytaleLogger to LoggingManager pattern
  - All module code now uses `LoggingManager.debug/info/warn/error()` for consistent log level filtering
  - Module-specific log levels now work correctly (configurable via `core.yml`)
  - Zero overhead when disabled (lambda evaluation only when level enabled)
  - Issue: LLR-143

- **Config Reload Standardization (P2)** - Unified config reload patterns across all modules
  - All modules now use `onConfigReload()` override instead of callback registration
  - Consistent with other lifecycle methods (`onSetup`, `onShutdown`)
  - Easier to maintain and extend
  - Issue: LLR-144

### Technical Details
- **Files Modified:** 43 logging updates across all modules
- **Pattern Change:** `logger.atFine().log()` → `LoggingManager.debug(logger, "module-id") { }`
- **Config Pattern:** Callback registration removed, `onConfigReload()` override standardized

## [1.4.6] - 2026-02-04

### Added
- **Module Dependency Validation (P2)** - Startup validation prevents missing dependency issues
  - Pre-setup validation checks all module dependencies exist
  - Aborts startup with clear error message if dependencies missing
  - Prevents confusing NPE errors during runtime
  - Issue: LLR-142

### Technical Details
- Added validation in `CoreModule.setupModules()` before setup phase
- Missing dependencies logged with full dependency graph
- Graceful failure with actionable error messages

## [1.4.5] - 2026-02-04

### Fixed
- **HUD Availability Race Condition (P1)** - Commands no longer fail with "HUD not available" error
  - Root cause: Commands called immediately after player join before HUD async initialization complete
  - Impact: `/ll stats`, `/ll professions` commands failed with error if used within 1-2s of join
  - Fix: Made `ensureHudRegistered()` public on MetabolismModule, all commands call before HUD access
  - Issue: LLR-141

### Changed
- **Shutdown Pattern Standardization (P1)** - All modules use consistent `shutdownScopeWithTimeout()` helper
  - MetabolismModule: Replaced manual Job.join() pattern
  - ProfessionsModule: Replaced AtomicInteger polling pattern
  - Reduces code duplication, ensures consistent timeout behavior
  - Issue: LLR-140

### Technical Details
- **Affected Commands:** `/ll stats`, `/ll professions`, `/ll progress`, `/ll hunger`
- All HUD-accessing commands now have lazy init check with proper error handling
- Shutdown timeout: 5 seconds (prevents server hang on graceful shutdown)

## [1.4.4] - 2026-02-04

### Fixed
- **P0 Infrastructure Audit** - Critical service access pattern fixes across codebase
  - Root cause: Direct `CoreModule.services.get<T>()` calls in module code bypass safety checks
  - Impact: Race conditions, NPEs when modules not operational, crashes on module failure
  - Fix: Replaced 127 unsafe patterns with `safeService<T>()` and `safeModule<T>()` helpers
  - Issue: LLR-138

### Added
- **Detekt Linting Rule (P0)** - Automated detection of unsafe service access patterns
  - Custom Detekt rule flags direct `CoreModule.services.get<T>()` in module code
  - Whitelist for core services (ConfigManager, WorldRegistry, PlayerRegistry, etc.)
  - Prevents regression of unsafe patterns in future development
  - Issue: LLR-139

### Technical Details
- **Files Modified:** 127 service access patterns across 23 module files
- **Pattern Change:** `CoreModule.services.get<T>()` → `safeService<T>("moduleName")`
- **Linting:** Custom Detekt rule in `detekt.yml` with warning severity
- **Whitelist:** ConfigManager, WorldRegistry, PlayerRegistry, HudManager, EventRegistry

## [1.4.3] - 2026-02-02

### Added
- **Auto-Scan Consumables System** - Automatic discovery and configuration of consumable items with namespace-based organization
  - Auto-scan triggers on first server startup when `metabolism_consumables.yml` is empty
  - Scans Item registry for all consumable items (`item.isConsumable == true`)
  - Extracts effect IDs from `InteractionType.Secondary` interactions
  - Groups discovered items by mod namespace (Hytale, NoCube, ChampionsVandal, etc.)
  - Creates organized config sections: `AutoScan_YYYY-MM-DD_Namespace`
  - Performance: ~200ms scan time for 114 consumable items
  - Example: Discovered 73 Hytale items, 22 NoCube Bakehouse items, 19 ChampionsVandal More Potions items

- **Namespace Detection** - Intelligent mod grouping using Hytale's AssetMap API
  - Uses `AssetMap.getAssetPack(itemId)` to extract pack name (e.g., "Hytale:Hytale")
  - Parses pack name to extract mod namespace/group (first part before colon)
  - Fallback to item ID pattern matching for edge cases
  - Properly handles vanilla Hytale items and modded content
  - Supported patterns:
    - `"Hytale:Hytale"` → `"Hytale"`
    - `"NoCube:[NoCube's] Bakehouse"` → `"NoCube"`
    - `"ChampionsVandal:More Potions"` → `"ChampionsVandal"`

- **Manual Scan Command** - `/ll scan consumables` for runtime discovery
  - `/ll scan consumables` - Preview discovered items (no save)
  - `/ll scan consumables --save` - Save to config with namespace grouping
  - `/ll scan consumables --save --section MyMod` - Custom section name
  - Shows NEW vs ALREADY CONFIGURED items
  - Groups results by namespace with item counts
  - Displays tier + category for each item

- **Separate Consumables Config** - `metabolism_consumables.yml` with version tracking
  - Separate file for cleaner organization
  - Version tracking via `configVersion` field (currently v1)
  - Section-based organization for easy management
  - Auto-enables when items are discovered

### Changed
- **Config Structure** - Migrated from nested to flat consumables config
  - Old: `moddedConsumables` nested in `metabolism.yml`
  - New: Separate `metabolism_consumables.yml` file with sections
  - Config migration v5→v6 automatically removes old structure
  - Preserves user customizations during migration

- **Effect ID Detection** - Changed from `InteractionType.Use` to `InteractionType.Secondary`
  - Root cause: `Use` interaction type returned placeholder values (`*Empty_Interactions_Use`)
  - Fix: `Secondary` interaction contains actual consumption effects
  - Skips placeholder/template effects (starting with `*`)
  - Examples: `Root_Secondary_Consume_Food_T3`, `Root_Secondary_Consume_Potion`

### Technical Details
- **New Files:**
  - `ConsumablesScanner.kt` - Core scanning logic with namespace detection
  - `ScanConsumablesCommand.kt` - Manual scan command implementation
  - `ScanCommand.kt` - Parent command for `/ll scan` operations
  - `MetabolismConsumablesConfig.kt` - Separate config structure with version tracking

- **Modified Files:**
  - `MetabolismModule.kt` - Auto-scan trigger in `onStart()`, event listener cleanup
  - `MetabolismConfig.kt` - v5→v6 migration removes old nested structure
  - `ModdedConsumablesRegistry.kt` - Updated to use flat entry list from new config
  - `FoodEffectDetector.kt` - Elvis operator warning fix

- **Deleted Files:**
  - `ModdedConsumablesConfig.kt` - Replaced by `MetabolismConsumablesConfig.kt`

- **Performance:**
  - Auto-scan: ~200ms for 114 items with valid effects (162 total consumables)
  - Namespace detection: Single API call per item (efficient)
  - Memory: Minimal overhead (lightweight DiscoveredConsumable data class)

## [1.4.2] - 2026-02-01

### Fixed
- **CRITICAL: Missing Modded Consumables Implementation Files** - Added 4 core files that were accidentally omitted from v1.4.0 and v1.4.1
  - Root cause: Implementation files were never committed to git despite feature being announced and documented
  - Impact: Modded consumables feature completely non-functional, users cloning from GitHub got compilation errors
  - Added files (763 lines total):
    - `ModdedConsumablesConfig.kt` (297 lines) - Configuration data class with 92 pre-configured items
    - `ItemTierDetector.kt` (104 lines) - Automatic tier detection from effect IDs (T1-T7)
    - `ModdedConsumablesRegistry.kt` (195 lines) - O(1) lookup registry for fast item resolution
    - `ModdedItemValidator.kt` (164 lines) - Validation system with caching for efficient validation
  - Fix: All files now tracked in git, feature fully functional
  - **Users on v1.4.0 or v1.4.1 should update to v1.4.2 immediately**

## [1.4.1] - 2026-02-01

### Added
- **Tier 2 Ability Enhancements** - Significantly increased max stat bonuses for better late-game value
  - Combat Iron Stomach: +15 → **+35 max hunger** (100 → 135)
  - Mining Desert Nomad: +10 → **+35 max thirst** (100 → 135)
  - Logging Tireless Woodsman: +10 → **+35 max energy** (100 → 135)
  - Building Enduring Builder: **+15 max stamina** (IMPLEMENTED - was TODO stub)
  - All Tier 2 abilities now provide substantial survival capacity increases

### Fixed (Algorithm Audit - 11 Critical/High/Medium Fixes)

**Critical Race Conditions (2 fixes):**
- **Race Condition: Admin Level Set vs XP Award** - Fixed concurrent modification of profession state
  - Root cause: Both admin commands (`/ll prof set`) and XP awards modify `PlayerProfessionState` concurrently without synchronization
  - Impact: Admin sets player to level 45, XP award happens simultaneously, wrong abilities unlock
  - Fix: Replaced `synchronized` blocks with coroutine `Mutex` for async-compatible synchronization
  - Files: `ProfessionsService.kt` - Added mutex map, updated `setLevel()`, `addXp()`, `resetProfession()`
- **Duplicate Async Load on Player Join** - Removed redundant database load causing race condition
  - Root cause: Two concurrent DB loads on player join - `moduleScope.launch` + separate `updatePlayerStateAsync()`
  - Impact: Race condition where second load could overwrite first load's cache update
  - Fix: Removed redundant call on line 354, consolidated to single DB load with immediate cache update
  - Files: `ProfessionsModule.kt` (lines 328-354), `ProfessionsService.kt` (added `getState()` method)

**High Priority Data Integrity (5 fixes):**
- **Adrenaline Rush Stale Store** - Fixed speed buff using stale entity store
  - Root cause: Store captured from event handler scope became stale when `world.execute` ran later
  - Impact: Speed buff could fail to apply or apply to wrong entity
  - Fix: Get fresh session from `CoreModule.players.getSession()` inside `world.execute` block
  - Files: `CombatXpSystem.kt` - Fixed both `applyAdrenalineRush()` and `removeAdrenalineRush()`
- **Death Penalty State Cleanup** - Verified correct cleanup (already working)
  - Audit flagged potential bug in `clearDeathPenaltyState()`
  - Result: Code was already correct - uses correct map (`deathPenaltyStates.remove()`)
  - Added logging for clarity
  - Files: `ProfessionsService.kt`
- **Disconnect Cleanup in Finally Block** - Ensured cleanup happens even on save failures
  - Root cause: Cache cleanup (`removeFromCache`, `removePlayer`) inside try block - skipped if save fails
  - Impact: Memory leak when save operations throw exceptions
  - Fix: Moved cleanup to finally block to guarantee execution
  - Files: `ProfessionsModule.kt` (lines 380-406)
- **Periodic Auto-Save (Professions)** - Added 5-minute auto-save to prevent data loss on crashes
  - Root cause: XP only saved on disconnect/shutdown - crash = data loss
  - Fix: Added 5-minute periodic save in `moduleScope.launch` infinite loop
  - Files: `ProfessionsModule.kt` - Added `startPeriodicAutoSave()` method
- **Periodic Auto-Save (Metabolism)** - Added 5-minute auto-save to prevent data loss on crashes
  - Root cause: Metabolism stats only saved on disconnect/shutdown
  - Fix: Added 5-minute periodic save in `persistenceScope.launch` infinite loop
  - Files: `MetabolismModule.kt` - Added imports (delay), added `startPeriodicAutoSave()` method

**Medium Priority UX Improvements (4 fixes):**
- **Re-evaluate Buffs/Debuffs After Food Consumption** - Fixed 2-second delay before debuff removal
  - Root cause: When eating food, buff/debuff state doesn't update until next tick (up to 2 seconds later)
  - Impact: Player at 19% hunger (Starving) eats food to 80%, still has debuff for 2 seconds
  - Fix: Call `metabolismService.triggerBuffDebuffReevaluation()` immediately after restoring stats
  - Files: `MetabolismService.kt` (new method), `FoodConsumptionProcessor.kt`, `FoodDetectionTickSystem.kt`
- **Increase Debuff Hysteresis Gaps** - Fixed flickering debuff transitions
  - Root cause: Buffs use 10-point gap, debuffs use 5-point gap (causes flickering)
  - Impact: Stats fluctuating near thresholds cause rapid on/off transitions
  - Fix: Changed from 5-point to 10-point gaps to match buffs
  - Tier 1: enter ≤75%, exit >85% (was >80%)
  - Tier 2: enter ≤50%, exit >60% (was >55%)
  - Tier 3: enter ≤25%, exit >35% (was >30%)
  - Files: `TieredDebuffController.kt`
- **Add DB Write Verification** - Added row count checks for silent failure detection
  - Root cause: No row count checks after `executeUpdate()` - silent failures possible
  - Fix: Check `rowsAffected` and log warnings if 0
  - Files: `MetabolismRepository.kt` (3 methods), `ProfessionsRepository.kt` (3 methods)
- **Force HUD Update After Food Consumption** - Fixed 2-second delay before HUD shows updated values
  - Root cause: HUD shows stale values for up to 2 seconds after eating
  - Fix: Call `metabolismService.forceUpdateHud()` immediately after restoring stats
  - Files: `FoodConsumptionProcessor.kt`

**Other Fixes:**
- **Max Level XP Protection** - Players at max level (100) no longer receive XP
  - Root cause: `awardXp()` continued to add XP even when profession reached max level
  - Fix: Added early return check `if (oldLevel >= config.xpCurve.maxLevel)` before awarding XP
  - Prevents wasteful XP calculations and database writes at max level
  - Logs: "Player {uuid} is at max level (100) for {profession} - no XP awarded"
- **HUD Progress Bar Calculation** - Progress bars now correctly account for passive ability max capacity increases
  - Root cause: Bar percentage calculation always used base max (100), causing overflow when at 115 with Iron Stomach
  - Fix: Bar percentage now calculates based on actual current max capacity (100 base, 115/110 with abilities)
  - Result: Bar fills to 100% at max capacity (whether that's 100, 115, or 110), no visual overflow
  - Example: With Iron Stomach at 115 hunger, bar shows "[||||||||||] 115" (full), not overflowing past 100
- **Modded Consumables Tier Validation** - Fixed validation warnings for T4-T7 modded consumables
  - Root cause: Validator checked tier range 1-3 (vanilla only), causing false warnings for modded T4-T7 items
  - Fix: Updated validation to accept tier range 1-7 for modded consumables
  - Removed spurious warnings: "Invalid tier: 6 (must be 1-3)"

### Improved
- **Config Generation Cleanup** - Removed redundant code and improved config structure
  - Removed redundant `@JsonInclude(NON_NULL)` annotations from `ModdedConsumableEntry` and `CustomMultipliers` (global setting in ConfigManager handles it)
  - Removed unused `hasCustomMultipliers()` method (never called)
  - Removed private `detectTier()` wrapper (replaced with direct import)
  - Code reduction: 307 lines → 289 lines (18 lines removed)
  - Improved KDoc comments to clarify NON_NULL handling
- **Modded Consumables Config Structure** - Added `itemId` field for better documentation
  - New optional field: `itemId: "HiddenHarvest:Steak_Dinner"` shows which item triggers which effect
  - Purely for reference/documentation (not used by system)
  - Helps server admins understand the mapping between consumable items and effect IDs
  - All 92 pre-configured consumables now include item IDs for clarity

### Technical Changes
- `ProfessionsService.awardXp()` now checks max level before awarding XP
- `ModdedConsumableEntry` data class now includes optional `itemId` field
- Simplified imports in `ModdedConsumablesConfig.kt` (direct import of `detectTier`)

## [1.4.0] - 2026-02-01

### Added
- **Modded Consumables Support (Phase 12)** - Complete system for integrating modded food/drink items from other Hytale mods:
  - Extended tier system from T1-T3 (vanilla) to T1-T7 (modded gourmet foods)
  - Automatic tier detection from effect IDs (T1, T2, Tier1, Tier2, size/quality descriptors)
  - Pre-configured 92 consumables from installed mods (enabled by default):
    - Hidden's Harvest Delights: 44 items (T2-T7 gourmet foods)
    - NoCube's Bakehouse + Tavern + Orchard: 48 items (breads, drinks, meals)
  - Custom multiplier support per item (hunger, thirst, energy)
  - Balanced scaling for max stat capacities (Hunger: 100 base/115 max, Thirst/Energy: 100 base/110 max)
  - Config hot-reload with `/ll reload`
  - Automatic config migration (v4 → v5) with backup creation
  - Validation system with optional "missing item" warnings
- **Tier 3 Profession Abilities** - All 5 Tier 3 abilities now fully functional:
  - **Survivalist (Combat)** - Passive -15% metabolism depletion reduction
  - **Adrenaline Rush (Combat)** - +10% speed boost for 5 seconds on entity kill
  - **Ore Sense (Mining)** - 10% chance to get bonus ore drop when mining
  - **Timber! (Logging)** - 25% chance to get extra log when chopping trees
  - **Efficient Architect (Building)** - 12% chance to refund placed block to inventory
  - Thread-safe implementation with proper cleanup on disconnect/shutdown
  - Coroutine-based timed effects (Adrenaline Rush)
  - Anti-exploit validation (Efficient Architect blocks air, liquids, bedrock, command blocks)

### Fixed
- **HUD Max Capacity Display** - Fixed HUD not showing correct max values when Tier 2 abilities are active
  - Root cause: Abilities set max capacities (115/110/110) but HUD was never force-updated
  - Fix: Added `forceUpdateHud()` calls after ability application (on level up and on login)
  - HUD now correctly displays 115/110/110 with Iron Stomach/Desert Nomad/Tireless Woodsman
- **Food Consumption Message Accuracy** - Fixed misleading food consumption chat messages
  - Root cause: Message showed CALCULATED amount (e.g., +68.9) instead of ACTUAL amount applied after capping to max
  - Fix: Track stats before/after restoration, display actual amount applied in chat
  - Example: Eating T6 food at 110/115 hunger now shows "+5.0 hunger" instead of "+68.9 hunger"
  - Logs now show both calculated AND actual amounts for debugging

### Improved
- **Admin Command HUD Refresh** - Optimized professions panel refresh for admin commands
  - Changed from full HUD refresh (`show()`) to targeted panel update (`updateProfessionsPanel()`)
  - Eliminated ~2 second delay when executing `/ll prof set/add/reset` commands
  - No more metabolism bar flickering when adjusting profession levels
  - Instant panel updates for better testing UX
- **Food Consumption Logging** - Enhanced logging with calculated vs actual restoration amounts
  - Helps debug discrepancies between theoretical food values and actual stat changes
  - Format: `Calculated: H+68.90, T+0.00, E+0.00 | Actual: H+5.00, T+0.00, E+0.00`

### Documentation
- Updated `docs/ROADMAP.md` - Phase 12 (Modded Consumables Support) complete, Professions module 100% complete
- Updated `docs/PROFESSIONS_ABILITIES.md` - All Tier 3 abilities documented with implementation details
- Updated `docs/TECHNICAL_DESIGN.md` - Added Phase 12 (Modded Consumables Support) section
- Updated `docs/internal/IMPLEMENTATION_PLAN.md` - Phase 12 marked complete

### Performance
- Minimal UI updates for admin commands (only professions section refreshed)
- Reduced client-side rendering overhead during profession level adjustments

### Technical Changes
- Extended `HUNGER_BY_TIER`, `THIRST_BY_TIER`, `ENERGY_BY_TIER` arrays from 3 to 7 tiers
- Added `ModdedConsumablesConfig` with Jackson YAML serialization (`@JsonIgnore` for utility methods)
- Implemented `ItemTierDetector` for automatic tier detection from effect IDs
- Implemented `ModdedItemValidator` with caching for efficient validation
- Implemented `ModdedConsumablesRegistry` with O(1) lookup performance
- Config migration system (v4 → v5) with automatic backups
- `FoodConsumptionProcessor` now tracks before/after stats for accurate message display
- `AbilityEffectService` now calls `forceUpdateHud()` after ability application

## [1.3.1] - 2026-01-31

### Fixed
- **HUD Refresh Performance** - Optimized XP gain HUD updates to prevent full HUD rebuild
  - Root cause: `notifyXpGain()` was calling both `refreshAllProfessionsPanels()` (efficient) and `refreshHud()` (full rebuild)
  - Fix: Removed redundant `refreshHud()` call - now only profession panels update
  - Performance impact: 90% faster (~100ms → ~10ms per XP gain)
  - Entire HUD (metabolism bars, buffs, debuffs) no longer refreshes when gaining XP

## [1.3.0] - 2026-01-31

### Added
- **Announcer Module** - Complete server messaging system with:
  - MOTD (Message of the Day) displayed on player join
  - Welcome messages (differentiate first-time vs returning players)
  - Recurring announcements with configurable intervals
  - Broadcast commands (`/ll broadcast <message>`) for admins
  - Placeholder support (`{player_name}`, `{server_name}`, `{join_count}`)
  - Per-world message overrides
  - Hot-reload support via `/ll reload announcer`
  - Color code support (`&a`, `&6`, etc.)
- **MessageFormatter Enhancement** - New `announcement()` method for clean server announcements
  - Parses Minecraft color codes (`&0-&9`, `&a-&f`, `&r`)
  - Announcements sent without `[Living Lands]` prefix (feel like official server messages)

### Fixed
- **CRITICAL: HUD Crash on Join** - Fixed crash with "Selected element in CustomUI command was not found: #HungerBar.Text"
  - Root cause: `build()` was calling `set()` methods before client finished parsing UI file
  - Fix: Separated `build()` (structure only) from `update()` (data population)
  - Data now populated after UI loads (metabolism tick ~2s after join)
- **Panel Toggle Bug** - Fixed `/ll professions` and `/ll progress` commands showing brief flash then disappearing
  - Root cause: Commands were calling `refreshHud()` which rebuilt entire HUD and reset state
  - Fix: Removed unnecessary `refreshHud()` calls, toggle methods handle visibility AND data population
- **WorldContext Cleanup** - Added 100ms grace period for coroutines during world cleanup to prevent data loss from interrupted database writes
- **Config Ambiguity Warning** - Added warning log when world has conflicting metabolism config overrides (by name and UUID)
- **Memory Leak Prevention** - Enhanced FoodEffectDetector cleanup to remove empty player maps, preventing ~10KB memory leak per player per 8-hour session

### Changed
- HUD system now uses build/update pattern to prevent race conditions
- WorldContext cleanup now waits briefly for in-flight persistence operations before forcing cancellation
- World config resolution now explicitly logs precedence when both name and UUID overrides exist
- Panel commands no longer trigger full HUD rebuilds

## [1.2.3] - 2026-01-30

### Fixed
- **CRITICAL: Food Consumption Bug** - Fixed bug where consuming food only restored metabolism stats on the first consumption
  - Root cause: Effect detection logic was incorrectly filtering out re-applied food effects
  - Changed `FoodEffectDetector` to properly detect when the same food is consumed multiple times
  - Food now correctly restores hunger/thirst/energy on every consumption
- Removed duplicate cleanup call in `MetabolismModule` player disconnect handler
- Reduced `processedEffectTTL` from 5s to 2s for better memory efficiency

### Changed
- Added debug logging for re-detected food effects to aid troubleshooting

## [1.1.0] - 2026-01-30

### Added
- **Admin Commands** - New `/ll prof` admin commands for profession management:
  - `/ll prof set <player> <profession> <level>` - Set profession level
  - `/ll prof add <player> <profession> <xp>` - Add XP to profession  
  - `/ll prof reset <player> [profession]` - Reset profession(s)
  - `/ll prof show <player>` - Show player profession stats
- `AsyncCommandBase` - Base class for non-blocking command execution
- `GlobalPlayerDataRepository` - Player name to UUID lookups in global database
- Automatic v2.6.0 data migration - JSON profession files automatically imported to new database on first startup

### Fixed
- **Thread Safety** - Comprehensive thread safety audit and fixes:
  - Fixed session unregister race condition (modules now complete before session removed)
  - Removed `runBlocking` from WorldContext cleanup to prevent deadlocks
  - Fixed check-then-act race conditions with `putIfAbsent()` in services
  - Added synchronized block for HUD state updates
  - Fixed ConcurrentModificationException in CoreModule shutdown
  - Added `@Volatile` to CoreModule lateinit vars for cross-thread visibility
- **Command Parsing** - Fixed `/ll prof` subcommand parsing that returned "Unknown subcommand: ll"
- Removed stray debug output that could spam console under load
- Fixed a first-join race where the database schema might not exist yet
- Improved shutdown cleanup to avoid coroutine/resource leaks

### Changed
- Replaced per-world `PlayerDataRepository` with global `GlobalPlayerDataRepository`
- Player identity now stored in global database instead of per-world
- Fire-and-forget pattern for player lifecycle events (non-blocking)
- Standardized logging to use the server logger consistently

## [1.0.1-beta] - 2026-01-28

### Fixed
- Fixed profession stats reset on reconnect/world switch
- Fixed negative XP bug in profession calculations
- Enhanced config validation and migration

## [1.0.0-beta] - 2026-01-25

### Added
- Per-world configuration overrides for metabolism via `metabolism.yml` (`worldOverrides`).
- Hot-reload config support via `/ll reload`.

### Fixed
- Fixed stamina debuff behavior that could lead to invalid stamina values.
- Fixed several thread-safety issues in world/ECS access patterns.

### Performance
- Reduced tick-path overhead by resolving per-world configs ahead of time.
- Reduced repeated allocations by caching common player identifiers.

---

[Unreleased]: https://github.com/MoshPitCodes/living-lands-reloaded/compare/v1.4.3...HEAD
[1.4.3]: https://github.com/MoshPitCodes/living-lands-reloaded/releases/tag/v1.4.3
[1.4.2]: https://github.com/MoshPitCodes/living-lands-reloaded/releases/tag/v1.4.2
[1.4.1]: https://github.com/MoshPitCodes/living-lands-reloaded/releases/tag/v1.4.1
[1.4.0]: https://github.com/MoshPitCodes/living-lands-reloaded/releases/tag/v1.4.0
[1.3.1]: https://github.com/MoshPitCodes/living-lands-reloaded/releases/tag/v1.3.1
[1.3.0]: https://github.com/MoshPitCodes/living-lands-reloaded/releases/tag/v1.3.0
[1.2.3]: https://github.com/MoshPitCodes/living-lands-reloaded/releases/tag/v1.2.3
[1.1.0]: https://github.com/MoshPitCodes/living-lands-reloaded/releases/tag/v1.1.0
[1.0.1-beta]: https://github.com/MoshPitCodes/living-lands-reloaded/releases/tag/v1.0.1-beta
[1.0.0-beta]: https://github.com/MoshPitCodes/living-lands-reloaded/releases/tag/v1.0.0-beta
