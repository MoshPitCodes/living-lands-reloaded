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

## Pull Request Guidelines

When creating pull requests for Living Lands Reloaded, follow these practices:

### PR Creation Process

1. **Branch Naming**
   - Feature: `feature/short-description`
   - Bug fix: `fix/short-description`
   - Performance: `perf/optimization-name`
   - Refactor: `refactor/component-name`

2. **Commit Messages**
   - Follow conventional commits: `type: description`
   - Types: `feat`, `fix`, `refactor`, `perf`, `docs`, `test`, `chore`
   - Example: `feat: add UUID string caching for performance`

3. **PR Title Format**
   - Clear, descriptive summary of all changes
   - Example: "Performance Optimizations, Config Migration System, and Jackson YAML Migration"

4. **PR Description Template**
   ```markdown
   ## Summary
   Brief overview of what this PR accomplishes
   
   ### [Feature/Fix/Performance] Category 1
   **Key Changes:**
   - Bullet point 1
   - Bullet point 2
   
   **Impact:**
   | Metric | Before | After | Improvement |
   |--------|--------|-------|-------------|
   
   ### [Feature/Fix] Category 2
   **Features:**
   - Feature 1
   - Feature 2
   
   ### Documentation
   - Updated file 1
   - Updated file 2
   
   ### Files Changed
   **New Files:**
   - File path - Description
   
   **Modified Files:**
   - File path - Changes made
   
   ### Testing
   ✅ Build successful
   ✅ Manual testing performed
   ✅ Config migration tested
   
   ---
   
   **Closes:** #issue or Phase reference
   **Version:** 1.0.0-beta
   ```

5. **Before Creating PR**
   - ✅ Code compiles without warnings: `./gradlew build`
   - ✅ All tests pass (when applicable)
   - ✅ Documentation updated (README.md, CHANGELOG.md, TECHNICAL_DESIGN.md)
   - ✅ Self-review completed
   - ✅ Performance impact assessed (if applicable)
   - ✅ Migration path tested (for config changes)

6. **Using git-flow-manager Agent**
   The `git-flow-manager` agent can help create PRs:
   - Automatically analyzes full commit history
   - Generates comprehensive PR description
   - Includes all changes since branch diverged from main
   - Formats with proper markdown structure

   Example: Simply ask "create a PR" and the agent will handle the rest.

### PR Review Checklist

**Code Quality:**
- [ ] Follows Kotlin coding conventions
- [ ] Proper error handling with try/catch
- [ ] Thread-safe operations (synchronized, ConcurrentHashMap)
- [ ] ECS access wrapped in `world.execute { }`
- [ ] Appropriate logging levels (FINE for debug, INFO for events)

**Performance:**
- [ ] No unnecessary allocations in hot paths
- [ ] Database operations are async (Dispatchers.IO)
- [ ] Proper resource cleanup (close connections, clear caches)
- [ ] Metrics documented (if performance-related)

**Architecture:**
- [ ] Per-world data isolation maintained
- [ ] Service registry used for cross-module communication
- [ ] Config in YAML, not database
- [ ] Module lifecycle properly implemented

**Testing:**
- [ ] Manual testing performed on Hytale server
- [ ] Config hot-reload tested
- [ ] Per-world isolation verified
- [ ] Migration paths tested (if config changes)

**Documentation:**
- [ ] KDoc comments for public APIs
- [ ] README.md updated (if user-facing changes)
- [ ] CHANGELOG.md updated with version entry
- [ ] TECHNICAL_DESIGN.md updated (if architecture changes)
- [ ] IMPLEMENTATION_PLAN.md phase marked complete

## Current Phase

See `docs/IMPLEMENTATION_PLAN.md` for detailed task breakdown.

**MVP Scope:** Core infrastructure + Metabolism module with HUD, debuffs, and food consumption.

## Building

```bash
./gradlew build
# Output: build/libs/livinglands-*.jar
```

## Deploying

```bash
# Build and deploy to global mods folder
./gradlew build && ./scripts/deploy_windows.sh

# Or just deploy (if already built)
./scripts/deploy_windows.sh
```

**Deployment Location:** `/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Mods/livinglands-1.0.0-beta.jar`

**Important:** 
- Always deploy to the **global mods folder** (`UserData/Mods`)
- Never deploy to the per-server mods folder (`Test1/mods/`) to avoid duplicate plugin errors
- The deploy script automatically handles this

## Server Paths (Windows)

**Test Server Location:** `/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test1`

### Key Paths
- **Global Mods Directory:** `/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Mods` - **Deploy JARs here** (applies to all servers)
- **Server Config:** `Test1/config.json` - Controls which mods are enabled/disabled
- **Server Logs:** `Test1/logs/YYYY-MM-DD_HH-MM-SS_server.log` - Latest server log files
- **Client Logs:** `/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Logs/YYYY-MM-DD_HH-MM-SS_client.log` - Latest client log files
- **Plugin Data:** `Test1/mods/MPC_LivingLandsReloaded/` - Runtime plugin data (config and databases)
  - `config/core.yml` - Core plugin configuration
  - `config/metabolism.yml` - Metabolism module configuration
  - `data/{world-uuid}/livinglands.db` - Per-world SQLite databases

**Important:** Hytale loads mods from two locations:
1. **Global:** `/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Mods` (applies to all servers) ✅ **Use this**
2. **Per-server:** `Saves/{world}/mods/` (specific to each world) ❌ **Don't use** (causes duplicates)

### Linux Mount Paths (WSL)
When accessing from WSL/Linux development environment:
- **Global Mods:** `/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Mods/`
- **Server Root:** `/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test1/`
- **Server Logs:** `/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test1/logs/` (find newest .log file)
- **Client Logs:** `/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Logs/` (find newest .log file)
- **Plugin JAR:** `/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Mods/livinglands-1.0.0-beta.jar`
- **Plugin Config:** `/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test1/mods/MPC_LivingLandsReloaded/config/`
- **Database:** `/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test1/mods/MPC_LivingLandsReloaded/data/6bd4ef4e-4cd2-4486-9fd1-5794a1015596/livinglands.db`

### Server Configuration

**Enable/Disable Plugin:**
Edit `Test1/config.json`:
```json
{
  "Mods": {
    "MPC:LivingLandsReloaded": {
      "Enabled": true    // Set to true to enable, false to disable
    }
  }
}
```

**Important:** The server will NOT load the plugin if `Enabled: false`. Check logs for:
```
[WARN] [PluginManager] Skipping mod MPC:LivingLandsReloaded (Disabled by server config)
```

### Debugging Commands

```bash
# Find latest server log file
ls -lt /mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test1/logs/*.log | head -1

# Find latest client log file
ls -lt /mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Logs/*.log | head -1

# Watch logs in real-time (if script exists)
./scripts/watch_windows_logs.sh

# Search for plugin errors in server logs
grep -i "livinglands\|metabolism\|error\|exception" /mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test1/logs/2026-01-25_*_server.log

# Search for plugin errors in client logs
grep -i "livinglands\|metabolism\|hud\|error\|exception" /mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Logs/2026-01-25_*_client.log

# Query metabolism database
nix develop --command sqlite3 "/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test1/mods/MPC_LivingLandsReloaded/data/6bd4ef4e-4cd2-4486-9fd1-5794a1015596/livinglands.db" "SELECT * FROM metabolism_stats;"

# Check if plugin is loaded
grep "LivingLandsReloaded" /mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/Test1/logs/*.log
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
