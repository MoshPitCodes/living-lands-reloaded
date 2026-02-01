# Living Lands Reloaded - Changelog (Mod Upload)

## 1.4.1 (Latest - Stability & Performance Update)

### 🎉 NEW: Tier 2 Ability Enhancements!

All Tier 2 profession abilities now provide **MASSIVE** max stat increases for late-game value:

- ⚡ **Combat (Iron Stomach):** +15 → **+35 max hunger** (100 → 135 total!)
- ⚡ **Mining (Desert Nomad):** +10 → **+35 max thirst** (100 → 135 total!)
- ⚡ **Logging (Tireless Woodsman):** +10 → **+35 max energy** (100 → 135 total!)
- ⚡ **Building (Enduring Builder):** **+15 max stamina** (NOW IMPLEMENTED - was missing!)

**Why the buff?** Level 45 is a significant milestone (mid-game) and deserves impactful rewards. These bonuses now allow you to:
- Go **35% longer** without eating/drinking (hunger/thirst)
- Survive **35% longer** in extended expeditions (energy)
- Build/fight **longer** without exhaustion (stamina)

### 🛡️ Critical Stability Fixes (11 Algorithm Audit Fixes)

This update addresses critical race conditions, data integrity issues, and UX problems discovered during comprehensive algorithm audit:

**Data Safety & Reliability:**
- ✅ **Race Condition Protection** - Fixed concurrent modification of profession stats when admin commands and XP awards happen simultaneously
- ✅ **Auto-Save System** - Added 5-minute periodic auto-save for both Professions and Metabolism data (prevents data loss on crashes)
- ✅ **Database Write Verification** - All database writes now verify success with row count checks and warnings
- ✅ **Memory Leak Prevention** - Guaranteed cleanup on player disconnect even when save operations fail

**Immediate Response Improvements:**
- ⚡ **Instant Food Effects** - Eating food now IMMEDIATELY updates buffs/debuffs and HUD (no more 2-second delay!)
  - Before: Starving debuff persisted for 2 seconds after eating
  - After: Debuff removed the instant you eat food
- ⚡ **Smoother Buff Transitions** - Debuff hysteresis increased from 5 to 10 points (matches buffs)
  - Prevents rapid flickering when stats hover near thresholds
  - Example: Tier 1 debuff now exits at 85% instead of 80% (more stable)

**Bug Fixes:**
- 🔧 **Max Level XP Protection** - Players at max level (100) no longer receive XP
  - Prevents wasteful processing when profession is maxed out
- 🔧 **HUD Progress Bar Fix** - Progress bars correctly account for passive ability max increases
  - Bar fills to 100% at max capacity (115 hunger with Iron Stomach, 110 thirst/energy with abilities)
- 🔧 **Modded Consumables Warnings** - Fixed false validation warnings for T4-T7 modded foods
  - Validator now correctly supports extended tier system (T1-T7)

### Improved
- 📝 **Config Readability** - Modded consumables config now includes item names for clarity
  - New `itemId` field shows which item triggers which effect
  - All 92 pre-configured consumables documented with item IDs
- 🧹 **Code Cleanup** - Removed redundant annotations and unused methods (18 lines of dead code removed)

### Technical Details
- Coroutine Mutex synchronization for async-compatible profession state locking
- Fresh session lookups prevent stale ECS store references
- Finally blocks guarantee cleanup execution
- Thread-safe ability state management with proper disconnect handling

---

## 1.4.0 (Modded Consumables + Professions Complete!)

### 🎉 NEW: Modded Consumables Support!

Living Lands now integrates seamlessly with food/drink mods! Your favorite gourmet meals from other mods now properly restore hunger, thirst, and energy.

#### What's New
- **92 Pre-configured Consumables** - Works out-of-the-box with popular food mods:
  - 🍖 **Hidden's Harvest Delights** - 44 gourmet foods (T2-T7)
  - 🍞 **NoCube's Bakehouse + Tavern + Orchard** - 48 items (breads, drinks, meals)
- **Extended Tier System** - Support for T1-T7 items (vanilla only had T1-T3)
  - T6 Exquisite Feast: Restores 53 hunger (69 with MEAT multiplier!)
  - T7 Legendary Feast: Restores 65 hunger (84.5 with MEAT multiplier!)
- **Automatic Tier Detection** - Smart detection from effect IDs (no manual config needed)
- **Custom Multipliers** - Fine-tune restoration amounts per item
- **Hot-Reload Support** - Edit config and `/ll reload` to apply changes instantly

#### How It Works
Just install your favorite food mod alongside Living Lands! The metabolism system will automatically detect and handle modded consumables. All 92 items are enabled by default.

### 🎉 Professions Module 100% Complete!

All Tier 3 profession abilities are now fully functional! Reach level 100 to unlock powerful endgame abilities:

#### Tier 3 Abilities
- **Survivalist (Combat)** - Passive -15% metabolism depletion reduction. Your survival stats drain 15% slower.
- **Adrenaline Rush (Combat)** - Gain +10% speed boost for 5 seconds after killing an entity. Perfect for chasing down fleeing enemies or escaping danger.
- **Ore Sense (Mining)** - 10% chance to get bonus ore drop when mining ore blocks. More resources for your efforts!
- **Timber! (Logging)** - 25% chance to get extra log when chopping trees. Stock up on wood faster!
- **Efficient Architect (Building)** - 12% chance to refund placed block back to inventory. Build more with less!

### Fixed
- 🔧 **HUD Max Capacity Display** - HUD now correctly shows 115/110/110 when Tier 2 abilities are active
  - Iron Stomach (Combat): +15 max hunger → 115 total
  - Desert Nomad (Mining): +10 max thirst → 110 total
  - Tireless Woodsman (Logging): +10 max energy → 110 total
- 🔧 **Food Consumption Messages** - Messages now show ACTUAL amount restored, not theoretical amount
  - Example: Eating T6 food at 110/115 hunger now shows "+5.0 hunger" instead of "+68.9 hunger"
  - Clearer feedback on how much your stats actually increased

### Improved
- ⚡ **Admin Command UX** - Instant HUD refresh when using `/ll prof set/add/reset` commands
  - Eliminated ~2 second delay when adjusting profession levels
  - No more metabolism bar flickering during testing
  - Professions panel updates instantly when panel is open
  - Better experience for server owners testing and debugging
- 📊 **Food Consumption Logging** - Enhanced debug logs show calculated vs actual restoration amounts

### Technical Implementation
- Thread-safe ability triggers with proper cleanup on disconnect/shutdown
- Coroutine-based timed effects for Adrenaline Rush (5 second duration)
- Anti-exploit validation for Efficient Architect (blocks air, liquids, bedrock, command blocks)
- Direct inventory manipulation for item bonuses (Ore Sense, Timber!)
- Ore and log detection helpers for accurate XP tracking

### Performance
- ⚡ Ability triggers: <5ms per activation
- ⚡ HUD refresh: Instant (targeted panel update, not full rebuild)
- 🎯 Zero allocations in hot paths
- 💾 Minimal memory overhead for ability tracking

---

## 1.3.1 (Hotfix)

### Performance Optimization
- ⚡ **HUD Refresh Optimization** - 90% faster XP updates! HUD no longer rebuilds entirely when gaining XP
  - Root cause: `notifyXpGain()` was calling both `refreshAllProfessionsPanels()` (efficient) AND `refreshHud()` (full rebuild)
  - Fix: Removed redundant `refreshHud()` call - now only profession panels update
  - Impact: ~100ms → ~10ms per XP gain
  - Entire HUD (metabolism bars, buffs, debuffs) no longer refreshes unnecessarily when gaining XP

---

## 1.3.0

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
