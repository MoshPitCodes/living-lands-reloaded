# Debugging Metabolism Persistence Issue

## Issue
Player metabolism stats reset to 100/100/100 every login, even after logging out with lower values.

## Expected Behavior
1. **First Login:** Create default stats (100/100/100) in database
2. **Gameplay:** Stats deplete normally
3. **Logout:** Save current stats to database
4. **Re-Login:** Load saved stats from database (should show depleted values)

## Actual Behavior
Stats always show 100/100/100 on login, regardless of previous session values.

## Debug Logging Added

The following log messages have been added to track the issue:

### On Player Login
```
🔵 initializePlayer() called: UUID=..., string=...
ensureStats() called for player: ...
✅ LOADED existing metabolism stats for ...: H=X, T=Y, E=Z
```
OR
```
✅ CREATED default metabolism stats for player: ... (H=100, T=100, E=100)
```

### On Player Logout
```
UPDATE metabolism_stats: player=..., rows affected=N, H=X, T=Y, E=Z
Saved and removed metabolism for disconnecting player ...
```

## Testing Steps

1. **Start the server:**
   ```bash
   cd libs
   java -jar Server/HytaleServer.jar
   ```

2. **Join the server** (first time)
   - Expected log: `✅ CREATED default metabolism stats`
   - HUD shows: 100/100/100

3. **Wait for stats to deplete**
   - Let hunger/thirst/energy drop (e.g., to 75/60/85)
   - HUD should show depleted values

4. **Disconnect from server**
   - Expected log: `UPDATE metabolism_stats: ... rows affected=1, H=75, T=60, E=85`
   - Expected log: `Saved and removed metabolism for disconnecting player`

5. **Re-join the server** (second time)
   - **Expected log:** `✅ LOADED existing metabolism stats for ...: H=75, T=60, E=85`
   - **Expected HUD:** 75/60/85
   - **ACTUAL (BUG):** May show `✅ CREATED default` or `LOADED ... H=100, T=100, E=100`

## What to Look For

### Scenario A: UPDATE returns 0 rows affected
```
UPDATE metabolism_stats: player=..., rows affected=0, H=75, T=60, E=85
```
**Diagnosis:** The database record doesn't exist when saving. The initial INSERT may have failed.

### Scenario B: Different player ID on reconnect
```
# First login
🔵 initializePlayer() called: UUID=abc-123, string=abc-123

# Second login (different UUID)
🔵 initializePlayer() called: UUID=xyz-789, string=xyz-789
```
**Diagnosis:** Player UUID is changing between sessions (shouldn't happen).

### Scenario C: Database file is reset/deleted
Check if database file exists:
```bash
find libs -name "*.db" -type f
```

Expected: `libs/LivingLandsReloaded/data/{world-uuid}/livinglands.db`

If the file doesn't exist after first logout, the database isn't being created or is being deleted.

### Scenario D: Always creates new record
```
# Every login shows
✅ CREATED default metabolism stats for player: ... (H=100, T=100, E=100)
```
**Diagnosis:** Database query is not finding the existing record. Could be:
- Table doesn't exist
- Query uses wrong player ID format
- Database connection issues

## Database Investigation

If database file exists, you can inspect it:
```bash
# Find the database
find . -name "livinglands.db"

# Query it (requires sqlite3)
sqlite3 path/to/livinglands.db "SELECT * FROM metabolism_stats;"
```

Expected output:
```
player-uuid|75.0|60.0|85.0|timestamp
```

## Hypothesis

Based on code review, the most likely issue is:

1. **First login:** `ensureStats()` inserts record successfully
2. **Logout:** `updateStats()` called, but:
   - Either the record was deleted
   - Or the transaction isn't committing
   - Or the database file is ephemeral (in-memory or temp location)

## Files to Check

- **Database location:** Should be created at `libs/LivingLandsReloaded/data/{world-uuid}/livinglands.db`
- **Config location:** Should be at `libs/LivingLandsReloaded/config/`

After server runs, check if these directories exist:
```bash
ls -la libs/LivingLandsReloaded/
```

## Next Steps

1. Start server with script: `./start_and_watch.sh`
2. Join, deplete stats, logout
3. Share the log output showing:
   - The `🔵 initializePlayer()` lines
   - The `✅ LOADED` or `✅ CREATED` lines
   - The `UPDATE metabolism_stats` lines
4. Check if database file exists: `find libs -name "*.db"`

This will tell us exactly where the persistence is failing.
