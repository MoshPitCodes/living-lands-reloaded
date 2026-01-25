# Configuration Migration System - Testing Guide

## Overview

This guide provides test scenarios for validating the Phase 3.5 configuration migration system.

## Prerequisites

1. **HytaleServer.jar** must be present in `libs/Server/`
2. Build the plugin: `./gradlew build`
3. Copy JAR to server plugins: `cp build/libs/livinglands-*.jar <server>/plugins/`
4. Have a test Hytale server ready

## Test Scenarios

### Scenario 1: Fresh Install (No Config)

**Purpose:** Verify default config generation with current version

**Steps:**
1. Delete `LivingLandsReloaded/config/` directory if it exists
2. Start server
3. Stop server

**Expected Results:**
- `config/core.yml` created with `configVersion: 1`
- `config/metabolism.yml` created with `configVersion: 2`
- Metabolism config has balanced rates:
  - `hunger.baseDepletionRateSeconds: 1440.0`
  - `thirst.baseDepletionRateSeconds: 1080.0`
  - `energy.baseDepletionRateSeconds: 2400.0`
- No backup files created (nothing to migrate)
- Logs show: "Created default config 'core'" and "Created default config 'metabolism'"

---

### Scenario 2: v1 Default Config Migration

**Purpose:** Verify migration updates old defaults to new values

**Steps:**
1. Copy `test-configs/metabolism-v1-defaults.yml` to server `config/metabolism.yml`
2. Start server
3. Check logs for migration messages
4. Stop server
5. Inspect `config/metabolism.yml` and backup file

**Expected Results:**
- Backup created: `metabolism.pre-migration-v1.YYYYMMDD-HHMMSS.yml.backup`
- `config/metabolism.yml` updated to:
  - `configVersion: 2`
  - `hunger.baseDepletionRateSeconds: 1440.0` (was 480.0)
  - `thirst.baseDepletionRateSeconds: 1080.0` (was 360.0)
  - `energy.baseDepletionRateSeconds: 2400.0` (was 600.0)
- Activity multipliers unchanged
- Logs show: "Migrated metabolism config from v1 to v2"

---

### Scenario 3: v1 Customized Config Preservation

**Purpose:** Verify user customizations are preserved during migration

**Steps:**
1. Copy `test-configs/metabolism-v1-customized.yml` to server `config/metabolism.yml`
2. Start server
3. Check logs
4. Stop server
5. Inspect `config/metabolism.yml`

**Expected Results:**
- Backup created
- `config/metabolism.yml` updated to:
  - `configVersion: 2`
  - **Depletion rates UNCHANGED** (custom values preserved):
    - `hunger.baseDepletionRateSeconds: 720.0` (not updated to 1440.0)
    - `thirst.baseDepletionRateSeconds: 540.0` (not updated to 1080.0)
    - `energy.baseDepletionRateSeconds: 900.0` (not updated to 2400.0)
  - Custom multipliers preserved
  - `saveIntervalSeconds: 120` preserved
- Logs show migration occurred but values preserved

---

### Scenario 4: Missing Version Field (Assume v1)

**Purpose:** Verify configs without version field are treated as v1

**Steps:**
1. Copy `test-configs/metabolism-no-version.yml` to server `config/metabolism.yml`
2. Start server
3. Check logs and config

**Expected Results:**
- Config treated as v1
- Migration applied (rates updated to v2 values)
- `configVersion: 2` added to file
- Backup created

---

### Scenario 5: Hot Reload After Migration

**Purpose:** Verify reload works after migration

**Steps:**
1. Use any migrated config from Scenario 2-4
2. Start server
3. Edit `config/metabolism.yml` (change `saveIntervalSeconds: 60` to `120`)
4. Run `/ll reload metabolism` in-game or console
5. Check logs

**Expected Results:**
- Config reloaded successfully
- No additional migration (already v2)
- Changes applied
- Logs show: "Reloaded config 'metabolism'"

---

### Scenario 6: Reload All Configs

**Purpose:** Verify global reload command

**Steps:**
1. Start server with valid configs
2. Edit both `core.yml` and `metabolism.yml`
3. Run `/ll reload` (no module specified)
4. Check logs

**Expected Results:**
- Both configs reloaded
- Logs show: "Reloaded config 'core'" and "Reloaded config 'metabolism'"
- Changes applied

---

### Scenario 7: Corrupted Config Fallback

**Purpose:** Verify fallback to defaults on parse error

**Steps:**
1. Create invalid YAML in `config/metabolism.yml`:
   ```yaml
   this is: not: valid: yaml:::
   ```
2. Start server
3. Check logs and config file

**Expected Results:**
- Parse error logged
- Default config created (overwrites corrupted file)
- No crash, server continues loading
- Logs show: "Failed to parse config 'metabolism': <error>. Using defaults."

---

### Scenario 8: Future Version Handling

**Purpose:** Verify behavior when config version > code version (downgrade scenario)

**Steps:**
1. Manually create `config/metabolism.yml` with `configVersion: 99`
2. Start server
3. Check logs

**Expected Results:**
- Config loads without migration
- Warning logged: "Config 'metabolism' has version 99 but current version is 2"
- Server continues (assumes forward compatible)

---

## Log Messages to Look For

### Success Messages
- `"Migrated metabolism config from v1 to v2"`
- `"Created backup: metabolism.pre-migration-v1.YYYYMMDD-HHMMSS.yml.backup"`
- `"Created default config 'metabolism'"`
- `"Reloaded config 'metabolism'"`

### Warning Messages
- `"Failed to parse config 'metabolism': <error>. Using defaults."`
- `"Config 'metabolism' has version X but current version is 2"`
- `"No migration path from vX to v2 for config 'metabolism'"`

### Error Messages (Should Not Occur)
- Any stack traces during config loading
- "Failed to save config" (unless disk full)

---

## Verification Checklist

After running all scenarios, verify:

- [ ] Default configs generate with correct versions
- [ ] v1 configs migrate to v2 automatically
- [ ] Old default values update to new balanced rates
- [ ] User customizations are preserved during migration
- [ ] Backup files created with timestamps before migration
- [ ] Configs without version field are treated as v1
- [ ] Hot reload works after migration
- [ ] Corrupted configs fall back to defaults gracefully
- [ ] No crashes or errors during migration process
- [ ] Migration only runs once (v2 configs don't re-migrate)

---

## Manual Testing Commands

```bash
# In server console or in-game with operator permissions:
/ll reload              # Reload all configs
/ll reload metabolism   # Reload only metabolism config
/ll reload core         # Reload only core config
```

---

## Cleanup Between Tests

```bash
# Stop server, then:
rm -rf LivingLandsReloaded/config/
rm -rf LivingLandsReloaded/data/

# Or to keep data but reset configs:
rm -rf LivingLandsReloaded/config/*.yml
rm -rf LivingLandsReloaded/config/*.backup
```

---

## Files for Testing

All test config files are located in `test-configs/`:

- `metabolism-v1-defaults.yml` - v1 with original default values
- `metabolism-v1-customized.yml` - v1 with user customizations
- `metabolism-no-version.yml` - v1 without version field
- `core-v1.yml` - CoreConfig v1 (no migrations yet)

---

## Expected Migration Behavior Summary

| Old Rate | New Rate | Change |
|----------|----------|--------|
| Hunger: 480s | 1440s | 3x slower (8 min → 24 min) |
| Thirst: 360s | 1080s | 3x slower (6 min → 18 min) |
| Energy: 600s | 2400s | 4x slower (10 min → 40 min) |

The migration uses a **10% tolerance check** to detect if users customized values:
- If current value is within 10% of old default → **UPDATE** to new default
- Otherwise → **PRESERVE** user's custom value

---

## Troubleshooting

### Migration Not Running
- Check `configVersion` field in config file
- Verify migration is registered in `MetabolismModule.setup()`
- Check logs for "Registered X migrations for 'metabolism'"

### Values Not Updating
- Verify original value matches old default (±10%)
- Check if value was user-customized (outside 10% tolerance)

### Backup Not Created
- Check file permissions in config directory
- Verify migration is actually running (check logs)

### Config Not Loading
- Check YAML syntax with online validator
- Review stack trace in logs
- Verify no-arg constructor exists in config class
