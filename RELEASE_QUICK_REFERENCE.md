# Release Quick Reference

**⚠️ CRITICAL: ALWAYS use Curseforge links for Discord announcements, NEVER GitHub!**

## Quick Release Steps

### 1. Build
```bash
./gradlew clean build
```

### 2. Upload to Curseforge (PRIMARY)
- Upload `build/libs/hytale-livinglands-{version}.jar`
- Copy the Curseforge download URL

### 3. Announce on Discord
```bash
discord_send_announcement \
  --webhookName "releases" \
  --version "{version}" \
  --headline "{headline}" \
  --style "beta|release|hotfix" \
  --downloadUrl "https://www.curseforge.com/..." \  # ← CURSEFORGE LINK ONLY!
  --changes '["Change 1", "Change 2"]'
```

**Auto-Appended:** Ko-fi donation section (☕ Support Development - Ko-fi.com/moshpitplays) is automatically added to all announcements!

## Download Link Priority

1. ✅ **Curseforge** - PRIMARY (use for Discord)
2. ⚠️ **Modrinth** - Alternative
3. ❌ **GitHub** - LAST RESORT (source code only)

## Full Documentation

See `docs/RELEASE_PROCESS.md` for complete checklist and templates.
