# Living Lands Reloaded - Changelog (Mod Upload)

## 1.3.0 (Latest)

### New Features
- 🎉 **Announcer Module** - Complete server messaging system with:
  - **MOTD (Message of the Day)** - Welcome messages displayed immediately on player join
  - **Welcome Messages** - Different messages for first-time vs returning players with join count tracking
  - **Recurring Announcements** - Automated server tips/info with configurable intervals (5m, 10m, etc.)
  - **Broadcast Commands** - `/ll broadcast <message>` for admins to send server-wide messages
  - **Placeholder Support** - Dynamic values: `{player_name}`, `{server_name}`, `{join_count}`
  - **Color Code Support** - Minecraft-style formatting (`&a` = green, `&6` = gold, etc.)
  - **Hot-Reload** - `/ll reload announcer` updates config without restart
- ✨ **MessageFormatter Enhancement** - New `announcement()` method parses color codes for clean server messages

### Critical Fixes
- 🔥 **HUD Crash on Player Join** - Fixed blocker crash with "Selected element not found: #HungerBar.Text"
  - Root cause: `build()` calling `set()` before client finished parsing UI file
  - Fix: Separated build (structure) from update (data population)
  - Impact: Players can now join without crashes
- 🔥 **Panel Toggle Bug** - Fixed `/ll professions` and `/ll progress` commands showing brief flash then disappearing
  - Root cause: Commands calling `refreshHud()` which rebuilt entire HUD
  - Fix: Removed unnecessary `refreshHud()` calls
  - Impact: Panel commands now work correctly

### Performance
- ⚡ MOTD send: <1ms per player
- ⚡ Welcome message: <5ms per player
- ⚡ Recurring announcements: <10ms broadcast to all players
- 💾 Memory impact: <1MB for join tracking

### Technical Changes
- HUD now uses build/update pattern to prevent race conditions
- Panel toggles populate data directly without full rebuilds
- Announcer uses coroutine-based scheduler with graceful shutdown
- In-memory join tracking with ConcurrentHashMap

---

## 1.2.3 (HOTFIX)

### Fixed
- 🔥 **CRITICAL: Food Consumption Bug** - Fixed bug where consuming food only restored metabolism stats on the first consumption. Subsequent food consumptions now properly restore hunger/thirst/energy every time.
  - Root cause: Effect detection logic was filtering out re-applied food effects (Hytale reuses effect indexes)
  - Changed detection algorithm to properly handle repeated consumptions
- **Memory Efficiency**: Reduced effect tracking TTL from 5s to 2s for better memory usage
- **Code Cleanup**: Removed duplicate player cleanup call on disconnect

### Improved
- **Debugging**: Added logging for re-detected food effects to aid troubleshooting

---

## 1.2.2

### Improved
- **Logging System**: Aligned with Java logging standards (ERROR→SEVERE, TRACE→FINEST, added CONFIG level)
- **Cleaner Logs**: Changed 215 INFO logs to FINE for reduced default verbosity
- **Codebase Quality**: Removed 770 lines of stub code and 87 TODO comments
- **Code Organization**: Added safety guards to prevent accidental enablement of incomplete modules

### Fixed
- **Player Disconnect**: Fixed race condition causing "Invalid entity reference" error when players disconnect
- **Log Filtering**: ERROR logs now properly use SEVERE level for production monitoring

### Developer
- **Documentation**: Created FUTURE_MODULES.md preserving design documentation for planned features
- **Build Scripts**: Added cleanup verification script for maintaining code quality

---

## 1.2.1

### Improved
- **Gradle 9.3.1**: Updated build system for better performance
- **Zero Build Warnings**: Fixed all Kotlin compiler warnings for cleaner builds
- **Dynamic Versioning**: Deploy scripts now read version from single source of truth

---

## 1.2.0

### New
- **Functional Tier 2 Abilities**: Profession abilities now actually work! Reaching level 45 unlocks permanent stat bonuses:
  - **Iron Stomach** (Combat L45): +15 max hunger capacity
  - **Desert Nomad** (Mining L45): +10 max thirst capacity
  - **Tireless Woodsman** (Logging L45): +10 max energy capacity
  - **Hearty Gatherer** (Gathering L45): +4 hunger and thirst when picking up food items
- **Unified HUD System**: Cleaner, more performant single-element HUD

### Improved
- **Ability Effect Tracking**: New centralized system ensures abilities apply correctly on login, level up, and world changes
- **Code Cleanup**: Removed ~1,200 lines of legacy HUD code for better maintainability

### Fixed
- **Duplicate Ability Bug**: Fixed abilities being applied multiple times on level up
- **HUD Crash**: Fixed crash caused by incorrect UI path resolution

---

## 1.1.0

### New
- **Admin Commands**: Full admin toolkit for managing player professions and metabolism
- **Thread Safety**: Improved concurrent access handling for high-population servers

### Fixed
- **Command Parsing**: Fixed argument parsing issues in admin commands
- **Race Conditions**: Eliminated potential data corruption under heavy load

---

## 1.0.1 (Initial Stable Release)

### New
- **RPG Survival Needs**: Hunger, Thirst, and Energy that change how you travel, fight, and explore
- **Professions Progression**: 5 professions with 100 levels each, earned naturally through gameplay
- **15 Passive Abilities**: Unlock powerful perks at levels 15, 45, and 100 in each profession
- **Automatic v2.6.0 Migration**: Your old profession levels transfer automatically on first startup
- **Welcome Messages**: Migrated players receive a personalized welcome showing their restored progress
- **Per-World Configuration**: Different survival profiles per world (creative/adventure/hardcore/arena)

### Improved
- **Migration System**: Fixed race conditions - migration now completes before players can join
- **XP Calculation**: Fixed negative XP display bug - profession progress now shows correctly
- **Performance**: Optimized for 100+ concurrent players with zero-allocation hot paths
- **Admin Control**: Quick config tuning without restarts via `/ll reload`

### Fixed
- **Migration Race Condition**: Migration now runs synchronously, preventing default data conflicts
- **Negative XP Display**: Total XP properly calculated from v2.6.0 level + currentXp
- **Stamina/Energy Edge Cases**: Resolved incorrect stamina behavior under certain debuffs
