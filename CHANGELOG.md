# Changelog

All notable changes to Living Lands Reloaded will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Automatic v2.6.0 data migration - JSON profession files automatically imported to new database on first startup

### Fixed
- Removed stray debug output that could spam console under load.
- Fixed a first-join race where the database schema might not exist yet.
- Improved shutdown cleanup to avoid coroutine/resource leaks.

### Changed
- Standardized logging to use the server logger consistently.

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

[Unreleased]: https://github.com/MoshPitCodes/living-lands-reloaded/compare/v1.0.0-beta...HEAD
[1.0.0-beta]: https://github.com/MoshPitCodes/living-lands-reloaded/releases/tag/v1.0.0-beta
