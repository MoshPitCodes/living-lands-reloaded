# Changelog

All notable changes to Living Lands Reloaded will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed
- **WorldContext Cleanup** - Added 100ms grace period for coroutines during world cleanup to prevent data loss from interrupted database writes
- **Config Ambiguity Warning** - Added warning log when world has conflicting metabolism config overrides (by name and UUID)
- **Memory Leak Prevention** - Enhanced FoodEffectDetector cleanup to remove empty player maps, preventing ~10KB memory leak per player per 8-hour session

### Changed
- WorldContext cleanup now waits briefly for in-flight persistence operations before forcing cancellation
- World config resolution now explicitly logs precedence when both name and UUID overrides exist

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

[Unreleased]: https://github.com/MoshPitCodes/living-lands-reloaded/compare/v1.2.3...HEAD
[1.2.3]: https://github.com/MoshPitCodes/living-lands-reloaded/releases/tag/v1.2.3
[1.1.0]: https://github.com/MoshPitCodes/living-lands-reloaded/releases/tag/v1.1.0
[1.0.1-beta]: https://github.com/MoshPitCodes/living-lands-reloaded/releases/tag/v1.0.1-beta
[1.0.0-beta]: https://github.com/MoshPitCodes/living-lands-reloaded/releases/tag/v1.0.0-beta
