# HUD Implementation - Critical Findings

## Document Purpose
This document captures critical discoveries made during the implementation of the Living Lands HUD system. It serves as a reference for future Hytale mod development and documents the painful lessons learned.

**Date:** 2026-01-26  
**Version:** 1.0.0-beta  
**Status:** Working (with known limitations)

---

## Executive Summary

After extensive debugging (6+ hours), we successfully implemented a working unified HUD system for Living Lands Reloaded. The journey revealed several undocumented limitations and requirements of Hytale's CustomUI system.

**Key Discovery:** Hytale's CustomUI has very specific requirements that are NOT documented in official docs.

---

## Critical Finding #1: Asset Deployment

### ❌ What Doesn't Work
**Hytale does NOT load UI assets from plugin JARs**, despite the `"IncludesAssetPack": true` flag in manifest.json.

**Attempted:**
```
livinglands-reloaded-1.0.0-beta.jar
  ├── manifest.json (IncludesAssetPack: true)
  └── Common/UI/Custom/Hud/*.ui
```

**Result:** Assets cached but not accessible during `builder.append()`

### ✅ What Works
Assets must be deployed as a **separate folder-based asset pack** in the Mods directory.

**Required Structure:**
```
UserData/Mods/
  ├── livinglands-reloaded-1.0.0-beta.jar    # Java plugin code
  └── MPC_LivingLandsReloaded/               # Asset pack folder
      ├── manifest.json                       # Pack manifest
      └── Common/
          ├── manifest.json                   # Common assets manifest
          └── UI/Custom/Hud/
              ├── LivingLandsHud.ui
              ├── MetabolismHud.ui
              └── ...
```

**Both manifests are required:**
- Root `manifest.json`: `{"Group": "MPC", "Name": "LivingLandsReloaded"}`
- `Common/manifest.json`: Same format

**Implementation:** Modified `scripts/deploy_windows.sh` to extract assets from JAR to separate folder.

---

## Critical Finding #2: UI Path Resolution

### ❌ What Doesn't Work
Full paths or paths with `UI/Custom/` prefix:

```kotlin
builder.append("UI/Custom/Hud/LivingLandsHud.ui")        // ❌ FAILS
builder.append("Common/UI/Custom/Hud/LivingLandsHud.ui") // ❌ FAILS
```

### ✅ What Works
Short paths relative to `Common/UI/Custom/`:

```kotlin
builder.append("Hud/LivingLandsHud.ui")  // ✅ WORKS
```

**Reason:** Hytale's `UICommandBuilder.append()` automatically prepends `Common/UI/Custom/` to the provided path.

**Path Resolution:**
| Code | Resolved Path |
|------|---------------|
| `"Hud/File.ui"` | `Common/UI/Custom/Hud/File.ui` ✅ |
| `"UI/Custom/Hud/File.ui"` | `Common/UI/Custom/UI/Custom/Hud/File.ui` ❌ (doubled) |

---

## Critical Finding #3: UI File Structure Requirements

### ❌ What Doesn't Work
Named root elements or multiple root elements:

```
// FAILS - Named root
Group #ProfessionsPanel {
  // content
}

// FAILS - Multiple roots
Group #Panel1 { }
Group #Panel2 { }
```

### ✅ What Works
Single anonymous root `Group { }` wrapper:

```
// WORKS
Group {
  Group #ProfessionsPanel {
    // content
  }
  
  Group #ProgressPanel {
    // content
  }
}
```

**Reason:** Hytale's CustomUI parser requires a single anonymous root element when using `append()`.

---

## Critical Finding #4: Invalid UI Syntax

### ❌ What Doesn't Work

**1. Percentage Values:**
```
Label #MyLabel {
  Anchor: (Width: 50%, Height: 20);  // ❌ FAILS - No % support
}
```

**2. Invalid LayoutMode:**
```
Group #MyGroup {
  LayoutMode: Center;  // ❌ FAILS - Not a valid value
}
```

**3. Mixed Anchor Offsets:**
```
Group #Separator {
  Anchor: (Height: 1, Top: 4, Bottom: 6);  // ❌ FAILS - Conflicting offsets
}
```

### ✅ What Works

**1. Numeric Values Only:**
```
Label #MyLabel {
  Anchor: (Width: 50, Height: 20);  // ✅ WORKS
}
```

**2. Valid LayoutMode Values:**
```
Group #MyGroup {
  LayoutMode: Top;    // ✅ WORKS
  // OR
  LayoutMode: Left;   // ✅ WORKS
}
```

**3. Single Offset Property:**
```
Group #Separator {
  Anchor: (Height: 1, Top: 4);  // ✅ WORKS
}
```

**Valid LayoutMode values:** `Top`, `Left` (possibly `Right`, `Bottom` - not tested)

---

## Critical Finding #5: Property Setter Limitations

### ❌ What Doesn't Work
Setting nested properties directly:

```kotlin
// FAILS - Cannot set nested Anchor properties
builder.set("#CombatBar.Anchor.Width", 50)

// UNKNOWN - May or may not work
builder.set("#MyLabel.Style.TextColor", "#ffffff")
```

**Error Message:** `"CustomUI Set command selector doesn't match a markup property. Selector: #CombatBar.Anchor.Width"`

### ✅ What Works
Setting top-level properties only:

```kotlin
// WORKS - Set top-level Width property
builder.set("#CombatBar.Width", 50)

// WORKS - Set top-level properties
builder.set("#MyLabel.Text", "Hello")
builder.set("#MyLabel.Visible", true)
```

**Rule:** Only set properties that exist at the top level of the element, not nested sub-properties.

---

## Critical Finding #6: Multiple HUD Elements Pattern

### ❌ What Doesn't Work (Initially Attempted)
Multiple HUD elements each calling `append()` via CompositeHud:

```kotlin
class CompositeHud : CustomUIHud {
    override fun build(builder: UICommandBuilder) {
        // Each child HUD calls builder.append()
        metabolismHud.build(builder)     // append("Hud/MetabolismHud.ui")
        professionsHud.build(builder)    // append("Hud/ProfessionsPanel.ui")
        progressHud.build(builder)       // append("Hud/ProgressPanel.ui")
    }
}
```

**Result:** Only the first HUD loads; subsequent appends fail with "Could not find document"

### ✅ What Works
Single unified HUD with one `append()` call:

```kotlin
class LivingLandsHudElement : CustomUIHud {
    override fun build(builder: UICommandBuilder) {
        // SINGLE append containing ALL UI elements
        builder.append("Hud/LivingLandsHud.ui")
        
        // Then set values for each section
        buildMetabolismSection(builder)
        buildProfessionsPanelSection(builder)
        buildProgressPanelSection(builder)
    }
}
```

**Reason:** Hytale's CustomUI system only supports ONE `append()` call per CustomUIHud instance.

**Note:** The "multiplehud" mod likely uses the same single-file unified approach, not multiple separate appends.

---

## Implementation Pattern: Unified HUD

### Architecture

```
LivingLandsHudElement (CustomUIHud)
    │
    ├─ build() → append("Hud/LivingLandsHud.ui")  ← SINGLE APPEND
    │
    ├─ Metabolism Section
    │   ├─ updateMetabolism()
    │   ├─ toggleStats()
    │   ├─ toggleBuffs()
    │   └─ toggleDebuffs()
    │
    ├─ Professions Panel Section
    │   ├─ toggleProfessionsPanel()
    │   ├─ refreshProfessionsPanel()
    │   └─ isProfessionsPanelVisible()
    │
    └─ Progress Panel Section
        ├─ toggleProgressPanel()
        └─ refreshProgressPanel()
```

### Key Pattern: Section Management

```kotlin
// 1. Build method appends UI and populates all sections
override fun build(builder: UICommandBuilder) {
    builder.append("Hud/LivingLandsHud.ui")
    buildMetabolismSection(builder)
    buildProfessionsPanelSection(builder)
    buildProgressPanelSection(builder)
}

// 2. Section builders set visibility and conditionally populate data
private fun buildProfessionsPanelSection(builder: UICommandBuilder) {
    builder.set("#ProfessionsPanel.Visible", professionsPanelVisible.get())
    
    if (professionsPanelVisible.get() && needsRefresh) {
        populateProfessionsData(builder)
        needsRefresh = false
    }
}

// 3. Update methods push incremental changes
fun updateMetabolism(hunger: Float, thirst: Float, energy: Float) {
    metabolismStats.set(MetabolismStats(hunger, thirst, energy))
    
    val builder = UICommandBuilder()
    builder.set("#HungerBar.Text", buildTextBar(hunger))
    builder.set("#ThirstBar.Text", buildTextBar(thirst))
    builder.set("#EnergyBar.Text", buildTextBar(energy))
    
    update(false, builder)  // Incremental update, no rebuild
}
```

---

## Error Messages Decoded

### "Could not find document [path] for Custom UI Append command"

**Possible Causes:**
1. **File doesn't exist** at resolved path
2. **Asset pack not loaded** (check logs for "Loaded pack: [name]")
3. **Path resolution issue** (using full path instead of short path)
4. **Parse error in UI file** (invalid syntax causes this misleading error!)

**Debug Steps:**
1. Check: `grep "Loaded pack.*YourMod" client.log`
2. Check: `grep "Added cached reference.*YourFile.ui" client.log`
3. Verify file exists at: `Mods/YourMod/Common/UI/Custom/Hud/YourFile.ui`
4. Check UI file syntax for errors (see Finding #4)

### "CustomUI Set command selector doesn't match a markup property"

**Cause:** Trying to set a nested property (e.g., `#Element.Anchor.Width`)

**Fix:** Set top-level properties only (e.g., `#Element.Width`)

### "Skipping pack at [name]: missing or invalid manifest.json"

**Cause:** Asset pack folder is missing `manifest.json` or has invalid JSON

**Fix:** Ensure both manifests exist:
- `YourMod/manifest.json`
- `YourMod/Common/manifest.json`

Both should have: `{"Group": "YourGroup", "Name": "YourModName"}`

---

## Testing Checklist for Future HUD Development

### 1. Asset Deployment
- [ ] Assets deployed to `UserData/Mods/ModName/` folder (not just JAR)
- [ ] Root `manifest.json` exists with Group and Name
- [ ] `Common/manifest.json` exists with Group and Name
- [ ] UI files at `Common/UI/Custom/Hud/*.ui`

### 2. Asset Loading
- [ ] Check logs: `Loaded pack: Group:Name from ModName`
- [ ] Check logs: `Added cached reference to asset...: UI/Custom/Hud/YourFile.ui`
- [ ] No "Skipping pack" warnings

### 3. UI File Syntax
- [ ] Has anonymous root `Group { }` wrapper
- [ ] No percentage values (e.g., `Width: 50%`)
- [ ] Valid LayoutMode values (`Top`, `Left`)
- [ ] No conflicting Anchor offsets
- [ ] Balanced braces (check with `grep -o '{' file.ui | wc -l`)

### 4. Code Implementation
- [ ] Using short paths: `builder.append("Hud/File.ui")`
- [ ] Only ONE `append()` call per CustomUIHud
- [ ] Setting top-level properties only (not nested like `.Anchor.Width`)
- [ ] Proper visibility toggles implemented
- [ ] Update methods use `update(false, builder)` for incremental changes

### 5. In-Game Testing
- [ ] HUD appears without crash on world join
- [ ] Toggle commands work (`/ll stats`, `/ll buffs`, etc.)
- [ ] Data updates correctly (metabolism values, XP, etc.)
- [ ] Panel visibility toggles correctly

---

## Performance Considerations

### Rebuild vs Update Pattern

**Rebuild (Expensive):**
```kotlin
// Forces full UI reconstruction
show()  // Calls build() internally
```

**Update (Efficient):**
```kotlin
// Incremental value changes only
val builder = UICommandBuilder()
builder.set("#HungerBar.Text", newValue)
update(false, builder)
```

**Best Practice:**
- Use `show()` for structural changes (adding/removing panels)
- Use `update()` for frequent value changes (stats ticking every second)

### Caching Considerations

**String Caching for Performance:**
```kotlin
// BAD - Creates new string every tick
builder.set("#HungerBar.Text", "$hunger")

// GOOD - Cache formatted strings
private val cachedHungerText = AtomicReference("")
fun updateHunger(hunger: Float) {
    val newText = formatHunger(hunger)
    if (newText != cachedHungerText.get()) {
        cachedHungerText.set(newText)
        builder.set("#HungerBar.Text", newText)
        update(false, builder)
    }
}
```

---

## Known Limitations

### 1. Nested Property Setters
**Status:** Not supported  
**Workaround:** Set top-level properties only or define in UI file

### 2. Dynamic Width/Height
**Status:** Cannot set `Anchor.Width` dynamically  
**Workaround:** Use top-level `Width` property or `FlexWeight`

### 3. Style Property Changes
**Status:** Unknown if `.Style.TextColor` works  
**Impact:** Color changes for abilities based on unlock status may not work  
**Workaround:** Define multiple label variants in UI file, toggle visibility

### 4. Asset Pack Hot-Reload
**Status:** Requires full Hytale restart  
**Impact:** UI changes need complete restart to test  
**Workaround:** Test syntax thoroughly before deploying

---

## References

### Official Documentation
- Hytale Modding Docs: https://hytalemodding.dev/en/docs (⚠️ Contains outdated info)
- Local API Reference: `docs/HYTALE_API_REFERENCE.md` (verified against JAR)

### Community Resources
- Hytale Basic UIs: https://github.com/trouble-dev/hytale-basic-uis (✅ Helpful examples)
- Assets.zip Analysis: `docs/ASSET_PACK_ANALYSIS.md` (our research)

### Related Documents
- `docs/TECHNICAL_DESIGN.md` - Architecture overview
- `docs/IMPLEMENTATION_PLAN.md` - Development phases
- `docs/HUD_LOADING_INVESTIGATION.md` - Detailed debugging log (355 lines)

---

## Lessons Learned

### 1. Trust Verified Code Over Documentation
The official Hytale docs had incorrect information about path resolution and asset loading. Always verify against:
- Working mods (e.g., old v2.6.0-beta JAR)
- Actual JAR inspection (`javap`, `unzip -l`)
- Official `Assets.zip` structure

### 2. Error Messages Can Be Misleading
"Could not find document" often meant "Failed to parse" not "File missing". Always check UI syntax when you see this error even if the file exists.

### 3. Start Simple, Build Up
When debugging, create a minimal working example:
- Single label in UI file
- One `append()` call
- Verify it works, then add complexity incrementally

### 4. Test on Real Server Early
Don't wait until "everything is perfect" to test. Deploy early and often to catch integration issues.

### 5. Document As You Go
This document saved hours of re-debugging. Write down discoveries immediately while context is fresh.

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2026-01-26 | Initial document with all critical findings |

---

## Critical Finding #6: ProgressBar Component (WORKING!)

### ✅ Built-in Animated Progress Bars
Hytale provides a **built-in `ProgressBar` component** that supports animated progress bars with shimmer effects!

**Discovery Source:** Analyzed MMOSkills mod (`MMOSkillTree-0.5.4.jar`) to understand their progress bar implementation.

### UI Syntax
```
Group #BarContainer {
  Anchor: (Height: 8);
  Background: #21262d;  // Background color
  Padding: (Top: 1, Bottom: 1);

  ProgressBar #MyProgressBar {
    BarTexturePath: "../Common/ProgressBar.png";
    EffectTexturePath: "../Common/ProgressBarEffect.png";
    EffectWidth: 102;
    EffectHeight: 58;
    EffectOffset: 74;
    Value: 0.0;  // Initial value (0.0 to 1.0)
  }
}
```

### Kotlin Code to Update Progress
```kotlin
// Set progress value (0.0 to 1.0) - MUST specify .Value property!
val progress = currentXP / maxXP.toDouble()
builder.set("#MyProgressBar.Value", progress.coerceIn(0.0, 1.0))
```

**CRITICAL:** You MUST specify `.Value` in the selector! Using just `#MyProgressBar` will crash with:
```
CustomUI command data must be an object if no property was selected. Selector: #MyProgressBar
```

### Key Properties
- **BarTexturePath**: Path to progress bar fill texture (relative to Common/)
- **EffectTexturePath**: Path to shimmer effect texture (animated overlay)
- **EffectWidth/EffectHeight**: Dimensions of effect texture
- **EffectOffset**: Horizontal offset for shimmer animation
- **Value**: Progress value (0.0 = empty, 1.0 = full)

### Built-in Textures
The textures `../Common/ProgressBar.png` and `../Common/ProgressBarEffect.png` refer to **Hytale's built-in game assets** (from `libs/Server/Assets.zip`). No need to provide custom textures unless you want custom styling.

### ✅ What Works
1. **Dynamic progress updates** - `builder.set("#ProgressBar", doubleValue)` works perfectly
2. **Animated shimmer effect** - Automatically animated by Hytale engine
3. **Smooth progress transitions** - Bar fills smoothly as value changes

### ❌ Limitations
1. **Cannot customize bar colors** - Uses built-in textures only (unless you provide custom textures)
2. **Cannot set width/height dynamically** - Bar dimensions must be fixed in UI file
3. **Requires container Group** - ProgressBar doesn't support Background directly, needs parent Group

### Implementation in Living Lands
**Used in:** `ProfessionsProgressPanel` section (lines 387-601 in `LivingLandsHud.ui`)

**Professions with animated bars:**
- Combat
- Mining
- Logging
- Building
- Gathering

**Update frequency:** Every 1 second (on HUD refresh tick)

---

## Future Work

### Improvements Needed
1. **Test `.Style.TextColor` setter** - Currently unknown if this works
2. **Implement color workaround** - If Style setters fail, use visibility toggles
3. **Clean up unused HUD classes** - Remove old `MetabolismHudElement`, `ProfessionsPanelElement`, etc.
4. **Add UI hot-reload script** - Automate restart process for faster iteration
5. **Create UI syntax validator** - Script to check for common errors before deploying
6. ~~**Animated progress bars** - Investigate MMOSkills implementation~~ ✅ **COMPLETED** (2026-01-26)

### Documentation Improvements
1. Add screenshots of working HUD
2. Create video tutorial for HUD implementation
3. Extract reusable patterns into a template/library
4. Submit corrections to official Hytale docs

---

## Contact

**Project:** Living Lands Reloaded  
**Repository:** https://github.com/MoshPitCodes/living-lands-reloaded  
**Version:** 1.0.0-beta (v3 rewrite)

For questions about these findings, refer to git history or check `docs/HUD_LOADING_INVESTIGATION.md` for the full debugging journey.
