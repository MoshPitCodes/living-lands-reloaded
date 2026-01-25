# Multiple World Testing Guide

**Purpose:** Verify per-world data isolation and proper metabolism tracking across different worlds.

**Server:** Test1  
**Current World UUID:** `6bd4ef4e-4cd2-4486-9fd1-5794a1015596`

---

## Test Scenarios

### Scenario 1: Same Player, Different Worlds - Data Isolation

**Objective:** Verify that the same player has completely independent metabolism stats in different worlds.

**Steps:**

1. **Setup World A (Test1 - default world)**
   ```bash
   # Join Test1 world
   # Run: /ll stats
   # Expected: H=100, T=100, E=100 (or existing stats if player joined before)
   ```

2. **Deplete Stats in World A**
   ```bash
   # Sprint/swim for 2-3 minutes to deplete stats
   # Run: /ll stats
   # Record values (e.g., H=85.3, T=78.2, E=92.1)
   ```

3. **Create and Join World B (Test2)**
   ```bash
   # In Hytale, create a new world called "Test2"
   # Host Test2 world
   # Join as the same player
   ```

4. **Verify Fresh Stats in World B**
   ```bash
   # Run: /ll stats
   # Expected: H=100, T=100, E=100 (fresh start)
   # Should NOT carry over World A stats
   ```

5. **Deplete Different Amounts in World B**
   ```bash
   # Stand idle for 1 minute (slower depletion)
   # Run: /ll stats
   # Record values (e.g., H=98.5, T=97.0, E=99.2)
   ```

6. **Return to World A - Verify Stats Persisted**
   ```bash
   # Stop Test2 server
   # Host Test1 world again
   # Join as the same player
   # Run: /ll stats
   # Expected: H=85.3, T=78.2, E=92.1 (World A stats preserved)
   ```

7. **Verify Databases Exist**
   ```bash
   ls -la /mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test1/mods/MPC_LivingLandsReloaded/data/
   ls -la /mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test2/mods/MPC_LivingLandsReloaded/data/
   
   # Should see different UUID folders:
   # Test1/data/{world-uuid-1}/livinglands.db
   # Test2/data/{world-uuid-2}/livinglands.db
   ```

**Expected Results:**
- ✅ World A stats: H=85.3, T=78.2, E=92.1
- ✅ World B stats: H=98.5, T=97.0, E=99.2
- ✅ Two separate database files with different UUIDs
- ✅ No stat bleed-through between worlds

---

### Scenario 2: Multiple Players, Same World

**Objective:** Verify that different players in the same world have independent metabolism stats.

**Steps:**

1. **Player 1 Joins Test1**
   ```bash
   # Join Test1 as Player 1
   # Run: /ll stats
   # Deplete to H=70, T=65, E=80
   ```

2. **Player 2 Joins Test1**
   ```bash
   # Join Test1 as Player 2 (different account or same account via second client)
   # Run: /ll stats
   # Expected: H=100, T=100, E=100 (fresh player)
   ```

3. **Both Players Active Simultaneously**
   ```bash
   # Player 1: Sprint around
   # Player 2: Stand idle
   # Wait 1 minute
   # Player 1: /ll stats -> Should deplete faster
   # Player 2: /ll stats -> Should deplete slower
   ```

4. **Verify Database Shows Both Players**
   ```bash
   sqlite3 "/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test1/mods/MPC_LivingLandsReloaded/data/{world-uuid}/livinglands.db" \
     "SELECT player_id, hunger, thirst, energy FROM metabolism_stats;"
   
   # Should show 2 rows with different player UUIDs
   ```

**Expected Results:**
- ✅ Two independent stat progressions
- ✅ Database contains 2 player records
- ✅ No interference between players

---

### Scenario 3: World Transfer Mid-Session

**Objective:** Verify proper cleanup and re-initialization when player transfers between worlds without disconnecting.

**Steps:**

1. **Join World A**
   ```bash
   # Join Test1
   # Run: /ll stats
   # Deplete to H=75, T=70, E=85
   ```

2. **Transfer to World B (via portal/command if available)**
   ```bash
   # Use Hytale world transfer mechanism (if supported)
   # OR disconnect and rejoin different world
   # Run: /ll stats in World B
   # Expected: Fresh stats OR previously saved World B stats
   ```

3. **Check Logs for Proper Cleanup**
   ```bash
   grep -i "disconnect\|unregister\|world" /mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test1/logs/*.log | tail -50
   
   # Should see:
   # - PlayerDisconnect event
   # - Metabolism save
   # - Session unregister
   # - World context cleanup (if applicable)
   ```

**Expected Results:**
- ✅ World A stats saved correctly
- ✅ World B stats loaded correctly
- ✅ No memory leaks (old world context cleaned up)
- ✅ HUD updates to reflect new world stats

---

### Scenario 4: Database Corruption Recovery

**Objective:** Verify graceful handling of missing or corrupted database files.

**Steps:**

1. **Backup Current Database**
   ```bash
   cp "/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test1/mods/MPC_LivingLandsReloaded/data/{world-uuid}/livinglands.db" \
      "/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test1/mods/MPC_LivingLandsReloaded/data/{world-uuid}/livinglands.db.backup"
   ```

2. **Delete Database**
   ```bash
   rm "/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test1/mods/MPC_LivingLandsReloaded/data/{world-uuid}/livinglands.db"
   ```

3. **Join World**
   ```bash
   # Start Hytale server
   # Join Test1
   # Run: /ll stats
   # Expected: H=100, T=100, E=100 (fresh stats)
   ```

4. **Verify New Database Created**
   ```bash
   ls -la "/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test1/mods/MPC_LivingLandsReloaded/data/{world-uuid}/livinglands.db"
   # Should exist
   ```

5. **Restore Backup (Optional)**
   ```bash
   rm "/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test1/mods/MPC_LivingLandsReloaded/data/{world-uuid}/livinglands.db"
   mv "/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test1/mods/MPC_LivingLandsReloaded/data/{world-uuid}/livinglands.db.backup" \
      "/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test1/mods/MPC_LivingLandsReloaded/data/{world-uuid}/livinglands.db"
   ```

**Expected Results:**
- ✅ No crashes or errors
- ✅ New database auto-created
- ✅ Player starts with default stats

---

## Verification Commands

### Check All World Databases
```bash
find "/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves" -name "livinglands.db" -exec echo "=== {} ===" \; -exec sqlite3 {} "SELECT player_id, hunger, thirst, energy, datetime(last_updated/1000, 'unixepoch') as last_seen FROM metabolism_stats;" \;
```

### Check World UUIDs
```bash
find "/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves" -type d -name "MPC_LivingLandsReloaded" | while read dir; do
  world=$(echo "$dir" | awk -F'/' '{print $(NF-2)}')
  echo "=== World: $world ==="
  ls -la "$dir/data/"
done
```

### Check Server Logs for World Events
```bash
# Find latest log
LOG=$(ls -t /mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test1/logs/*.log | head -1)

# Check world events
grep -i "ADD WORLD EVENT\|REMOVE WORLD EVENT\|world.*uuid" "$LOG" | tail -20

# Check player events
grep -i "PLAYER READY\|PLAYER DISCONNECT" "$LOG" | tail -20

# Check metabolism events
grep -i "metabolism.*initialize\|metabolism.*save" "$LOG" | tail -20
```

---

## Known Issues & Edge Cases

### Issue 1: WorldContext Not Lazy-Created
**Symptom:** `AddWorldEvent` doesn't fire on server start  
**Workaround:** WorldContext created on-demand when player joins  
**Status:** Expected behavior (lazy initialization)

### Issue 2: Duplicate Disconnect Events
**Symptom:** `PlayerDisconnectEvent` fires twice during server shutdown  
**Fix:** Idempotent handler with early return (PR #6)  
**Status:** Resolved

### Issue 3: Session Timing
**Symptom:** Session unregistered before module save complete  
**Fix:** Centralized lifecycle hooks - modules save BEFORE session unregister (PR #4)  
**Status:** Resolved

---

## Success Criteria

For multi-world support to be considered fully tested:

- [ ] ✅ Same player has independent stats in different worlds
- [ ] ✅ Stats persist correctly after world switches
- [ ] ✅ Multiple players in same world don't interfere
- [ ] ✅ Database files properly isolated by world UUID
- [ ] ✅ HUD updates correctly on world change
- [ ] ✅ No memory leaks when switching worlds
- [ ] ✅ Graceful handling of missing databases
- [ ] ✅ No cross-world data contamination

---

## Test Execution Checklist

```
[ ] Scenario 1: Same Player, Different Worlds
    [ ] Step 1: Join World A
    [ ] Step 2: Deplete stats in World A
    [ ] Step 3: Create and join World B
    [ ] Step 4: Verify fresh stats in World B
    [ ] Step 5: Deplete different amounts in World B
    [ ] Step 6: Return to World A - verify stats persisted
    [ ] Step 7: Verify separate database files exist

[ ] Scenario 2: Multiple Players, Same World
    [ ] Step 1: Player 1 joins and depletes stats
    [ ] Step 2: Player 2 joins with fresh stats
    [ ] Step 3: Both active simultaneously
    [ ] Step 4: Verify database shows both players

[ ] Scenario 3: World Transfer Mid-Session
    [ ] Step 1: Join World A with stats
    [ ] Step 2: Transfer to World B
    [ ] Step 3: Check logs for proper cleanup

[ ] Scenario 4: Database Corruption Recovery
    [ ] Step 1: Backup database
    [ ] Step 2: Delete database
    [ ] Step 3: Join world - verify fresh stats
    [ ] Step 4: Verify new database created
    [ ] Step 5: Restore backup (optional)

[ ] All Success Criteria Met
```

---

## Reporting Issues

If any test fails, report with:

1. **Test Scenario:** Which scenario failed
2. **Expected Behavior:** What should have happened
3. **Actual Behavior:** What actually happened
4. **Logs:** Relevant log excerpts
5. **Database State:** Query results showing player stats
6. **Steps to Reproduce:** Exact steps taken

---

**Last Updated:** 2026-01-25  
**Plugin Version:** 1.0.0-beta  
**Test Status:** Not yet executed
