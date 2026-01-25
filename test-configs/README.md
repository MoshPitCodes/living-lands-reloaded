# Test Configurations for Migration System

This directory contains test configuration files and documentation for validating the Phase 3.5 configuration migration system.

## Contents

### Test Config Files

- **`metabolism-v1-defaults.yml`** - v1 config with original default values
  - All depletion rates set to old defaults (480s/360s/600s)
  - Should migrate and update all rates to v2 defaults

- **`metabolism-v1-customized.yml`** - v1 config with user customizations
  - Custom depletion rates different from defaults
  - Should preserve user values during migration

- **`metabolism-no-version.yml`** - Config without version field
  - Should be treated as v1 and migrated

- **`core-v1.yml`** - CoreConfig v1
  - Currently has no migrations defined

### Documentation

- **`TESTING_GUIDE.md`** - Comprehensive testing procedures
  - 8 test scenarios covering all migration paths
  - Expected results for each scenario
  - Log messages to look for
  - Troubleshooting guide

## Quick Start

1. **Build the plugin** (requires HytaleServer.jar in `libs/Server/`):
   ```bash
   ./gradlew build
   ```

2. **Copy plugin to server**:
   ```bash
   cp build/libs/livinglands-*.jar <your-server>/plugins/
   ```

3. **Run test scenarios** from `TESTING_GUIDE.md`:
   - Start with Scenario 1 (fresh install)
   - Then test migration scenarios 2-4
   - Finally test reload and error handling scenarios

## Migration Summary

The v1 → v2 migration for MetabolismConfig:

| Stat | Old Default | New Default | Change |
|------|-------------|-------------|--------|
| Hunger | 480s (8 min) | 1440s (24 min) | 3x slower |
| Thirst | 360s (6 min) | 1080s (18 min) | 3x slower |
| Energy | 600s (10 min) | 2400s (40 min) | 4x slower |

**Smart Preservation:** User customizations (values >10% different from old defaults) are preserved during migration.

## Current Status

⚠️ **Build Status:** Cannot compile without HytaleServer.jar dependency

✅ **Implementation:** Phase 3.5 migration system fully implemented
- VersionedConfig interface
- ConfigMigration framework
- ConfigMigrationRegistry
- MetabolismConfig with v1→v2 migration
- Backup creation before migration
- Fallback to defaults on error

## Next Steps

1. Obtain HytaleServer.jar and place in `libs/Server/`
2. Build plugin: `./gradlew build`
3. Run test scenarios from TESTING_GUIDE.md
4. Report any issues found during testing

## Need Help?

See `TESTING_GUIDE.md` for:
- Detailed test procedures
- Expected results
- Log messages to verify
- Troubleshooting common issues
