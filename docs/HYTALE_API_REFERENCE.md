# Hytale Server API Reference

**Version:** Extracted from HytaleServer.jar (2024)
**Package:** `com.hypixel.hytale`
**Date:** 2026-01-24 (Updated)
**Important:** This document has been updated based on actual JAR analysis. Older docs contain incorrect patterns.

**Latest Update (2026-01-24):** Added comprehensive UI & HUD system documentation with critical implementation findings. Hytale uses a custom UI DSL format, not XML. HUD system requires specific setup patterns discovered through extensive testing.

---

## Table of Contents

1. [Plugin Development](#plugin-development)
2. [Event System](#event-system)
3. [Entity Component System (ECS)](#entity-component-system-ecs)
4. [Player API](#player-api)
5. [Command System](#command-system)
6. [World & Blocks](#world--blocks)
7. [Configuration](#configuration)
8. [UI & HUD](#ui--hud)
9. [NPC System](#npc-system)
10. [Package Structure](#package-structure)

---

## Plugin Development

### Core Plugin Classes

| Class | Package | Purpose |
|-------|---------|---------|
| `JavaPlugin` | `com.hypixel.hytale.server.core.plugin` | Base class for Java plugins |
| `JavaPluginInit` | `com.hypixel.hytale.server.core.plugin` | Plugin initialization context |
| `PluginBase` | `com.hypixel.hytale.server.core.plugin` | Abstract base plugin class |
| `PluginManager` | `com.hypixel.hytale.server.core.plugin` | Manages plugin lifecycle |
| `PluginManifest` | `com.hypixel.hytale.common.plugin` | Plugin metadata definition |
| `PluginIdentifier` | `com.hypixel.hytale.common.plugin` | Plugin ID and version |
| `PluginState` | `com.hypixel.hytale.server.core.plugin` | Plugin state enum |

### Plugin Lifecycle

```java
public class MyPlugin extends JavaPlugin {

    public MyPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        // Initialize resources, register systems
        // Called after all plugins are loaded
    }

    @Override
    protected void start() {
        // Begin operations
        // Called after all plugins are set up
    }

    @Override
    protected void shutdown() {
        // Cleanup resources
        // Called before server shutdown
    }
}
```

### Lifecycle Methods

| Method | When Called | Purpose |
|--------|-------------|---------|
| `setup()` | After plugin loading, before start | Register listeners, systems, commands |
| `start()` | After all plugins setup | Begin module operation |
| `shutdown()` | Before server shutdown | Save data, cleanup resources |

### Available Registries

All available from `JavaPlugin`:

| Registry | Type | Purpose |
|----------|------|---------|
| `getEventRegistry()` | `EventRegistry` | Register event listeners |
| `getCommandRegistry()` | `CommandRegistry` | Register commands |
| `getEntityStoreRegistry()` | `ComponentRegistryProxy<EntityStore>` | Register ECS systems for entities |
| `getChunkStoreRegistry()` | `ComponentRegistryProxy<ChunkStore>` | Register ECS systems for chunks |
| `getLogger()` | `HytaleLogger` | Plugin logging |
| `getFile()` | `Path` | Plugin file location |

---

## Event System

### Core Event Classes

| Class | Package | Purpose |
|-------|---------|---------|
| `IEvent` | `com.hypixel.hytale.event` | Base event interface |
| `IAsyncEvent` | `com.hypixel.hytale.event` | Async event interface |
| `ICancellable` | `com.hypixel.hytale.event` | Cancellable event interface |
| `EventRegistry` | `com.hypixel.hytale.event` | Event registration hub |
| `EventBus` | `com.hypixel.hytale.event` | Event dispatching |
| `EventPriority` | `com.hypixel.hytale.event` | Handler priority enum |
| `IEventDispatcher` | `com.hypixel.hytale.event` | Event dispatch interface |

### Event Listener Registration

```java
public class MyPlugin extends JavaPlugin {

    @Override
    protected void setup() {
        getEventRegistry().register(PlayerConnectEvent.class, event -> {
            try {
                getLogger().info("Player connected: " + event.getPlayerRef().getUuid());
            } catch (Exception e) {
                getLogger().warning("Error in PlayerConnectEvent: " + e.getMessage());
            }
        });
    }
}
```

### Player Events

`com.hypixel.hytale.server.core.event.events.player`

| Event | Description | When Fired |
|-------|-------------|------------|
| `PlayerConnectEvent` | Player begins connection | Early connection phase |
| `PlayerSetupConnectEvent` | Player setup phase | After connection, before ready |
| `PlayerReadyEvent` | Player fully loaded | When ECS ready for player |
| `PlayerDisconnectEvent` | Player disconnecting | During disconnect |
| `PlayerSetupDisconnectEvent` | Player disconnect setup | Before disconnect completes |
| `PlayerChatEvent` | Player sends chat message | On chat message |
| `PlayerInteractEvent` | Player interaction | Right-click, left-click |
| `PlayerMouseButtonEvent` | Mouse button input | Mouse click/hold |
| `PlayerMouseMotionEvent` | Mouse movement | Mouse look |
| `PlayerCraftEvent` | Crafting event | When player crafts |
| `AddPlayerToWorldEvent` | Player added to world | When player joins world |
| `DrainPlayerFromWorldEvent` | Player removed from world | When player leaves world |

### Entity Events

`com.hypixel.hytale.server.core.event.events.entity`

| Event | Description | When Fired |
|-------|-------------|------------|
| `EntityEvent` | Base entity event | Parent class |
| `EntityRemoveEvent` | Entity removed from world | Entity despawned |
| `LivingEntityUseBlockEvent` | Living entity uses block | Interaction |
| `LivingEntityInventoryChangeEvent` | Inventory change | Item added/removed |

### ECS Block/Game Events

`com.hypixel.hytale.server.core.event.events.ecs`

| Event | Description | When Fired |
|-------|-------------|------------|
| `BreakBlockEvent` | Block broken | Mining, destroying |
| `PlaceBlockEvent` | Block placed | Placing blocks |
| `DamageBlockEvent` | Block damaged | Hitting blocks |
| `UseBlockEvent` / `.Pre` / `.Post` | Block used | Right-click interaction |
| `CraftRecipeEvent` / `.Pre` / `.Post` | Crafting | Recipe crafted |
| `DropItemEvent` | Item dropped | Q key or death |
| `InteractivelyPickupItemEvent` | Item picked up | Walking into items |
| `SwitchActiveSlotEvent` | Hotbar slot changed | Number keys, scroll |
| `ChangeGameModeEvent` | Game mode changed | /gamemode command |
| `DiscoverZoneEvent` | Zone discovered | Entering new area |

### Other Events

| Event | Package | Description |
|-------|---------|-------------|
| `BootEvent` | `...event.events` | Server boot |
| `ShutdownEvent` | `...event.events` | Server shutdown |
| `PrepareUniverseEvent` | `...event.events` | Universe preparing |
| `KillFeedEvent.KillerMessage` | `...damage.event` | Kill notification |
| `KillFeedEvent.DecedentMessage` | `...damage.event` | Death notification |
| `PluginSetupEvent` | `...plugin.event` | Plugin setup |
| `PluginEvent` | `...plugin.event` | Generic plugin event |

### Event Priority

```java
public enum EventPriority {
    LOWEST,     // Executed last
    LOW,        // Executed near last
    NORMAL,     // Default
    HIGH,       // Executed near first
    HIGHEST,    // Executed first
    MONITOR     // Receives event but doesn't modify it
}
```

---

## Entity Component System (ECS)

### Core ECS Classes

`com.hypixel.hytale.component`

| Class | Purpose |
|-------|---------|
| `Component` | Base component class |
| `ComponentType` | Type identifier for components |
| `ComponentRegistry` | Registry for component types |
| `ComponentRegistryProxy` | Proxy for component registration |
| `Ref<S>` | Entity reference (handle to entity) |
| `Store<S>` | ECS store (contains all entities) |
| `Archetype` | Entity archetype (component combination) |
| `ArchetypeChunk` | Chunk of entities with same archetype |
| `CommandBuffer<S>` | Deferred command queue |
| `Resource` | ECS resource (global data) |
| `ResourceType` | Resource type identifier |

### System Types

`com.hypixel.hytale.component.system`

| Class | Purpose |
|-------|---------|
| `ISystem` | Base system interface |
| `System` | Base system class |
| `EntityEventSystem<S, E>` | Event-driven system (handles events) |
| `WorldEventSystem` | World-level event system |
| `RefSystem` | Reference-based system (tracks entity refs) |
| `RefChangeSystem` | Ref add/remove system |
| `QuerySystem` | Query-based system (fetches entities) |
| `MetricSystem` | Metrics collection system |
| `EcsEvent` | ECS event |
| `CancellableEcsEvent` | Cancellable ECS event |

### Ticking Systems

`com.hypixel.hytale.component.system.tick`

| Class | Purpose |
|-------|---------|
| `TickingSystem` | Per-tick system |
| `TickableSystem` | Tickable interface |
| `EntityTickingSystem` | Entity ticking |
| `ArchetypeTickingSystem` | Archetype tick looping |
| `DelayedEntitySystem` | Delayed entity operations |

### Query System

`com.hypixel.hytale.component.query`

| Class | Purpose |
|-------|---------|
| `Query<S>` | Base query class |
| `Query.any()` | Match all entities |
| `AndQuery<S>` | Logical AND of queries |
| `OrQuery<S>` | Logical OR of queries |
| `NotQuery<S>` | Logical NOT of query |
| `AnyQuery<S>` | Match any entity |
| `ExactArchetypeQuery<S>` | Exact archetype match |

### Store Types

| Class | Package | Purpose |
|-------|---------|---------|
| `EntityStore` | `com.hypixel.hytale.server.core.world.storage` | Entity ECS store |
| `ChunkStore` | `com.hypixel.hytale.server.core.world.storage` | Chunk ECS store |

### ECS System Registration

```java
public class MyPlugin extends JavaPlugin {

    @Override
    protected void setup() {
        // Register entity event system
        getEntityStoreRegistry().registerSystem(new MyBlockBreakSystem());

        // Register chunk system
        getChunkStoreRegistry().registerSystem(new MyChunkSystem());
    }
}

// Example: EntityEventSystem
public class MyBlockBreakSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    public MyBlockBreakSystem() {
        super(BreakBlockEvent.class);
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void handle(int entityIndex, ArchetypeChunk<EntityStore> chunk,
                      Store<EntityStore> store, CommandBuffer<EntityStore> cmd,
                      BreakBlockEvent event) {
        // Handle block break event
        getLogger().info("Block broken at: " + event.getBlockPos());
    }
}

// Example: Ticking System
public class MyTickingSystem extends EntityTickingSystem {

    @Override
    public Query<EntityStore> getQuery() {
        // Only match entities with specific components
        Query<EntityStore> query = Query.any();
        // Add component filters...
        return query;
    }

    @Override
    public void tick(Ref<EntityStore> ref, Store<EntityStore> store, World world) {
        // Called every tick for matching entities
    }
}
```

### ECS Thread Safety

**Important:** Hytale's ECS is NOT thread-safe. All ECS access must occur on the WorldThread.

```java
// WRONG - May crash
var component = store.getComponent(ref, MyComponent.getComponentType());

// CORRECT - Use world.execute()
world.execute(() -> {
    var component = store.getComponent(ref, MyComponent.getComponentType());
    // Safe to modify here
});
```

---

## Player API

### Entity Hierarchy

**Inheritance Chain:**
```
Entity (base class)
├── Fields:
│   ├── protected UUID legacyUuid
│   ├── protected World world
│   └── protected Ref<EntityStore> reference
├── Public Methods:
│   ├── public UUID getUuid()
│   └── public World getWorld()
│
└── LivingEntity
    └── Player
        └── Inherits all from Entity
```

### Player Classes

| Class | Package | Purpose |
|-------|---------|---------|
| `PlayerRef` | `com.hypixel.hytale.server.core.universe` | Player reference (handle) |
| `Player` | `com.hypixel.hytale.server.core.entity.entities` | Player entity component |
| `PlayerInput` | `...modules.entity.player` | Player input state |
| `PlayerSettings` | `...modules.entity.player` | Player settings |

### Player Entity Properties (inherited from Entity)

| Property | Type | Access | Notes |
|----------|------|--------|-------|
| `world` | `World` | public | Player's current world instance |
| `uuid` | `UUID?` | deprecated | Kotlin property, use `getUuid()` instead |
| `getUuid()` | `UUID` | method | **Recommended** - player UUID (not deprecated) |
| `getWorld()` | `World` | method | Player's current world |

**Important:** 
- `Player.world` property works (accesses `getWorld()` method)
- `Player.uuid` property is deprecated - use `player.getUuid()` method instead
- These are inherited from the `Entity` base class through `LivingEntity`

### Player Ref Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `getUuid()` | `UUID` | Player UUID (deprecated but functional) |
| `getUsername()` | `String` | Player username |
| `getPlayerConnection()` | `PacketHandler` | Connection handler |
| `getChunkTracker()` | `ChunkTracker` | Chunk visibility tracker |
| `getLanguage()` | `String` | Player language |
| `getTransform()` | `Transform` | Player transform (position/rotation) |
| `getHeadRotation()` | `Vector3f` | Head rotation |
| `sendMessage(Message)` | `void` | Send message to player |
| `isValid()` | `boolean` | Check if ref is valid |
| `getReference()` | `Ref<EntityStore>` | Get entity reference |
| `getHolder()` | `Holder<EntityStore>` | Get entity holder |
| `getComponent(ComponentType<T>)` | `T` | Get player component |

**Important:** 
- `PlayerRef.getUuid()` method is deprecated
- `PlayerRef` has a private `worldUuid` field with NO public getter
- To access player's world, use `Player` entity component via events or ECS system

### Event Differences

**PlayerReadyEvent** (extends `PlayerEvent`):
- Has `playerRef` field
- Has `player` field (use this for UUID and world)

**PlayerDisconnectEvent** (extends `PlayerRefEvent`):
- Has `playerRef` field only
- Does NOT have `player` field

### Player Component Managers

| Class | Purpose |
|-------|---------|
| `HotbarManager` | Hotbar slot management |
| `MovementManager` | Movement control |
| `CameraManager` | Camera control |
| `WindowManager` | UI window management |
| `PageManager` | UI page management |
| `HudManager` | HUD management |
| `ChunkTracker` | Chunk visibility tracking |
| `HiddenPlayersManager` | Player hiding |

### Player Data & Persistence

| Class | Purpose |
|-------|---------|
| `PlayerWorldData` | Per-world player data |
| `PlayerConfigData` | Player configuration |
| `PlayerDeathPositionData` | Last death position |
| `PlayerRespawnPointData` | Respawn location |

### Player Ref Usage

```java
// PlayerReadyEvent - has both playerRef and player
eventRegistry.register(PlayerReadyEvent.class, event -> {
    try {
        PlayerRef playerRef = event.getPlayerRef();
        Player player = event.getPlayer();
        
        // Correct: Get UUID from entity component
        UUID playerId = player.getUuid();
        
        // Correct: Get world from entity component
        World world = player.getWorld();
        
        if (world != null) {
            getLogger().info("Player ready: " + playerId + " in world " + world.getName());
        }
    } catch (Exception e) {
        getLogger().warning("Error in PlayerReadyEvent: " + e.getMessage());
    }
});

// PlayerDisconnectEvent - only has playerRef
eventRegistry.register(PlayerDisconnectEvent.class, event -> {
    try {
        PlayerRef playerRef = event.getPlayerRef();
        
        // Only access playerRef - PlayerDisconnectEvent doesn't have .player
        UUID playerId = playerRef.getUuid();
        
        getLogger().info("Player disconnected: " + playerId);
    } catch (Exception e) {
        getLogger().warning("Error in PlayerDisconnectEvent: " + e.getMessage());
    }
});
```

**Important Notes:**
- `PlayerReadyEvent` extends `PlayerEvent` - has both `playerRef` and `player` fields
- `PlayerDisconnectEvent` extends `PlayerRefEvent` - only has `playerRef` field
- Use `player.getUuid()` method instead of deprecated `player.uuid` property
- `Player.world` property works (inherited from `Entity.getWorld()`)
- `PlayerRef` is a handle, `Player` is the ECS entity component

---

## Command System

### Command Classes

`com.hypixel.hytale.server.core.command.system`

| Class | Package | Purpose |
|----------|------|---------|
| `CommandBase` | `...command.system.basecommands` | Base synchronous command class |
| `AbstractCommand` | `...command.system` | Abstract command base |
| `CommandRegistry` | `...command.system` | Command registration |
| `CommandContext` | `...command.system` | Command execution context |
| `CommandSender` | `...command.system` | Command sender interface |
| `CommandOwner` | `...command.system` | Command owner interface |
| `CommandCollection` | `...command.system.basecommands` | Command collection for grouping |

**Note:** `AbstractPlayerCommand`, `AbstractWorldCommand`, and player/world/context-specific base classes mentioned in older references are **not present** in the current server JAR. Use `CommandBase` for all command implementations.

### Argument Types

`...arguments.types`

| Class | Purpose |
|-------|---------|
| `Argument<?, T>` | Base argument type |
| `SingleArgumentType<T>` | Single value argument type |
| `StringArgumentType` | String argument type |

**Note:** `ArgTypes` factory class mentioned in older references is **not present** in the current server JAR. Argument types must be defined by extending `Argument` or `SingleArgumentType`.

### Argument System

`...arguments.system`

| Class | Purpose |
|-------|---------|
| `RequiredArg<T>` | Required argument |
| `OptionalArg<T>` | Optional argument |
| `DefaultArg<T>` | Argument with default value |
| `FlagArg` | Boolean flag (no value) |

### Command Example

```java
public class MyCommand extends CommandBase {

    public MyCommand() {
        super("mycommand", "Description", false); // name, description, requiresOp
    }

    @Override
    protected void executeSync(CommandContext ctx) {
        // Get command sender
        CommandSender sender = ctx.sender();
        
        // Check if sender is a player
        if (ctx.isPlayer()) {
            Ref<EntityStore> playerRef = ctx.senderAsPlayerRef();
            // Access player data using ECS...
        }
        
        // Send message back to sender
        Message message = Message.raw("Hello from mycommand!");
        ctx.sendMessage(message);
    }
}
```

### Command Registration

```java
public class MyPlugin extends JavaPlugin {

    @Override
    protected void setup() {
        getCommandRegistry().registerCommand(new MyCommand());
    }
}
```

---

## World & Blocks

### World Classes

`com.hypixel.hytale.server.core.universe.world`

| Class | Purpose |
|-------|---------|
| `World` | World instance |
| `WorldConfig` | World configuration |
| `WorldConfigProvider` | World config provider |
| `WorldMap` | World map |
| `WorldMapTracker` | Map tracking |
| `WorldNotificationHandler` | World notifications |

### Block & Chunk Classes

| Class | Purpose |
|-------|---------|
| `BlockChunk` | Block chunk (16x16x16) |
| `BlockSection` | Block section |
| `BlockState` | Block state (type + properties) |
| `BlockStateRegistry` | Block state registry |
| `BlockAccessor` | Block read/write access |
| `ChunkAccessor` | Chunk read/write access |
| `SetBlockSettings` | Block placement options |
| `PlaceBlockSettings` | Block placement options |

### World Generation

| Class | Purpose |
|-------|---------|
| `IWorldGen` | World gen interface |
| `IWorldGenProvider` | World gen provider |
| `GeneratedChunk` | Generated chunk data |
| `GeneratedBlockChunk` | Block chunk generation data |
| `GeneratedEntityChunk` | Entity chunk generation data |
| `FlatWorldGenProvider` | Flat world generator |
| `VoidWorldGenProvider` | Void world generator |

---

## Configuration

### Server Configuration

| Class | Package | Purpose |
|-------|---------|---------|
| `HytaleServerConfig` | `com.hypixel.hytale.server.core` | Main server config |
| `HytaleServerConfig.Module` | | Module config |
| `HytaleServerConfig.ModConfig` | | Mod config |
| `WorldConfig` | `...world` | World configuration |
| `GameplayConfig` | `...asset.type.gameplay` | Gameplay settings |

### Gameplay Configurations

| Config Class | Purpose |
|--------------|---------|
| `PlayerConfig` | Player settings |
| `CombatConfig` | Combat settings |
| `DeathConfig` | Death settings |
| `RespawnConfig` | Respawn settings |
| `CraftingConfig` | Crafting settings |
| `SpawnConfig` | Spawn settings |
| `WorldMapConfig` | World map settings |

---

## UI & HUD

### UI Classes

| Class | Package | Purpose |
|-------|---------|---------|
| `Window` | `...entities.player.windows` | UI window |
| `ContainerWindow` | | Container window |
| `ItemContainerWindow` | | Item container |
| `BlockWindow` | | Block interaction window |
| `CustomUIPage` | `...pages` | Custom UI page |
| `CustomUIHud` | `...player.hud` | Custom HUD overlay |
| `HudManager` | `...hud` | HUD management |
| `UICommandBuilder` | `...ui.builder` | UI command builder for HUDs |

### Custom HUD System

**Important:** Hytale uses a custom UI DSL format (NOT XML/HTML).

#### Creating a Custom HUD

```kotlin
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef

class MyHud(playerRef: PlayerRef) : CustomUIHud(playerRef) {
    
    private var firstBuild = true
    
    override fun build(builder: UICommandBuilder) {
        if (firstBuild) {
            // Append UI file on first build
            builder.append("Hud/MyHud.ui")
            firstBuild = false
        }
        
        // Update UI element values
        builder.set("elementId.text", "New Text")
        builder.set("progressBar.text", "[=====-----] 50.0")
    }
}
```

#### UI DSL File Format

UI files use Hytale's custom DSL syntax with `Group`, `Label`, `Anchor`, and `Style` keywords.

**File Location:** `src/main/resources/Hud/MyHud.ui`

**Example UI File:**

```
// Simple HUD with text labels
Group #MyHudContainer {
  LayoutMode: Top;
  Anchor: (Top: 16, Left: 16, Width: 200, Height: 80);

  Group #ContentPanel {
    LayoutMode: Top;
    Anchor: (Width: 165, Height: 66);
    Background: #1a1a1a(0.70);
    Padding: (Full: 8);

    // Text label row
    Group #Row1 {
      LayoutMode: Left;
      Anchor: (Height: 16);

      Label #label_name {
        Text: "Status";
        Anchor: (Width: 50, Height: 16);
        Style: (FontSize: 11, HorizontalAlignment: Start, VerticalAlignment: Center, TextColor: #e67e22, RenderBold: true);
      }

      Label #label_value {
        Text: "[==========] 100.0";
        Anchor: (Width: 115, Height: 16);
        Style: (FontSize: 11, HorizontalAlignment: Start, VerticalAlignment: Center, TextColor: #e67e22);
      }
    }
  }
}
```

#### UI DSL Syntax Reference

| Element | Purpose | Example |
|---------|---------|---------|
| `Group` | Container element | `Group #MyGroup { ... }` |
| `Label` | Text display | `Label #MyLabel { Text: "Hello"; }` |
| `LayoutMode` | Layout direction | `LayoutMode: Top;` (vertical) or `Left` (horizontal) |
| `Anchor` | Position/size | `Anchor: (Top: 16, Left: 16, Width: 200, Height: 80);` |
| `Background` | Background color | `Background: #1a1a1a(0.70);` (color with alpha) |
| `Padding` | Inner spacing | `Padding: (Full: 8);` or `(Left: 8, Right: 8)` |
| `Style` | Text styling | `Style: (FontSize: 11, TextColor: #ffffff, RenderBold: true);` |
| `Visible` | Visibility | `Visible: false;` |

#### Style Properties

| Property | Values | Purpose |
|----------|--------|---------|
| `FontSize` | Integer | Text size (e.g., `11`) |
| `TextColor` | Hex color | Text color (e.g., `#ffffff`) |
| `HorizontalAlignment` | `Start`, `Center`, `End` | Horizontal text alignment |
| `VerticalAlignment` | `Start`, `Center`, `End` | Vertical text alignment |
| `RenderBold` | `true`, `false` | Bold text |
| `RenderUppercase` | `true`, `false` | Uppercase text |
| `LetterSpacing` | Integer | Letter spacing |

#### UICommandBuilder Methods

```kotlin
builder.append("Hud/MyHud.ui")           // Append UI file
builder.set("elementId.text", "value")   // Set text content
builder.set("elementId.visible", false)  // Set visibility
```

**Important Notes:**
1. UI files must use the Hytale DSL format (Group/Label syntax), not XML
2. Place UI files in `src/main/resources/Common/UI/Custom/Hud/` (not just `Hud/`)
3. Reference file path as `"Hud/MyHud.ui"` (relative to `Common/UI/Custom/`)
4. Element IDs use `#` prefix in UI file AND in code (e.g., `builder.set("#HungerBar.Text", ...)`)
5. Element IDs **cannot contain underscores** - use camelCase only (e.g., `#HungerBar` not `#hunger_bar`)
6. Use text-based progress bars (e.g., `[||||||||||]`) - no native progress bar widget
7. Colors support alpha channel: `#RRGGBB(alpha)` where alpha is 0.0-1.0

#### Critical HUD Implementation Requirements (Discovered 2026-01-24)

**1. Manifest Configuration**
```json
{
  "IncludesAssetPack": true
}
```
- Must set `"IncludesAssetPack": true` in `src/main/resources/manifest.json`
- Without this, UI files won't be loaded by the client

**2. UI File Structure**
```
// CORRECT - Root Group has NO ID
Group {
  LayoutMode: Top;
  Anchor: (Top: 20, Left: 20, Width: 200, Height: 100);
  
  Group #MetabolismBars {
    // Nested groups can have IDs
  }
}

// WRONG - Don't add ID to root Group
Group #MyHud {  // ❌ This will cause issues
  ...
}
```

**3. Element Naming Rules**
- **NO underscores**: `#HungerBar` ✅  `#hunger_bar` ❌
- **Use camelCase**: `#ThirstBar` ✅  `#thirst-bar` ❌
- **Keep IDs simple**: Avoid special characters except `#`

**4. HUD Registration Thread Safety**
```kotlin
// WRONG - Calling on event thread
eventRegistry.register(PlayerReadyEvent::class.java) { event ->
    val hud = MyHud(event.playerRef)
    player.hudManager.setCustomHud(playerRef, hud)  // ❌ Wrong thread
    hud.show()
}

// CORRECT - Execute on world thread
eventRegistry.register(PlayerReadyEvent::class.java) { event ->
    val player = event.player
    val world = player.world ?: return@register
    
    world.execute {  // ✅ Run on world thread
        val hud = MyHud(event.playerRef)
        player.hudManager.setCustomHud(event.playerRef, hud)
        hud.show()
    }
}
```
- **HUD setup MUST run on world thread**
- Use `world.execute { }` to ensure thread safety
- Based on v2.6.0 pattern: `session.executeOnWorld()`

**5. Build Method Pattern**
```kotlin
override fun build(builder: UICommandBuilder) {
    // Call append() EVERY time build() is called
    builder.append("Hud/MyHud.ui")
    
    // Set values in the SAME build() call
    builder.set("#HungerBar.Text", "[||||||||||] 100")
    builder.set("#ThirstBar.Text", "[||||||||||] 100")
}
```
- **Don't use `firstBuild` flag** - append every time
- **Set values immediately** after append in same method
- Matches v2.6.0 `LivingLandsHud.build()` pattern

**6. Update Pattern**
```kotlin
// Periodic updates (from ticker)
fun updateHud() {
    val builder = UICommandBuilder()
    builder.set("#HungerBar.Text", buildTextBar(hunger))
    builder.set("#ThirstBar.Text", buildTextBar(thirst))
    update(false, builder)  // Push to client
}
```
- Use `update(false, builder)` to push changes
- Call from ticker or scheduled task
- Don't call immediately after `show()` - UI needs time to load

**7. Working Example**
```kotlin
// UI File: src/main/resources/Common/UI/Custom/Hud/MetabolismHud.ui
Group {
  LayoutMode: Top;
  Anchor: (Top: 20, Left: 20, Width: 220, Height: 80);

  Group #MetabolismBars {
    Background: #1a1a1a(0.8);
    Padding: (Full: 10);
    LayoutMode: Top;

    Group #HungerRow {
      LayoutMode: Left;

      Label #HungerLabel {
        Text: "Hunger";
        Style: (FontSize: 14, TextColor: #e67e22, RenderBold: true);
        Anchor: (Width: 60);
      }

      Label #HungerBar {
        Text: "[||||||||||] 100";
        Style: (FontSize: 14, TextColor: #e67e22);
      }
    }
  }
}

// Kotlin Code
class MetabolismHud(playerRef: PlayerRef) : CustomUIHud(playerRef) {
    override fun build(builder: UICommandBuilder) {
        builder.append("Hud/MetabolismHud.ui")
        builder.set("#HungerBar.Text", "[||||||||||] 100")
    }
}

// Registration (in PlayerReadyEvent handler)
world.execute {
    val hud = MetabolismHud(playerRef)
    player.hudManager.setCustomHud(playerRef, hud)
    hud.show()
}
```

**Common Errors and Solutions:**

| Error | Cause | Solution |
|-------|-------|----------|
| "Could not find document Hud/MyHud.ui" | manifest.json missing `IncludesAssetPack: true` | Add to manifest |
| "Failed to parse file" at line with `_` | Element ID has underscore | Use camelCase |
| "Selected element not found" | Wrong selector format or missing `#` | Use `#ElementId.Property` |
| HUD shows but never updates | Not calling `update()` from ticker | Implement periodic update |
| Client crash on join | HUD setup not on world thread | Wrap in `world.execute { }` |

### Messages

| Class | Purpose |
|-------|---------|
| `Message` | Chat/UI message builder |
| `Message.raw()` | Raw text message |
| `.color(TextColor)` | Set message color |
| `.insert(Message)` | Insert nested message |

### Message Example

```java
Message message = Message.raw("Hello ");
message.insert(Message.text("Player").color(TextColor.BLUE));
message.insert(Message.raw("!"));

ctx.sendMessage(message);
```

---

## NPC System

### NPC Classes

`com.hypixel.hytale.server.npc`

| Class | Purpose |
|-------|---------|
| `NPCEntity` | NPC entity type |
| `EntityRegistry` | Entity registration |
| `EntityRegistration` | Entity type registration |

### Entity Utilities

| Class | Purpose |
|-------|---------|
| `Entity` | Base entity |
| `LivingEntity` | Living entity |
| `EntityUtils` | Entity utilities |
| `EntitySnapshot` | Entity state snapshot |

---

## Package Structure

```
com.hypixel.hytale
├── assetstore/               # Asset management
├── builtin/                  # Built-in modules
├── codec/                    # Serialization
├── common/                   # Shared utilities
├── component/                # ECS core
│   ├── query/                # Query system
│   └── system/               # System base classes
│       └── tick/             # Ticking systems
├── event/                    # Event system
├── logger/                   # Logging
├── plugin/                   # Plugin system
├── protocol/                 # Network protocol
└── server/
    ├── core/
    │   ├── asset/            # Asset types
    │   ├── auth/             # Authentication
    │   ├── command/          # Commands
    │   ├── entity/           # Entity system
    │   │   ├── entities/     # Entity types
    │   │   └── modules/      # Entity modules
    │   ├── event/            # Server events
    │   │   └── events/       # Event implementations
    │   ├── modules/          # Server modules
    │   ├── plugin/           # Plugin management
    │   ├── permissions/      # Permissions
    │   ├── prefab/           # Prefabs
    │   ├── universe/         # Universe/World
    │   │   ├── world/        # World classes
    │   │   └── storage/      # ECS stores
    │   └── ui/               # UI system
    ├── npc/                  # NPC system
    ├── spawning/             # Spawning
    └── worldgen/             # World generation
```

---

## Complete Plugin Example

**Corrected for Current HytaleServer.jar (2024):**

```java
package com.example.myplugin;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.archetype.ArchetypeChunk;
import com.hypixel.hytale.component.command.CommandBuffer;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.universe.World;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.Message;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;

public class MyPlugin extends JavaPlugin {

    public MyPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        super.setup();

        // Register event listeners
        getEventRegistry().register(PlayerReadyEvent.class, event -> {
            Player player = event.getPlayer();
            
            // Recommended: Use getUuid() method
            UUID playerId = player.getUuid();
            
            // Correct: Access world via getWorld() method
            World world = player.getWorld();
            
            if (world != null) {
                getLogger().info("Player ready: " + playerId + 
                    " in world " + world.getName());
            }
        });

        // Register ECS systems
        getEntityStoreRegistry().registerSystem(new BlockBreakSystem());

        // Register commands
        getCommandRegistry().registerCommand(new GreetCommand());
    }

    @Override
    protected void start() {
        getLogger().info("Plugin started!");
    }

    @Override
    protected void shutdown() {
        getLogger().info("Plugin shutting down...");
    }
}

class BlockBreakSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    public BlockBreakSystem() {
        super(BreakBlockEvent.class);
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void handle(int entityIndex, ArchetypeChunk<EntityStore> chunk,
                      Store<EntityStore> store, CommandBuffer<EntityStore> cmd,
                      BreakBlockEvent event) {
        getLogger().info("Block broken at: " + event.getBlockPos());
    }
}

class GreetCommand extends CommandBase {

    public GreetCommand() {
        super("greet", "Greet a player", false);
    }

    @Override
    protected void executeSync(CommandContext ctx) {
        CommandSender sender = ctx.sender();
        Message message = Message.raw("Hello from greet command!");
        ctx.sendMessage(message);
    }
}
```

**Key API Notes:**
- Use `CommandBase` for all commands - player/world-specific base classes don't exist
- Access player world via `Player.getWorld()` method or `player.world` property
- Use `player.getUuid()` method instead of deprecated `player.uuid` property
- `PlayerReadyEvent` has both `playerRef` and `player` fields
- `PlayerDisconnectEvent` only has `playerRef` field

---

## API Corrections (2026-01-24)

This section documents corrections from the initial API review:

| Original Claim | Actual API | Status |
|---------------|------------|--------|
| "Player doesn't have uuid" | Entity base class has `getUuid()` method | **INCORRECT** - Fixed |
| "Player doesn't have world" | Entity base class has `getWorld()` method and `world` property accessible via Kotlin | **INCORRECT** - Fixed |
| "Use PlayerRef.worldUuid" | PlayerRef only has private `worldUuid` field | **CORRECT** |
| "UUID is only on PlayerRef" | Entity/Player inherits UUID from Entity base | **INCORRECT** - Fixed |

**Entity Inheritance:**
- `Entity` base class has `uuid` (protected field) and `getUuid()` (public method)
- `Entity` base class has `world` (protected field) and `getWorld()` (public method)
- `Player` → `LivingEntity` → `Entity` inherits both properties/methods

**Deprecation:**
- Kotlin property `player.uuid` is deprecated - use `player.getUuid()` method instead
- `PlayerRef.getUuid()` is deprecated but still works
- `Player.getWorld()` method is NOT deprecated

---

**End of Hytale API Reference**