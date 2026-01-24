# Living Lands - Standard Module Structure

**Version:** 2.7.0
**Date:** 2026-01-24
**Purpose:** Reference guide for creating and organizing Living Lands modules

---

## Table of Contents

1. [Overview](#overview)
2. [Canonical Module Structure](#canonical-module-structure)
3. [File Naming Conventions](#file-naming-conventions)
4. [Required vs Optional Components](#required-vs-optional-components)
5. [Module Skeleton Example](#module-skeleton-example)
6. [Integration Patterns](#integration-patterns)
7. [Configuration Best Practices](#configuration-best-practices)
8. [Module Development Checklist](#module-development-checklist)

---

## Overview

This document defines the canonical folder structure for Living Lands modules. All modules must follow this structure to ensure consistency and maintainability.

### Principles

1. **Only `{ModuleName}Module.java` at module root** - All other files in subdirectories
2. **Singular folder names** - `listener/`, `command/`, `system/` (not plural)
3. **Logical grouping** - Related classes share subdirectories
4. **Consistent naming** - Follow naming patterns across modules

---

## Canonical Module Structure

```
modules/{module-name}/
├── {ModuleName}Module.java              [REQUIRED] Entry point
├── config/
│   └── {ModuleName}ModuleConfig.java    [REQUIRED] Module configuration
├── system/
│   ├── {ModuleName}System.java         [OPTIONAL] Core business logic
│   └── {FeatureName}System.java        [OPTIONAL] Secondary systems
├── data/
│   ├── Player{ModuleName}Data.java     [OPTIONAL] Per-player data class
│   └── {ModuleName}DataPersistence.java[OPTIONAL] Data persistence
├── listener/                           [OPTIONAL] Event listeners
│   ├── {ModuleName}PlayerListener.java
│   └── {ModuleName}GameListener.java
├── command/                            [OPTIONAL] Commands
│   └── {Subcommand}.java
├── ui/
│   ├── {ModuleName}UIProvider.java     [OPTIONAL] Settings UI
│   └── {ModuleName}HudElement.java     [OPTIONAL] HUD rendering
├── {feature}/                          [OPTIONAL] Domain-specific packages
│   ├── {Feature}Manager.java
│   ├── {Feature}Type.java
│   └── {Feature}Handler.java
└── stats/                              [OPTIONAL] Stat definitions
    ├── {Stat}Stat.java
    └── ...
```

### Real Examples

#### Metabolism Module

```
modules/metabolism/
├── MetabolismModule.java
├── config/
│   └── MetabolismModuleConfig.java
├── system/
│   ├── MetabolismSystem.java
│   ├── DebuffsSystem.java
│   └── ActivityDetector.java
├── data/
│   └── PlayerMetabolismData.java
├── listener/
│   ├── MetabolismPlayerListener.java
│   ├── FoodConsumptionProcessor.java
│   ├── CombatDetectionListener.java
│   └── BedInteractionListener.java
├── ui/
│   ├── MetabolismUIProvider.java
│   └── MetabolismHudElement.java
├── buff/
│   ├── BuffsSystem.java
│   ├── BuffType.java
│   └── NativeBuffDetector.java
├── debuff/
│   ├── DebuffType.java
│   ├── DebuffEffectsSystem.java
│   └── NativeDebuffDetector.java
├── consumables/
│   ├── ConsumableType.java
│   ├── ConsumableRegistry.java
│   ├── ConsumableItem.java
│   └── FoodEffectDetector.java
├── poison/
│   ├── PoisonItem.java
│   ├── PoisonEffectType.java
│   ├── PoisonEffectsSystem.java
│   ├── PoisonRegistry.java
│   └── NativePoisonDetector.java
└── stats/
    ├── HungerStat.java
    ├── ThirstStat.java
    └── EnergyStat.java
```

#### Leveling Module

```
modules/leveling/
├── LevelingModule.java
├── config/
│   ├── LevelingModuleConfig.java
│   ├── ProfessionConfig.java
│   ├── AbilityConfig.java
│   └── XpSourceConfig.java
├── system/
│   └── LevelingSystem.java
├── data/
│   ├── PlayerLevelingData.java
│   └── LevelingDataPersistence.java
├── listener/
│   ├── LevelingPlayerListener.java
│   ├── MiningXpSystem.java
│   ├── LoggingXpSystem.java
│   ├── GatheringXpSystem.java
│   ├── BuildingXpSystem.java
│   ├── CombatXpSystem.java
│   └── BlockPlaceTrackingSystem.java
├── command/
│   └── SetLevelCommand.java
├── ui/
│   ├── LevelingUIProvider.java
│   ├── SkillGuiElement.java
│   ├── SkillsPanelElement.java
│   └── LevelUpNotification.java
├── profession/
│   ├── ProfessionType.java
│   ├── ProfessionData.java
│   └── XpCalculator.java
├── ability/
│   ├── AbilitySystem.java
│   ├── AbilityType.java
│   ├── PermanentBuffManager.java
│   ├── TimedBuffManager.java
│   └── handlers/
│       ├── MiningAbilityHandler.java
│       ├── LoggingAbilityHandler.java
│       ├── GatheringAbilityHandler.java
│       ├── BuildingAbilityHandler.java
│       └── CombatAbilityHandler.java
└── util/
    ├── PlacedBlockPersistence.java
    └── PlayerPlacedBlockChecker.java
```

#### Claims Module

```
modules/claims/
├── ClaimsModule.java
├── config/
│   └── ClaimsModuleConfig.java
├── system/
│   └── ClaimSystem.java
├── data/
│   ├── ClaimDataPersistence.java
│   ├── PlotClaim.java
│   ├── ClaimPermission.java
│   └── ClaimFlags.java
├── listener/
│   ├── ClaimsPlayerListener.java
│   ├── ClaimProtectionListener.java
│   └── ClaimNPCBurnSystem.java
├── command/
│   ├── ClaimSubcommand.java
│   ├── UnclaimSubcommand.java
│   └── TrustSubcommand.java
├── ui/
│   ├── ClaimsUIProvider.java
│   └── ClaimSelectorPage.java
└── map/
    ├── ClaimsWorldMap.java
    ├── ClaimsImageBuilder.java
    ├── ClaimsWorldMapProvider.java
    ├── ClaimMarkerProvider.java
    └── ClaimsMapUpdateSystem.java
```

---

## File Naming Conventions

### Classes

| Pattern | Example | Usage |
|---------|---------|-------|
| `{ModuleName}Module` | `MetabolismModule` | Module entry point (at root only) |
| `{ModuleName}System` | `MetabolismSystem` | Core business logic |
| `{Feature}System` | `DebuffsSystem`, `BuffsSystem` | Feature-specific system |
| `{Feature}Manager` | `SpeedManager`, `PoisonRegistry` | Manages feature state |
| `{Feature}Type` | `BuffType`, `DebuffType` | Enum/feature type definition |
| `{Feature}Handler` | `MiningAbilityHandler` | Handles specific functionality |
| `{Feature}Detector` | `FoodEffectDetector` | Detects game events |
| `{Feature}Registry` | `PoisonRegistry` | Registry of items/effects |
| `{Feature}Provider` | `ClaimMarkerProvider` | Provides to other systems |
| `Player{ModuleName}Data` | `PlayerMetabolismData` | Per-player data class |
| `{ModuleName}DataPersistence` | `LevelingDataPersistence` | Data I/O class |
| `{ModuleName}UIProvider` | `MetabolismUIProvider` | Settings UI integration |
| `{ModuleName}HudElement` | `MetabolismHudElement` | HUD rendering |
| `{ModuleName}PlayerListener` | `MetabolismPlayerListener` | Player events |
| `{ModuleName}...Listener` | `ClaimProtectionListener` | Other event listeners |
| `{Subcommand}` | `ClaimSubcommand`, `SetLevelCommand` | Command implementation |

### Folders

| Folder Name | Naming Convention | Purpose |
|-------------|-------------------|---------|
| `config/` | Lowercase | Configuration classes |
| `system/` | Lowercase, singular | Business logic/ECS systems |
| `data/` | Lowercase | Data classes and persistence |
| `listener/` | Lowercase, singular | Event listeners (NOT listeners/) |
| `command/` | Lowercase, singular | Commands (NOT commands/) |
| `ui/` | Lowercase | UI/HUD classes |
| `stats/` | Lowercase | Stat definitions |
| `{feature}/` | Lowercase, feature name | Domain-specific classes |

---

## Required vs Optional Components

### Always Required

| Component | File Required | Purpose |
|-----------|---------------|---------|
| Module Entry Point | `{ModuleName}Module.java` | Registers module, handles lifecycle |
| Configuration | `{ModuleName}ModuleConfig.java` | JSON-serializable config class |

### Commonly Required

| Component | When Needed | Purpose |
|-----------|-------------|---------|
| Main System | Always | Core module functionality |
| Player Data | Per-player state | Track player-specific data |
| Data Persistence | Has player data | Save/load to disk |
| Player Listener | Needs player events | Player connect/disconnect, events |

### Optional Components

| Component | When Needed | Purpose |
|-----------|-------------|---------|
| Additional Systems | Multiple features | Separate business logic |
| Other Listeners | Specific events | ECS systems, block events, etc. |
| Commands | Admin/player commands | `/ll subcommand` commands |
| UI Provider | Settings tab | Module options in `/ll settings` |
| HUD Element | In-game display | Show stats to player |
| Feature Packages | Complex features | Group related classes |

---

## Module Skeleton Example

```java
package com.livinglands.modules.{module-name};

import com.hypixel.hytale.logger.HytaleLogger;
import com.livinglands.api.AbstractModule;
import com.livinglands.api.ModuleContext;

import java.util.Set;

public class {ModuleName}Module extends AbstractModule {

    public static final String ID = "{module}";
    public static final String NAME = "{ModuleName}";
    public static final String VERSION = "1.0.0-beta";

    // Systems
    private {ModuleName}System {module}System;

    // Dependencies
    private ExampleDependencyModule exampleModule;

    public {ModuleName}Module() {
        super(ID, NAME, VERSION, Set.of(CoreModule.ID));
    }

    @Override
    protected void onSetup(ModuleContext context) {
        this.context = context;
        this.logger = context.logger();
        this.configDirectory = context.pluginDirectory().resolve(ID);

        // Load config
        config = loadConfig(
            "{module}/config.json",
            {ModuleName}ModuleConfig.class,
            {ModuleName}ModuleConfig::defaultConfig
        );

        // Get dependencies
        exampleModule = getDependency(CoreModule.ID, CoreModule.class);

        // Initialize systems
        {module}System = new {ModuleName}System(
            logger,
            config,
            context.playerRegistry()
        );

        // Register event listeners
        registerEventListeners();

        // Register ECS systems
        registerEcsSystems();

        // Register commands
        registerCommands();

        // Register HUD elements
        registerHudElements();

        // Register UI providers
        registerUiProviders();
    }

    @Override
    protected void onStart() {
        logger.info("Starting %s module %s", NAME, VERSION);
    }

    @Override
    protected void onShutdown() {
        if ({module}System != null) {
            {module}System.shutdown();
        }
        logger.info("Shutdown %s module", NAME);
    }

    private void registerEventListeners() {
        context.eventRegistry().registerListener(
            PlayerReadyEvent.class,
            {module}System::onPlayerReady
        );
        context.eventRegistry().registerListener(
            PlayerDisconnectEvent.class,
            {module}System::onPlayerDisconnect
        );
    }

    private void registerEcsSystems() {
        context.entityStoreRegistry().registerSystem(
            new {ModuleName}System()
        );
    }

    private void registerCommands() {
        context.commandRegistry().registerCommand(
            new {ModuleName}Subcommand({module}System)
        );
    }

    private void registerHudElements() {
        var hudModule = getDependency(HudModule.ID, HudModule.class);
        if (hudModule != null) {
            hudModule.registerElement(
                new {ModuleName}HudElement({module}System)
            );
        }
    }

    private void registerUiProviders() {
        var uiSystem = getDependency(CoreModule.ID, CoreModule.class)
            .getUiSystem();
        if (uiSystem != null) {
            uiSystem.registerProvider(
                new {ModuleName}UIProvider({module}System)
            );
        }
    }

    // Public getters for other modules
    public {ModuleName}System getSystem() {
        return {module}System;
    }
}
```

---

## Integration Patterns

### Accessing Other Modules

#### Required Dependencies

```java
@Override
public {ModuleName}Module() {
    super(ID, NAME, VERSION, Set.of(CoreModule.ID, HudModule.ID));
}

@Override
protected void onSetup(ModuleContext context) {
    // Get required module (throws if not found)
    CoreModule coreModule = requireDependency(
        CoreModule.ID,
        CoreModule.class
    );

    // Use services from core module
    SpeedManager speedManager = coreModule.getSpeedManager();
    PlayerDeathBroadcaster deathBroadcaster = coreModule.getDeathBroadcaster();
}
```

#### Optional Dependencies

```java
@Override
protected void onSetup(ModuleContext context) {
    // Get optional module (returns Optional<T>)
    var economyModuleOpt = context.getModule(EconomyModule.ID, EconomyModule.class);

    if (economyModuleOpt.isPresent()) {
        var economyModule = economyModuleOpt.get();
        // Integrate with economy
    } else {
        logger.info("Economy module not available, using fallback");
    }
}
```

### Providing Services to Other Modules

```java
public class {ModuleName}Module extends AbstractModule {

    // Service instances
    private final {ModuleName}Manager manager = new {ModuleName}Manager();

    // Public getter for other modules
    public {ModuleName}Manager getManager() {
        return manager;
    }
}

// Other module usage:
public class ConsumerModule extends AbstractModule {

    @Override
    protected void onSetup(ModuleContext context) {
        var producerModule = getDependency(ProducerModule.ID, ProducerModule.class);
        var manager = producerModule.getManager();
        // Use service
    }
}
```

### Event Broadcasting

```java
// Broadcaster in CoreModule
public class PlayerDeathBroadcaster
    extends EntityEventSystem<EntityStore, KillFeedEvent.DecedentMessage> {

    private final List<Consumer<UUID>> listeners = new ArrayList<>();

    public void addListener(Consumer<UUID> listener) {
        listeners.add(listener);
    }

    @Override
    public void handle(...) {
        UUID playerId = event.getDecedentId();
        for (var listener : listeners) {
            listener.accept(playerId);
        }
    }
}

// Listener in other module
public class LevelingModule extends AbstractModule {

    @Override
    protected void onSetup(ModuleContext context) {
        var deathBroadcaster = getDependency(CoreModule.ID, CoreModule.class)
            .getDeathBroadcaster();

        deathBroadcaster.addListener(this::onPlayerDeath);
    }

    private void onPlayerDeath(UUID playerId) {
        // Handle death
    }
}
```

---

## Configuration Best Practices

### Config Class Structure

```java
package com.livinglands.modules.{module}.config;

public record {ModuleName}ModuleConfig(
    MainConfig main,
    FeatureConfig feature
) {
    public static {ModuleName}ModuleConfig defaultConfig() {
        return new {ModuleName}ModuleConfig(
            MainConfig.defaults(),
            FeatureConfig.defaults()
        );
    }
}

public record MainConfig(
    boolean enabled,
    double setting1,
    int setting2
) {
    public static MainConfig defaults() {
        return new MainConfig(
            true,   // enabled
            1.0,    // setting1
            100     // setting2
        );
    }
}

public record FeatureConfig(
    FeatureSubConfig subfeature
) {
    public static FeatureConfig defaults() {
        return new FeatureConfig(
            FeatureSubConfig.defaults()
        );
    }
}
```

### Config Loading

```java
// In AbstractModule (base class)
protected <T> T loadConfig(String filename,
                           Class<T> type,
                           Supplier<T> defaultSupplier) {
    Path configPath = configDirectory.resolve(filename);

    if (Files.exists(configPath)) {
        String json = Files.readString(configPath);
        T existingConfig = GSON.fromJson(json, type);
        T defaultConfig = defaultSupplier.get();

        // Migrate config
        T migratedConfig = ConfigMigrationManager.migrateConfig(
            existingJson,
            defaultConfig,
            type,
            logger,
            name + "/" + filename
        );

        saveConfig(filename, migratedConfig);
        return migratedConfig;
    } else {
        T defaultConfig = defaultSupplier.get();
        saveConfig(filename, defaultConfig);
        return defaultConfig;
    }
}
```

### Configuration Hot-Reload

```java
// Implement ReloadableConfig interface
public class {ModuleName}Module extends AbstractModule
    implements ReloadableConfig {

    @Override
    public void reloadConfig() {
        config = loadConfig(
            "{module}/config.json",
            {ModuleName}ModuleConfig.class,
            {ModuleName}ModuleConfig::defaultConfig
        );

        // Update systems with new config
        if ({module}System != null) {
            {module}System.updateConfig(config.main());
        }

        logger.info("Reloaded %s configuration", ID);
    }

    @Override
    public String getModuleName() {
        return ID;
    }
}
```

---

## Module Development Checklist

### Planning Phase

- [ ] Define module purpose and scope
- [ ] Identify required module dependencies
- [ ] Identify optional module integrations
- [ ] Design configuration structure
- [ ] Determine data persistence needs

### Implementation Phase

- [ ] Create module directory structure
- [ ] Create `{ModuleName}Module.java` entry point
- [ ] Create `{ModuleName}ModuleConfig.java` config class
- [ ] Implement main `{ModuleName}System` class
- [ ] Create `Player{ModuleName}Data` if needed
- [ ] Create `{ModuleName}DataPersistence` if needed
- [ ] Register event listeners
- [ ] Register ECS systems
- [ ] Register commands (if any)
- [ ] Create UI/HUD elements (if any)

### Integration Phase

- [ ] Test module standalone
- [ ] Test with required dependencies
- [ ] Test with optional dependencies
- [ ] Test config hot-reload (if implemented)
- [ ] Verify data persistence
- [ ] Verify cross-module communication

### Documentation Phase

- [ ] Update `/ll help` command
- [ ] Add settings UI provider (if applicable)
- [ ] Document configuration options
- [ ] Document module interactions
- [ ] Update main TECHNICAL_DESIGN.md

### Quality Checks

- [ ] Code follows naming conventions
- [ ] All imports use new package structure
- [ ] Thread safety: `ConcurrentHashMap` for shared state
- [ ] ECS access wrapped in `world.execute()`
- [ ] Proper error handling and logging
- [ ] Player cleanup in `onPlayerDisconnect()`
- [ ] System cleanup in `onShutdown()`
- [ ] Build passes: `./gradlew build -x test`

---

## Quick Reference: Module Imports

### Common Imports

```java
// Plugin API
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

// Living Lands API
import com.livinglands.api.AbstractModule;
import com.livinglands.api.ModuleContext;
import com.livinglands.api.ModuleState;
import com.livinglands.api.ReloadableConfig;

// Core Services
import com.livinglands.core.CoreModule;
import com.livinglands.core.HudModule;
import com.livinglands.core.NotificationModule;
import com.livinglands.core.PlayerRegistry;
import com.livinglands.core.events.PlayerDeathBroadcaster;
import com.livinglands.core.util.SpeedManager;

// Hytale API - Events
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.event.events.player.*;
import com.hypixel.hytale.server.core.event.events.ecs.*;

// Hytale API - ECS
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.component.archetype.ArchetypeChunk;
import com.hypixel.hytale.component.command.CommandBuffer;
import com.hypixel.hytale.component.query.Query;

// Hytale API - Commands
import com.hypixel.hytale.server.core.command.system.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;

// Hytale API - UI
import com.hypixel.hytale.server.core.event.Message;
import com.hypixel.hytale.server.core.universe.World;
import com.hypixel.hytale.server.core.universe.PlayerRef;

// Utilities
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
```

---

**End of Standard Module Structure**