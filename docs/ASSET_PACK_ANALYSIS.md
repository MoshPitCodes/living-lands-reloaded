# Hytale Asset Pack Structure Analysis

**Date:** January 26, 2026  
**Purpose:** Document correct asset pack structure for Hytale mods

---

## Executive Summary

This analysis examined the official Hytale `Assets.zip` to understand the correct asset pack structure for mods. **Key finding:** Hytale loads asset packs from **directories and ZIP files**, NOT from JARs. The JAR is only used for Java class loading.

---

## Official Hytale Asset Structure

### Root Level (`Assets.zip`)
```
manifest.json                  # Pack manifest
CommonAssetsIndex.hashes       # Hash verification file (optional for mods)
Common/                        # Common assets (UI, textures, audio)
Server/                        # Server-side assets (items, NPCs, configs)
```

### Root `manifest.json` Format
```json
{
  "Group": "Hytale",
  "Name": "Hytale"
}
```

**Note:** The official manifest is minimal - just Group and Name. No version, description, or other fields.

### Common Assets Directory Structure
```
Common/
  BlockTextures/                 # Block texture PNGs
  UI/
    Crosshairs/                  # Crosshair textures
    Custom/
      Common.ui                  # Main UI definitions (shared styles)
      Common/                    # Common UI components
        ActionButton.ui
        TextButton.ui
        Buttons/                 # Button textures
          Primary@2x.png
          Secondary@2x.png
          ...
      Hud/                       # HUD-related UI files
        TimeLeft.ui
      Pages/                     # Page UI files
        BarterPage.ui
        CommandListPage.ui
        ...
```

### UI File Syntax (Official Examples)

**TimeLeft.ui (Simple HUD):**
```
Group {
  LayoutMode: Left;
  Anchor: (Top: 20, Height: 60);

  Group #TimeLeft {
    Background: #000000(0.2);
    Anchor: (Left: 20);
    Padding: (Horizontal: 20, Vertical: 10);
    LayoutMode: Left;

    Group {
      Background: "Clock.png";
      Anchor: (Width: 40, Height: 40, Right: 10);
    }

    TimerLabel #TimeLabel {
      Style: (FontSize: 32, Alignment: Center);
      Seconds: 15 * 60;
    }
  }
}
```

**Common.ui (Style Definitions):**
```
$Sounds = "Sounds.ui";

@Panel = Group {
  Background: (TexturePath: "Common/ContainerFullPatch.png", Border: 20);
};

@TitleLabel = Label {
  Style: (FontSize: 40, Alignment: Center);
};

@DefaultLabelStyle = (FontSize: 16, TextColor: #96a9be);
```

---

## How Hytale Loads Asset Packs

### Load Order (from server logs)
```
1. [AssetModule|P] Loaded pack: Hytale:Hytale from Assets.zip
2. [AssetModule|P] Loading packs from directory: mods           # Per-save mods folder
3. [AssetModule|P] Loading packs from directory: UserData/Mods  # Global mods folder
```

### Pack Loading Requirements

1. **Directory Structure:**
   - Pack must be a **folder** or **ZIP file**
   - NOT a JAR file (JARs are only for Java classes)

2. **manifest.json at Root:**
   - Must have `"Group"` field
   - Must have `"Name"` field
   - Missing manifest causes: `Skipping pack at [name]: missing or invalid manifest.json`

3. **Assets in Common/ Subdirectory:**
   - UI files go in `Common/UI/Custom/...`
   - Textures go in `Common/...`

---

## Previous Problem Analysis

### Why Assets Failed Before

1. **JAR Not Treated as Asset Pack:**
   - `IncludesAssetPack: true` in manifest does NOT work
   - Hytale only loads packs from directories and ZIP files
   - JAR is only used for Java class loading

2. **Missing Folder-Based Asset Pack:**
   - Assets were packaged in JAR
   - Server looked in `mods/MPC_LivingLandsReloaded/` folder
   - Folder had no `manifest.json` or `Common/` directory
   - Result: `Skipping pack at MPC_LivingLandsReloaded: missing or invalid manifest.json`

3. **UI File Resolution:**
   - Even when assets were cached by client
   - `builder.append("Hud/LivingLandsHud.ui")` failed
   - Because the asset pack wasn't properly loaded

---

## Solution: Separate Asset Pack Deployment

### New Structure (Global Mods Folder)
```
UserData/Mods/
  livinglands-reloaded-1.0.0-beta.jar    # Java plugin
  MPC_LivingLandsReloaded/                # Asset pack folder
    manifest.json                         # Pack manifest
    Common/
      manifest.json                       # Common assets manifest
      UI/
        Custom/
          Hud/
            LivingLandsHud.ui
            MetabolismHud.ui
            ProfessionsPanel.ui
            ProfessionsProgressPanel.ui
```

### Pack Manifest (Root)
```json
{
  "Group": "MPC",
  "Name": "LivingLandsReloaded"
}
```

### Common Manifest
```json
{
  "Group": "MPC",
  "Name": "LivingLandsReloaded"
}
```

### Deploy Script Updates

The `scripts/deploy_windows.sh` now:
1. Copies JAR to `UserData/Mods/`
2. Extracts `Common/` folder from JAR to `MPC_LivingLandsReloaded/`
3. Creates root `manifest.json` for the asset pack

---

## UI Path Resolution

### Expected Path Format

When calling `builder.append()`, the path should be relative to `Common/UI/Custom/`:

| UI File Location | append() Path |
|-----------------|---------------|
| `Common/UI/Custom/Hud/LivingLandsHud.ui` | `Hud/LivingLandsHud.ui` |
| `Common/UI/Custom/Pages/MyPage.ui` | `Pages/MyPage.ui` |
| `Common/UI/Custom/Common/Button.ui` | `Common/Button.ui` |

### Official Example (BarterPage.ui)
```
$C = "../Common.ui";   # Relative path to Common.ui
$C.@PageOverlay {}     # Use styles from Common.ui
```

---

## Verification Checklist

After deployment, check server logs for:

- [ ] `Loaded pack: MPC:LivingLandsReloaded from MPC_LivingLandsReloaded`
- [ ] No `Skipping pack at MPC_LivingLandsReloaded` warnings
- [ ] UI files accessible via `builder.append("Hud/LivingLandsHud.ui")`

If still failing, check:
1. Root `manifest.json` has correct `Group` and `Name`
2. `Common/` folder exists with proper structure
3. UI files are present in `Common/UI/Custom/Hud/`
4. No JSON syntax errors in manifest files

---

## References

- **Official Assets:** `Hytale/install/release/package/game/latest/Assets.zip`
- **Server Logs:** `Saves/{world}/logs/*.log`
- **Global Mods:** `UserData/Mods/`
- **Previous Investigation:** `docs/HUD_LOADING_INVESTIGATION.md`
