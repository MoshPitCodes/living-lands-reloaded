# Living Lands - Module Structure & Architecture

## Overview

Living Lands is organized as a modular system where each feature is self-contained. This document describes the module architecture, how modules interact, and best practices for creating new modules.

## Module Architecture

### Core Hierarchy

```
LivingLandsPlugin (JavaPlugin entry point)
    ├── CoreModule (singleton hub)
    │   ├── ServiceRegistry (type-safe service locator)
    │   ├── PlayerRegistry (player session tracking)
    │   ├── WorldRegistry (per-world contexts)
    │   ├── ConfigManager (YAML config, hot-reload)
    │   ├── MultiHudManager (composite HUD system)
    │   └── EventRegistry (global event bus)
    │
    └── Modules (feature implementations)
        ├── MetabolismModule (hunger, thirst, energy, buffs, debuffs)
        ├── ProfessionsModule (XP, leveling, abilities)
        ├── AnnouncerModule (MOTD, welcome messages)
        └── ClaimsModule (land protection - future)
```

### Per-World Architecture

```
WorldContext (per world UUID)
    ├── PersistenceService (SQLite database)
    ├── PlayerRegistry (world-specific player sessions)
    └── Module Repositories (player data storage per-module)
        ├── MetabolismRepository
        ├── ProfessionsRepository
        └── ClaimsRepository
```

## Module Implementation Pattern

### Basic Module Structure

```kotlin
class MyModule : AbstractModule(
    id = "mymodule",
    name = "My Module",
    version = "1.0.0",
    dependencies = setOf("metabolism")  // If dependent on other modules
) {
    private lateinit var config: MyModuleConfig
    private lateinit var service: MyService
    
    // ========== Lifecycle Hooks ==========
    
    override suspend fun onSetup() {
        // Load configuration
        config = CoreModule.config.load(MyModuleConfig.MODULE_ID, MyModuleConfig())
        
        // Register services
        service = MyService(logger, config)
        CoreModule.services.register<MyService>(service)
        
        // Register commands
        CoreModule.mainCommand.registerSubCommand(MyCommand())
    }
    
    override suspend fun onStart() {
        // Check if enabled
        if (!config.enabled) {
            markDisabledByConfig()
            return
        }
        
        // Register ECS systems (only when enabled)
        // Register background tasks
        // Start event listeners
    }
    
    override suspend fun onShutdown() {
        // Wait for async operations
        shutdownScopeWithTimeout(persistenceScope, "persistence", 5000)
        
        // Cleanup and save
        service.saveAllData()
        service.clearCache()
    }
    
    override fun onConfigReload() {
        // Reload configuration
        val newConfig = CoreModule.config.load(MyModuleConfig.MODULE_ID, MyModuleConfig())
        service.updateConfig(newConfig)
        LoggingManager.info(logger, "mymodule") { "Config reloaded" }
    }
    
    // ========== Module Hooks ==========
    
    override suspend fun onPlayerJoin(playerId: UUID, session: PlayerSession) {
        // Called when player joins (only if STARTED)
        // Initialize player-specific data
    }
    
    override suspend fun onPlayerDisconnect(playerId: UUID, session: PlayerSession) {
        // Called when player leaves (only if STARTED)
        // Save player data, cleanup resources
    }
}
```

## Service Registration & Access

### Registering Services (in onSetup)

```kotlin
override suspend fun onSetup() {
    // Register as singleton
    val myService = MyService(logger, config)
    CoreModule.services.register<MyService>(myService)
}
```

### Accessing Services (Safe Pattern - P0-138)

```kotlin
// ✅ CORRECT - Safe pattern (handles missing/disabled modules)
val metabolism = safeService<MetabolismService>("metabolism")
if (metabolism != null) {
    metabolism.restoreEnergy(playerId, amount)
} else {
    LoggingManager.debug(logger, "mymodule") { "Metabolism module unavailable" }
}

// ✅ CORRECT - Direct access for core services only
val config = CoreModule.services.get<ConfigManager>()  // Always available
```

### Core Services (Direct Access OK)

These services are always available:
- `ConfigManager` - Configuration management
- `WorldRegistry` - Per-world contexts
- `PlayerRegistry` - Player session tracking
- `HudManager` - HUD element management
- `EventRegistry` - Global event bus
- `GlobalPlayerDataRepository` - Global player data
- `SpeedManager` - Player speed modifications

## Configuration Pattern (P2-144)

### Standard Config Reload Implementation

```kotlin
override fun onConfigReload() {
    // Reload from file
    val newConfig = CoreModule.config.load(MyModuleConfig.MODULE_ID, MyModuleConfig())
    
    // Update services
    myService.updateConfig(newConfig)
    config = newConfig
    
    // Handle enable/disable transitions
    if (newConfig.enabled && !config.enabled) {
        markStarted()
        LoggingManager.info(logger, "mymodule") { "Re-enabled via config reload" }
    } else if (!newConfig.enabled && config.enabled) {
        markDisabledByConfig()
        LoggingManager.info(logger, "mymodule") { "Disabled via config reload" }
    }
}
```

**Key Points:**
- NO callback registration in onSetup() - CoreModule calls this automatically
- Works with hot-reload (`/ll reload`)
- Clean state transitions (STARTED ↔ DISABLED_BY_CONFIG)

## Logging Standard (P2-143)

### Logging in Modules

```kotlin
import com.livinglands.core.logging.LoggingManager

class MyService(private val logger: HytaleLogger) {
    fun doWork() {
        // TRACE - Hot path details
        LoggingManager.trace(logger, "mymodule") { "Hot path: processing $count items" }
        
        // DEBUG - Detailed diagnostics
        LoggingManager.debug(logger, "mymodule") { "State: $value, enabled=$enabled" }
        
        // CONFIG - Configuration messages
        LoggingManager.config(logger, "mymodule") { "Loaded config: version=$version" }
        
        // INFO - General lifecycle
        LoggingManager.info(logger, "mymodule") { "Player joined, initializing data" }
        
        // WARN - Recoverable issues
        LoggingManager.warn(logger, "mymodule") { "Value is negative: $value (clamping)" }
        
        // ERROR - Unrecoverable errors
        LoggingManager.error(logger, "mymodule") { "Failed to save data for $playerId" }
    }
}
```

**All modules should use LoggingManager** (not direct logger calls) for proper module-level filtering.

## Dependency Validation (P2-142)

### Declaring Dependencies

```kotlin
class MyModule : AbstractModule(
    id = "mymodule",
    dependencies = setOf("metabolism", "other-required-module")
) {
    // Dependencies are validated before onSetup()
    // Missing dependencies abort startup with clear error
}
```

### Accessing Dependencies

```kotlin
override suspend fun onSetup() {
    // Safe access to optional dependencies
    val metabolism = safeModule<MetabolismModule>("metabolism")
    if (metabolism != null && metabolism.state.isOperational()) {
        // Use metabolism module
    }
    
    // Get optional service with fallback
    val service = safeService<MetabolismService>("metabolism")
    if (service != null) {
        service.doWork()
    }
}
```

## Shutdown Patterns (P1-140)

### Standardized Shutdown with Timeout

```kotlin
override suspend fun onShutdown() {
    // Wait for pending async operations (with 5-second timeout)
    shutdownScopeWithTimeout(persistenceScope, "persistence", 5000)
    
    // Do synchronous cleanup
    myService.saveAllData()
    myService.clearCache()
    
    LoggingManager.info(logger, "mymodule") { "Shutdown complete" }
}
```

**All modules should use this pattern** for consistent shutdown behavior and data safety.

## Module States & Transitions (P0-P2 Improvements)

```
DISABLED
    ↓ setup()
SETUP
    ↓ start()
┌─────────────────────────┐
│                         │
v                         v
STARTED              DISABLED_BY_CONFIG
    ↓                     ↑
    │ config reload       │
    └─ (enabled=true) ────┘
    │
    ↓ shutdown()
STOPPED
```

**Key Improvements (P0-P2):**
- **Dependency Validation** (P2-142): Validates before setup
- **HUD Race Condition** (P1-141): Lazy initialization on demand
- **Shutdown Patterns** (P1-140): Standardized timeout handling
- **Config Reload** (P2-144): Lifecycle hook instead of callbacks
- **Safe Service Access** (P0-138): Prevents NPE from missing modules
- **Logging** (P2-143): Consistent LoggingManager usage

## Best Practices

### DO

✅ Use `LoggingManager` for all module logging
✅ Register services in `onSetup()` (always, even if disabled)
✅ Register commands in `onSetup()` (they check state internally)
✅ Override `onConfigReload()` for config changes (not callbacks)
✅ Use `safeService()` and `safeModule()` for optional dependencies
✅ Call `markDisabledByConfig()` when disabled
✅ Use `shutdownScopeWithTimeout()` for async cleanup
✅ Declare dependencies in module constructor

### DON'T

❌ Use direct `logger.atFine()` in module code (use LoggingManager)
❌ Register ECS systems when disabled
❌ Register config reload callbacks (use onConfigReload() override)
❌ Access other modules directly (use safeService/safeModule)
❌ Forget to handle disabled state
❌ Block in synchronous code (use AsyncModuleCommand)
❌ Lose track of async operations on shutdown

## Creating New Modules

### Step 1: Define Module Class

```kotlin
class MyNewModule : AbstractModule(
    id = "mynewmodule",
    name = "My New Feature",
    version = "1.0.0",
    dependencies = emptySet()  // Add dependencies as needed
) {
    // ... implementation
}
```

### Step 2: Register with CoreModule

```kotlin
// In LivingLandsPlugin.onEnable()
CoreModule.registerModule(MyNewModule())
```

### Step 3: Create Configuration

```kotlin
// src/main/resources/config/mynewmodule.yml
configVersion: 1
enabled: true
```

### Step 4: Implement Required Methods

- `onSetup()` - Load config, register services/commands
- `onStart()` - Start systems if enabled
- `onShutdown()` - Cleanup resources
- `onConfigReload()` - Handle config changes
- Optional: `onPlayerJoin()`, `onPlayerDisconnect()`

### Step 5: Add Module Hooks

```kotlin
override suspend fun onPlayerJoin(playerId: UUID, session: PlayerSession) {
    // Initialize player data
}

override suspend fun onPlayerDisconnect(playerId: UUID, session: PlayerSession) {
    // Save player data
}
```

## Module Interaction Examples

### Module Depending on Another Module

```kotlin
class MyModule : AbstractModule(
    id = "mymodule",
    dependencies = setOf("metabolism")  // Requires metabolism
) {
    override suspend fun onSetup() {
        // Config is always available
        config = CoreModule.config.load(...)
        
        // Services are registered (even though metabolism may be disabled)
        myService = MyService(...)
        CoreModule.services.register<MyService>(myService)
    }
    
    override suspend fun onStart() {
        if (!config.enabled) {
            markDisabledByConfig()
            return
        }
        
        // Access optional dependency (may be disabled)
        val metabolism = safeModule<MetabolismModule>("metabolism")
        if (metabolism != null && metabolism.state.isOperational()) {
            // Safe to use metabolism
        }
    }
}
```

### Cross-Module Communication (Safe Pattern)

```kotlin
class MyService(private val logger: HytaleLogger) {
    fun restorePlayerEnergy(playerId: UUID, amount: Double) {
        // P0-138: Safe service access pattern
        val metabolism = safeService<MetabolismService>("metabolism")
        if (metabolism != null) {
            metabolism.restoreEnergy(playerId, amount)
        } else {
            LoggingManager.debug(logger, "mymodule") {
                "Metabolism unavailable - cannot restore energy"
            }
        }
    }
}
```

## See Also

- [MODULE_LIFECYCLE.md](MODULE_LIFECYCLE.md) - Detailed lifecycle documentation
- [TECHNICAL_DESIGN.md](TECHNICAL_DESIGN.md) - Architecture overview
- [LOGGING.md](LOGGING.md) - Logging system guide
