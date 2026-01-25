# Discord Embedded Post Template

This template provides a standardized format for creating Discord embedded posts for Living Lands Reloaded releases and announcements using the MCP Discord server.

## MCP Discord Configuration

**Webhook Name:** `releases`  
**Logo Thumbnail:** `https://raw.githubusercontent.com/MoshPitCodes/living-lands-reloaded/main/.github/assets/logo/living-lands-reloaded-logo.png`

All posts should include the Living Lands Reloaded logo as a thumbnail in the top-right corner.

---

## Release Announcement Template

### MCP Tool Parameters

Use `discord_send_announcement` with the following structure:

```kotlin
webhook_name: "releases"
version: "vX.X.X" // e.g., "v1.0.0-beta"
headline: "[Concise main feature headline]"
changes: [
    "[Feature/Change 1]",
    "[Feature/Change 2]",
    "[Feature/Change 3]",
    // ... up to 10 items
]
download_url: "[Optional GitHub release URL]"
style: "release" | "beta" | "hotfix" | "custom"
beta_warning: true | false
thumbnail_url: "https://raw.githubusercontent.com/MoshPitCodes/living-lands-reloaded/main/.github/assets/logo/living-lands-reloaded-logo.png"
```

### Embed Structure

**Title:** `[Emoji] Living Lands Reloaded vX.X.X - [Headline]`

**Description:**
```
[Brief 1-2 sentence overview of the release]

**[Section 1 Title]**
• [Feature/Change 1]
• [Feature/Change 2]
• [Feature/Change 3]

**[Section 2 Title]**
• [Item 1]
• [Item 2]

**[Additional Sections as needed]**

**[Closing Statement]**
```

**Color (by style):** 
- **release:** `#57F287` (Green)
- **beta:** `#FEE75C` (Yellow)
- **hotfix:** `#ED4245` (Red)
- **custom:** `#5865F2` (Blurple)

**Thumbnail:** `https://raw.githubusercontent.com/MoshPitCodes/living-lands-reloaded/main/.github/assets/logo/living-lands-reloaded-logo.png` (Always included)

**Donation Section:** Automatically added to all announcements
- `☕ Support Development`
- `Enjoying Living Lands? Consider supporting development: Ko-fi.com/moshpitplays`

**Footer:** 
- `Living Lands Reloaded • [Date]`
- Or: `Version X.X.X • [Release Type]`

---

## Example 1: Beta Release (MCP Tool Call)

**Using `discord_send_announcement`:**

```json
{
  "webhook_name": "releases",
  "version": "v1.0.0-beta",
  "headline": "The Foundation - Complete Architectural Rewrite",
  "changes": [
    "🏗️ Per-world player progression with isolated SQLite databases",
    "⚙️ Hot-reloadable YAML configuration with automatic migration",
    "📦 Modular architecture designed for Hytale's evolving API",
    "⚡ Performance optimizations: UUID caching, async DB ops, efficient HUD updates",
    "🔧 Developer experience: Jackson YAML, type-safe services, comprehensive logging",
    "🚀 Foundation ready for Metabolism, Leveling, and Claims modules"
  ],
  "download_url": "https://github.com/MoshPitCodes/living-lands-reloaded/releases/tag/v1.0.0-beta",
  "style": "beta",
  "beta_warning": true,
  "thumbnail_url": "https://raw.githubusercontent.com/MoshPitCodes/living-lands-reloaded/main/.github/assets/logo/living-lands-reloaded-logo.png",
  "footer_text": "Living Lands Reloaded • Beta Release"
}
```

**Rendered Output:**

**Title:** `🌱 Living Lands Reloaded v1.0.0-beta - The Foundation - Complete Architectural Rewrite`

**Description:**
```
The complete architectural rewrite is here! Living Lands Reloaded v1.0.0-beta brings a solid foundation for the future of survival mechanics in Hytale.

**Changes:**
• 🏗️ Per-world player progression with isolated SQLite databases
• ⚙️ Hot-reloadable YAML configuration with automatic migration
• 📦 Modular architecture designed for Hytale's evolving API
• ⚡ Performance optimizations: UUID caching, async DB ops, efficient HUD updates
• 🔧 Developer experience: Jackson YAML, type-safe services, comprehensive logging
• 🚀 Foundation ready for Metabolism, Leveling, and Claims modules

⚠️ **Beta Notice**: Please backup your world before testing!

☕ Support Development
Enjoying Living Lands? Consider supporting development: Ko-fi.com/moshpitplays
```

**Color:** `#FEE75C` (Beta Yellow)

**Thumbnail:** Living Lands Reloaded logo (top-right)

**Footer:** `Living Lands Reloaded • Beta Release`

---

## Example 2: Teaser/Coming Soon Announcement

**Using `discord_send_teaser`:**

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
    "⚡ Optimized performance: async operations, UUID caching",
    "🍖 Metabolism Module (hunger/thirst/energy) coming next!",
    "📊 Future: Leveling System, Claims Module, and more..."
  ],
  "additional_info": "Beta release is imminent! Looking for testers - let us know if interested. Stay tuned to this channel for updates.",
  "style": "beta",
  "thumbnail_url": "https://raw.githubusercontent.com/MoshPitCodes/living-lands-reloaded/main/.github/assets/logo/living-lands-reloaded-logo.png",
  "footer_text": "Living Lands Reloaded • Beta Coming Soon"
}
```

**Rendered Output:**

**Title:** `👀 v1.0.0-beta - Coming Soon - A Complete Rewrite`

**Description:**
```
Something exciting is on the way... 🌱

✨ What to Expect
• 🏗️ Complete rewrite with modern architecture and solid foundation
• 🗄️ Per-world player progression with isolated SQLite databases
• ⚙️ Hot-reloadable YAML configuration with automatic migration
• 📦 Modular architecture designed to scale with Hytale's API
• ⚡ Optimized performance: async operations, UUID caching
• 🍖 Metabolism Module (hunger/thirst/energy) coming next!
• 📊 Future: Leveling System, Claims Module, and more...

💡 More Info
Beta release is imminent! Looking for testers - let us know if interested. Stay tuned to this channel for updates.

☕ Support Development
Enjoying Living Lands? Consider supporting development: Ko-fi.com/moshpitplays
```

**Color:** `#FEE75C` (Beta Yellow)

**Thumbnail:** Living Lands Reloaded logo (top-right)

**Footer:** `Living Lands Reloaded • Beta Coming Soon`

---

## Example 3: Custom Message (Simple Text)

**Using `discord_send_message`:**

For custom announcements that don't fit the release format, use `discord_send_message`:

```json
{
  "webhook_name": "releases",
  "content": "👀 **Coming Soon: Living Lands Reloaded v1.0.0-beta**\n\nSomething's brewing in the world of Hytale survival mechanics...\n\n**🌟 What to Expect**\n• Complete rewrite from the ground up\n• Solid architecture for long-term stability\n• Per-world progression systems\n• Smooth configuration management\n\n**💡 Why a Rewrite?**\nv2.6.0 was great, but v3 brings modern patterns, better performance, and a foundation that grows with Hytale's evolving API.\n\n**📅 Timeline**\nBeta release coming soon! Keep an eye on this channel for updates.",
  "username": "Living Lands Bot"
}
```

**Note:** Simple messages don't support embeds with thumbnails. For announcements with the logo, use `discord_send_announcement` instead.

---

## Example 4: Hotfix Release

**Using `discord_send_announcement`:**

```json
{
  "webhook_name": "releases",
  "version": "v1.0.1",
  "headline": "Critical Hotfix",
  "changes": [
    "🐛 Fixed database connection leak on world unload",
    "🔧 Resolved config reload crash with active players",
    "🎨 Corrected HUD element z-index collision",
    "📦 No config changes required - just update JAR and restart"
  ],
  "download_url": "https://github.com/MoshPitCodes/living-lands-reloaded/releases/tag/v1.0.1",
  "style": "hotfix",
  "thumbnail_url": "https://raw.githubusercontent.com/MoshPitCodes/living-lands-reloaded/main/.github/assets/logo/living-lands-reloaded-logo.png",
  "footer_text": "Living Lands Reloaded • Hotfix"
}
```

**Rendered Output:**

**Description includes:**
- 🐛 Bug fixes listed
- 📦 Installation instructions
- ☕ Support Development section (automatically added)

**Color:** `#ED4245` (Red)

**Thumbnail:** Living Lands Reloaded logo (top-right)

**Footer:** `Living Lands Reloaded • Hotfix`

---

## Emoji Guide

Use emojis to enhance readability and visual appeal:

- **🌱** - New features, growth, nature theme
- **🏗️** - Infrastructure, architecture, foundation
- **📊** - Performance, metrics, statistics
- **🔧** - Fixes, tools, maintenance
- **🚀** - Future plans, upcoming features
- **💡** - Insights, rationale, explanations
- **📅** - Timeline, schedule, dates
- **🐛** - Bug fixes
- **📦** - Installation, deployment
- **👀** - Teasers, previews
- **⚡** - Speed, performance improvements
- **🎨** - UI/UX changes
- **📚** - Documentation
- **✅** - Completed tasks, verification

---

## Best Practices

1. **Keep it Concise:** Discord embeds should be scannable. Use bullet points liberally.
2. **Highlight Impact:** Focus on what changed and why it matters to users.
3. **Use Sections:** Break content into logical sections with bold headers.
4. **Version Consistency:** Always include version number in title and footer.
5. **Call to Action:** End with next steps or what users should expect.
6. **Test First:** Preview your embed before posting to ensure formatting looks good.

---

## Technical Notes

### MCP Discord Tools

**Primary Tool:** `discord_send_announcement` (for releases)
- Supports rich embeds with color coding
- Automatically formats changes as bullet points
- Includes thumbnail support
- Beta warnings when needed
- Max 10 changes per announcement
- **Use when:** The release is available now

**Teaser Tool:** `discord_send_teaser` (for upcoming releases)
- Rich embeds with "coming soon" language
- Highlights features without "is live" messaging
- Includes thumbnail support
- Perfect for building anticipation
- Max 10 highlights
- **Use when:** Announcing an upcoming release or teasing features

**Secondary Tool:** `discord_send_message` (for simple messages)
- Plain text or markdown
- No embed support (no thumbnail)
- Max 2000 characters
- Use for quick updates or non-release announcements

### Logo/Thumbnail URL

**Always use:** `https://raw.githubusercontent.com/MoshPitCodes/living-lands-reloaded/main/.github/assets/logo/living-lands-reloaded-logo.png`

### Color Codes Reference (by style)
```
release:  #57F287 (Green)
beta:     #FEE75C (Yellow)
hotfix:   #ED4245 (Red)
custom:   #5865F2 (Blurple)
```

### Character Limits
- **Headline:** 256 characters max
- **Changes (each):** Keep concise (50-80 chars recommended)
- **Changes (total):** Max 10 items
- **Footer:** 100 characters max
- **Message content:** 2000 characters max (for `discord_send_message`)

### Discord Markdown Support
- **Bold:** `**text**`
- **Italic:** `*text*` or `_text_`
- **Underline:** `__text__`
- **Strikethrough:** `~~text~~`
- **Code:** `` `code` ``
- **Code Block:** ` ```code block``` `
- **Bullet Points:** Automatically added by MCP tool, or use `•` (U+2022) for manual formatting

---

## Template Checklist

Before posting, verify:
- [ ] Webhook name is `"releases"`
- [ ] Version number is correct (e.g., `"v1.0.0-beta"`)
- [ ] Headline is concise and descriptive (max 256 chars)
- [ ] Changes array has 1-10 items
- [ ] Each change starts with emoji for visual interest
- [ ] Download URL points to correct GitHub release (if applicable)
- [ ] Style matches release type (`"release"` / `"beta"` / `"hotfix"` / `"custom"`)
- [ ] Thumbnail URL is included: `https://raw.githubusercontent.com/MoshPitCodes/living-lands-reloaded/main/.github/assets/logo/living-lands-reloaded-logo.png`
- [ ] Beta warning enabled for beta releases (`beta_warning: true`)
- [ ] Footer text is descriptive (optional)
- [ ] No typos in JSON structure
