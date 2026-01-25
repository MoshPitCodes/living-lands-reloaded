# Discord Teaser Post - Living Lands Reloaded v1.0.0-beta

## MCP Discord Tool Call

**Using `discord_send_teaser` (RECOMMENDED):**

```json
{
  "webhook_name": "releases",
  "version": "v1.0.0-beta",
  "headline": "Coming Soon - A Complete Rewrite",
  "highlights": [
    "🏗️ Complete rewrite with modern architecture and solid foundation",
    "🗄️ Per-world player progression with isolated SQLite databases",
    "⚙️ Hot-reloadable YAML configuration with automatic migration",
    "📦 Modular architecture designed to scale with Hytale's API",
    "⚡ Optimized performance: async operations, UUID caching, efficient HUD updates",
    "🔧 Type-safe service registry for clean inter-module communication",
    "🍖 Metabolism Module (hunger/thirst/energy) coming next!",
    "📊 Future: Leveling System, Claims Module, and more..."
  ],
  "additional_info": "Beta release is imminent! Infrastructure is complete, Metabolism module is next. Looking for beta testers - join us on this journey! Stay tuned to this channel for updates.",
  "style": "beta",
  "thumbnail_url": "https://raw.githubusercontent.com/MoshPitCodes/living-lands-reloaded/main/.github/assets/logo/living-lands-reloaded-logo.png",
  "footer_text": "Living Lands Reloaded • Beta Coming Soon"
}
```

**Expected Output:**

**Title:** `👀 v1.0.0-beta - Coming Soon - A Complete Rewrite`

**Description:**
```
Something exciting is on the way... 🌱

✨ What to Expect
• 🏗️ Complete rewrite with modern architecture and solid foundation
• 🗄️ Per-world player progression with isolated SQLite databases
• ⚙️ Hot-reloadable YAML configuration with automatic migration
• 📦 Modular architecture designed to scale with Hytale's API
• ⚡ Optimized performance: async operations, UUID caching, efficient HUD updates
• 🔧 Type-safe service registry for clean inter-module communication
• 🍖 Metabolism Module (hunger/thirst/energy) coming next!
• 📊 Future: Leveling System, Claims Module, and more...

💡 More Info
Beta release is imminent! Infrastructure is complete, Metabolism module is next. Looking for beta testers - join us on this journey! Stay tuned to this channel for updates.

☕ Support Development
Enjoying Living Lands? Consider supporting development: Ko-fi.com/moshpitplays
```

**Color:** `#FEE75C` (Beta Yellow)

**Thumbnail:** Living Lands Reloaded logo (top-right)

**Footer:** `Living Lands Reloaded • Beta Coming Soon`

---

## Alternative: Simple Message (No Embed)

**Using `discord_send_message`:**

For a simpler teaser without embeds, use:

```json
{
  "webhook_name": "releases",
  "content": "👀 **Living Lands Reloaded v1.0.0-beta - Coming Soon**\n\nSomething big is in the works for Hytale survival mechanics... 🌱\n\n**🏗️ A Complete Rewrite**\nThe Living Lands you know and love is being rebuilt from the ground up with modern architecture, better performance, and a rock-solid foundation for the future.\n\n**✨ What's New in v3**\n• Per-world player progression with isolated SQLite databases\n• Hot-reloadable YAML configuration with automatic migration\n• Modular architecture designed to scale with Hytale's API\n• Optimized performance with async operations and smart caching\n\n**🚀 What's Coming**\n• Metabolism Module (hunger/thirst/energy)\n• Leveling System (profession-based XP)\n• Claims Module (land protection)\n\n**📅 Timeline**\nBeta release is imminent! Infrastructure complete, Metabolism module next.\n\n**👉 Want to Help?**\nWe're looking for beta testers! Interested? Let us know.\n\n—\n*Living Lands Reloaded v1.0.0-beta*",
  "username": "Living Lands Bot"
}
```

**Note:** This version doesn't include the logo thumbnail. For branded announcements with the logo, use `discord_send_announcement` instead.

---

## Alternative: Shorter Teaser

**Using `discord_send_teaser`:**

For a condensed version:

```json
{
  "webhook_name": "releases",
  "version": "v1.0.0-beta",
  "headline": "The Rewrite Begins",
  "highlights": [
    "🏗️ Complete architectural rewrite with modern patterns",
    "🗄️ Per-world progression with isolated databases",
    "⚙️ Automatic config migration for seamless upgrades",
    "⚡ Better performance and rock-solid foundation",
    "✅ Core infrastructure complete",
    "🚧 Metabolism module in progress - coming soon!"
  ],
  "additional_info": "Beta release coming soon! Stay tuned.",
  "style": "beta",
  "thumbnail_url": "https://raw.githubusercontent.com/MoshPitCodes/living-lands-reloaded/main/.github/assets/logo/living-lands-reloaded-logo.png",
  "footer_text": "Living Lands Reloaded v1.0.0-beta"
}
```

---

## Usage Instructions

1. **Choose your format:**
   - **Full teaser** (8 highlights + additional info) - Best for generating hype and providing details
   - **Short teaser** (6 highlights) - Best for quick status updates
   - **Simple message** - Best for informal updates (no logo thumbnail, no donation section)

2. **Copy the JSON** from your chosen format above

3. **Use MCP Discord tool:**
   - For teaser embeds with logo: `discord_send_teaser` with the JSON parameters
   - For release embeds: `discord_send_announcement` (when it's actually released)
   - For simple text: `discord_send_message` with content string

4. **Customize as needed:**
   - Adjust the headline for your announcement style
   - Add/remove highlights to fit your message (max 10)
   - Update additional_info for context or call-to-action
   - Update footer text for different contexts

5. **Post timing:**
   - Use `discord_send_teaser` when announcing upcoming features (WIP)
   - Use `discord_send_announcement` when the release is available
   - Switch to `style: "release"` and green color when going stable

6. **Donation section:**
   - Automatically added to all teaser and announcement embeds
   - Links to Ko-fi.com/moshpitplays
   - No need to manually include in your parameters

---

## Notes

- **Logo thumbnail** automatically appears in top-right corner for all `discord_send_teaser` and `discord_send_announcement` calls
- **Donation section** automatically added to all embeds with Ko-fi link (Ko-fi.com/moshpitplays)
- **Beta color (#FEE75C yellow)** signals work-in-progress status
- **When full release happens:** Use `discord_send_announcement` instead and change `style: "release"` for green color (#57F287)
- **Webhook:** All posts use the `"releases"` webhook configured for Living Lands announcements
