# Living Lands - Testing Guide

**Build:** Debug Build 2 (AddPlayerToWorldEvent + YAML fix)  
**Status:** ✅ Deployed and ready to test

---

## Current Setup

### File Locations

**Build Output (WSL):**
```
/home/moshpitcodes/Development/living-lands-reloaded/build/libs/livinglands-1.0.0-beta.jar
```

**Deployed Location (Windows):**
```
/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Mods/livinglands-1.0.0-beta.jar
```

**Server/Plugin Data:**
```
/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test1/mods/LivingLandsReloadedReloaded/
├── config/
│   ├── core.yml
│   └── metabolism.yml
└── data/
    └── (SQLite databases will be created per-world)
```

**Server Logs:**
```
/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test1/logs/
└── 2026-01-24_*.log
```

### ✅ Already Deployed

Build 2 is already in the Mods folder (deployed at 21:35). You're ready to test!

---

## Quick Test

### 1. Start Server

In Hytale:
1. Click "Host & Join"
2. Select "Test1" world
3. Click "Host Server"

### 2. Monitor Logs

In WSL terminal:
```bash
cd /home/moshpitcodes/Development/living-lands-reloaded
./scripts/watch_windows_logs.sh
```

### 3. Join Server

In Hytale client, join the Test1 server

### 4. Check Logs

Look for:
```
=== ADD PLAYER TO WORLD EVENT FIRED ===
>>> onPlayerAddedToWorld() called
World: Test1
```

### 5. Test Commands

In game chat:
- `/ll`
- `/ll reload`

---

## What Changed in Build 2

### Fix 1: YAML Type Tags

**Problem (Build 1):**
```
Failed to parse config 'core': Global tag is not allowed: tag:yaml.org,2002:com.livinglands.core.config.CoreConfig
```

**Cause:**
Config files were created with YAML type tags:
```yaml
!!com.livinglands.core.config.CoreConfig {
  }
```

**Fix:**
Added `tagInspector = { _ -> true }` to LoaderOptions to allow loading these tags.

### Fix 2: Player Event

**Problem (Build 1):**
- `PlayerReadyEvent` registered but never fires
- Player joins but no event triggered

**Solution:**
- Switched to `AddPlayerToWorldEvent`
- This should fire when player is added to world
- Simplified handler to just log (confirm event fires first)

---

## Expected Test Results

### Scenario A: AddPlayerToWorldEvent Fires ✅

**Logs:**
```
[INFO] [LivingLands|P] === ADD PLAYER TO WORLD EVENT FIRED ===
[INFO] [LivingLands|P] Event: AddPlayerToWorldEvent@...
[INFO] [LivingLands|P] World: Test1
[INFO] [LivingLands|P] >>> onPlayerAddedToWorld() called
```

**What this means:**
- Events work! ✅
- Player detection works ✅
- Next step: Implement player session creation

**Next Actions:**
1. Extract Player component from event.holder
2. Get PlayerRef and UUID
3. Create PlayerSession
4. Initialize metabolism stats
5. Register HUD

### Scenario B: AddPlayerToWorldEvent Doesn't Fire ❌

**Logs:**
```
[INFO] [World|Test1] Adding player 'moshpitplays' to world...
[INFO] [World|Test1] Player 'moshpitplays' joined world...
(No Living Lands event logs)
```

**What this means:**
- Event doesn't fire or wrong event
- Need to try other events
- May need polling approach

**Next Actions:**
1. Try `PlayerConnectEvent`
2. Try `PlayerSetupConnectEvent`
3. Check if events fire before plugin loads
4. Consider polling `world.getPlayers()`

### Scenario C: Command Works ✅

**In-game:**
```
[Chat] /ll reload
[Response] Reloaded: core, metabolism
```

**What this means:**
- Commands work! ✅
- Command registration API correct ✅
- Can build full command suite

### Scenario D: Command Doesn't Work ❌

**In-game:**
```
[Chat] /ll
[Response] Unknown command: ll
```

**What this means:**
- Command name format issue
- May need different registration approach
- Try without subcommand space

**Next Actions:**
1. Change command name from `"ll reload"` to just `"ll"`
2. Implement subcommands differently
3. Look at MMOSkillTree plugin for examples

### Scenario E: YAML Still Fails ❌

**Logs:**
```
[WARN] [LivingLands|P] Failed to parse config 'core': Global tag is not allowed...
```

**What this means:**
- YAML fix didn't work
- Need different approach

**Next Actions:**
1. Remove type tags from saved YAML files
2. Configure representer to not add tags
3. Use custom YAML constructor

---

## Deployment Commands

### Build and Deploy

```bash
cd /home/moshpitcodes/Development/living-lands-reloaded

# Build
./gradlew clean build

# Deploy to Windows
./scripts/deploy_windows.sh
```

### Watch Logs

```bash
./scripts/watch_windows_logs.sh
```

### Manual Log Check

```bash
# View latest logs
tail -100 "/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test1/logs/"*.log

# Filter for Living Lands
grep "LivingLands" "/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test1/logs/"*.log | tail -50
```

---

## Clean Slate Testing

If you want to test with fresh configs:

```bash
# Remove plugin data
rm -rf "/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test1/mods/LivingLandsReloaded"

# Restart server - new configs will be created
```

---

## What to Report

After testing, please share:

### 1. Server Startup Logs

```bash
grep "LivingLands" "/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test1/logs/"*.log | head -30
```

Look for:
- Event registration messages
- Config creation/parsing
- Module startup

### 2. Player Join Logs

```bash
grep -A5 -B5 "Adding player" "/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test1/logs/"*.log | tail -20
```

Look for:
- "=== ADD PLAYER TO WORLD EVENT FIRED ==="
- Any Living Lands messages when player joins

### 3. Command Test Results

- What command did you type? (`/ll`, `/ll reload`, etc.)
- What response did you get?
- Screenshot if possible

### 4. Any Errors

```bash
grep -i "error\|exception\|failed" "/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test1/logs/"*.log | grep "LivingLands"
```

---

## Debug Info

### Check if Build 2 is Deployed

```bash
ls -lh "/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Mods/livinglands-1.0.0-beta.jar"
```

Should show: `17M Jan 24 21:35` or later

### Check Config Files

```bash
# Core config
cat "/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test1/mods/LivingLandsReloadedReloaded/config/core.yml"

# Metabolism config  
cat "/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test1/mods/LivingLandsReloadedReloaded/config/metabolism.yml"
```

If they have `!!com.livinglands...` tags, that's expected - Build 2 should handle them.

### Compare Builds

```bash
# Verify deployed JAR matches build
diff build/libs/livinglands-1.0.0-beta.jar "/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Mods/livinglands-1.0.0-beta.jar"
```

No output = files are identical ✅

---

## Known Issues (from Build 1)

### ✅ Should be Fixed in Build 2

1. **YAML parse error** - Added tagInspector to allow type tags
2. **PlayerReadyEvent doesn't fire** - Switched to AddPlayerToWorldEvent

### ❌ Still Present

1. **AddWorldEvent doesn't fire** - Not critical, we can lazy-load world contexts
2. **No HUD** - Waiting for player event to work first
3. **Commands unknown** - Need to test if format is correct

---

## Success Criteria

**Minimum for Build 2:**
- [ ] No YAML parse errors on startup
- [ ] AddPlayerToWorldEvent fires when player joins
- [ ] Logs show "=== ADD PLAYER TO WORLD EVENT FIRED ==="

**Bonus:**
- [ ] Commands recognized (even if not fully working)
- [ ] Config files load successfully
- [ ] PlayerDisconnectEvent still works

---

## Timeline

- **Build 1 Deployed:** Jan 24 20:24 (libs/Server)
- **Build 1 Tested:** Jan 24 20:30 (Windows Test1)
- **Build 2 Created:** Jan 24 21:35
- **Build 2 Deployed:** Jan 24 21:35 (UserData/Mods)
- **Ready to Test:** Now!

---

**You're all set! Start the server and join to see if AddPlayerToWorldEvent fires.**
