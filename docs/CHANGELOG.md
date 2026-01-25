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

### Added
- **UUID String Caching** - New `UuidStringCache` utility eliminates 3000+ string allocations per second with 100 players
- **Creative Mode Pausing** - Metabolism now pauses when players switch to Creative mode
- **Versioned Configuration System** - Automatic config migration with backup support
  - `VersionedConfig` interface for configs requiring migration
  - `ConfigMigration` framework for sequential version updates
  - Timestamped backups created before migrations
  - Migration path validation and fallback handling
- **Enhanced Logging** - INFO-level logs show exact stat values during load/save operations
- **Test Configuration Suite** - Test configs and comprehensive migration testing guide in `test-configs/`
- **Manifest Group Field** - Added required "Group" field to fix plugin detection

### Changed
- **Halved Metabolism Depletion Rates** (v2 config)
  - Hunger: 24 minutes → 48 minutes (idle, 100 to 0)
  - Thirst: 18 minutes → 36 minutes (idle, 100 to 0)
  - Energy: Unchanged at 40 minutes base rate
- **Consolidated Player State** - Replaced 4 separate HashMaps with single `PlayerMetabolismState`
  - Reduces map lookups from 5 to 1 per tick (80% reduction)
  - Better memory locality and cache efficiency
  - Single entry per player instead of 4
- **Mutable State Container** - `PlayerMetabolismState` uses volatile fields for zero-allocation updates
  - Immutable `MetabolismStats` only created for database persistence
  - Eliminates `copy()` allocations on every tick
- **Optimized Timestamp Usage** - Single `System.currentTimeMillis()` call per tick cycle
  - Timestamp passed through update chain to avoid redundant system calls
  - Reduced system calls from 4+ to 1 per tick (75% reduction)
- **Moved Configuration** - `MetabolismConfig` relocated to `config` subpackage for better organization
- **Updated Implementation Plan** - Marked Phase 3.5 (Configuration Migration System) as complete

### Fixed
- **Critical Timing Bug** - Metabolism was processing 30 times per second instead of using proper delta time
  - Players experienced 3x faster depletion than configured
  - Now uses per-player delta tracking with 0.1s minimum interval
- **Plugin Loading** - Added missing "Group" field to manifest.json preventing server detection
- **UUID String Allocations** - Eliminated repeated `UUID.toString()` calls in hot paths
- **Persistence Verification** - Confirmed stats are properly loaded from and saved to database

### Removed
- **Unused Fields** - Removed `lastTickTimeMs` and `tickIntervalMs` volatile fields from `MetabolismTickSystem`
  - Dead code that was never used after refactoring
  - Small reduction in volatile read overhead

### Performance
Impact with 100 concurrent players:

| Metric | Before | After | Improvement |
|--------|--------|-------|-------------|
| String allocations/sec | 3000+ | 0 | **100%** ✓ |
| HashMap lookups per tick | 5 | 1 | **80%** ✓ |
| Object allocations per tick | 1+ | 0 | **100%** ✓ |
| System calls per tick | 4+ | 1 | **75%** ✓ |
| Memory overhead per player | 4 Entry objects | 1 Entry object | **75%** ✓ |

---

## [1.0.0-beta] - 2026-01-25

### Added
- Initial beta release
- Core infrastructure with CoreModule, registries, and service locator
- Metabolism system with hunger, thirst, and energy tracking
- Per-world SQLite persistence with automatic schema initialization
- Multi-component HUD system for displaying metabolism stats
- YAML configuration with hot-reload support
- `/ll` command with subcommands (reload, stats)
- Event-driven player session management
- ECS integration with tick-based metabolism processing
- Activity state detection (idle, walking, sprinting, swimming, combat)
- Activity-based depletion multipliers
- Hysteresis for state changes to prevent flickering
- Database migration system for schema versioning
- Comprehensive logging with debug mode support
- Thread-safe concurrent collections for player data

### Technical Details
- **Language**: Kotlin 2.0+ targeting Java 25
- **Build System**: Gradle 9.3.0 with Kotlin DSL
- **Server**: Hytale Server API (30 TPS tick rate)
- **Persistence**: SQLite with WAL mode and connection pooling
- **Architecture**: Modular ECS-based system with per-world contexts

### Known Issues
- Config YAML serialization uses Java object notation instead of proper YAML (cosmetic only, works correctly)
- Database operations use `runBlocking` on World Thread (causes brief freezes on player join)
- Single database connection with global lock (potential bottleneck at extreme scale)
- HUD reflection could be optimized with MethodHandle

### Configuration Files
Default configuration created on first run:
- `LivingLandsReloaded/config/core.yml` - Master settings and module toggles
- `LivingLandsReloaded/config/metabolism.yml` - Metabolism depletion rates and multipliers

### Database Schema
Per-world SQLite databases created at:
- `LivingLandsReloaded/data/{world-uuid}/livinglands.db`

Tables:
- `players` - Player registry with join times
- `metabolism_stats` - Hunger, thirst, energy per player
- `schema_versions` - Module schema version tracking

---

## Version History

- **v1.0.0-beta** (2026-01-25) - Initial beta release
- **Unreleased** - Performance optimizations and config migration system

---

## Migration Guide

### Upgrading to Unreleased (from v1.0.0-beta)

**Automatic Migration:**
- Metabolism config will auto-migrate from v1 to v2 on startup
- Backup created as `metabolism.pre-migration-v1.TIMESTAMP.yml.backup`
- New depletion rates (2x slower) will be applied

**Manual Configuration:**
If you customized your v1 config, the migration will preserve your customizations and update the base rates proportionally. Review the migrated config to ensure your custom values are acceptable.

**Breaking Changes:**
- None - All changes are backward compatible
- Old v1 configs will auto-migrate
- Database schema unchanged

**Performance Impact:**
- Significantly improved performance at scale (50+ players)
- Reduced memory usage per player
- Smoother tick performance (no more allocation spikes)

---

## Roadmap

### Phase 4: Module System (Next)
- [ ] Module lifecycle management
- [ ] Dynamic module loading/unloading
- [ ] Module dependency resolution

### Phase 5: Metabolism Core
- [ ] Buff/debuff system based on stat thresholds
- [ ] Death penalties for depleted stats
- [ ] Food consumption integration

### Phase 7: Food System
- [ ] Food item detection and consumption
- [ ] Food effect application (restore stats)
- [ ] Custom food configuration

### Future Enhancements
- [ ] Async player initialization (remove `runBlocking`)
- [ ] Connection pooling for database (HikariCP)
- [ ] MethodHandle for HUD reflection optimization
- [ ] Batch database writes for periodic saves
- [ ] Additional stats (temperature, sanity, etc.)
- [ ] Potion effects integration
- [ ] Admin commands for stat manipulation
- [ ] Metrics and monitoring endpoints

---

## Credits

**Developer:** MoshPitCodes  
**GitHub:** https://github.com/MoshPitCodes/hytale-livinglands  
**License:** MIT  

### Acknowledgments
- Performance audit conducted by java-kotlin-backend agent
- Architecture review and optimization recommendations
- Hytale modding community for API documentation

---

## Support

**Issues:** Report bugs at https://github.com/MoshPitCodes/hytale-livinglands/issues  
**Documentation:** See `/docs` directory for technical details  
**Testing:** See `test-configs/TESTING_GUIDE.md` for migration testing

---

[Unreleased]: https://github.com/MoshPitCodes/hytale-livinglands/compare/v1.0.0-beta...HEAD
[1.0.0-beta]: https://github.com/MoshPitCodes/hytale-livinglands/releases/tag/v1.0.0-beta
