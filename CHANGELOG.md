# Changelog

All notable changes to Living Lands Reloaded will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed
- **Critical: Debug Logging in Production** - Removed 5 `println()` statements from `MetabolismHudElement`
  - Statements were spamming console ~30 times per second per player
  - Eliminated performance degradation and console pollution
- **Critical: Race Condition in Database Initialization** - Fixed async schema creation in `MetabolismService.initializePlayer()`
  - Changed from `scope.launch { it.initialize() }` to `runBlocking { it.initialize() }`
  - Prevents "table not found" SQL errors on first player join
- **Critical: Memory Leak in Coroutine Cleanup** - Improved `WorldContext.cleanup()` safety
  - Coroutine scope now cancelled FIRST to prevent new operations
  - Added try-catch wrapper and proper error logging
  - Prevents resource leaks on plugin shutdown
- **Logging Standardization** - Converted all `java.util.logging.Logger` usage to `HytaleLogger`
  - Updated 6 files: DebuffsSystem, BuffsSystem, SpeedManager, FoodEffectDetector, FoodConsumptionProcessor, MetabolismModule
  - Converted 31+ logger calls to `.atFine().log()`, `.atInfo().log()`, etc.
  - Ensures consistent logging across entire codebase

## [1.0.0-beta] - 2026-01-25

### Added
- **Per-World Configuration System** - Major new feature allowing different metabolism settings per world
  - World-specific config overrides in `metabolism.yml` via `worldOverrides` section
  - Support for world name (case-insensitive) or UUID-based overrides
  - Deep merge strategy - only specify fields that differ from global defaults
  - Hot-reloadable with `/ll reload` - updates all worlds immediately
  - Config validation with warnings for unknown worlds or invalid values
  - Zero performance overhead - configs pre-resolved at world creation
  - Thread-safe with `@Volatile` and `ConcurrentHashMap` for concurrent world access
  
- **Config Validation** - `MetabolismConfigValidator` validates worldOverrides
  - Warns about unknown world names
  - Validates value ranges (negative checks, sensible bounds)
  - Permissive validation - logs warnings instead of failing
  
- **World Registry Enhancements**
  - Added name→UUID mapping for case-insensitive world lookups
  - New methods: `getWorldIdByName()`, `getContextByName()`, `getAllWorldNames()`
  - World names cached for fast resolution

- **UUID String Caching** - Performance optimization
  - Eliminates ~3000 string allocations/second with 100 players
  - Cached strings cleaned up on player disconnect
  - Used throughout service layer for player ID lookups

### Changed
- **Config Schema v3 → v4 Migration**
  - Added `worldOverrides: Map<String, MetabolismWorldOverride>` field
  - Migration automatically adds empty map for existing configs
  - Bumped `MetabolismConfig.CURRENT_VERSION` from 3 to 4

- **Service Layer API Updates**
  - `MetabolismService.processTick()` now accepts `worldConfig` parameter
  - `MetabolismService.processTickWithDelta()` now accepts `worldConfig` parameter
  - Added `getConfigForWorld(WorldContext)` and `getConfigForWorld(UUID)` methods
  - `updateConfig()` now re-resolves configs for all active worlds

- **System Integration Updates**
  - `MetabolismTickSystem` retrieves and passes world-specific config
  - `BuffsSystem.tick()` accepts `BuffsConfig` parameter
  - `DebuffsSystem.tick()` accepts `DebuffsConfig` parameter
  - All helper methods updated to use passed config instead of instance field

- **WorldContext Enhancements**
  - Added `@Volatile metabolismConfig: MetabolismConfig?` field
  - Added `resolveMetabolismConfig()` method for config resolution
  - Configs resolved at world creation and on hot-reload

### Fixed
- **Critical Stamina Debuff Bug** - Fixed negative stamina values
  - Changed from MULTIPLICATIVE to ADDITIVE modifiers
  - Stamina debuffs now correctly reduce max stamina (e.g., 10 → 6.5 for Parched)
  - Added crash recovery system to clean up stale modifiers on rejoin
  - Prevented modifier stacking during tier transitions

- **Thread Safety Issues** - Fixed 3 critical issues from code review
  - Removed nested `world.execute` calls (deadlock risk)
  - Added entity reference validation (`entityRef.isValid` checks)
  - Fixed modifier cleanup on disconnect (memory leak prevention)

- **Nullable Safety** - Fixed `player.world` null handling in MetabolismTickSystem

### Performance
- **Zero Hot-Path Overhead** - Per-world config lookups
  - Config pre-resolved at world creation (one-time cost)
  - Hot path just reads pre-merged config reference (<1ns)
  - No allocations during tick processing

- **Consolidated Player State** - Reduced lookups from 4 to 1 per tick
  - Single `ConcurrentHashMap<String, PlayerMetabolismState>` instead of 4 maps
  - Better cache locality for player data
  - Reduced memory overhead (1 entry per player instead of 4)

### Documentation
- Added `PER_WORLD_CONFIG_PROGRESS.md` - Comprehensive implementation guide
- Added `TESTING_GUIDE.md` - Test scenarios for stamina debuff fix
- Updated `AGENTS.md` - Server paths, debugging commands, per-world config usage

## Example Configuration (v4)

```yaml
# metabolism.yml v4

configVersion: 4
enabled: true

# Global defaults (apply to all worlds)
hunger:
  baseDepletionRateSeconds: 2880.0  # 48 minutes
thirst:
  baseDepletionRateSeconds: 2160.0  # 36 minutes
energy:
  baseDepletionRateSeconds: 2400.0  # 40 minutes

# Per-world overrides
worldOverrides:
  hardcore:  # World name (case-insensitive)
    hunger:
      baseDepletionRateSeconds: 960.0   # 2x faster (16 min)
    thirst:
      baseDepletionRateSeconds: 720.0   # 3x faster (12 min)
  
  creative:
    hunger:
      enabled: false  # Disable metabolism completely
    thirst:
      enabled: false
    energy:
      enabled: false
  
  pvparena:
    energy:
      baseDepletionRateSeconds: 300.0  # 5 minutes
      activityMultipliers:
        combat: 3.0  # 3x drain in combat
```

## Upgrade Notes

### From Any Version to 1.0.0-beta

1. **Config Migration Automatic**
   - Config will auto-migrate from v3 to v4 on first load
   - Empty `worldOverrides` section added automatically
   - No manual intervention required

2. **Per-World Config is Optional**
   - System works without any worldOverrides
   - All worlds use global defaults if no overrides specified
   - Fully backward compatible

3. **Testing Recommended**
   - Restart server to load new JAR
   - Verify config migration succeeded (check logs)
   - Test `/ll reload` to ensure hot-reload works
   - If using multiple worlds, add test override and verify with logs

## Known Issues

- **Base Stamina Hardcoded** - `DebuffsSystem.BASE_STAMINA = 10f`
  - Acceptable for vanilla Hytale (base stamina is 10)
  - May be incorrect if another mod changes base stamina
  - Will be made configurable in future release

- **Hytale Modifier API Quirk** - MULTIPLICATIVE modifiers behave unexpectedly for values < 1.0
  - Workaround: Using ADDITIVE modifiers with calculated offset
  - Risk: Future Hytale updates may change modifier behavior

## Credits

- **Java-Kotlin-Backend Agent** - Architectural guidance and validation
- **Error-Detective Agent** - Log analysis and debugging
- **Code-Review Agent** - Quality assurance and security review

---

[Unreleased]: https://github.com/MoshPitCodes/living-lands-reloaded/compare/v1.0.0-beta...HEAD
[1.0.0-beta]: https://github.com/MoshPitCodes/living-lands-reloaded/releases/tag/v1.0.0-beta
