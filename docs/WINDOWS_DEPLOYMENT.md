# Windows Hytale Server Deployment

**Server Location:** `/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test1`  
**Plugin Deployed:** Build 2 (livinglands-1.0.0-beta.jar)  
**Status:** ✅ Ready to test

---

## Current Status

### ✅ Build 2 Deployed

- **Location:** `Test1/mods/livinglands-1.0.0-beta.jar`
- **Size:** 17 MB
- **Build Time:** 2026-01-24 21:35

### 📁 Existing Data (from Build 1)

**Config Files Created:**
- `LivingLandsReloaded/config/core.yml`
- `LivingLandsReloaded/config/metabolism.yml`

**Issue Found:**
```yaml
!!com.livinglands.core.config.CoreConfig {
  }
```

The configs have YAML type tags (`!!com.livinglands...`), which caused the parse error in Build 1.

**Build 2 Fix:** Added `tagInspector = { _ -> true }` to allow loading these files.

---

## Testing Instructions

### 1. Start/Restart Server

In Windows:
1. Open Hytale
2. Go to "Host & Join"
3. Select "Test1" world
4. Click "Host Server"

Or if server is already running, stop and restart it.

### 2. Monitor Logs

In WSL terminal:
```bash
cd /home/moshpitcodes/Development/living-lands-reloaded
./scripts/watch_windows_logs.sh
```

This will tail the latest log file and filter for Living Lands messages.

### 3. Join Server

In Hytale client:
1. Join the "Test1" server
2. Watch the log output for events

### 4. Test Commands

In game chat, try:
- `/ll`
- `/ll reload`
- `/ll stats`

---

## What to Look For

### On Server Start

```
[INFO] [LivingLands|P] === REGISTERING EVENT LISTENERS ===
[INFO] [LivingLands|P] Registered: AddWorldEvent
[INFO] [LivingLands|P] Registered: RemoveWorldEvent
[INFO] [LivingLands|P] Registered: AddPlayerToWorldEvent     <-- NEW in Build 2
[INFO] [LivingLands|P] Registered: PlayerDisconnectEvent
[INFO] [LivingLands|P] === EVENT LISTENERS REGISTERED ===
[INFO] [LivingLands|P] Created default config 'core'
[INFO] [LivingLands|P] Created default config 'metabolism'
```

**Good:** No "Failed to parse config" errors  
**Bad:** YAML parse errors still appear

### When You Join

**SUCCESS ✅:**
```
[INFO] [LivingLands|P] === ADD PLAYER TO WORLD EVENT FIRED ===
[INFO] [LivingLands|P] Event: AddPlayerToWorldEvent@...
[INFO] [LivingLands|P] World: Test1
[INFO] [LivingLands|P] >>> onPlayerAddedToWorld() called
```

**FAILURE ❌:**
```
[INFO] [World|Test1] Adding player 'moshpitplays' to world...
[INFO] [World|Test1] Player 'moshpitplays' joined world...
(No Living Lands event logs)
```

### When You Leave

```
[INFO] [LivingLands|P] === PLAYER DISCONNECT EVENT FIRED ===
[INFO] [LivingLands|P] PlayerRef: com.hypixel.hytale...
```

This should fire (it did in Build 1).

### When You Run Commands

**If commands work:**
```
[Chat] /ll reload
[Response] Reloaded: core, metabolism
```

**If commands don't work:**
```
[Chat] /ll
[Response] Unknown command: ll
```

---

## Quick Commands

### Deploy New Build

```bash
cd /home/moshpitcodes/Development/living-lands-reloaded

# Build
./gradlew clean build

# Deploy to Windows server
./scripts/deploy_windows.sh
```

### Watch Logs

```bash
./scripts/watch_windows_logs.sh
```

### Check Latest Logs Manually

```bash
# View full log
cat "/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test1/logs/"*.log | tail -100

# Filter for Living Lands only
grep --line-buffered -E "(LivingLandsReloaded|===|>>>)"" "/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test1/logs/"*.log | tail -50
```

### Check Config Files

```bash
# View core config
cat "/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test1/mods/LivingLandsReloaded/config/core.yml"

# View metabolism config
cat "/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test1/mods/LivingLandsReloaded/config/metabolism.yml"
```

### Clean Start (if needed)

If you want to test with fresh configs:

```bash
# Remove plugin data directory
rm -rf "/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test1/mods/LivingLandsReloaded"
```

Then restart server - new default configs will be created.

---

## Known Issues from Build 1

### ❌ YAML Parse Error (Should be FIXED in Build 2)

**Old:**
```
Failed to parse config 'core': Global tag is not allowed: tag:yaml.org,2002:com.livinglands.core.config.CoreConfig
```

**Fix Applied:** LoaderOptions now has `tagInspector = { _ -> true }`

### ❌ PlayerReadyEvent Never Fires

**Old Event:** `PlayerReadyEvent` - never triggered when player joined

**New Event:** `AddPlayerToWorldEvent` - should fire when player is added to world

### ❌ AddWorldEvent Never Fires

Still present in Build 2. Not critical - we can initialize world contexts lazily.

### ✅ PlayerDisconnectEvent Works

This fires correctly. Proves event system is functional.

---

## File Locations

### Build Output (Linux/WSL)
```
/home/moshpitcodes/Development/living-lands-reloaded/build/libs/livinglands-1.0.0-beta.jar
```

### Windows Server (via WSL mount)
```
/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test1/mods/livinglands-1.0.0-beta.jar
```

### Plugin Data (Windows)
```
/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test1/mods/LivingLandsReloaded/
├── config/
│   ├── core.yml
│   └── metabolism.yml
└── data/
    └── (per-world databases will be created here)
```

### Server Logs (Windows)
```
/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test1/logs/
└── 2026-01-24_21-30-30_server.log (example)
```

---

## Deployment Scripts

### `deploy_windows.sh`
```bash
#!/bin/bash
# Copies build to Windows server
./scripts/deploy_windows.sh
```

### `watch_windows_logs.sh`
```bash
#!/bin/bash
# Monitors Windows server logs
./scripts/watch_windows_logs.sh
```

Both scripts are now in the project root and ready to use.

---

## Next Steps After Testing

### If AddPlayerToWorldEvent Fires ✅

1. **Implement player session creation**
   - Extract Player component from Holder
   - Get PlayerRef and UUID
   - Create PlayerSession
   - Store in CoreModule.players

2. **Initialize Metabolism**
   - Load player stats from database
   - Register with MetabolismTickSystem
   - Show HUD

3. **Test HUD visibility**
   - Check if HUD appears on screen
   - Verify stat values display
   - Test HUD updates

### If AddPlayerToWorldEvent Doesn't Fire ❌

1. **Try other player events**
   - PlayerConnectEvent
   - PlayerSetupConnectEvent
   - Check each event in Hytale API

2. **Try polling approach**
   - Check world.getPlayers() every second
   - Track which players we've initialized
   - Initialize on first detection

3. **Check event registration timing**
   - Move event registration to `start()` phase
   - Try registering after world loads
   - Add event priority/order

### If Commands Work ✅

Great! Focus on player events and HUD.

### If Commands Don't Work ❌

1. **Change command name format**
   - Try just `"ll"` without space
   - Implement subcommands differently
   - Look at working plugin examples

2. **Test minimal command**
   - Create simple command with no arguments
   - Test if ANY command works

---

## Support

If you see unexpected behavior, capture:
1. Full server startup sequence
2. Player join logs
3. Command attempt output
4. Any exception stack traces

Share the relevant log sections and I'll help diagnose.

---

**Build 2 is deployed and ready to test!**

Start the server, join, and look for "=== ADD PLAYER TO WORLD EVENT FIRED ==="
