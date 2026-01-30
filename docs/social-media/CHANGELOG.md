# Living Lands Reloaded - Changelog (Mod Upload)

## 1.0.1 (Stable Release)

### New
- **RPG Survival Needs**: Hunger, Thirst, and Energy that change how you travel, fight, and explore
- **Professions Progression**: 5 professions with 100 levels each, earned naturally through gameplay
- **15 Passive Abilities**: Unlock powerful perks at levels 3, 7, and 10 in each profession
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
