# Discord Post Example - How to Send

This document shows how to actually send the teaser post using the MCP Discord tools.

## Quick Start: Send the Teaser Post

To send the v1.0.0-beta teaser post to Discord, use the following MCP tool call:

### Full Teaser Version (RECOMMENDED)

```
Send a Discord teaser using discord_send_teaser with these parameters:

webhook_name: "releases"
version: "v1.0.0-beta"
headline: "Coming Soon - A Complete Rewrite"
highlights: [
  "🏗️ Complete rewrite with modern architecture and solid foundation",
  "🗄️ Per-world player progression with isolated SQLite databases",
  "⚙️ Hot-reloadable YAML configuration with automatic migration",
  "📦 Modular architecture designed to scale with Hytale's API",
  "⚡ Optimized performance: async operations, UUID caching, efficient HUD updates",
  "🔧 Type-safe service registry for clean inter-module communication",
  "🍖 Metabolism Module (hunger/thirst/energy) coming next!",
  "📊 Future: Leveling System, Claims Module, and more..."
]
additional_info: "Beta release is imminent! Infrastructure is complete, Metabolism module is next. Looking for beta testers - join us on this journey! Stay tuned to this channel for updates."
style: "beta"
thumbnail_url: "https://raw.githubusercontent.com/MoshPitCodes/living-lands-reloaded/main/.github/assets/logo/living-lands-reloaded-logo.png"
footer_text: "Living Lands Reloaded • Beta Coming Soon"
```

---

## What You'll See

After sending, Discord will display:

**Embed Card:**
- **Title:** 👀 v1.0.0-beta - Coming Soon - A Complete Rewrite
- **Color:** Yellow (#FEE75C) left border
- **Thumbnail:** Living Lands logo in top-right corner
- **Description:** "Something exciting is on the way... 🌱"
- **What to Expect Section:** All 8 highlights listed as bullet points
- **More Info Section:** Additional context and call-to-action
- **Support Development Section:** Ko-fi donation link (automatically added)
- **Footer:** "Living Lands Reloaded • Beta Coming Soon"

**Key Differences from Release Announcements:**
- Uses 👀 (eyes) emoji instead of test tube
- Says "Coming Soon" instead of "is live"
- Teaser-focused language ("What to Expect" vs "What's New")
- No beta warning (since it's not released yet)
- Donation section automatically added to all embeds

---

## Alternative Versions

### Shorter Teaser (6 highlights)

```
Send a Discord teaser using discord_send_teaser with these parameters:

webhook_name: "releases"
version: "v1.0.0-beta"
headline: "The Rewrite Begins"
highlights: [
  "🏗️ Complete architectural rewrite with modern patterns",
  "🗄️ Per-world progression with isolated databases",
  "⚙️ Automatic config migration for seamless upgrades",
  "⚡ Better performance and rock-solid foundation",
  "✅ Core infrastructure complete",
  "🚧 Metabolism module in progress - coming soon!"
]
additional_info: "Beta release coming soon! Stay tuned."
style: "beta"
thumbnail_url: "https://raw.githubusercontent.com/MoshPitCodes/living-lands-reloaded/main/.github/assets/logo/living-lands-reloaded-logo.png"
footer_text: "Living Lands Reloaded v1.0.0-beta"
```

### Simple Text Message (No Embed)

```
Send a Discord message using discord_send_message with these parameters:

webhook_name: "releases"
content: "👀 **Living Lands Reloaded v1.0.0-beta - Coming Soon**

Something big is in the works for Hytale survival mechanics... 🌱

**🏗️ A Complete Rewrite**
v3 brings modern architecture, better performance, and a solid foundation.

**✨ What's New**
• Per-world progression with SQLite databases
• Hot-reloadable YAML configuration
• Modular architecture for Hytale's API
• Optimized performance with async operations

**🚀 What's Coming**
• Metabolism Module (hunger/thirst/energy)
• Leveling System (XP & professions)
• Claims Module (land protection)

Beta release imminent! Looking for testers - let us know if interested!

—
*Living Lands Reloaded v1.0.0-beta*"
username: "Living Lands Bot"
```

---

## Tips for Using MCP Discord Tools

1. **Always include the logo:** Add `thumbnail_url` to all announcement posts
2. **Use the right tool:**
   - `discord_send_teaser` - For upcoming releases (uses "coming soon" language)
   - `discord_send_announcement` - For available releases (uses "is live" language)
   - `discord_send_message` - For simple text updates (no embed)
3. **Use beta style for betas:** `style: "beta"` gives yellow color
4. **Keep highlights/changes concise:** Each item should be 50-80 characters for readability
5. **Test first:** If unsure, send to a test channel first
6. **Max 10 items:** Tools limit you to 10 bullet points

---

## Verification

After posting, check:
- ✅ Logo appears in top-right corner
- ✅ Yellow color border on left side of embed
- ✅ All highlights/changes are formatted as bullet points
- ✅ "Support Development" section with Ko-fi link appears
- ✅ Beta warning appears (if enabled for releases)
- ✅ Footer text is correct
- ✅ No typos or formatting issues

---

## Next Steps

When ready to send:
1. Choose your format (full, short, or simple)
2. Copy the parameters from this document
3. Ask Claude Code to send using `discord_send_announcement` or `discord_send_message`
4. Verify the post appears correctly in Discord

For future releases, use the templates in `DISCORD_POST_TEMPLATE.md` to create new announcements.
