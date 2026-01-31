# Living Lands Reloaded - Release Process

## Overview

This document describes the complete release process for Living Lands Reloaded, including building, publishing to platforms, and announcing releases.

**IMPORTANT:** Always use the **Curseforge download link** for Discord announcements, not GitHub releases.

## Release Checklist

### 1. Pre-Release Preparation

- [ ] All issues for milestone are completed and tested
- [ ] All tests pass: `./gradlew build`
- [ ] No compiler warnings
- [ ] Version updated in `version.properties`
- [ ] `CHANGELOG.md` updated with version entry
- [ ] `docs/social-media/CHANGELOG.md` updated for mod page
- [ ] `docs/social-media/MODPAGE_DESCRIPTION.md` updated if needed
- [ ] Linear issues marked as complete

### 2. Build Release JAR

```bash
# Clean build
./gradlew clean build

# Verify JAR exists
ls -lh build/libs/hytale-livinglands-*.jar
```

### 3. Platform Publishing

#### 3.1 GitHub Release (Optional - For Source Code)

GitHub releases are **optional** and primarily for source code references, not for player downloads.

```bash
# Create GitHub release (optional)
gh release create v1.3.0 \
  build/libs/hytale-livinglands-1.3.0.jar \
  --title "v1.3.0" \
  --notes-file CHANGELOG.md \
  --prerelease
```

#### 3.2 Curseforge (PRIMARY DISTRIBUTION)

**Curseforge is the primary distribution platform for player downloads.**

1. Upload `build/libs/hytale-livinglands-{version}.jar` to Curseforge
2. Set version name: `v{version}` (e.g., `v1.3.0`)
3. Copy changelog from `docs/social-media/CHANGELOG.md`
4. Select version type:
   - Alpha: Early testing, expect bugs
   - Beta: Feature complete, testing phase
   - Release: Stable production release
5. Select game version compatibility
6. **Copy the Curseforge download URL** - You'll need this for Discord announcements

**Example Curseforge URL format:**
```
https://www.curseforge.com/hytale/mods/living-lands-reloaded/files/{file-id}
```

### 4. Discord Announcements

**CRITICAL:** Always use the **Curseforge download link**, never GitHub.

#### 4.1 Configure Discord Webhook (First Time Only)

```bash
# Add webhook configuration
# Get webhook URL from Discord: Server Settings > Integrations > Webhooks
discord_add_webhook \
  --name "releases" \
  --url "https://discord.com/api/webhooks/YOUR_WEBHOOK_ID/YOUR_WEBHOOK_TOKEN" \
  --description "Release announcements channel"
```

#### 4.2 Send Release Announcement

**Important:** The Discord MCP server **automatically appends** a Ko-fi donation section to all announcements:
- **Ko-fi Username:** moshpitplays
- **Ko-fi URL:** https://ko-fi.com/moshpitplays
- **Field Name:** "☕ Support Development"
- **Auto-included in:** Release announcements, teaser announcements, and all embed formats

You don't need to manually add the Ko-fi link—it's already handled!

**For Beta Releases:**

```bash
discord_send_announcement \
  --webhookName "releases" \
  --version "1.3.0" \
  --headline "Server Announcements Module - MOTD, Welcome Messages, Recurring Tips" \
  --style "beta" \
  --betaWarning true \
  --downloadUrl "https://www.curseforge.com/hytale/mods/living-lands-reloaded/files/{file-id}" \
  --changes '["Complete Announcer Module with MOTD, welcome messages, and recurring announcements", "Fixed critical HUD crash on player join", "Panel toggle commands now work correctly", "Hot-reload support for announcements"]'
```

**For Stable Releases:**

```bash
discord_send_announcement \
  --webhookName "releases" \
  --version "1.3.0" \
  --headline "Server Announcements Module - MOTD, Welcome Messages, Recurring Tips" \
  --style "release" \
  --betaWarning false \
  --downloadUrl "https://www.curseforge.com/hytale/mods/living-lands-reloaded/files/{file-id}" \
  --changes '["Complete Announcer Module", "Critical bug fixes", "Performance improvements"]'
```

**For Hotfixes:**

```bash
discord_send_announcement \
  --webhookName "releases" \
  --version "1.2.3" \
  --headline "Critical Food Consumption Bug Fix" \
  --style "hotfix" \
  --downloadUrl "https://www.curseforge.com/hytale/mods/living-lands-reloaded/files/{file-id}" \
  --changes '["Fixed food consumption only working on first use", "Improved effect detection for repeated consumptions"]'
```

#### 4.3 Send Teaser (For Upcoming Features)

```bash
discord_send_teaser \
  --webhookName "releases" \
  --version "1.4.0" \
  --headline "Tier 3 Mastery Abilities Coming Soon" \
  --style "beta" \
  --highlights '["Speed bursts for level 100 professions", "Bonus resource drops", "Resource conservation mechanics", "And more to be revealed..."]' \
  --additionalInfo "Stay tuned for the full reveal!"
```

### 5. Post-Release

- [ ] Update Linear project board
- [ ] Close milestone issues
- [ ] Create next milestone if needed
- [ ] Monitor Discord for feedback
- [ ] Update README.md if version changed
- [ ] Git tag the release: `git tag v1.3.0 && git push --tags`

## Download Link Priority

**ALWAYS use this priority order for download links:**

1. **Curseforge** (PRIMARY) - Use for Discord announcements and mod page
2. **Modrinth** (if available) - Alternative platform
3. **GitHub Releases** (LAST RESORT) - Only for source code references

**Why Curseforge?**
- Primary mod distribution platform
- Better analytics and tracking
- Player-friendly interface
- Integrated mod management tools
- More discoverability

**When to use GitHub?**
- Referencing source code
- Developer documentation
- Issue tracking
- Pull request discussions

## Release Types

### Alpha (v0.x.x)
- Early development
- Expect breaking changes
- Testing new features
- Not recommended for production servers

### Beta (v1.x.x-beta)
- Feature complete
- Testing and refinement
- May have minor bugs
- Suitable for test servers

### Release (v1.x.x)
- Stable and tested
- Production ready
- Recommended for public servers
- Full documentation

### Hotfix (v1.x.x)
- Critical bug fixes
- Emergency patches
- Immediate deployment recommended

## Version Numbering

Follow Semantic Versioning (SemVer):

- **Major (X.0.0)**: Breaking changes, major rewrites
- **Minor (0.X.0)**: New features, backwards compatible
- **Patch (0.0.X)**: Bug fixes, no new features

**Beta suffix:** `-beta` (e.g., `1.3.0-beta`)

## Rollback Procedure

If a release has critical issues:

1. Immediately announce the issue on Discord
2. Pull the Curseforge file (set to archived)
3. Create hotfix branch: `git checkout -b hotfix/v1.3.1`
4. Fix the issue
5. Follow hotfix release process
6. Announce hotfix on Discord with `--style hotfix`

## Discord Announcement Templates

**Note:** All Discord announcements automatically include a "☕ Support Development" section with Ko-fi link at the bottom.

### Beta Release (Rich Embed)
```
📦 v1.3.0-beta is live!

Server Announcements Module - MOTD, Welcome Messages, Recurring Tips

What's New:
→ Complete Announcer Module with MOTD, welcome messages, and recurring announcements
→ Fixed critical HUD crash on player join
→ Panel toggle commands now work correctly
→ Hot-reload support for announcements

⚠️ Warning
This is a beta release. Back up your world before updating!

🔗 Download
[Get it here](Curseforge Link)

☕ Support Development
Enjoying Living Lands Reloaded? Consider supporting development: Ko-fi.com/moshpitplays
```

### Stable Release (Rich Embed)
```
📦 v1.3.0 is live!

Server Announcements Module - Production Ready

What's New:
→ Complete Announcer Module
→ Critical bug fixes
→ Performance improvements
→ Full documentation

🔗 Download
[Get it here](Curseforge Link)

☕ Support Development
Enjoying Living Lands Reloaded? Consider supporting development: Ko-fi.com/moshpitplays
```

### Hotfix Release (Rich Embed)
```
🚨 v1.2.3 is live!

Critical Food Consumption Bug Fix

What's New:
→ Fixed food consumption only working on first use
→ Improved effect detection for repeated consumptions

🔗 Download
[Get it here](Curseforge Link)

☕ Support Development
Enjoying Living Lands Reloaded? Consider supporting development: Ko-fi.com/moshpitplays
```

### Teaser Announcement (Rich Embed)
```
👀 v1.4.0 - Tier 3 Mastery Abilities Coming Soon

Something exciting is on the way... 🌱

✨ What to Expect
→ Speed bursts for level 100 professions
→ Bonus resource drops
→ Resource conservation mechanics
→ And more to be revealed...

💡 More Info
Stay tuned for the full reveal!

☕ Support Development
Enjoying Living Lands Reloaded? Consider supporting development: Ko-fi.com/moshpitplays
```

## Notes

- **NEVER** use GitHub release links in Discord announcements
- **ALWAYS** verify Curseforge upload before announcing
- Test the download link before posting to Discord
- Keep announcement messages concise and clear
- Use emojis sparingly (✅, 🔧, ⚠️, 🎉, 🚀, 🔥)
- Include version number in all announcements
- Mention if update is critical/recommended/optional
