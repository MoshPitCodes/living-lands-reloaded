# Living Lands - Agent Instructions

## Project Overview

**Living Lands Reloaded** is a Hytale server mod implementing survival mechanics (hunger, thirst, energy) with global player progression and a professions system.

- **Language:** Kotlin (Java 25 compatible)
- **Build:** Gradle with Kotlin DSL
- **Server:** HytaleServer.jar in `libs/Server/`
- **Current Version:** See `version.properties` (single source of truth)
- **Current Status:** MVP Complete - Metabolism system, buffs/debuffs, professions with functional Tier 1 & 2 abilities, unified HUD system

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
2. Verify all Hytale API calls against `docs/api-reference/README.md` (verified via `javap` from HytaleServer.jar)
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

Modules: Metabolism, Professions, Hud

**Future Modules:** See `docs/FUTURE_MODULES.md` for planned modules (Economy, Groups, Claims)
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

Commands should extend `ModuleCommand` for module-specific commands or `CommandBase` for general commands.

**External Command Tutorials (TroubleDev) - Critical Version Mismatches:**
- Some tutorials show `PlayerRef.getWorld()`/`playerRef.world`; our verified API does not expose this. Use `Universe.get().getWorld(playerRef.worldUuid)`.
- Some tutorials show usage-variant commands using a "description-only" constructor. Our JAR constructors are `AbstractAsyncCommand(name, description)`, `AbstractAsyncCommand(name, description, requiresConfirmation)`, or `AbstractAsyncCommand(name)` (no description-only overload).
- `AbstractTargetPlayerCommand` injects an `OptionalArg<PlayerRef>` (optional `--player`), so it is generally incompatible with usage variants that rely on distinct required-argument counts.
- Treat the `sourceRef` in target-player style commands as nullable at runtime (console can execute commands).

**Logger Setup in Commands:**
Commands use `HytaleLogger` from `CoreModule.logger`:

```kotlin
import com.hypixel.hytale.logger.HytaleLogger
import com.livinglands.core.CoreModule

class MyCommand : ModuleCommand(
    name = "mycommand",
    description = "Description",
    moduleId = "mymodule",
    operatorOnly = false
) {
    protected val logger: HytaleLogger = CoreModule.logger
    
    override fun executeIfModuleEnabled(ctx: CommandContext) {
        logger.atInfo().log("Command executed")
        ctx.sendMessage(Message.raw("Response"))
    }
}
```

**For Services (Constructor Injection):**
```kotlin
class MyService(
    private val logger: HytaleLogger
) {
    fun doSomething() {
        logger.atFine().log("Service operation")
    }
}
```

### Player API Access
PlayerRef world access differs by context. Prefer these verified patterns:
```kotlin
// From PlayerReadyEvent
val world = event.player.world

// Or from entity store
val player = store.getComponent(ref, Player.getComponentType())
val world = player.world

// From PlayerRef
val world = Universe.get().getWorld(playerRef.worldUuid)
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

### Logging

**Logger Types:**

1. **Services (Constructor Injection):** Use `HytaleLogger` passed as constructor parameter
2. **Commands:** Use `protected val logger: HytaleLogger = CoreModule.logger`
3. **Never use:** `FluentLogger.forEnclosingClass()` or `Logger.getLogger()` - use `HytaleLogger` only

**Logging Levels (via LoggingManager):**

Use `LoggingManager` for all logging with configurable log levels:

```kotlin
import com.hypixel.hytale.logger.HytaleLogger
import com.livinglands.core.logging.LoggingManager

class MyService(private val logger: HytaleLogger) {
    fun example() {
        // TRACE - Hot path details (very verbose)
        LoggingManager.trace(logger, "metabolism") { "Tick for player $id" }
        
        // DEBUG - Detailed diagnostics
        LoggingManager.debug(logger, "metabolism") { "Stats: hunger=$hunger" }
        
        // CONFIG - Configuration-related messages
        LoggingManager.config(logger, "metabolism") { "Loaded config: enabled=$enabled" }
        
        // INFO - General events (default level)
        LoggingManager.info(logger, "metabolism") { "Player joined" }
        
        // WARN - Potential issues
        LoggingManager.warn(logger, "metabolism") { "Invalid value detected" }
        
        // ERROR - Critical problems
        LoggingManager.error(logger, "metabolism") { "Failed to save data" }
        LoggingManager.error(logger, "metabolism", exception) { "Operation failed" }
    }
}
```

**Direct HytaleLogger (for non-module code):**
```kotlin
logger.atInfo().log("Simple message")
logger.atSevere().withCause(exception).log("Error occurred")
```

**Configuration:** See `docs/LOGGING.md` for full details on configurable log levels.

## Directory Structure

```
src/main/kotlin/com/livinglands/
├── LivingLandsPlugin.kt        # Entry point
├── core/                       # CoreModule, registries
├── api/                        # Module interface, AbstractModule
├── modules/
│   ├── metabolism/             # Hunger/thirst/energy
│   ├── professions/            # XP and professions system
│   └── claims/                 # Land protection (stub with safety guard)
└── util/                       # SpeedManager, helpers

LivingLandsReloaded/            # Runtime data folder
├── config/*.yml                # Hot-reloadable configs
├── data/global/                # Global player data (metabolism, professions)
└── data/{world-uuid}/*.db      # Per-world SQLite (future: claims)
```

## Implementation Rules

1. **Global + Per-world data** - Player progression (metabolism, professions) stored globally; world-specific data (claims) stored per-world
2. **API verification first** - Always check `docs/api-reference/` before using online docs
3. **Config in YAML** - Never store config in database
4. **Fail gracefully** - Catch exceptions, log, continue
5. **Proper logging** - Use `LoggingManager` with appropriate log levels (TRACE/DEBUG/CONFIG/INFO/WARN/ERROR)
6. **Thread-safe collections** - Use `ConcurrentHashMap` for shared state
7. **Event error handling** - Wrap all event handlers in try/catch blocks
8. **UUID access** - Use `PlayerRef.uuid` property, not deprecated `getUuid()` if possible

## Linear Project Management

**IMPORTANT:** Always use Linear MCP tools to keep the project board in sync with development progress.

### Linear Workflow Integration

1. **Before Starting Work**
   - Check Linear for assigned issues: `linear_list_issues` (filter by assignee, status)
   - Update issue status to "in_progress" when beginning work: `linear_update_issue`
   - Review issue details for context: `linear_get_issue`

2. **During Development**
   - Keep Linear issues updated as progress is made
   - Add comments with progress updates: `linear_add_comment`
   - Create new issues for discovered bugs/features: `linear_create_issue`

3. **Before Creating PRs**
   - Ensure related Linear issue exists
   - Update Linear issue with completion status
   - Reference Linear issue key in PR description (e.g., "Closes: MPC-123")

4. **Using linearapp Agent**
   Delegate complex Linear workflows to the `linearapp` agent:
   - Sprint planning and cycle management
   - Breaking down features into subtasks
   - Organizing backlog with labels and priorities
   - Generating status reports

   Example:
   ```bash
   # Let linearapp agent handle sprint planning
   /linearapp Create a sprint for metabolism system refactoring
   ```

### Linear Issue References in Git

**Branch Naming with Linear:**
- Feature: `feature/MPC-123-short-description`
- Bug fix: `fix/MPC-456-short-description`
- Use Linear issue key as branch prefix for automatic linking

**Commit Messages with Linear:**
- Reference Linear issues: `feat: add metabolism caching (MPC-123)`
- Auto-close issues: `fix: resolve hunger bar flicker (Closes MPC-456)`

**PR Description Template (Updated):**
```markdown
## Summary
Brief overview of what this PR accomplishes

**Closes:** MPC-123, MPC-456
**Related:** MPC-789 (partial implementation)
**Version:** X.Y.Z

### [Feature/Fix/Performance] Category 1
...
```

### Proactive Linear Checks

Claude Code should AUTOMATICALLY check Linear when:
- Starting a new feature or bug fix
- Creating a pull request
- Completing a development phase
- User mentions "task", "issue", "sprint", or "backlog"

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
| **linearapp** | Project planning, sprint management | Create cycles, organize issues, break down features, status reports |

### Task-Based Agents (Use via / or delegate)

| Agent | Command Pattern | Good For |
|-------|----------------|----------|
| **explore** | Codebase searches, structure analysis | Finding files by patterns, searching code |
| **general** | Multi-step autonomous tasks | Complex workflows requiring multiple operations |
| **java-kotlin-backend** | Core backend development | Kotlin-specific implementation, Gradle build config, server API integration |
| **nixos** | Environment setup, package management | NixOS/Nix Flake reproducible dev environments, Java 25 + Kotlin, Gradle 9.3.0 |

### Code Implementation

Use `java-kotlin-backend` agent PROACTIVELY before writing significant code:
1. Plan a feature (e.g., MetabolismSystem)
2. Java-Kotlin-Backend agent analyzes the planned implementation
3. Apply suggested fixes/refinements
4. Rebuild and test

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
   - Feature: `feature/MPC-123-short-description`
   - Bug fix: `fix/MPC-456-short-description`
   - Performance: `perf/optimization-name`
   - Refactor: `refactor/component-name`
   - Release: `release/vX.Y.Z`
   - Use Linear issue key as branch prefix for automatic linking

2. **Commit Messages**
   - Follow conventional commits: `type: description`
   - Types: `feat`, `fix`, `refactor`, `perf`, `docs`, `test`, `chore`, `release`
   - Example: `feat: add UUID string caching for performance (MPC-123)`
   - Reference Linear issues in commit messages

3. **PR Title Format**
   - Clear, descriptive summary of all changes
   - Example: "Performance Optimizations, Config Migration System, and Jackson YAML Migration"

4. **PR Description Template**
   ```markdown
   ## Summary
   Brief overview of what this PR accomplishes
   
   **Closes:** MPC-123, MPC-456
   **Related:** MPC-789 (partial implementation)
   **Version:** X.Y.Z
   
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
   - Build successful
   - Manual testing performed
   - Config migration tested
   ```

5. **Before Creating PR**
   - Code compiles without warnings: `./gradlew build`
   - All tests pass (when applicable)
   - Documentation updated (README.md, CHANGELOG.md, TECHNICAL_DESIGN.md)
   - Self-review completed
   - Performance impact assessed (if applicable)
   - Migration path tested (for config changes)

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

## Building and Deploying

### IMPORTANT: Always Use Scripts

**Never manually run gradle commands or copy files for deployment.** Always use the provided scripts which handle versioning, asset extraction, and proper deployment paths.

### Available Scripts

All scripts are in the `scripts/` directory. See `scripts/README.md` for full documentation.

| Script | Purpose | When to Use |
|--------|---------|-------------|
| `./scripts/deploy_windows.sh` | Build and deploy to Windows Hytale | **Primary deployment method** |
| `./scripts/deploy_debug.sh` | Deploy with debug logging enabled | Troubleshooting issues |
| `./scripts/deploy_client_assets.sh` | Deploy UI assets only | UI-only changes |
| `./scripts/watch_windows_logs.sh` | Monitor server logs | During testing |
| `./scripts/watch_logs.sh` | Generic log watching | General debugging |
| `./scripts/check_world_databases.sh` | Inspect SQLite databases | Data verification |
| `./scripts/migrate_data_folder.sh` | Migrate player data | After folder renames |

### Standard Deployment Workflow

```bash
# 1. Build and deploy (ALWAYS use this)
./scripts/deploy_windows.sh

# 2. Watch logs for errors
./scripts/watch_windows_logs.sh

# 3. If issues, deploy with debug
./scripts/deploy_debug.sh
```

### Why Use Scripts?

1. **Version Management** - Scripts read from `version.properties` (single source of truth)
2. **Asset Extraction** - Automatically extracts UI assets to proper location
3. **Path Handling** - Handles WSL/Windows path translation
4. **Cleanup** - Removes old versioned JARs to prevent conflicts
5. **Validation** - Checks build output exists before deploying
6. **Guidance** - Provides next steps and troubleshooting hints

### Script Maintenance

When updating version numbers:
1. Update `version.properties` only
2. Scripts will automatically use the new version

If scripts need path updates (new test server, different user):
1. Edit the script's path variables at the top
2. See `scripts/README.md` for path configuration

## Server Paths (Windows)

**Saves Directory:** `/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/`

**Note:** Replace `{SAVE_NAME}` with your actual save name (e.g., "MyWorld", "Survival", "Creative", etc.)

### Key Paths
- **Global Mods Directory:** `/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Mods` - **Deploy JARs here** (applies to all servers)
- **Server Config:** `Saves/{SAVE_NAME}/config.json` - Controls which mods are enabled/disabled
- **Server Logs:** `Saves/{SAVE_NAME}/logs/YYYY-MM-DD_HH-MM-SS_server.log` - Latest server log files
- **Client Logs:** `/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Logs/YYYY-MM-DD_HH-MM-SS_client.log` - Latest client log files
- **Plugin Data:** `Saves/{SAVE_NAME}/mods/MPC_LivingLandsReloaded/` - Runtime plugin data (config and databases)
  - `config/core.yml` - Core plugin configuration
  - `config/metabolism.yml` - Metabolism module configuration
  - `data/{world-uuid}/livinglands.db` - Per-world SQLite databases

**Important:** Hytale loads mods from two locations:
1. **Global:** `/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Mods` (applies to all servers) - **Use this**
2. **Per-server:** `Saves/{SAVE_NAME}/mods/` (specific to each world) - **Don't use** (causes duplicates)

### Linux Mount Paths (WSL)
When accessing from WSL/Linux development environment:
- **Global Mods:** `/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Mods/`
- **Saves Directory:** `/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/`
- **Server Root:** `/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/{SAVE_NAME}/`
- **Server Logs:** `/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/{SAVE_NAME}/logs/` (find newest .log file)
- **Client Logs:** `/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Logs/` (find newest .log file)
- **Plugin JAR:** Deployed via `./scripts/deploy_windows.sh`
- **Plugin Config:** `/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/{SAVE_NAME}/mods/MPC_LivingLandsReloaded/config/`
- **Database:** `/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/{SAVE_NAME}/mods/MPC_LivingLandsReloaded/data/{world-uuid}/livinglands.db`

### Server Configuration

**Enable/Disable Plugin:**
Edit `Saves/{SAVE_NAME}/config.json`:
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

**Note:** Replace `{SAVE_NAME}` with your actual save name.

```bash
# Find latest server log file
ls -lt /mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/{SAVE_NAME}/logs/*.log | head -1

# Find latest client log file
ls -lt /mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Logs/*.log | head -1

# Watch logs in real-time (PREFERRED METHOD)
./scripts/watch_windows_logs.sh

# Search for plugin errors in server logs (replace date pattern as needed)
grep -i "livinglands\|metabolism\|error\|exception" /mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/{SAVE_NAME}/logs/2026-01-*_server.log

# Search for plugin errors in client logs (replace date pattern as needed)
grep -i "livinglands\|metabolism\|hud\|error\|exception" /mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Logs/2026-01-*_client.log

# Query metabolism database (replace {world-uuid} with actual UUID from check_world_databases.sh)
nix develop --command sqlite3 "/mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/{SAVE_NAME}/mods/MPC_LivingLandsReloaded/data/{world-uuid}/livinglands.db" "SELECT * FROM metabolism_stats;"

# Check if plugin is loaded
grep "LivingLandsReloaded" /mnt/c/Users/moshpit/AppData/Roaming/Hytale/UserData/Saves/{SAVE_NAME}/logs/*.log
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
- `docs/api-reference/` - **Hytale API reference documentation** (extracted from HytaleServer.jar)
  - `01-server-api-reference.md` - Plugin system, commands, events, world management
  - `02-asset-system-reference.md` - Asset loading and management
  - `03-world-generation-reference.md` - World generation JSON + verified server worldgen APIs
  - `README.md` - API reference overview and navigation
  - Extracted and verified against actual JAR
  - Contains corrected API patterns (old docs have errors)
  - Updated 2026-01-31
  - **Always check this first before assuming API from external docs**

### Online Documentation
- **Hytale Modding Docs:** https://hytalemodding.dev/en/docs
  - May contain outdated information
  - Always verify against `docs/api-reference/`

## UI Patterns (CustomUIPage)

Living Lands uses patterns adapted from [hytale-basic-uis](https://github.com/trouble-dev/hytale-basic-uis) for full-screen modal pages.

### Page Types

**BasicCustomUIPage** - For static displays (no user interaction):
```kotlin
class InfoPage(playerRef: PlayerRef) : BasicCustomUIPage(playerRef) {
    override fun build(cmd: UICommandBuilder) {
        cmd.append("Pages/InfoPage.ui")
        cmd.set("#Title.Text", "Server Info")
        cmd.set("#Value.Text", "42")  // Numbers as String
    }
}
```

**InteractiveCustomUIPage<T>** - For interactive forms/buttons:
```kotlin
class SettingsPage(playerRef: PlayerRef) 
    : InteractiveCustomUIPage<SettingsPage.FormData>(
        playerRef,
        CustomPageLifetime.CanDismissOrCloseThroughInteraction,
        FormData.CODEC
    ) {
    
    data class FormData(var action: String = "", var name: String = "") {
        companion object {
            val CODEC = BuilderCodec.builder(FormData::class.java) { FormData() }
                .append(KeyedCodec("Action", Codec.STRING), ...)
                .add()
                .append(KeyedCodec("@PlayerName", Codec.STRING), ...)  // @ = UI input
                .add()
                .build()
        }
    }
    
    override fun build(ref: Ref<EntityStore>, cmd: UICommandBuilder, 
                       evt: UIEventBuilder, store: Store<EntityStore>) {
        cmd.append("Pages/SettingsPage.ui")
        evt.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#SaveButton",
            EventData()
                .append("Action", "Save")
                .append("@PlayerName", "#NameInput.Value")
        )
    }
    
    override fun handleDataEvent(ref: Ref<EntityStore>, store: Store<EntityStore>, 
                                  data: FormData) {
        if (data.action == "Save") {
            playerRef.sendMessage(Message.raw("Saved: ${data.name}"))
        }
        val player = store.getComponent(ref, Player.getComponentType())
        player.pageManager.setPage(ref, store, Page.None)  // Close page
    }
}
```

### Key Concepts

1. **@ Prefix in Codec** - Fields prefixed with `@` in `KeyedCodec` are read from UI elements
2. **EventData.append()** - Bind UI element values: `EventData().append("@Field", "#Element.Value")`
3. **Element Selectors** - Use `#ElementId.Property` format: `#Title.Text`, `#Value.Text`
4. **World Thread Required** - Always wrap in `world.execute { }` when accessing Player/Entity
5. **Close Pages** - Call `player.pageManager.setPage(ref, store, Page.None)` when done

### UI File Format

```ui
// Pages/MyPage.ui
Group {
    Anchor: (Width: 400, Height: 200);
    Background: #1a1a2e(0.95);
    LayoutMode: Top;  // Vertical stacking
    Padding: (Full: 20);

    Label #Title {
        Text: "My Page";
        Style: (FontSize: 24, TextColor: #ffffff);
    }

    $C.@TextField #NameInput {  // $C = Common.ui templates
        PlaceholderText: "Enter name...";
    }
}
```

**See:** `docs/UI_PATTERNS.md` for complete guide with examples.

## Key References

### Documentation
- `docs/TECHNICAL_DESIGN.md` - Full architecture details
- `docs/IMPLEMENTATION_PLAN.md` - Phased task list
- `docs/UI_PATTERNS.md` - **CustomUIPage patterns guide** (BasicCustomUIPage, InteractiveCustomUIPage)
- `docs/LOGGING.md` - **Logging system guide** (LoggingManager, log levels, direct logger vs LoggingManager)
- `docs/FUTURE_MODULES.md` - **Planned future modules** (Economy, Groups, Claims) with design documentation
- `docs/api-reference/` - **Hytale API reference documentation** (extracted from HytaleServer.jar)
  - `01-server-api-reference.md` - Plugin system, commands, events, world management
  - `02-asset-system-reference.md` - Asset loading and management
  - `03-world-generation-reference.md` - World generation JSON + verified server worldgen APIs
  - `README.md` - API reference overview and navigation
  - **Always check this before using online docs** - contains verified API patterns
- `docs/PHASE_0_1_AUDIT.md` - Issues found and fixed in Phases 0-1
- `scripts/README.md` - **Deployment scripts documentation**

### External References
- **v2.6.0-beta Repository:** https://github.com/MoshPitCodes/hytale-livinglands
  - Working version with complete metabolism, leveling, HUD systems
  - **Assess for reuse, do not copy paste**
  - Study logic/algorithms, rewrite with current API
  - Key systems to examine: MetabolismSystem, BuffsSystem, FoodConsumptionProcessor, LevelingService

- **hytale-basic-uis Repository:** https://github.com/trouble-dev/hytale-basic-uis
  - Reference implementation for CustomUIPage patterns
  - Tutorial progression: static -> interactive -> dynamic
  - **Base classes adapted** to `src/main/kotlin/com/livinglands/core/ui/`

### Server Resources
- `libs/Server/HytaleServer.jar` - Server JAR for API inspection
  - Use `javap -p libs/Server/HytaleServer.jar <ClassName>` to inspect classes
- `libs/Server/Assets.zip` - Game assets (textures, UI, sounds)
- `libs/Server/Extracted/HytaleServer/` - Extracted JAR for reference browsing
  - Key directories: `server/core/event/`, `server/core/entity/entities/`, `server/core/universe/`
