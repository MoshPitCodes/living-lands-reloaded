# Living Lands - Agent Instructions

## Project Overview

**Living Lands** is a Hytale server mod implementing survival mechanics (hunger, thirst, energy) with per-world player progression.

- **Language:** Kotlin (Java 25 compatible)
- **Build:** Gradle with Kotlin DSL
- **Server:** HytaleServer.jar in `libs/Server/`
- **Current Version:** 1.0.0-beta (v3 rewrite from v2.6.0)

## Previous Version (v2.6.0-beta)

A working version exists in the public repository with **complete implemented modules**:

**Repository:** https://github.com/MoshPitCodes/hytale-livinglands

### Working Features in v2.6.0 (Assess for Reuse)

When examining v2.6.0 code, assess these components for reuse rather than copying:

| Component | Reuse Assessment | Notes |
|-----------|-----------------|-------|
| **MetabolismSystem** | HIGH | Core stat tracking, verify only API differences |
| **Buffs/DebuffsSystem** | HIGH | Hysteresis logic, stat thresholds - adapt to new architecture |
| **FoodConsumptionProcessor** | MEDIUM | Effect detection patterns may need API updates |
| **LevelingService** | MEDIUM | XP/Profession logic good, integrate with new persistence |
| **HUD System** | MEDIUM | Multi-component HUD architecture worth studying |
| **Command Implementation** | LOW | Old uses different API, reimplement using CommandBase |
| **Config System** | LOW | Old uses JSON, new uses YAML - patterns useful only |

**Assessment Guidelines:**
1. Study the **logic/algorithms** but rewrite to use new API
2. Verify all Hytale API calls against `docs/HYTALE_API_REFERENCE.md`
3. Adapt to new modular architecture with proper lifecycle
4. Don't copy paste - patterns changed too much between versions

## Architecture

```
LivingLandsPlugin (JavaPlugin entry point)
    └── CoreModule (singleton hub)
        ├── ServiceRegistry (type-safe service locator)
        ├── PlayerRegistry (player session tracking)
        ├── WorldRegistry (per-world contexts)
        ├── ConfigManager (YAML hot-reload)
        └── MultiHudManager (composite HUD system)

WorldContext (per world UUID)
    └── PersistenceService (SQLite DB)
        └── Repositories (PlayerData, Metabolism, etc.)

Modules: Metabolism, Leveling, Claims, Hud
```

## Key Patterns

### ECS Thread Safety
All ECS access must occur on the WorldThread. Use `world.execute { }` for safety:
```kotlin
world.execute {
    val component = store.getComponent(ref, ComponentType.getComponentType())
}
```

### Event Registration
Use `eventRegistry.register()` with error handling:
```kotlin
eventRegistry.register(PlayerReadyEvent::class.java) { event ->
    try {
        // Handle event
    } catch (e: Exception) {
        logger.atInfo().log("Error in event: ${e.message}")
    }
}
```

### Command Implementation
Extend `CommandBase` for all commands:
```kotlin
class MyCommand : CommandBase("mycommand", "Description", false) {
    override fun executeSync(ctx: CommandContext) {
        ctx.sendMessage(Message.raw("Response"))
    }
}
```

### Player API Access
PlayerRef does NOT have `worldUuid` getter. Access world via:
```kotlin
// From PlayerReadyEvent
val world = event.player.world

// Or from entity store
val player = store.getComponent(ref, Player.getComponentType())
val world = player.world
```

### Service Registration
```kotlin
CoreModule.services.register<MyService>(instance)
val service = CoreModule.services.get<MyService>()
```

### Hysteresis for State Changes
Use different enter/exit thresholds to prevent flickering:
- Debuff enters at 20%, exits at 40%
- Buff enters at 90%, exits at 80%

## Directory Structure

```
src/main/kotlin/com/livinglands/
├── LivingLandsPlugin.kt        # Entry point
├── core/                       # CoreModule, registries
├── api/                        # Module interface, AbstractModule
├── modules/
│   ├── metabolism/             # Hunger/thirst/energy
│   ├── leveling/               # XP and professions
│   └── claims/                 # Land protection
└── util/                       # SpeedManager, helpers

LivingLandsReloaded/            # Runtime data folder
├── config/*.yml                # Hot-reloadable configs
└── data/{world-uuid}/*.db      # Per-world SQLite
```

## Implementation Rules

1. **Per-world isolation** - All player data keyed by (worldId, playerId)
2. **API verification first** - Always check `docs/HYTALE_API_REFERENCE.md` before using online docs
3. **Config in YAML** - Never store config in database
4. **Fail gracefully** - Catch exceptions, log, continue
5. **Minimal logging** - Debug only when `config.debug = true`
6. **Thread-safe collections** - Use `ConcurrentHashMap` for shared state
7. **Event error handling** - Wrap all event handlers in try/catch blocks
8. **UUID access** - Use `PlayerRef.uuid` property, not deprecated `getUuid()` if possible

## Agent Usage Guidelines

When working on this project, leverage Claude Code agents for specialized tasks:

### Primary Agents (Auto-proactive)

| Agent | When to Use | For What |
|-------|-------------|----------|
| **code-review** | After writing significant code | Review code quality, security, maintainability before committing |
| **architecture-review** | After implementing features | Check architectural consistency with project patterns |
| **markdown-formatter** | Creating/updating docs | Ensure proper markdown syntax and structure |
| **git-flow-manager** | Branch management | Feature branches, releases, pull requests |
| **error-detective** | Debugging issues | Analyze logs, investigate production errors |

### Task-Based Agents (Use via / or delegate)

| Agent | Command Pattern | Good For |
|-------|----------------|----------|
| **explore** | Codebase searches, structure analysis | Finding files by patterns, searching code |
| **general** | Multi-step autonomous tasks | Complex workflows requiring multiple operations |
| **java-kotlin-backend** | Core backend development | Kotlin-specific implementation, Gradle build config, server API integration |
| **nixos** | Environment setup, package management | NixOS/Nix Flake reproducible dev environments, Java 25 + Kotlin, Gradle 9.3.0 |

### Code Review Workflow

Use `code-review` agent PROACTIVELY after writing significant code:
1. Implement a feature (e.g., MetabolismSystem)
2. Code-review agent analyzes the implementation
3. Apply suggested fixes/refinements
4. Rebuild and test

### Architecture Review Workflow

Use `architecture-review` when:
- Adding new modules or major systems
- Refactoring existing components
- After completing a phase from IMPLEMENTATION_PLAN.md

### Example Agent Delegation

```bash
# For exploring v2.6.0 code to assess reuse
/delegate Code architecture, find metabolism systems, assess API usage patterns

# Code will automatically invoke explore agent for complex searches
```

**Important:** The error-detective agent should be used PROACTIVELY when debugging issues as it specializes in log analysis and pattern recognition.

## Current Phase

See `docs/IMPLEMENTATION_PLAN.md` for detailed task breakdown.

**MVP Scope:** Core infrastructure + Metabolism module with HUD, debuffs, and food consumption.

## Building

```bash
./gradlew build
# Output: build/libs/livinglands-*.jar
```

## Hytale Server Resources

### Compile Dependencies
- `libs/Server/HytaleServer.jar` - Server JAR (83.7 MB)
  - Marked as `compileOnly` in build.gradle.kts
  - Contains all server-side API classes
  - Use `javap` to inspect classes: `javap -p libs/Server/HytaleServer.jar <ClassName>`

### Extracted Reference
- `libs/Server/Extracted/HytaleServer/` - JAR contents extracted for reference
  - Full package structure under `com/hypixel/hytale/`
  - Browse compiled .class files directly
  - Useful for inspecting classes without decompiling
  - Key directories:
    - `server/core/` - Core server APIs
    - `server/core/event/` - Event system
    - `server/core/commands/` - Command system
    - `server/core/entity/entities/` - Entity types
    - `server/core/universe/` - World and player management
    - `component/` - ECS framework

### Game Assets
- `libs/Server/Assets.zip` - Game asset archive (3.35 GB)
  - Block textures (Common/BlockTextures/*.png)
  - UI assets and templates
  - Sound files
  - Entity models
  - World generation data
  - Configuration files

  **Note:** Assets are packaged into the server but not directly accessible at compile time. Access assets via Hytale's asset system at runtime.

### Additional Files
- `libs/Server/HytaleServer.aot` - Ahead-of-time compiled server binary
- `libs/Server/Licenses/` - Third-party license files (Apache-2.0, MIT, BSD, etc.)

### API Reference
- `docs/HYTALE_API_REFERENCE.md` - Maintained local API reference
  - Extracted and verified against actual JAR
  - Contains corrected API patterns (old docs have errors)
  - Updated 2026-01-24
  - **Always check this first before assuming API from external docs**

### Online Documentation
- **Hytale Modding Docs:** https://hytalemodding.dev/en/docs
  - May contain outdated information
  - Always verify against `docs/HYTALE_API_REFERENCE.md`

## Key References

### Documentation
- `docs/TECHNICAL_DESIGN.md` - Full architecture details
- `docs/IMPLEMENTATION_PLAN.md` - Phased task list
- `docs/HYTALE_API_REFERENCE.md` - **Primary API reference** (verified against JAR)
  - Always check this before using online docs
  - Contains corrected patterns and removed deprecated APIs
- `docs/PHASE_0_1_AUDIT.md` - Issues found and fixed in Phases 0-1

### External References
- **v2.6.0-beta Repository:** https://github.com/MoshPitCodes/hytale-livinglands
  - Working version with complete metabolism, leveling, HUD systems
  - **Assess for reuse, do not copy paste**
  - Study logic/algorithms, rewrite with current API
  - Key systems to examine: MetabolismSystem, BuffsSystem, FoodConsumptionProcessor, LevelingService

### Server Resources
- `libs/Server/HytaleServer.jar` - Server JAR for API inspection
  - Use `javap -p libs/Server/HytaleServer.jar <ClassName>` to inspect classes
- `libs/Server/Assets.zip` - Game assets (textures, UI, sounds)
- `libs/Server/Extracted/HytaleServer/` - Extracted JAR for reference browsing
  - Key directories: `server/core/event/`, `server/core/entity/entities/`, `server/core/universe/`
