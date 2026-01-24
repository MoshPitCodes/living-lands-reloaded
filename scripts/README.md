# Deployment Scripts

Collection of helper scripts for building, deploying, and managing the Living Lands plugin.

## Scripts Overview

### Deployment Scripts

#### `deploy_windows.sh`
Builds and deploys the plugin to Windows Hytale server.

**Usage:**
```bash
./scripts/deploy_windows.sh
```

**What it does:**
1. Builds the JAR with Gradle
2. Copies to Windows mods directory: `C:\Users\moshpit\AppData\Roaming\Hytale\UserData\Mods\`
3. Shows deployment status and next steps

**Requirements:**
- WSL with Windows filesystem access (`/mnt/c/`)
- Gradle installed

---

#### `deploy_debug.sh`
Builds and deploys with debug logging enabled.

**Usage:**
```bash
./scripts/deploy_debug.sh
```

**What it does:**
1. Builds the JAR
2. Deploys to Windows
3. Enables debug mode in `core.yml`

---

### Log Monitoring Scripts

#### `watch_windows_logs.sh`
Monitors Hytale server logs in real-time on Windows.

**Usage:**
```bash
./scripts/watch_windows_logs.sh
```

**Monitors:**
- Latest server log file in `C:\Users\moshpit\AppData\Roaming\Hytale\UserData\Saves\Test1\logs\`

---

#### `watch_logs.sh`
Generic log watching script.

**Usage:**
```bash
./scripts/watch_logs.sh
```

---

### Migration Scripts

#### `migrate_data_folder.sh`
Migrates data from old folder name to new folder name.

**Usage:**
```bash
./scripts/migrate_data_folder.sh
```

**What it does:**
- Migrates from `LivingLands` to `LivingLandsReloaded`
- Copies config files (preserves customizations)
- Copies database files (preserves player data)
- Keeps old folder for safety

**When to use:**
- After updating to plugin version with renamed folder
- When you have existing player data to preserve

---

## Common Workflows

### First Time Deployment
```bash
# Build and deploy
./scripts/deploy_windows.sh

# Watch logs
./scripts/watch_windows_logs.sh
```

### After Folder Name Change
```bash
# Deploy new version
./scripts/deploy_windows.sh

# Migrate existing data
./scripts/migrate_data_folder.sh

# Verify migration
ls "/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test1/mods/"
```

### Debug Issues
```bash
# Deploy with debug enabled
./scripts/deploy_debug.sh

# Watch logs in real-time
./scripts/watch_windows_logs.sh
```

---

## Script Locations

All scripts assume:
- **Project root:** `/home/moshpitcodes/Development/living-lands-reloaded/`
- **Windows mods:** `/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Mods/`
- **Server save:** `/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test1/`

Modify paths in scripts if your setup differs.

---

## Requirements

- **WSL** (Windows Subsystem for Linux)
- **Gradle** 9.3.0+
- **Kotlin** 2.1.0+
- **Java** 25+
- **Windows Hytale Server** installed

---

## Troubleshooting

### Script won't execute
```bash
# Make script executable
chmod +x scripts/script-name.sh
```

### Wrong paths
Edit the script and update:
- `WINDOWS_MODS_DIR` variable
- `MODS_DIR` variable
- File paths

### Build fails
```bash
# Clean build
./gradlew clean build
```

### Migration doesn't find folders
Check the paths in `migrate_data_folder.sh`:
```bash
MODS_DIR="/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test1/mods"
```
