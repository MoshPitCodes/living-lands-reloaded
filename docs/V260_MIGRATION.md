# v2.6.0 to v1.0.0-beta Migration

## Overview

Living Lands Reloaded v1.0.0-beta includes **automatic migration** from v2.6.0-beta player data.

If you're upgrading from v2.6.0, your players' profession levels and XP will be automatically imported on first startup.

## What Gets Migrated

**Professions Data:**
- All 5 professions: Combat, Mining, Logging, Building, Gathering
- Current level for each profession
- Total XP earned in each profession

**What Does NOT Get Migrated:**
- Metabolism stats (hunger/thirst/energy) - players start fresh
- Claims data - v2.6.0 claims are not compatible with planned v1.0.0 claims
- HUD preferences - players start with default HUD visibility

## How It Works

### Automatic Detection

On first startup, the plugin checks for legacy v2.6.0 data:

```
Mods/LivingLands/leveling/playerdata/*.json  (v2.6.0 format)
  ↓
Mods/MPC_LivingLandsReloaded/data/global/livinglands.db  (v1.0.0 format)
```

Both versions store data in the global `UserData/Mods/` directory, not per-server.

### Migration Process

1. **Plugin starts** - ProfessionsModule initializes (**BLOCKS until migration completes**)
2. **Detection** - Checks for `Mods/LivingLands/leveling/playerdata/` directory
3. **Migration** - For each `{player-uuid}.json` file:
   - Parse JSON profession data
   - Check if player already has data:
     - **If NO data:** Migrate immediately
     - **If default data (all level 1, low XP):** OVERWRITE with v2.6.0 data
     - **If real progress (any level > 1):** SKIP to preserve player's current progress
   - Insert into new SQLite database (or overwrite existing defaults)
   - Rename file to `{player-uuid}.json.migrated`
4. **Logging** - Reports migration results
5. **Player joins** - Now safe to join, migration is complete

### Example Log Output

```
[INFO] [Professions] Detected v2.6.0 legacy data, starting migration...
[INFO] [Professions] Starting v2.6.0 data migration from: Mods/LivingLands/leveling/playerdata
[INFO] [Professions] v2.6.0 migration complete: 15/15 migrated, 0 failed
[INFO] [Professions] Successfully migrated 15 players from v2.6.0
```

## File Structure

### Before Migration (v2.6.0)

```
UserData/Mods/
├── LivingLands/
│   ├── leveling/
│   │   ├── config.json
│   │   └── playerdata/
│   │       ├── 550e8400-e29b-41d4-a716-446655440000.json
│   │       ├── 7c9e6679-7425-40de-944b-e07fc1f90ae7.json
│   │       └── ...
│   ├── claims/ (not migrated)
│   ├── metabolism/ (not migrated - players start fresh)
│   └── .version (2.6.0-beta)
└── hytale-livinglands-2.6.0-beta.jar
```

### After Migration (v1.0.0-beta)

```
UserData/Mods/
├── LivingLands/
│   ├── leveling/
│   │   ├── config.json
│   │   └── playerdata/
│   │       ├── 550e8400-e29b-41d4-a716-446655440000.json.migrated  ← Renamed
│   │       ├── 7c9e6679-7425-40de-944b-e07fc1f90ae7.json.migrated  ← Renamed
│   │       └── ...
│   ├── claims/ (ignored)
│   ├── metabolism/ (ignored)
│   └── .version (2.6.0-beta)
├── MPC_LivingLandsReloaded/
│   ├── config/
│   │   ├── core.yml
│   │   ├── metabolism.yml
│   │   └── professions.yml
│   ├── data/
│   │   └── global/
│   │       └── livinglands.db  ← New database with migrated data
│   └── Common/ (UI assets)
└── livinglands-reloaded-1.0.0-beta.jar
```

## v2.6.0 JSON Format (Reference)

```json
{
  "playerId": "550e8400-e29b-41d4-a716-446655440000",
  "professions": {
    "COMBAT": {
      "level": 25,
      "currentXp": 125000,
      "xpToNextLevel": 15000
    },
    "MINING": {
      "level": 18,
      "currentXp": 75000,
      "xpToNextLevel": 12000
    },
    "BUILDING": {
      "level": 42,
      "currentXp": 350000,
      "xpToNextLevel": 25000
    },
    "LOGGING": {
      "level": 10,
      "currentXp": 25000,
      "xpToNextLevel": 8000
    },
    "GATHERING": {
      "level": 15,
      "currentXp": 50000,
      "xpToNextLevel": 10000
    }
  },
  "hudEnabled": true,
  "lastSaveTime": 1737936000000,
  "totalXpEarned": 625000
}
```

## Migration Behavior

### Successful Migration

- JSON file is parsed successfully
- Player does NOT have data, OR has only default data (all level 1)
- All professions are inserted into SQLite (or existing defaults overwritten)
- File renamed to `.migrated`
- Logs: "Migrated player {uuid}: 5 professions"

**Smart Overwrite Logic:**
- Migration will OVERWRITE existing data if all professions are level 1 with low XP (< 1000)
- This handles the race condition where player joins create defaults before migration
- Migration will SKIP if player has real progress (any level > 1)

### Skipped (Player Already Has Data)

- Player already exists in new database
- File left unchanged (not renamed)
- Logs: "Player {uuid} already has data, skipping migration"

### Failed Migration

- JSON parse error or missing required fields
- File left unchanged
- Logs: "Failed to migrate player file: {filename}"
- Included in "failed" count

## Re-running Migration

The migration is **idempotent** - safe to run multiple times:

1. Already-migrated files have `.migrated` extension (ignored)
2. Players already in database are skipped
3. Only new/unmigrated JSON files are processed

To manually re-migrate a specific player:
1. Remove `.migrated` extension from their JSON file
2. Delete their entry from database: `DELETE FROM professions_stats WHERE player_id = '{uuid}';`
3. Restart server (migration runs on startup)

## Troubleshooting

### "No v2.6.0 legacy data found"

- Check that `Mods/LivingLands/leveling/playerdata/` directory exists
- Ensure JSON files exist in that directory
- Path must be sibling to `MPC_LivingLandsReloaded/` in the Mods folder

### "Failed to migrate X players"

- Check server logs for specific errors
- Common issues:
  - Malformed JSON
  - Missing `playerId` field
  - Unknown profession names

### "Players lost their levels after migration"

- Check that migration completed successfully (logs)
- Verify database contains data: `SELECT * FROM professions_stats;`
- Ensure new plugin JAR is the only Living Lands plugin loaded

## Database Schema (v1.0.0-beta)

```sql
CREATE TABLE professions_stats (
    player_id TEXT NOT NULL,
    profession TEXT NOT NULL,
    xp INTEGER NOT NULL DEFAULT 0,
    level INTEGER NOT NULL DEFAULT 1,
    last_updated INTEGER NOT NULL,
    PRIMARY KEY (player_id, profession)
);

CREATE INDEX idx_professions_player ON professions_stats(player_id);
```

## Manual Migration (Advanced)

If automatic migration fails, you can manually migrate using SQLite:

```bash
# Open database
sqlite3 LivingLandsReloaded/data/global/livinglands.db

# Insert player data (example)
INSERT INTO professions_stats (player_id, profession, xp, level, last_updated)
VALUES 
  ('550e8400-e29b-41d4-a716-446655440000', 'combat', 125000, 25, 1737936000000),
  ('550e8400-e29b-41d4-a716-446655440000', 'mining', 75000, 18, 1737936000000),
  ('550e8400-e29b-41d4-a716-446655440000', 'building', 350000, 42, 1737936000000),
  ('550e8400-e29b-41d4-a716-446655440000', 'logging', 25000, 10, 1737936000000),
  ('550e8400-e29b-41d4-a716-446655440000', 'gathering', 50000, 15, 1737936000000);
```

## Implementation Details

**Migration Code:**
- `LegacyPlayerData.kt` - Data classes matching v2.6.0 JSON structure
- `V260DataMigration.kt` - Migration logic using Jackson for JSON parsing
- `ProfessionsModule.kt` - Calls migration on module startup

**Thread Safety:**
- Migration runs asynchronously in `moduleScope`
- Uses `Dispatchers.IO` for file operations
- Repository operations are already thread-safe

**Performance:**
- Migration runs once on first startup
- Minimal overhead - only processes unmigrated files
- Batch inserts for efficiency
