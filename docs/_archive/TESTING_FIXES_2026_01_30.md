# Testing Guide - Fixes from 2026-01-30

## Overview

This guide provides step-by-step testing procedures for the 3 fixes deployed in commit `0820cb7` (PR #30).

**Deployment:** v1.2.3  
**Date:** 2026-01-30  
**Server:** Windows Test Server

**Note:** Replace `{SAVE_NAME}` throughout this guide with your actual save name (e.g., "MyWorld", "Survival", "Creative", etc.)

---

## Pre-Testing Checklist

- [ ] Hytale fully restarted (close and reopen)
- [ ] Server started and running
- [ ] Plugin loaded successfully (check logs for "LivingLandsReloaded" startup messages)
- [ ] Join the server as a player

---

## Fix #1: WorldContext Cleanup Grace Period

**What it does:** Waits 100ms for in-flight database writes before cancelling coroutines during world cleanup.

**Goal:** Verify no data loss when world is unloaded during player operations.

### Test Procedure

This fix is hard to test manually without admin commands, but you can verify it by checking logs:

1. **Join the server**
2. **Unload the world** (requires admin command or server shutdown)
3. **Check logs for graceful cleanup:**
   ```bash
   ./scripts/watch_windows_logs.sh
   ```
   
   Look for:
   - ✅ No "Cleanup timeout" warnings (means all writes completed within 100ms)
   - ✅ Clean shutdown messages: "WorldContext cleanup for world..."
   - ✅ No "Error during WorldContext cleanup" exceptions

### Expected Result

- ✅ World unloads cleanly
- ✅ No timeout warnings (coroutines complete quickly)
- ✅ Player data saved correctly (verify on rejoin)

### Pass/Fail Criteria

**PASS** if:
- No "Cleanup timeout" messages
- No exceptions during world cleanup
- Player data intact after world reload

**FAIL** if:
- Exceptions during cleanup
- Data loss on rejoin

---

## Fix #2: World Override Ambiguity Warning

**What it does:** Warns when a world has conflicting metabolism config overrides (by name AND UUID).

**Goal:** Verify admin sees clear warning when config is ambiguous.

### Test Procedure

1. **Edit metabolism.yml** to add conflicting overrides:
   
   ```bash
   # Find your world UUID
   ./scripts/check_world_databases.sh
   # Note the UUID (e.g., 6bd4ef4e-4cd2-4486-9fd1-5794a1015596)
   ```

2. **Add to `metabolism.yml`:**
   ```yaml
   worldOverrides:
     {SAVE_NAME}:  # By world name (replace with your actual save name)
       hunger:
         baseDepletionRateSeconds: 1000.0
     
     "6bd4ef4e-4cd2-4486-9fd1-5794a1015596":  # By UUID (same world!)
       hunger:
         baseDepletionRateSeconds: 2000.0  # CONFLICT!
   ```

3. **Reload config:**
   - In-game: `/ll reload`
   - Or restart server

4. **Check logs:**
   ```bash
   ./scripts/watch_windows_logs.sh
   ```
   
   Look for:
   ```
   [WARN] World '{SAVE_NAME}' (6bd4ef4e-4cd2-4486-9fd1-5794a1015596) has conflicting 
          overrides by name and UUID. Using name-based override. Consider removing 
          one to avoid confusion.
   ```

5. **Verify precedence:**
   - Join the world
   - Check hunger depletion rate (should be 1000.0 from name-based override)
   - Use `/ll stats` to monitor

### Expected Result

- ✅ Warning appears in logs
- ✅ Name-based override wins (1000.0 depletion rate)
- ✅ UUID-based override ignored

### Pass/Fail Criteria

**PASS** if:
- Warning message appears with correct world name and UUID
- Name-based override is applied (1000.0)
- UUID-based override is NOT applied (2000.0)

**FAIL** if:
- No warning appears
- Wrong override applied
- Server crashes or throws exceptions

### Cleanup

Remove the conflicting override from `metabolism.yml` after testing:
```yaml
worldOverrides:
  {SAVE_NAME}:
    hunger:
      baseDepletionRateSeconds: 1000.0
  # Removed UUID-based override
```

Reload config: `/ll reload`

---

## Fix #3: FoodEffectDetector Memory Leak Prevention

**What it does:** Periodically removes empty player maps from food effect tracking to prevent memory leaks.

**Goal:** Verify cleanup runs and removes dead entries.

### Test Procedure

1. **Enable FINE logging** (to see cleanup messages):
   
   Edit `config/core.yml`:
   ```yaml
   logging:
     globalLevel: FINE
     moduleOverrides:
       metabolism: FINE
   ```
   
   Reload: `/ll reload`

2. **Consume food repeatedly** (20+ times):
   - Eat any food item (cooked meat, bread, etc.)
   - Wait 1-2 seconds between consumptions
   - Repeat 20+ times over ~2 minutes

3. **Wait for cleanup interval** (5 seconds):
   - After 5 seconds, cleanup should run
   - Check logs for cleanup statistics

4. **Check logs:**
   ```bash
   ./scripts/watch_windows_logs.sh
   ```
   
   Look for:
   ```
   [FINE] FoodEffectDetector cleanup: removed X processed maps, Y previous effect maps
   ```

5. **Verify cleanup is working:**
   - Cleanup messages should appear every 5 seconds (while food is being consumed)
   - After stopping food consumption, cleanup should remove your player maps after ~7 seconds
   - No excessive memory growth

### Expected Result

- ✅ Cleanup runs every 5 seconds
- ✅ Empty player maps are removed
- ✅ Logs show "removed X processed maps, Y previous effect maps"
- ✅ Memory doesn't grow unbounded

### Pass/Fail Criteria

**PASS** if:
- Cleanup messages appear in logs (FINE level)
- Maps are removed after food consumption stops
- No memory leak warnings
- No exceptions during cleanup

**FAIL** if:
- No cleanup messages (cleanup not running)
- Maps are never removed
- Exceptions during cleanup
- Memory grows continuously

### Advanced Testing (Optional)

Use JConsole or VisualVM to monitor heap memory:

1. **Connect to Java process** (Hytale server)
2. **Watch heap usage** before/after food consumption
3. **Verify memory is released** after cleanup runs

**Expected:** Memory usage remains stable, no continuous growth.

### Cleanup

Restore normal logging level:

Edit `config/core.yml`:
```yaml
logging:
  globalLevel: INFO
  moduleOverrides:
    metabolism: INFO
```

Reload: `/ll reload`

---

## Summary Testing Checklist

| Fix | Test | Status | Notes |
|-----|------|--------|-------|
| **#1: Grace Period** | World unload during player join | ⬜ | Check logs for clean shutdown |
| **#2: Config Warning** | Conflicting world overrides | ⬜ | Warning appears, name wins |
| **#3: Memory Leak** | Food consumption + cleanup logs | ⬜ | Cleanup runs every 5 seconds |

---

## Common Issues

### Issue: No cleanup messages for Fix #3
**Cause:** Log level not set to FINE  
**Fix:** Edit `config/core.yml`, set `globalLevel: FINE`, reload config

### Issue: No warning for Fix #2
**Cause:** World UUID doesn't match actual UUID  
**Fix:** Run `./scripts/check_world_databases.sh` to find correct UUID

### Issue: Can't test Fix #1
**Cause:** No admin commands to unload worlds  
**Fix:** Monitor shutdown logs instead - shutdown triggers cleanup

---

## Post-Testing

After testing all 3 fixes:

1. **Restore normal config:**
   - Remove test overrides from `metabolism.yml`
   - Set logging back to INFO level
   - Reload: `/ll reload`

2. **Document results:**
   - Note any issues found
   - Capture relevant log excerpts
   - Report findings

3. **Mark fixes as verified** (if all pass)

---

## Troubleshooting Commands

**Note:** Replace `{SAVE_NAME}` with your actual save name.

```bash
# Watch server logs in real-time
./scripts/watch_windows_logs.sh

# Find world UUID
./scripts/check_world_databases.sh

# Check latest server log
ls -lt /mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/{SAVE_NAME}/logs/*.log | head -1

# Search for specific warnings
grep -i "conflicting overrides" /mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/{SAVE_NAME}/logs/*.log

# Search for cleanup messages
grep -i "FoodEffectDetector cleanup" /mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/{SAVE_NAME}/logs/*.log
```

---

**Status:** Ready for Testing  
**Author:** Claude  
**Date:** 2026-01-30
