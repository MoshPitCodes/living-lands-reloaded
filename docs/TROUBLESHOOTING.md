# Troubleshooting Guide

## Plugin Not Loading

### Symptom
Server logs show:
```
[SEVERE] [PluginManager] Failed to load pending plugin from 'mods\livinglands-1.0.0-beta.jar'. Failed to load manifest file!
```

No HUD appears, no logs from the plugin.

### Root Cause
The Hytale server cannot find or parse the `plugin.json` manifest file in the JAR.

### Verification Steps

1. **Check JAR contents:**
   ```bash
   jar tf build/libs/livinglands-1.0.0-beta.jar | grep plugin.json
   ```
   Should output: `plugin.json`

2. **Verify plugin.json content:**
   ```bash
   unzip -p build/libs/livinglands-1.0.0-beta.jar plugin.json
   ```
   Should show valid JSON with `id`, `name`, `version`, `main`.

3. **Check server logs:**
   Look for `[PluginManager]` entries in `libs/*.log`

### Possible Causes

#### 1. Invalid plugin.json Format
The Hytale server expects a specific manifest format. Our current `plugin.json`:

```json
{
  "id": "livinglands",
  "name": "Living Lands",
  "version": "${version}",
  "description": "Survival mechanics mod",
  "authors": ["Living Lands Team"],
  "main": "com.livinglands.LivingLandsPlugin",
  "dependencies": []
}
```

**Check:** Is `${version}` being replaced by Gradle? 

```bash
unzip -p build/libs/livinglands-1.0.0-beta.jar plugin.json | grep version
```

If it shows `"version": "${version}"` instead of `"version": "1.0.0-beta"`, the Gradle `processResources` task isn't expanding the variable.

**Fix:** Update `build.gradle.kts`:
```kotlin
tasks {
    processResources {
        filesMatching("plugin.json") {
            expand(mapOf("version" to version))
        }
    }
}
```

#### 2. Wrong Manifest Location
Hytale might expect `manifest.json` instead of `plugin.json`.

**Test:** Check what other plugins use:
```bash
jar tf mods/AdvancedItemInfo-1.0.5.jar | grep -E "\.json$" | head -5
jar tf mods/MMOSkillTree-0.4.2.jar | grep -E "\.json$" | head -5
```

#### 3. Missing Required Fields
Hytale might require additional fields in the manifest.

**Test:** Compare with working plugin manifests:
```bash
unzip -p mods/AdvancedItemInfo-1.0.5.jar plugin.json
unzip -p mods/MMOSkillTree-0.4.2.jar manifest.json
```

#### 4. JAR Corruption
The JAR might be corrupted during build or copy.

**Test:**
```bash
jar tvf build/libs/livinglands-1.0.0-beta.jar | head -20
```

Should show valid JAR structure.

### Solutions

#### Solution 1: Fix Version Expansion
Ensure Gradle replaces `${version}` in plugin.json:

```kotlin
// build.gradle.kts
tasks {
    processResources {
        filesMatching("plugin.json") {
            expand(mapOf("version" to project.version))
        }
    }
}
```

Rebuild:
```bash
./gradlew clean build
unzip -p build/libs/livinglands-1.0.0-beta.jar plugin.json | grep version
```

#### Solution 2: Use Hardcoded Version
If expansion fails, hardcode the version temporarily:

```json
{
  "id": "livinglands",
  "name": "Living Lands",
  "version": "1.0.0-beta",
  "main": "com.livinglands.LivingLandsPlugin"
}
```

#### Solution 3: Match Hytale's Expected Format
If Hytale uses different field names, update `plugin.json` to match working plugins.

#### Solution 4: Check Plugin Class
Verify the main class exists and extends `JavaPlugin`:

```bash
jar tf build/libs/livinglands-1.0.0-beta.jar | grep --line-buffered -E "(LivingLandsReloaded|===|>>>)"Plugin
```

Should show: `com/livinglands/LivingLandsPlugin.class`

### Debug Steps

1. **Enable verbose Gradle logging:**
   ```bash
   ./gradlew build --info | grep processResources
   ```

2. **Extract and inspect JAR:**
   ```bash
   mkdir -p test-jar
   cd test-jar
   jar xf ../build/libs/livinglands-1.0.0-beta.jar
   cat plugin.json
   ls -la com/livinglands/
   ```

3. **Compare with working plugin:**
   ```bash
   mkdir -p working-plugin
   cd working-plugin
   jar xf ../mods/MMOSkillTree-0.4.2.jar
   cat manifest.json  # or plugin.json
   ```

4. **Test minimal plugin:**
   Create a bare minimum plugin to isolate the issue.

### Expected Behavior

When the plugin loads correctly, you should see in server logs:

```
[INFO] [PluginManager] - livinglands:LivingLands from path livinglands-1.0.0-beta.jar
[INFO] [Living Lands] ========================================
[INFO] [Living Lands]   Living Lands v1.0.0-beta
[INFO] [Living Lands]   https://github.com/MoshPitCodes
[INFO] [Living Lands] ========================================
[INFO] [Living Lands] Living Lands setting up...
```

---

## No HUD Visible

If plugin loads but HUD doesn't appear:

### Possible Causes
1. **UI file not found** - Check `ui/MetabolismHud.ui` in JAR
2. **CustomUIHud API mismatch** - Hytale's HUD system changed
3. **HUD not registered** - Player ready event not firing
4. **Reflection failure** - CompositeHud can't call build()

### Debug Steps

1. **Check UI file in JAR:**
   ```bash
   jar tf build/libs/livinglands-1.0.0-beta.jar | grep -i hud
   ```

2. **Check logs for HUD errors:**
   ```bash
   grep -i "hud\|metabolism" libs/*.log
   ```

3. **Test with `/ll stats` command:**
   If stats command works, metabolism is functioning but HUD registration failed.

---

## No Stat Depletion

If plugin loads but stats don't deplete:

### Possible Causes
1. **ECS system not registered**
2. **Player events not firing**
3. **World not loaded**

### Debug Steps

1. **Enable debug logging:**
   Edit `LivingLandsReloaded/config/core.yml`:
   ```yaml
   debug: true
   ```

2. **Run `/ll reload` and check logs**

3. **Join world and check logs for:**
   - "Initialized metabolism for player..."
   - "Player has no UUID..."
   - "No world context for player..."

---

## General Debugging

### Enable Full Logging

Set debug mode in `core.yml`:
```yaml
debug: true
enabledModules:
  - metabolism
```

### Check Plugin Status

Look for these log patterns:
- `✅ GOOD`: `"Living Lands setting up..."`
- `❌ BAD`: `"Failed to load pending plugin"`
- `✅ GOOD`: `"CoreModule initialized"`
- `✅ GOOD`: `"Metabolism module started"`

### Verify File Structure

```
LivingLandsReloaded/
├── config/
│   ├── core.yml
│   └── metabolism.yml
└── data/
    └── {world-uuid}/
        └── livinglands.db
```

### Check JAR Size

```bash
ls -lh build/libs/livinglands-1.0.0-beta.jar
```

Should be ~17 MB (includes dependencies).

---

## Common Errors

| Error | Cause | Solution |
|-------|-------|----------|
| "Failed to load manifest file" | Invalid/missing plugin.json | Check JSON format, verify in JAR |
| No logs at all | Plugin not in `mods/` folder | Copy JAR to server's `mods/` directory |
| "Module 'metabolism' failed" | Code error in module | Check server logs for exception |
| HUD not appearing | UI file missing or API mismatch | Verify UI file in JAR, check CustomUIHud API |

---

## Getting Help

If issues persist:

1. **Collect information:**
   - Server version: `HytaleServer --version`
   - Java version: `java -version`
   - JAR contents: `jar tf livinglands-1.0.0-beta.jar > jar-contents.txt`
   - Server logs: Latest `.log` file from server directory

2. **Create GitHub issue** with:
   - Description of problem
   - Server logs (relevant sections)
   - JAR contents list
   - Steps to reproduce

3. **Check compatibility:**
   - Hytale Server build date
   - Other plugins loaded
   - Java version match
