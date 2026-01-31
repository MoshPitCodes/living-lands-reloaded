# Module Lifecycle Architecture

This document describes the Living Lands module lifecycle system, including how to properly handle module enable/disable via configuration.

## Overview

Living Lands uses a modular architecture where features are organized into self-contained modules. Each module follows a defined lifecycle:

```
DISABLED -> SETUP -> STARTED -> STOPPED
                  \-> DISABLED_BY_CONFIG
                  \-> ERROR
```

## Module States

| State | Description |
|-------|-------------|
| `DISABLED` | Module is registered but not yet initialized |
| `SETUP` | Module has completed setup (services registered, commands added) |
| `STARTED` | Module is fully operational (processing events, running systems) |
| `STOPPED` | Module has been shut down |
| `DISABLED_BY_CONFIG` | Module setup complete but disabled via config |
| `ERROR` | Module encountered an error during lifecycle |

### State Transitions

```
                    +-----------------+
                    |    DISABLED     |
                    +-----------------+
                            |
                    setup() | (onSetup)
                            v
                    +-----------------+
                    |     SETUP       |
                    +-----------------+
                            |
                    start() | (onStart)
                            |
            +---------------+---------------+
            |               |               |
            v               v               v
    +-----------+  +------------------+  +-------+
    |  STARTED  |  | DISABLED_BY_CONFIG |  | ERROR |
    +-----------+  +------------------+  +-------+
            |               |
  shutdown() |  config reload (enabled=true)
            |               |
            v               v
    +-----------+     +-----------+
    |  STOPPED  |     |  STARTED  |
    +-----------+     +-----------+
```

## Creating a Module

### Basic Structure

```kotlin
class MyModule : AbstractModule(
    id = "mymodule",
    name = "My Module",
    version = "1.0.0",
    dependencies = emptySet()  // or setOf("metabolism") for dependencies
) {
    private lateinit var myConfig: MyModuleConfig
    
    override suspend fun onSetup() {
        // 1. Load configuration (always runs)
        myConfig = CoreModule.config.load(MyModuleConfig.MODULE_ID, MyModuleConfig())
        
        // 2. Register services (always - needed by commands and other modules)
        CoreModule.services.register<MyService>(myService)
        
        // 3. Register commands (always - they check module state internally)
        CoreModule.mainCommand.registerSubCommand(MyCommand())
    }
    
    override suspend fun onStart() {
        // Check if module is enabled
        if (!myConfig.enabled) {
            markDisabledByConfig()  // Important! Sets state correctly
            return
        }
        
        // Register ECS systems (only when enabled)
        registerSystem(MyTickSystem(...))
        
        // Start background tasks
        startBackgroundTasks()
    }
    
    override suspend fun onShutdown() {
        // Cleanup resources
        stopBackgroundTasks()
        saveAllData()
    }
}
```

### Key Points

1. **Commands are always registered** in `onSetup()` - they will check module state internally
2. **Services are always registered** - they may return empty data when module is disabled
3. **ECS systems registered conditionally** in `onStart()` based on config
4. **Always call `markDisabledByConfig()`** when disabled - don't just return early

## Creating Module-Aware Commands

### Synchronous Commands

Use `ModuleCommand` for simple synchronous commands:

```kotlin
class MyCommand : ModuleCommand(
    name = "mycommand",
    description = "Does something",
    moduleId = "mymodule"  // Module to check
) {
    override fun executeIfModuleEnabled(ctx: CommandContext) {
        // Only called if module is in STARTED state
        // Module state check is automatic
        MessageFormatter.commandSuccess(ctx, "Command executed!")
    }
}
```

### Asynchronous Commands

Use `AsyncModuleCommand` for commands that need database/network operations:

```kotlin
class MyAsyncCommand : AsyncModuleCommand(
    name = "myasync",
    description = "Does async work",
    moduleId = "mymodule",
    operatorOnly = true
) {
    override fun validateInputs(ctx: CommandContext): ValidationResult {
        // Parse arguments synchronously (fast, no I/O)
        val args = ctx.inputString?.split(" ") ?: return ValidationResult.error("Usage: ...")
        return ValidationResult.success(ParsedArgs(args))
    }
    
    override suspend fun executeAsyncIfModuleEnabled(ctx: CommandContext, validatedData: Any?) {
        // Only called if module is in STARTED state
        // Runs asynchronously - can do database operations
        val data = validatedData as ParsedArgs
        val result = database.query(data)
        // ...
    }
}
```

### Command Behavior by Module State

| Module State | Command Behavior |
|-------------|-----------------|
| `STARTED` | Executes normally |
| `DISABLED_BY_CONFIG` | Shows "module disabled in configuration" error |
| `SETUP` | Shows "module is initializing" error |
| `ERROR` | Shows "module encountered an error" error |
| `STOPPED` | Shows "module has been stopped" error |
| `DISABLED` | Shows "module not loaded" error |

## Hot-Reload Support

Modules can be enabled/disabled at runtime via configuration hot-reload:

### Implementing Hot-Reload

```kotlin
override suspend fun onSetup() {
    // Register config reload callback
    CoreModule.config.onReload(MyModuleConfig.MODULE_ID) {
        handleConfigReload()
    }
}

private fun handleConfigReload() {
    val oldEnabled = myConfig.enabled
    
    // Reload configuration
    myConfig = CoreModule.config.load(MyModuleConfig.MODULE_ID, MyModuleConfig())
    
    when {
        !oldEnabled && myConfig.enabled -> {
            // Module re-enabled
            logger.atFine().log("Module enabled via config reload")
            markStarted()
            // Re-initialize runtime state as needed
        }
        
        oldEnabled && !myConfig.enabled -> {
            // Module disabled
            logger.atFine().log("Module disabled via config reload")
            markDisabledByConfig()
            // Clean up runtime state as needed
        }
    }
}
```

### Limitations

- **ECS Systems**: Cannot be dynamically registered/unregistered. A server restart is recommended for full enable/disable.
- **Player State**: Players online during state change may need to rejoin for full effect.
- **Dependencies**: If Module A depends on Module B, disabling Module B may affect Module A.

## Player Lifecycle Hooks

Only modules in `STARTED` state receive player lifecycle notifications:

```kotlin
override suspend fun onPlayerJoin(playerId: UUID, session: PlayerSession) {
    // Called when a player joins
    // Only called if module.state.isOperational() == true
    initializePlayerData(playerId)
}

override suspend fun onPlayerDisconnect(playerId: UUID, session: PlayerSession) {
    // Called when a player disconnects
    // Only called if module.state.isOperational() == true
    savePlayerData(playerId)
}
```

**Important**: Modules in `DISABLED_BY_CONFIG` state do NOT receive player events. This is intentional - a disabled module should not process any player data.

## Best Practices

### DO

- Register commands in `onSetup()` - they use `ModuleCommand`/`AsyncModuleCommand` to check state
- Register services in `onSetup()` - other modules may query them
- Call `markDisabledByConfig()` when config.enabled is false
- Support hot-reload by implementing config reload callbacks
- Provide clear error messages when commands fail due to disabled state

### DON'T

- Register ECS systems when module is disabled
- Process player events when module is disabled
- Forget to call `markDisabledByConfig()` - this leaves state incorrect
- Block in commands - use `AsyncModuleCommand` for I/O operations

## Example: Full Module Implementation

See `ProfessionsModule.kt` for a complete example implementing:

- Configuration loading with hot-reload
- Service registration
- Module-aware commands
- ECS system registration (conditional)
- Player lifecycle handling
- State transition (STARTED <-> DISABLED_BY_CONFIG)

## Migration Guide

If you have existing commands that extend `CommandBase` directly:

### Before

```kotlin
class OldCommand : CommandBase("old", "Description", false) {
    override fun executeSync(ctx: CommandContext) {
        // Command logic
    }
}
```

### After

```kotlin
class NewCommand : ModuleCommand(
    name = "new",
    description = "Description",
    moduleId = "mymodule"
) {
    override fun executeIfModuleEnabled(ctx: CommandContext) {
        // Command logic (unchanged, just moved here)
    }
}
```

For async commands, use `AsyncModuleCommand` instead.
