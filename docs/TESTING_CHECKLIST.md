# Living Lands Reloaded - Testing Checklist

**Version:** v1.2.3  
**Date:** 2026-01-31  
**Focus:** Balance validation + AsyncModuleCommand fix

---

## Pre-Testing Setup

- [ ] **Hytale client completely closed** (not minimized)
- [ ] **JAR deployed:** `livinglands-reloaded-1.2.3.jar` in Global Mods directory
- [ ] **Asset pack deployed:** `MPC_LivingLandsReloaded/` folder exists
- [ ] **Server config enabled:**
  ```json
  {
    "Mods": {
      "MPC:LivingLandsReloaded": {
        "Enabled": true
      }
    }
  }
  ```
- [ ] **Restart Hytale client**
- [ ] **Host world and join**

---

## Critical Tests (Must Pass)

### 1. Plugin Load Verification

**Check server logs for successful load:**
```bash
./scripts/watch_windows_logs.sh
```

- [ ] `[LivingLandsReloaded] Plugin enabled` appears
- [ ] `[CoreModule] Registered module: metabolism` appears
- [ ] `[MetabolismModule] Module started successfully` appears
- [ ] `Loaded pack: MPC:LivingLandsReloaded` appears (asset pack)
- [ ] No `ERROR` or `SEVERE` messages during startup
- [ ] No `Could not find document` errors for UI files

### 2. HUD Display

- [ ] **HUD appears on screen** (top-right or configured position)
- [ ] **Hunger bar visible** (red/orange color)
- [ ] **Thirst bar visible** (blue color)
- [ ] **Energy bar visible** (yellow/green color)
- [ ] **Values display correctly** (e.g., "85%", not "NaN" or "0%")
- [ ] **Bars update in real-time** (watch for 30 seconds)

### 3. AsyncModuleCommand Fix (/ll testmeta)

**Test with valid arguments:**
```
/ll testmeta restore energy 25
```

**Expected behavior:**
- [ ] Command executes **without error**
- [ ] Energy restored by 25%
- [ ] HUD updates to show new energy value
- [ ] Success message displayed in chat

**Test with invalid arguments (should fail gracefully):**
```
/ll testmeta restore invalid 25
/ll testmeta restore energy abc
/ll testmeta restore energy
```

**Expected behavior:**
- [ ] Error message: "Invalid stat type" or "Invalid amount"
- [ ] No crash or exception
- [ ] Command usage help displayed

**Test all stat types:**
```
/ll testmeta restore hunger 25
/ll testmeta restore thirst 25
/ll testmeta restore energy 25
```

- [ ] All three commands work
- [ ] HUD updates correctly for each stat

**Test drain mode:**
```
/ll testmeta drain hunger 25
/ll testmeta drain thirst 25
/ll testmeta drain energy 25
```

- [ ] All three commands work
- [ ] Stats decrease as expected

---

## Balance Validation Tests

### Test Setup

**Environment:**
- [ ] Creative mode (no death penalty during testing)
- [ ] Known food source available (e.g., apples, bread)
- [ ] Known water source accessible
- [ ] Timer/stopwatch available (phone, computer)

**Initial state:**
```
/ll testmeta restore hunger 100
/ll testmeta restore thirst 100
/ll testmeta restore energy 100
```

### 4. Idle Depletion Rates

**Test:** Stand still (no movement) for 10 minutes

**Expected rates (from config):**
- Hunger: 2880s = 48 min to deplete (0.347%/min)
- Thirst: 2700s = 45 min to deplete (0.370%/min)
- Energy: 2400s = 40 min to deplete (0.417%/min)

**Measurements:**
- [ ] **T=0 min:** Hunger 100%, Thirst 100%, Energy 100%
- [ ] **T=5 min:** Hunger ~98.3%, Thirst ~98.1%, Energy ~97.9%
- [ ] **T=10 min:** Hunger ~96.5%, Thirst ~96.3%, Energy ~95.8%

**Tolerance:** ±2% variation is acceptable

**Pass criteria:**
- [ ] All stats decrease at expected rates (within tolerance)
- [ ] No sudden jumps or freezes
- [ ] HUD updates smoothly

### 5. Walking Depletion (Multipliers)

**Test:** Walk continuously for 5 minutes (no sprinting, no jumping)

**Expected multipliers:**
- Hunger: 1.2x = 0.416%/min
- Thirst: 1.1x = 0.407%/min
- Energy: 0.8x = 0.333%/min (REDUCED - walking should be sustainable)

**Measurements:**
- [ ] **T=0 min:** Hunger 100%, Thirst 100%, Energy 100%
- [ ] **T=5 min:** Hunger ~97.9%, Thirst ~98.0%, Energy ~98.3%

**Pass criteria:**
- [ ] Energy depletes **slower** than hunger/thirst (sustainable)
- [ ] Hunger/thirst deplete moderately
- [ ] No stamina exhaustion from normal walking

### 6. Sprinting Depletion (Combat-Friendly)

**Test:** Sprint continuously for 3 minutes

**Expected multipliers (REDUCED):**
- Hunger: 1.6x = 0.555%/min
- Thirst: 1.5x = 0.556%/min
- Energy: 1.8x = 0.750%/min

**Measurements:**
- [ ] **T=0 min:** Hunger 100%, Thirst 100%, Energy 100%
- [ ] **T=3 min:** Hunger ~98.3%, Thirst ~98.3%, Energy ~97.8%

**Pass criteria:**
- [ ] Depletion is **moderate** (not punishing)
- [ ] Player can sprint for extended periods without exhaustion
- [ ] Energy depletes faster but not critically

### 7. Swimming Mechanics (Thirst Inverted)

**Test:** Swim continuously for 3 minutes

**Expected multipliers:**
- Hunger: 1.4x = 0.486%/min
- Thirst: **0.8x** = 0.296%/min (INVERTED - you're in water!)
- Energy: 1.4x = 0.583%/min

**Measurements:**
- [ ] **T=0 min:** Hunger 100%, Thirst 100%, Energy 100%
- [ ] **T=3 min:** Hunger ~98.5%, Thirst ~99.1%, Energy ~98.3%

**Pass criteria:**
- [ ] **Thirst depletes SLOWER than idle** (inverted mechanic works)
- [ ] Hunger/energy deplete moderately
- [ ] Swimming in water feels logical

### 8. Combat Depletion (Major Balance Change)

**Test:** Continuous combat for 5 minutes (spawned hostile mobs or dummy targets)

**Expected multipliers (HEAVILY REDUCED):**
- Hunger: 1.8x = 0.625%/min (was 2.5x = 0.868%/min)
- Thirst: 1.6x = 0.593%/min (was 2.2x = 0.815%/min)
- Energy: 2.0x = 0.833%/min (was 2.2x = 0.917%/min)

**Measurements:**
- [ ] **T=0 min:** Hunger 100%, Thirst 100%, Energy 100%
- [ ] **T=5 min:** Hunger ~96.9%, Thirst ~97.0%, Energy ~95.8%

**Old system (v1.2.2):**
- T=5 min: Hunger ~95.7%, Thirst ~95.9%, Energy ~95.4% (much harsher)

**Pass criteria:**
- [ ] **Depletion is noticeably less punishing** than old system
- [ ] Player can sustain 5+ minutes of combat without food
- [ ] Combat feels **fun**, not frustrating

**Extended combat test (15 minutes):**
- [ ] **T=15 min:** Hunger ~90.6%, Thirst ~91.1%, Energy ~87.5%
- [ ] **Old system:** Hunger ~87.0%, Thirst ~87.9%, Energy ~86.2%

**Pass criteria:**
- [ ] Player needs food every **25-30 minutes** of continuous combat (not 16 min)
- [ ] Combat exploration is sustainable
- [ ] No rage-quit moments from starvation deaths

### 9. Debuff Thresholds (Hysteresis)

**Test:** Drain hunger to trigger debuffs

**Hunger debuff (Malnourished):**
- Enter: 20% (drain to 20%, debuff activates)
- Exit: 40% (restore to 40%, debuff clears)

```
/ll testmeta drain hunger 81  # Set to 19%
```

- [ ] **At 19% hunger:** "Malnourished" debuff appears
- [ ] **Visual indicator:** Screen tint, icon, or message
- [ ] **Effect:** Slowness or reduced damage

```
/ll testmeta restore hunger 25  # Set to 44%
```

- [ ] **At 44% hunger:** "Malnourished" debuff clears
- [ ] **No flickering** when crossing 40% threshold

**Repeat for thirst and energy:**
- [ ] Thirst debuff (Dehydrated): Enter 20%, Exit 40%
- [ ] Energy debuff (Exhausted): Enter 20%, Exit 40%

**Pass criteria:**
- [ ] Hysteresis prevents flickering (5% dead zone works)
- [ ] Debuffs are noticeable but not game-breaking
- [ ] Clear visual/gameplay feedback

### 10. Food Consumption

**Test:** Consume food items (apples, bread, cooked meat)

**Setup:**
```
/ll testmeta drain hunger 50  # Set to 50%
```

**Consume food:**
- [ ] Eat apple/bread/meat
- [ ] **Hunger bar increases** immediately
- [ ] **Amount matches expected restoration** (e.g., +20% for bread)
- [ ] **No errors in logs** during consumption

**Saturation test:**
```
/ll testmeta restore hunger 100  # Already at 100%
```

- [ ] Eat food at 100% hunger
- [ ] **Food consumed** (disappears from inventory)
- [ ] **Hunger stays at 100%** (no overflow)
- [ ] **No errors or crashes**

---

## Regression Tests

### 11. Configuration Hot-Reload

**Test:** Change metabolism.yml while server is running

**Edit config:**
```yaml
baseStats:
  hunger:
    baseDepletionRateSeconds: 1000.0  # Change from 2880.0
```

**Reload:**
```
/ll reload
```

- [ ] "Configuration reloaded successfully" message
- [ ] **New depletion rate takes effect** (hunger depletes faster)
- [ ] **No errors or crashes**
- [ ] **Persistent data not lost** (stats remain at same values)

**Revert config back to 2880.0 and reload again:**
- [ ] Config reverts to original depletion rate
- [ ] No errors

### 12. Per-World Data Isolation

**Test:** Join different worlds

**Setup:**
```
World 1: TestWorld (UUID: abc-123)
World 2: Creative (UUID: def-456)
```

**In World 1:**
```
/ll testmeta drain hunger 50  # Set to 50%
```

- [ ] Hunger at 50% in World 1
- [ ] Database file created: `data/{world-1-uuid}/livinglands.db`

**Switch to World 2:**
- [ ] **Hunger resets to 100%** (new world, fresh stats)
- [ ] Database file created: `data/{world-2-uuid}/livinglands.db`

**Switch back to World 1:**
- [ ] **Hunger restored to 50%** (persistent across worlds)
- [ ] Correct database loaded

**Pass criteria:**
- [ ] Stats are isolated per world UUID
- [ ] No stat leakage between worlds
- [ ] Databases created in correct paths

### 13. Multiplayer Sync

**Test:** Join with second player (if available)

**Player 1 actions:**
```
/ll testmeta drain hunger 50  # P1 at 50%
```

**Player 2 observations:**
- [ ] **P2's HUD shows their own stats** (not P1's stats)
- [ ] **P1's stats are independent** of P2's stats

**Player 2 actions:**
```
/ll testmeta restore energy 100  # P2 at 100%
```

- [ ] **P1's energy unaffected**
- [ ] **No stat crosstalk between players**

**Pass criteria:**
- [ ] Player stats are completely isolated
- [ ] No server crashes with multiple players
- [ ] HUD updates correctly for each player

---

## Performance Tests

### 14. Server Performance

**Test:** Monitor server TPS (ticks per second) during testing

**Baseline (idle):**
- [ ] Server TPS: 20 (optimal)
- [ ] No lag or stuttering

**During active gameplay (10+ minutes):**
- [ ] Server TPS remains 20
- [ ] No gradual degradation
- [ ] No memory leaks (check logs for heap warnings)

**With multiple players (if available):**
- [ ] TPS remains stable
- [ ] No performance impact from metabolism system

### 15. Database Performance

**Test:** Check database query performance

**Run continuous stats updates for 10 minutes:**
- [ ] No database lock errors in logs
- [ ] No "database is locked" SQLite errors
- [ ] Stats save/load correctly after server restart

**Check database file size:**
```bash
./scripts/check_world_databases.sh
```

- [ ] Database size is reasonable (< 1 MB for single player)
- [ ] No exponential growth over time

---

## Edge Case Tests

### 16. Death/Respawn

**Test:** Die and respawn

**Setup:**
```
/ll testmeta drain hunger 50  # Set to 50%
/kill @s  # Kill player
```

**After respawn:**
- [ ] **Stats persist** (still at 50% hunger)
- [ ] **HUD reappears** correctly
- [ ] **No errors in logs**

**Note:** Death penalty not implemented yet (Phase 11.C)

### 17. Server Restart

**Test:** Restart server with modified stats

**Setup:**
```
/ll testmeta drain hunger 30  # Set to 70%
/ll testmeta drain thirst 40  # Set to 60%
/ll testmeta drain energy 50  # Set to 50%
```

**Restart server** (stop and start world)

**After restart:**
- [ ] **Hunger at 70%** (persisted)
- [ ] **Thirst at 60%** (persisted)
- [ ] **Energy at 50%** (persisted)
- [ ] HUD displays correct values immediately

**Pass criteria:**
- [ ] Stats persist across server restarts
- [ ] Database loaded correctly
- [ ] No data loss

### 18. Invalid Commands

**Test:** Try to break command system

**Invalid stat types:**
```
/ll testmeta restore health 50
/ll testmeta restore invalid 50
```

- [ ] Error message: "Invalid stat type. Use: hunger, thirst, or energy"
- [ ] No crash or exception

**Invalid amounts:**
```
/ll testmeta restore hunger -50
/ll testmeta restore hunger 500
/ll testmeta restore hunger abc
```

- [ ] Error message: "Amount must be between 0 and 100"
- [ ] No crash or exception

**Missing arguments:**
```
/ll testmeta restore hunger
/ll testmeta restore
/ll testmeta
```

- [ ] Error message with usage help
- [ ] No crash

---

## Sign-Off Criteria

**All critical tests must pass before proceeding to Phase 11.1.**

### Build Quality

- [x] Plugin compiles without warnings
- [ ] All critical tests passed (1-10)
- [ ] All regression tests passed (11-13)
- [ ] No ERROR or SEVERE logs during testing
- [ ] Performance is acceptable (14-15)

### Balance Validation

- [ ] Idle depletion feels natural (not too fast)
- [ ] Walking/sprinting is sustainable
- [ ] Swimming thirst mechanic is intuitive
- [ ] **Combat is fun, not frustrating** (key goal)
- [ ] 25-30 minute food cycle achieved
- [ ] Debuffs are noticeable but fair

### AsyncModuleCommand Fix

- [ ] `/ll testmeta` command works with arguments
- [ ] No "wrong number of arguments" errors
- [ ] All stat types (hunger/thirst/energy) work
- [ ] Both restore and drain modes work
- [ ] Invalid input handled gracefully

### Next Phase Readiness

- [ ] No blocking issues from this build
- [ ] Database schema is stable (no migrations needed)
- [ ] Config structure is finalized
- [ ] Ready to begin Phase 11.1 (Professions Data Model)

---

## Reporting Issues

**If any test fails:**

1. **Capture error logs:**
   ```bash
   ./scripts/watch_windows_logs.sh > test_failure_log.txt
   ```

2. **Document failure:**
   - Which test failed
   - Expected behavior
   - Actual behavior
   - Error messages (if any)
   - Steps to reproduce

3. **Create Linear issue:**
   - Title: "Test Failure: [Test Name]"
   - Priority: High (blocks Phase 11.1)
   - Label: "bug", "testing"
   - Description: Failure details + logs

4. **Triage:**
   - Critical (blocks all progress): Fix immediately
   - Major (impacts gameplay): Fix before Phase 11.1
   - Minor (edge case): Document and defer

---

## Test Results Template

**Date:** ___________  
**Tester:** ___________  
**Version:** v1.2.3  
**World:** ___________  
**Duration:** _____ minutes  

### Critical Tests
- Plugin Load: ☐ Pass ☐ Fail
- HUD Display: ☐ Pass ☐ Fail
- /ll testmeta: ☐ Pass ☐ Fail

### Balance Tests
- Idle Depletion: ☐ Pass ☐ Fail
- Walking: ☐ Pass ☐ Fail
- Sprinting: ☐ Pass ☐ Fail
- Swimming: ☐ Pass ☐ Fail
- Combat: ☐ Pass ☐ Fail _(Most important!)_
- Debuffs: ☐ Pass ☐ Fail
- Food Consumption: ☐ Pass ☐ Fail

### Regression Tests
- Hot-Reload: ☐ Pass ☐ Fail
- Per-World Isolation: ☐ Pass ☐ Fail
- Multiplayer: ☐ Pass ☐ Fail _(if available)_

### Performance
- Server TPS: _____ (20 = optimal)
- Lag: ☐ None ☐ Occasional ☐ Frequent
- Crashes: ☐ None ☐ Rare ☐ Common

### Overall Assessment
☐ **Ready for Phase 11.1** (all critical tests pass)  
☐ **Needs fixes** (list below)  
☐ **Major rework required** (critical failures)

**Notes:**
___________________________________________
___________________________________________
___________________________________________

