# Testing Guide: Stamina Debuff Fix & Architecture Improvements

**Date:** 2026-01-25  
**Version:** 1.0.0-beta (Post-Critical Fixes)  
**Status:** 🔴 READY FOR TESTING - Server restart required

---

## What Was Fixed

### 1. Critical: Stamina Debuff Math (ADDITIVE Modifiers)
**Problem:** Stamina went negative (-1.5) or increased (19.5) instead of decreasing  
**Fix:** Switched from MULTIPLICATIVE to ADDITIVE modifier calculation  
**Files:** `DebuffsSystem.kt` lines 237-270

### 2. Crash Recovery System (NEW)
**Feature:** Removes stale modifiers left from abnormal disconnects  
**Benefit:** Players won't have "stuck" debuffs after server crash  
**Files:** `MetabolismModule.kt` lines 269-284

### 3. Modifier Stacking Prevention
**Fix:** Explicitly removes buff modifier before applying debuff  
**Benefit:** Prevents buff+debuff from stacking during transitions  
**Files:** `DebuffsSystem.kt` line 273

### 4. Production Log Cleanup
**Change:** Moved debug messages from INFO to FINE level  
**Benefit:** Cleaner console output in production  
**Files:** `DebuffsSystem.kt` lines 276, 286, 313

---

## Testing Environment

**Server:** Test3 (recommended - has multiple worlds)  
**Worlds Available:**
- `default` (UUID: 862829d0-c75c-4340-8e39-aa52317fdff5)
- `test4` (UUID: <different-uuid>)

**Plugin Location:** `/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Mods/livinglands-1.0.0-beta.jar`

**Config Location:** `/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test3/mods/MPC_LivingLandsReloaded/config/`

**Database Location:** `/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test3/mods/MPC_LivingLandsReloaded/data/{world-uuid}/livinglands.db`

---

## Pre-Test Checklist

- [ ] ✅ Built and deployed new JAR (26 MB)
- [ ] 🔴 **RESTART HYTALE SERVER** - Old JAR still loaded until restart
- [ ] Check server config enables plugin: `Test3/config.json` → `"Enabled": true`
- [ ] Verify plugin loads: Check logs for `[LivingLands] LivingLands v1.0.0-beta enabled`
- [ ] Join world and run `/ll stats` to confirm plugin responds

---

## Test Suite

### Test 1: Basic Debuff Application ✅ CRITICAL

**Objective:** Verify stamina debuffs reduce max stamina correctly (not negative)

**Steps:**
1. Join `default` world
2. Run `/ll stats` to see current stats
3. Wait for thirst to naturally deplete OR use debug command to set thirst low
4. Monitor stamina max as thirst crosses debuff thresholds

**Expected Results:**

| Thirst % | Debuff Level | Expected Stamina Max | Expected Behavior |
|----------|--------------|----------------------|-------------------|
| 76-100% | None | 10/10 | No debuff |
| 51-75% | Thirsty (85%) | 8-9 / 8-9 | Max reduced by 15% |
| 26-50% | Parched (65%) | 6-7 / 6-7 | Max reduced by 35% |
| 0-25% | Dehydrated (40%) | 4 / 4 | Max reduced by 60% |

**Success Criteria:**
- ✅ Stamina max **DECREASES** (not increases)
- ✅ Stamina max **NEVER NEGATIVE** (not -1.5)
- ✅ HUD shows correct debuff name ("Thirsty", "Parched", "Dehydrated")
- ✅ `/ll debuffs` command shows active debuff

**Failure Indicators:**
- ❌ Stamina shows negative value (e.g., -1.5 / -1.5)
- ❌ Stamina max increases instead of decreases (e.g., 19.5 / 19.5)
- ❌ Stamina max stays at 10 despite debuff being shown
- ❌ No debuff applied at all

**Log Analysis:**
```bash
# Find latest Test3 server log
ls -lt /mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test3/logs/*.log | head -1

# Check for debuff application messages (FINE level - may not appear)
grep -i "\[DEBUFF\].*Applied" <logfile> | tail -20

# Check for errors
grep -i "error\|exception" <logfile> | grep -i "metabolism\|stamina"
```

---

### Test 2: Debuff Tier Transitions ✅ IMPORTANT

**Objective:** Verify debuff properly transitions between tiers without stacking

**Steps:**
1. Start with thirst at 100% (no debuff)
2. Let thirst drop to 70% → "Thirsty" should appear
3. Let thirst drop to 45% → "Parched" should appear (replaces "Thirsty")
4. Let thirst drop to 20% → "Dehydrated" should appear (replaces "Parched")
5. Drink water → thirst goes to 95%
6. Verify "Dehydrated" disappears (uses 40% exit threshold)

**Expected Results:**
- ✅ Only ONE stamina debuff active at a time
- ✅ Debuff name changes correctly in HUD
- ✅ Stamina max updates correctly for each tier
- ✅ `/ll debuffs` shows only current tier debuff

**Success Criteria:**
- Stamina max updates: 10 → 8.5 → 6.5 → 4 → 10
- No modifier stacking (buff removed before debuff applied)

---

### Test 3: Debuff Removal & Buff Application ✅ IMPORTANT

**Objective:** Verify buffs apply correctly after debuff is removed

**Steps:**
1. Get "Dehydrated" debuff (thirst ≤25%, stamina max = 4)
2. Drink water until thirst = 95% (enter buff zone)
3. Wait one tick cycle (≈1 second)
4. Check `/ll buffs` and `/ll stats`

**Expected Results:**
- ✅ "Dehydrated" debuff removed from HUD
- ✅ Stamina max returns to 10 (or 11+ if buff applies)
- ✅ "Satiated" buff appears (if all stats ≥90%)
- ✅ No lingering debuff modifier

**Success Criteria:**
- Buff modifier `livinglands_buff_stamina` applied correctly
- Debuff modifier `livinglands_debuff_stamina` fully removed
- Stamina max = 11.3 if Satiated buff active (13% increase)

---

### Test 4: Crash Recovery System 🆕 CRITICAL

**Objective:** Verify stale modifiers are cleaned up after abnormal disconnect

**Steps:**
1. Get "Parched" debuff (thirst = 40%, stamina max = 6.5)
2. Run `/ll stats` to confirm debuff is active
3. **Force-kill Hytale server process** (Ctrl+C or Task Manager)
4. Restart server
5. Rejoin world
6. Run `/ll stats` immediately after spawn

**Expected Results:**
- ✅ Stamina max = 10 (clean slate)
- ✅ No "Parched" debuff in HUD
- ✅ Metabolism stats loaded from database (thirst still ≈40%)
- ✅ Debuff will re-apply on next tick cycle (stamina max drops to 6.5 again)

**Success Criteria:**
- No stale modifier left from previous session
- Stats load correctly from database
- Debuff system re-applies modifiers based on current stat values

**Log Analysis:**
```bash
# Check for modifier cleanup on join
grep -i "Cleaning up stale modifiers" <logfile>

# Check for stats loaded from DB
grep -i "Loaded metabolism stats" <logfile>
```

---

### Test 5: Multi-World Isolation ✅ ARCHITECTURE

**Objective:** Verify per-world data isolation works correctly

**Steps:**
1. Join `default` world
2. Let hunger drop to 40% (trigger "Hungry" debuff)
3. Run `/ll stats` to confirm stats (e.g., hunger = 40%, health max reduced)
4. Run `/tp world test4` to teleport to test4 world
5. Run `/ll stats` immediately after teleport

**Expected Results:**
- ✅ Stats in `test4` are INDEPENDENT (likely 100%/100%/100% if first visit)
- ✅ No debuff carried over from `default` world
- ✅ HUD shows different values (or nothing if fresh spawn)
- ✅ Database shows separate entries for each world

**Database Verification:**
```bash
# Check default world data
nix develop --command sqlite3 "/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test3/mods/MPC_LivingLandsReloaded/data/862829d0-c75c-4340-8e39-aa52317fdff5/livinglands.db" "SELECT * FROM metabolism_stats;"

# Check test4 world data (replace UUID with actual test4 UUID)
nix develop --command sqlite3 "/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test3/mods/MPC_LivingLandsReloaded/data/{test4-uuid}/livinglands.db" "SELECT * FROM metabolism_stats;"
```

**Success Criteria:**
- Two separate database files exist
- Each database has independent player stat entries
- Teleporting between worlds saves/loads correct stats

---

### Test 6: Config Reload (Thread Safety) ✅ STABILITY

**Objective:** Verify hot-reload works without breaking active systems

**Steps:**
1. Join world and get "Thirsty" debuff (thirst = 70%)
2. Edit `config/metabolism.yml` → Change `thirst.baseDepletionRateSeconds: 1440.0` to `720.0`
3. Run `/ll reload` in-game
4. Check console for reload confirmation
5. Wait and observe thirst depletion rate

**Expected Results:**
- ✅ Config reloads without errors
- ✅ Existing debuff remains active (not removed by reload)
- ✅ Thirst depletion rate changes to 2x faster (720 seconds instead of 1440)
- ✅ No crashes or exceptions

**Success Criteria:**
- Console shows: `[INFO] Configuration reloaded successfully`
- Thread-safe config access (ConcurrentHashMap)
- Active modifiers persist through reload

---

### Test 7: Player Disconnect Cleanup ✅ MEMORY LEAK CHECK

**Objective:** Verify modifiers are removed on normal disconnect

**Steps:**
1. Join world and get "Dehydrated" debuff (stamina max = 4)
2. Run `/ll debuffs` to confirm debuff active
3. Disconnect normally (leave server via menu)
4. Check server logs for cleanup message
5. Rejoin and check `/ll stats`

**Expected Results:**
- ✅ Server logs show: `Player {name} disconnected, cleaning up resources`
- ✅ Modifier removed from EntityStatMap on disconnect
- ✅ On rejoin: Stats loaded from DB, debuff re-applied based on stat values

**Log Analysis:**
```bash
# Check disconnect cleanup
grep -i "disconnected.*cleaning" <logfile>

# Check for modifier removal
grep -i "removeModifier" <logfile>
```

---

## Known Issues / Limitations

### 1. Base Stamina Hardcoded ⚠️
**Current:** `BASE_STAMINA = 10f` hardcoded in `DebuffsSystem.kt`  
**Risk:** If another mod changes base stamina, our calculations will be wrong  
**Mitigation:** Make configurable in `metabolism.yml` (future enhancement)

### 2. No Per-World Config Overrides ⚠️
**Current:** All worlds use same config (e.g., same depletion rates)  
**Limitation:** Cannot have "hardcore" world with 2x faster depletion  
**Mitigation:** Requires architecture change (future enhancement)

### 3. Hytale Modifier API Undocumented 🐛
**Issue:** MULTIPLICATIVE modifiers behave unexpectedly for values < 1.0  
**Workaround:** Using ADDITIVE modifiers with calculated offset  
**Risk:** Future Hytale updates may change modifier behavior

---

## Success Metrics

**Fix is SUCCESSFUL if:**
- ✅ All 7 tests pass
- ✅ Stamina debuffs reduce max stamina correctly (no negatives)
- ✅ No crashes or exceptions in logs
- ✅ Crash recovery cleans up stale modifiers
- ✅ Multi-world isolation works correctly

**Fix is FAILED if:**
- ❌ Stamina still goes negative or increases
- ❌ Debuffs don't apply at all
- ❌ Crashes on player join/disconnect
- ❌ Modifiers stack instead of replacing

---

## If Tests Fail: Debug Plan

### Scenario 1: Stamina Still Goes Negative
**Possible Causes:**
1. ADDITIVE calculation is also wrong
2. Base stamina is not 10 (other mod interference)
3. Modifier system works differently than expected

**Debug Steps:**
1. Add more logging to see actual values:
   ```kotlin
   logger.info("BEFORE: current=${statValue?.getCurrent()}, max=${statValue?.getMax()}")
   // Apply modifier
   logger.info("AFTER: current=${statValue?.getCurrent()}, max=${statValue?.getMax()}")
   ```
2. Try getting base dynamically instead of hardcoding
3. Check for other mods that modify stamina (disable them)

### Scenario 2: Debuffs Don't Apply At All
**Possible Causes:**
1. Event not firing (PlayerReadyEvent issue)
2. World thread blocking
3. EntityRef becoming invalid

**Debug Steps:**
1. Add logging to `tick()` method to confirm it's running
2. Check `entityRef.isValid` at start of tick
3. Verify `store.getComponent()` returns non-null EntityStatMap

### Scenario 3: Crash on Player Join
**Possible Causes:**
1. Database migration failed
2. Null pointer in stats loading
3. World not fully initialized

**Debug Steps:**
1. Check database schema: `sqlite3 livinglands.db ".schema"`
2. Add try/catch around cleanup code
3. Verify `PlayerReadyEvent` provides valid world context

---

## Log File Locations

**Server Logs:**
```bash
/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test3/logs/YYYY-MM-DD_HH-MM-SS_server.log
```

**Client Logs:**
```bash
/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Logs/YYYY-MM-DD_HH-MM-SS_client.log
```

**Find Latest:**
```bash
# Server
ls -lt /mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test3/logs/*.log | head -1

# Client
ls -lt /mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Logs/*.log | head -1
```

---

## Next Steps After Testing

### If All Tests Pass ✅
1. Mark Phase 1.2 complete in `IMPLEMENTATION_PLAN.md`
2. Update `CHANGELOG.md` with fix details
3. Create git commit: `fix: stamina debuffs now use ADDITIVE modifiers to prevent negative values`
4. Move on to Phase 1.3 (Food consumption system)

### If Tests Fail ❌
1. Use error-detective agent to analyze logs
2. Implement debug plan based on failure scenario
3. Consult java-kotlin-backend agent for alternative approaches
4. Re-test until fix is verified

---

## Contact / Support

**Repository:** https://github.com/MoshPitCodes/living-lands-reloaded  
**v2.6.0 Reference:** https://github.com/MoshPitCodes/hytale-livinglands

**Key Files to Review:**
- `src/main/kotlin/com/livinglands/modules/metabolism/buffs/DebuffsSystem.kt`
- `src/main/kotlin/com/livinglands/modules/metabolism/buffs/BuffsSystem.kt`
- `src/main/kotlin/com/livinglands/modules/metabolism/MetabolismModule.kt`
- `docs/PHASE_0_1_AUDIT.md` (previous issues and fixes)

---

**Good luck with testing! 🚀**
