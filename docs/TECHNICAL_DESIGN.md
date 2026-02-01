# Living Lands - Technical Design Document

This document provides a deep technical dive into how the Living Lands mod works internally. For user-facing documentation, see the [README](../README.md).

**Version:** 1.4.0  
**Language:** Kotlin (Java 25 compatible)  
**Last Updated:** 2026-02-01

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [CoreModule & Service Registry](#coremodule--service-registry)
3. [World Management](#world-management)
4. [MultiHUD System](#multihud-system)
5. [Persistence Layer](#persistence-layer)
6. [Configuration System](#configuration-system)
   - [ConfigManager](#configmanager)
   - [Configuration Data Classes](#configuration-data-classes)
   - [Reload Command](#reload-command)
   - [Configuration Migration](#configuration-migration)
7. [Module System](#module-system)
8. [Thread Safety & ECS Access](#thread-safety--ecs-access)
9. [Metabolism Core](#metabolism-core)
10. [Buff & Debuff Systems](#buff--debuff-systems)
11. [Speed Modification](#speed-modification)
12. [Food & Potion Detection](#food--potion-detection)
13. [Poison System](#poison-system)
14. [Native Effect Integration](#native-effect-integration)
15. [Announcer Module](#announcer-module)
16. [Professions Module](#professions-module)
17. [Modded Consumables Support](#modded-consumables-support)
18. [Appendix: Key Classes Reference](#appendix-key-classes-reference)
19. [Version History](#version-history)

---

## Architecture Overview

Living Lands is built on a modular plugin architecture with CoreModule as the central hub:

```
┌─────────────────────────────────────────────────────────────────────┐
│                      LivingLandsPlugin                              │
│  Entry point (JavaPlugin), owns CoreModule singleton                │
├─────────────────────────────────────────────────────────────────────┤
│                         CoreModule                                  │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │ ServiceRegistry   │ ConfigManager  │ ModuleLifecycle         │   │
│  │ WorldRegistry     │ CommandManager │ EventBus                │   │
│  │ MultiHudManager   │ PlayerRegistry │                         │   │
│  └─────────────────────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────────────┤
│                    WorldContext (per World UUID)                    │
│  ┌───────────────────┬───────────────────┬────────────────────┐    │
│  │ PersistenceService│ PlayerDataRepo    │ Module Repositories │    │
│  │ (SQLite DB)       │                   │                     │    │
│  └───────────────────┴───────────────────┴────────────────────┘    │
├─────────────────────────────────────────────────────────────────────┤
│                           Modules                                   │
│  ┌────────────┬────────────┬────────────┬────────────┬─────────┐   │
│  │ Metabolism │ Leveling   │ Claims     │ Hud        │ Economy │   │
│  │ Module     │ Module     │ Module     │ Module     │ (future)│   │
│  └────────────┴────────────┴────────────┴────────────┴─────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

### Key Design Principles

1. **Per-World Player Data**: All player progression is isolated per world UUID
2. **Service Locator Pattern**: CoreModule provides service registry for cross-module communication
3. **Thread Safety**: ConcurrentHashMap for shared state, coroutines for async operations
4. **ECS Thread Compliance**: All ECS access via `world.execute { }`
5. **Hysteresis**: Different enter/exit thresholds to prevent state flickering
6. **Graceful Degradation**: Systems fail silently to avoid server crashes
7. **Config/Data Separation**: YAML configs (hot-reloadable) separate from SQLite player data

### Directory Structure

```
LivingLandsReloaded/
├── config/                          # YAML configuration (hot-reloadable)
│   ├── core.yml                     # Core module settings
│   ├── metabolism.yml               # Metabolism module settings
│   ├── leveling.yml                 # Leveling module settings
│   └── claims.yml                   # Claims module settings
├── data/                            # SQLite databases
│   ├── global/
│   │   └── livinglands.db           # Global player data (metabolism stats)
│   ├── {world-uuid-1}/
│   │   └── livinglands.db           # World 1 module data (claims, etc.)
│   ├── {world-uuid-2}/
│   │   └── livinglands.db           # World 2 module data (claims, etc.)
│   └── ...
└── logs/                            # Debug logs (optional)
```

---

## CoreModule & Service Registry

CoreModule is the central hub that manages all other modules and provides shared services.

### CoreModule Singleton

```kotlin
/**
 * Central module that manages all Living Lands functionality.
 * Provides service registry, world management, and module lifecycle.
 */
object CoreModule {
    
    // Service registry for cross-module communication
    lateinit var services: ServiceRegistry
        private set
    
    // World management - tracks worlds by UUID
    lateinit var worlds: WorldRegistry
        private set
    
    // Multi-HUD support (MHUD pattern)
    lateinit var hudManager: MultiHudManager
        private set
    
    // Player tracking across all worlds
    lateinit var players: PlayerRegistry
        private set
    
    // Configuration management (file-based, hot-reloadable)
    lateinit var config: ConfigManager
        private set
    
    // Module lifecycle management
    private val modules = ConcurrentHashMap<String, Module>()
    private val moduleOrder = mutableListOf<String>()
    
    internal fun initialize(plugin: LivingLandsPlugin) {
        services = ServiceRegistry()
        worlds = WorldRegistry(plugin)
        hudManager = MultiHudManager()
        players = PlayerRegistry()
        config = ConfigManager(plugin.dataPath.resolve("config"))
        
        // Register core services
        services.register<WorldRegistry>(worlds)
        services.register<PlayerRegistry>(players)
        services.register<MultiHudManager>(hudManager)
        services.register<ConfigManager>(config)
    }
    
    fun registerModule(module: Module) {
        modules[module.id] = module
    }
    
    fun <T : Module> getModule(id: String): T? {
        @Suppress("UNCHECKED_CAST")
        return modules[id] as? T
    }
    
    suspend fun setupModules(context: ModuleContext) {
        val sorted = resolveDependencyOrder()
        sorted.forEach { moduleId ->
            modules[moduleId]?.setup(context)
        }
        moduleOrder.clear()
        moduleOrder.addAll(sorted)
    }
    
    suspend fun startModules() {
        moduleOrder.forEach { moduleId ->
            modules[moduleId]?.start()
        }
    }
    
    suspend fun shutdownModules() {
        moduleOrder.reversed().forEach { moduleId ->
            modules[moduleId]?.shutdown()
        }
    }
    
    private fun resolveDependencyOrder(): List<String> {
        // Kahn's algorithm for topological sort
        val inDegree = mutableMapOf<String, Int>()
        val graph = mutableMapOf<String, MutableList<String>>()
        
        modules.keys.forEach { id ->
            inDegree[id] = 0
            graph[id] = mutableListOf()
        }
        
        modules.forEach { (id, module) ->
            module.dependencies.forEach { dep ->
                if (modules.containsKey(dep)) {
                    graph[dep]?.add(id)
                    inDegree[id] = inDegree.getOrDefault(id, 0) + 1
                }
            }
        }
        
        val queue = ArrayDeque<String>()
        inDegree.filter { it.value == 0 }.keys.forEach { queue.add(it) }
        
        val result = mutableListOf<String>()
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            result.add(current)
            graph[current]?.forEach { neighbor ->
                inDegree[neighbor] = inDegree.getOrDefault(neighbor, 1) - 1
                if (inDegree[neighbor] == 0) {
                    queue.add(neighbor)
                }
            }
        }
        
        return result
    }
}
```

### Service Registry

```kotlin
/**
 * Type-safe service locator for cross-module communication.
 * Services are registered by interface type and retrieved by type.
 */
class ServiceRegistry {
    
    private val services = ConcurrentHashMap<KClass<*>, Any>()
    
    /**
     * Register a service instance.
     */
    inline fun <reified T : Any> register(service: T) {
        services[T::class] = service
    }
    
    /**
     * Get a service by type. Returns null if not registered.
     */
    inline fun <reified T : Any> get(): T? {
        @Suppress("UNCHECKED_CAST")
        return services[T::class] as? T
    }
    
    /**
     * Get a service by type. Throws if not registered.
     */
    inline fun <reified T : Any> require(): T {
        return get<T>() ?: throw IllegalStateException(
            "Service ${T::class.simpleName} not registered"
        )
    }
    
    /**
     * Check if a service is registered.
     */
    inline fun <reified T : Any> has(): Boolean {
        return services.containsKey(T::class)
    }
    
    /**
     * Unregister a service.
     */
    inline fun <reified T : Any> unregister() {
        services.remove(T::class)
    }
}
```

### Module Context

```kotlin
/**
 * Context provided to modules during setup phase.
 * Contains references to plugin resources and registries.
 */
data class ModuleContext(
    val plugin: LivingLandsPlugin,
    val logger: HytaleLogger,
    val pluginDirectory: Path,
    val eventRegistry: EventRegistry,
    val commandRegistry: CommandRegistry,
    val entityStoreRegistry: ComponentRegistryProxy<EntityStore>,
    val chunkStoreRegistry: ComponentRegistryProxy<ChunkStore>
)
```

---

## World Management

Living Lands tracks worlds and provides per-world context for data isolation.

### WorldRegistry

```kotlin
/**
 * Manages world lifecycle and provides WorldContext instances.
 * Each world has isolated player data and persistence.
 */
class WorldRegistry(private val plugin: LivingLandsPlugin) {
    
    private val worlds = ConcurrentHashMap<UUID, WorldContext>()
    private val worldsByName = ConcurrentHashMap<String, UUID>()
    
    /**
     * Called when a world is added to the universe.
     */
    fun onWorldAdded(event: AddWorldEvent) {
        val world = event.world
        val worldId = world.uuid
        
        val dataPath = plugin.dataPath
            .resolve("data")
            .resolve(worldId.toString())
        
        Files.createDirectories(dataPath)
        
        val context = WorldContext(
            worldId = worldId,
            worldName = world.name,
            persistence = PersistenceService(dataPath)
        )
        
        worlds[worldId] = context
        worldsByName[world.name] = worldId
        
        plugin.logger.fine("World registered: ${world.name} ($worldId)")
    }
    
    /**
     * Called when a world is removed from the universe.
     */
    fun onWorldRemoved(event: RemoveWorldEvent) {
        val worldId = event.world.uuid
        val context = worlds.remove(worldId)
        worldsByName.remove(event.world.name)
        
        context?.let {
            runBlocking {
                it.persistence.close()
            }
        }
        
        plugin.logger.fine("World unregistered: ${event.world.name} ($worldId)")
    }
    
    /**
     * Get WorldContext by world UUID.
     */
    fun getContext(worldId: UUID): WorldContext? = worlds[worldId]
    
    /**
     * Get WorldContext by World instance.
     */
    fun getContext(world: World): WorldContext? = worlds[world.uuid]
    
    /**
     * Get WorldContext by world name.
     */
    fun getContextByName(name: String): WorldContext? {
        val worldId = worldsByName[name] ?: return null
        return worlds[worldId]
    }
    
    /**
     * Get all active world contexts.
     */
    fun getAllContexts(): Collection<WorldContext> = worlds.values
    
    /**
     * Check if a world is registered.
     */
    fun hasWorld(worldId: UUID): Boolean = worlds.containsKey(worldId)
}
```

### WorldContext

```kotlin
/**
 * Per-world context containing persistence and repositories.
 * Each world has completely isolated player data.
 */
class WorldContext(
    val worldId: UUID,
    val worldName: String,
    val persistence: PersistenceService
) {
    // Core repositories
    val playerData: PlayerDataRepository by lazy {
        PlayerDataRepository(persistence)
    }
    
    // Module-specific repositories (lazy initialized)
    private val repositories = ConcurrentHashMap<KClass<*>, Any>()
    
    /**
     * Get or create a module-specific repository.
     */
    inline fun <reified T : Any> getRepository(factory: (PersistenceService) -> T): T {
        @Suppress("UNCHECKED_CAST")
        return repositories.getOrPut(T::class) {
            factory(persistence)
        } as T
    }
    
    /**
     * Called when a player joins this world.
     */
    suspend fun onPlayerJoin(playerId: UUID) {
        playerData.ensurePlayer(playerId)
    }
    
    /**
     * Called when a player leaves this world.
     */
    suspend fun onPlayerLeave(playerId: UUID) {
        playerData.updateLastSeen(playerId)
    }
}
```

### World Events Integration

```kotlin
/**
 * Register world event listeners in plugin setup.
 */
fun LivingLandsPlugin.registerWorldEvents() {
    // World lifecycle
    eventRegistry.registerListener(AddWorldEvent::class.java) { event ->
        CoreModule.worlds.onWorldAdded(event)
    }
    
    eventRegistry.registerListener(RemoveWorldEvent::class.java) { event ->
        CoreModule.worlds.onWorldRemoved(event)
    }
    
    // Player world transitions
    eventRegistry.registerListener(AddPlayerToWorldEvent::class.java) { event ->
        val worldContext = CoreModule.worlds.getContext(event.world)
        worldContext?.let {
            runBlocking {
                it.onPlayerJoin(event.playerRef.uuid)
            }
        }
    }
    
    eventRegistry.registerListener(DrainPlayerFromWorldEvent::class.java) { event ->
        val worldContext = CoreModule.worlds.getContext(event.world)
        worldContext?.let {
            runBlocking {
                it.onPlayerLeave(event.playerRef.uuid)
            }
        }
    }
}
```

---

## MultiHUD System

Living Lands bundles the MHUD pattern to support multiple HUD elements from different modules simultaneously.

### The Problem

Hytale's `HudManager` only supports a single `CustomUIHud` per player at a time. When one module sets a custom HUD, it overwrites any existing HUD from other modules.

### The Solution

Based on [MHUD by Buuz135](https://github.com/Buuz135/MHUD), we use a composite pattern to combine multiple HUD elements into a single `CustomUIHud` that delegates to all registered elements.

### MultiHudManager

```kotlin
/**
 * Manages multiple HUD elements per player using composite pattern.
 * Allows modules and external mods to register HUD elements by namespace.
 */
class MultiHudManager {
    
    // Track which players have composite HUDs
    private val playerHuds = ConcurrentHashMap<UUID, CompositeHud>()
    
    // Reflection cache for calling protected build() method
    private val buildMethod: Method by lazy {
        CustomUIHud::class.java.getDeclaredMethod(
            "build", 
            UICommandBuilder::class.java
        ).apply {
            isAccessible = true
        }
    }
    
    /**
     * Register a HUD element for a player.
     * 
     * @param player The player entity
     * @param playerRef The player reference
     * @param namespace Unique identifier (e.g., "livinglands:metabolism")
     * @param hud The CustomUIHud implementation
     */
    fun setHud(
        player: Player, 
        playerRef: PlayerRef, 
        namespace: String, 
        hud: CustomUIHud
    ) {
        val playerId = playerRef.uuid
        val hudManager = player.hudManager
        val currentHud = hudManager.customHud
        
        when (currentHud) {
            is CompositeHud -> {
                // Add to existing composite
                currentHud.huds[namespace] = hud
                hudManager.setCustomHud(playerRef, currentHud)
                currentHud.show()
            }
            else -> {
                // Create new composite
                val huds = mutableMapOf<String, CustomUIHud>()
                huds[namespace] = hud
                
                // Preserve existing non-composite HUD
                if (currentHud != null) {
                    huds["_external"] = currentHud
                }
                
                val composite = CompositeHud(playerRef, huds, buildMethod)
                playerHuds[playerId] = composite
                hudManager.setCustomHud(playerRef, composite)
            }
        }
    }
    
    /**
     * Remove a HUD element by namespace.
     */
    fun removeHud(player: Player, playerRef: PlayerRef, namespace: String) {
        val hudManager = player.hudManager
        val currentHud = hudManager.customHud
        
        if (currentHud is CompositeHud) {
            currentHud.huds.remove(namespace)
            
            if (currentHud.huds.isEmpty()) {
                // No more HUDs, clear completely
                hudManager.setCustomHud(playerRef, null)
                playerHuds.remove(playerRef.uuid)
            } else {
                // Refresh the composite
                hudManager.setCustomHud(playerRef, currentHud)
                currentHud.show()
            }
        }
    }
    
    /**
     * Check if a HUD namespace is registered for a player.
     */
    fun hasHud(player: Player, namespace: String): Boolean {
        val currentHud = player.hudManager.customHud
        return currentHud is CompositeHud && currentHud.huds.containsKey(namespace)
    }
    
    /**
     * Get all registered HUD namespaces for a player.
     */
    fun getNamespaces(player: Player): Set<String> {
        val currentHud = player.hudManager.customHud
        return if (currentHud is CompositeHud) {
            currentHud.huds.keys.toSet()
        } else {
            emptySet()
        }
    }
    
    /**
     * Clean up when a player disconnects.
     */
    fun onPlayerDisconnect(playerId: UUID) {
        playerHuds.remove(playerId)
    }
}
```

### CompositeHud

```kotlin
/**
 * Internal composite HUD that delegates to multiple child HUDs.
 */
internal class CompositeHud(
    playerRef: PlayerRef,
    val huds: MutableMap<String, CustomUIHud>,
    private val buildMethod: Method
) : CustomUIHud(playerRef) {
    
    override fun build(builder: UICommandBuilder) {
        huds.values.forEach { hud ->
            try {
                buildMethod.invoke(hud, builder)
            } catch (e: Exception) {
                // Log but don't crash - one bad HUD shouldn't break others
                CoreModule.services.get<HytaleLogger>()?.let { logger ->
                    logger.warning("Failed to build HUD: ${e.message}")
                }
            }
        }
    }
}
```

### Module HUD Example

```kotlin
/**
 * Example: Metabolism module HUD element.
 */
class MetabolismHudElement(
    playerRef: PlayerRef,
    private val metabolismService: MetabolismService
) : CustomUIHud(playerRef) {
    
    override fun build(builder: UICommandBuilder) {
        // Reference to UI file in assets
        builder.append("Pages/LivingLands_MetabolismHud.ui")
    }
}

/**
 * Example: Registering HUD in a module.
 */
class MetabolismModule : AbstractModule() {
    
    override suspend fun onPlayerReady(player: Player, playerRef: PlayerRef) {
        val hudElement = MetabolismHudElement(playerRef, metabolismService)
        CoreModule.hudManager.setHud(
            player, 
            playerRef, 
            "livinglands:metabolism", 
            hudElement
        )
    }
    
    override suspend fun onPlayerDisconnect(playerId: UUID) {
        // HUD cleanup is automatic via MultiHudManager.onPlayerDisconnect
    }
}
```

### External Mod Integration

Other mods can register their HUDs through the Living Lands API:

```kotlin
// In external mod
class MyModPlugin : JavaPlugin(init) {
    
    override fun setup() {
        // Get Living Lands HUD manager
        val livingLands = pluginManager.getPlugin("LivingLands")
        val hudManager = livingLands?.let {
            // Access via reflection or public API
            CoreModule.hudManager
        }
        
        // Register custom HUD
        eventRegistry.registerListener(PlayerReadyEvent::class.java) { event ->
            val player = event.player
            val playerRef = event.playerRef
            
            hudManager?.setHud(
                player,
                playerRef,
                "mymod:custom_hud",
                MyCustomHud(playerRef)
            )
        }
    }
}
```

---

## Persistence Layer

Living Lands uses SQLite for player data persistence with a **dual-database architecture**:

1. **Global Database** (`data/global/livinglands.db`) - Stores player stats that follow players across worlds (e.g., metabolism stats)
2. **Per-World Databases** (`data/{world-uuid}/livinglands.db`) - Stores world-specific module data (e.g., land claims)

### Global vs Per-World Persistence

**When to Use Global Persistence:**
- Player progression that should follow across worlds (metabolism, XP, skills)
- Player preferences (HUD settings, notifications)
- Account-wide data (achievements, unlocks)

**When to Use Per-World Persistence:**
- World-specific gameplay (land claims, placed structures)
- Server-specific data (permissions, bans)
- Data that should reset when changing worlds

**Implementation:**
```kotlin
// Global persistence (metabolism stats)
class MetabolismRepository(private val globalPersistence: GlobalPersistenceService) {
    suspend fun ensureStats(playerId: UUID): MetabolismStats {
        // Loads from data/global/livinglands.db
        // Stats follow player across all worlds
    }
}

// Per-world persistence (land claims)
class ClaimsRepository(private val worldPersistence: PersistenceService) {
    suspend fun getClaims(playerId: UUID): List<Claim> {
        // Loads from data/{world-uuid}/livinglands.db
        // Claims are isolated per world
    }
}
```

### GlobalPersistenceService

```kotlin
/**
 * Server-wide SQLite database service for global player data.
 * Location: LivingLandsReloaded/data/global/livinglands.db
 * 
 * Used for stats that follow players across worlds (metabolism, XP, etc.)
 */
class GlobalPersistenceService(private val dataPath: Path) {
    
    private val dbPath = dataPath.resolve("global").resolve("livinglands.db")
    private var connection: Connection? = null
    
    init {
        // Ensure directory exists
        Files.createDirectories(dbPath.parent)
        
        // Initialize SQLite connection
        connection = DriverManager.getConnection("jdbc:sqlite:$dbPath")
        
        // Enable WAL mode for better concurrency
        connection?.createStatement()?.execute("PRAGMA journal_mode=WAL")
        
        // Initialize core tables
        initializeTables()
    }
    
    private fun initializeTables() {
        connection?.createStatement()?.executeUpdate("""
            CREATE TABLE IF NOT EXISTS module_schemas (
                module_id TEXT PRIMARY KEY,
                schema_version INTEGER NOT NULL DEFAULT 1
            )
        """)
    }
    
    /**
     * Execute a database operation on IO dispatcher.
     */
    suspend fun <T> execute(block: (Connection) -> T): T {
        return withContext(Dispatchers.IO) {
            val conn = connection ?: throw IllegalStateException("Connection closed")
            synchronized(conn) {
                block(conn)
            }
        }
    }
    
    /**
     * Execute a transactional database operation.
     */
    suspend fun <T> transaction(block: (Connection) -> T): T {
        return withContext(Dispatchers.IO) {
            val conn = connection ?: throw IllegalStateException("Connection closed")
            synchronized(conn) {
                conn.autoCommit = false
                try {
                    val result = block(conn)
                    conn.commit()
                    result
                } catch (e: Exception) {
                    conn.rollback()
                    throw e
                } finally {
                    conn.autoCommit = true
                }
            }
        }
    }
    
    /**
     * Close the database connection.
     */
    suspend fun close() {
        withContext(Dispatchers.IO) {
            connection?.let { conn ->
                synchronized(conn) {
                    if (!conn.isClosed) {
                        conn.close()
                    }
                }
            }
            connection = null
        }
    }
}
```

### PersistenceService (Per-World)

```kotlin
/**
 * Per-world SQLite database service.
 * Location: LivingLandsReloaded/data/{world-uuid}/livinglands.db
 */
class PersistenceService(private val worldDataPath: Path) {
    
    private val dbPath = worldDataPath.resolve("livinglands.db")
    private val connection: Connection
    
    init {
        // Ensure directory exists
        Files.createDirectories(worldDataPath)
        
        // Initialize SQLite connection
        connection = DriverManager.getConnection("jdbc:sqlite:$dbPath")
        
        // Enable WAL mode for better concurrency
        connection.createStatement().execute("PRAGMA journal_mode=WAL")
        
        // Initialize core tables
        initializeTables()
    }
    
    private fun initializeTables() {
        connection.createStatement().executeUpdate("""
            CREATE TABLE IF NOT EXISTS players (
                uuid TEXT PRIMARY KEY,
                first_joined INTEGER NOT NULL,
                last_seen INTEGER NOT NULL,
                schema_version INTEGER NOT NULL DEFAULT 1
            )
        """)
        
        connection.createStatement().executeUpdate("""
            CREATE TABLE IF NOT EXISTS module_schemas (
                module_id TEXT PRIMARY KEY,
                schema_version INTEGER NOT NULL DEFAULT 1
            )
        """)
    }
    
    /**
     * Execute a database operation on IO dispatcher.
     */
    suspend fun <T> execute(block: (Connection) -> T): T {
        return withContext(Dispatchers.IO) {
            synchronized(connection) {
                block(connection)
            }
        }
    }
    
    /**
     * Execute a transactional database operation.
     */
    suspend fun <T> transaction(block: (Connection) -> T): T {
        return withContext(Dispatchers.IO) {
            synchronized(connection) {
                connection.autoCommit = false
                try {
                    val result = block(connection)
                    connection.commit()
                    result
                } catch (e: Exception) {
                    connection.rollback()
                    throw e
                } finally {
                    connection.autoCommit = true
                }
            }
        }
    }
    
    /**
     * Get or create module schema version.
     */
    suspend fun getModuleSchemaVersion(moduleId: String): Int {
        return execute { conn ->
            val stmt = conn.prepareStatement(
                "SELECT schema_version FROM module_schemas WHERE module_id = ?"
            )
            stmt.setString(1, moduleId)
            val rs = stmt.executeQuery()
            if (rs.next()) rs.getInt(1) else 0
        }
    }
    
    /**
     * Update module schema version.
     */
    suspend fun setModuleSchemaVersion(moduleId: String, version: Int) {
        execute { conn ->
            val stmt = conn.prepareStatement("""
                INSERT INTO module_schemas (module_id, schema_version) 
                VALUES (?, ?)
                ON CONFLICT(module_id) DO UPDATE SET schema_version = ?
            """)
            stmt.setString(1, moduleId)
            stmt.setInt(2, version)
            stmt.setInt(3, version)
            stmt.executeUpdate()
        }
    }
    
    /**
     * Execute raw SQL (for migrations).
     */
    suspend fun executeRaw(sql: String) {
        execute { conn ->
            conn.createStatement().executeUpdate(sql)
        }
    }
    
    /**
     * Close the database connection.
     */
    suspend fun close() {
        withContext(Dispatchers.IO) {
            synchronized(connection) {
                if (!connection.isClosed) {
                    connection.close()
                }
            }
        }
    }
}
```

### Repository Pattern

```kotlin
/**
 * Base repository interface for data access.
 */
interface Repository<T, ID> {
    suspend fun findById(id: ID): T?
    suspend fun save(entity: T)
    suspend fun delete(id: ID)
    suspend fun existsById(id: ID): Boolean
}

/**
 * Core player data repository.
 */
class PlayerDataRepository(
    private val persistence: PersistenceService
) : Repository<PlayerData, UUID> {
    
    override suspend fun findById(id: UUID): PlayerData? {
        return persistence.execute { conn ->
            val stmt = conn.prepareStatement(
                "SELECT uuid, first_joined, last_seen FROM players WHERE uuid = ?"
            )
            stmt.setString(1, id.toString())
            val rs = stmt.executeQuery()
            
            if (rs.next()) {
                PlayerData(
                    uuid = UUID.fromString(rs.getString("uuid")),
                    firstJoined = Instant.ofEpochMilli(rs.getLong("first_joined")),
                    lastSeen = Instant.ofEpochMilli(rs.getLong("last_seen"))
                )
            } else null
        }
    }
    
    override suspend fun save(entity: PlayerData) {
        persistence.execute { conn ->
            val stmt = conn.prepareStatement("""
                INSERT INTO players (uuid, first_joined, last_seen)
                VALUES (?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET last_seen = ?
            """)
            stmt.setString(1, entity.uuid.toString())
            stmt.setLong(2, entity.firstJoined.toEpochMilli())
            stmt.setLong(3, entity.lastSeen.toEpochMilli())
            stmt.setLong(4, entity.lastSeen.toEpochMilli())
            stmt.executeUpdate()
        }
    }
    
    override suspend fun delete(id: UUID) {
        persistence.execute { conn ->
            val stmt = conn.prepareStatement("DELETE FROM players WHERE uuid = ?")
            stmt.setString(1, id.toString())
            stmt.executeUpdate()
        }
    }
    
    override suspend fun existsById(id: UUID): Boolean {
        return persistence.execute { conn ->
            val stmt = conn.prepareStatement(
                "SELECT 1 FROM players WHERE uuid = ?"
            )
            stmt.setString(1, id.toString())
            stmt.executeQuery().next()
        }
    }
    
    /**
     * Ensure player exists, creating if necessary.
     */
    suspend fun ensurePlayer(playerId: UUID) {
        if (!existsById(playerId)) {
            val now = Instant.now()
            save(PlayerData(playerId, now, now))
        }
    }
    
    /**
     * Update last seen timestamp.
     */
    suspend fun updateLastSeen(playerId: UUID) {
        persistence.execute { conn ->
            val stmt = conn.prepareStatement(
                "UPDATE players SET last_seen = ? WHERE uuid = ?"
            )
            stmt.setLong(1, Instant.now().toEpochMilli())
            stmt.setString(2, playerId.toString())
            stmt.executeUpdate()
        }
    }
}

/**
 * Core player data model.
 */
data class PlayerData(
    val uuid: UUID,
    val firstJoined: Instant,
    val lastSeen: Instant
)
```

### Module Repository Examples

#### Global Repository (Metabolism)

```kotlin
/**
 * Metabolism data repository using global persistence.
 * Stats are stored in data/global/livinglands.db and follow players across worlds.
 */
class MetabolismRepository(
    private val globalPersistence: GlobalPersistenceService
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
    
    /**
     * Initialize metabolism tables in global database.
     */
    suspend fun initialize() {
        val currentVersion = globalPersistence.getModuleSchemaVersion("metabolism")
        
        if (currentVersion < SCHEMA_VERSION) {
            globalPersistence.executeRaw("""
                CREATE TABLE IF NOT EXISTS metabolism_stats (
                    player_uuid TEXT PRIMARY KEY,
                    hunger REAL NOT NULL DEFAULT 100.0,
                    thirst REAL NOT NULL DEFAULT 100.0,
                    energy REAL NOT NULL DEFAULT 100.0,
                    last_updated INTEGER NOT NULL
                )
            """)
            
            globalPersistence.setModuleSchemaVersion("metabolism", SCHEMA_VERSION)
        }
    }
    
    suspend fun getStats(playerId: UUID): MetabolismStats? {
        return globalPersistence.execute { conn ->
            val stmt = conn.prepareStatement("""
                SELECT hunger, thirst, energy, last_updated 
                FROM metabolism_stats WHERE player_uuid = ?
            """)
            stmt.setString(1, playerId.toString())
            val rs = stmt.executeQuery()
            
            if (rs.next()) {
                MetabolismStats(
                    playerId = playerId,
                    hunger = rs.getDouble("hunger"),
                    thirst = rs.getDouble("thirst"),
                    energy = rs.getDouble("energy"),
                    lastUpdated = Instant.ofEpochMilli(rs.getLong("last_updated"))
                )
            } else null
        }
    }
    
    suspend fun saveStats(stats: MetabolismStats) {
        globalPersistence.execute { conn ->
            val stmt = conn.prepareStatement("""
                INSERT INTO metabolism_stats (player_uuid, hunger, thirst, energy, last_updated)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(player_uuid) DO UPDATE SET
                    hunger = ?, thirst = ?, energy = ?, last_updated = ?
            """)
            stmt.setString(1, stats.playerId.toString())
            stmt.setDouble(2, stats.hunger)
            stmt.setDouble(3, stats.thirst)
            stmt.setDouble(4, stats.energy)
            stmt.setLong(5, stats.lastUpdated.toEpochMilli())
            stmt.setDouble(6, stats.hunger)
            stmt.setDouble(7, stats.thirst)
            stmt.setDouble(8, stats.energy)
            stmt.setLong(9, stats.lastUpdated.toEpochMilli())
            stmt.executeUpdate()
        }
    }
    
    suspend fun deleteStats(playerId: UUID) {
        globalPersistence.execute { conn ->
            val stmt = conn.prepareStatement(
                "DELETE FROM metabolism_stats WHERE player_uuid = ?"
            )
            stmt.setString(1, playerId.toString())
            stmt.executeUpdate()
        }
    }
}

data class MetabolismStats(
    val playerId: UUID,
    val hunger: Double,
    val thirst: Double,
    val energy: Double,
    val lastUpdated: Instant
)
```

#### Per-World Repository (Claims)

```kotlin
/**
 * Claims data repository using per-world persistence.
 * Claims are stored in data/{world-uuid}/livinglands.db and are isolated per world.
 */
class ClaimsRepository(
    private val worldPersistence: PersistenceService
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
    
    /**
     * Initialize claims tables in per-world database.
     */
    suspend fun initialize() {
        val currentVersion = worldPersistence.getModuleSchemaVersion("claims")
        
        if (currentVersion < SCHEMA_VERSION) {
            worldPersistence.executeRaw("""
                CREATE TABLE IF NOT EXISTS land_claims (
                    claim_id TEXT PRIMARY KEY,
                    player_uuid TEXT NOT NULL,
                    world_id TEXT NOT NULL,
                    chunk_x INTEGER NOT NULL,
                    chunk_z INTEGER NOT NULL,
                    created_at INTEGER NOT NULL,
                    FOREIGN KEY (player_uuid) REFERENCES players(uuid)
                        ON DELETE CASCADE
                )
            """)
            
            worldPersistence.setModuleSchemaVersion("claims", SCHEMA_VERSION)
        }
    }
    
    suspend fun getClaims(playerId: UUID): List<Claim> {
        return worldPersistence.execute { conn ->
            val stmt = conn.prepareStatement("""
                SELECT claim_id, chunk_x, chunk_z, created_at
                FROM land_claims WHERE player_uuid = ?
            """)
            stmt.setString(1, playerId.toString())
            val rs = stmt.executeQuery()
            
            val claims = mutableListOf<Claim>()
            while (rs.next()) {
                claims.add(Claim(
                    claimId = UUID.fromString(rs.getString("claim_id")),
                    playerId = playerId,
                    chunkX = rs.getInt("chunk_x"),
                    chunkZ = rs.getInt("chunk_z"),
                    createdAt = Instant.ofEpochMilli(rs.getLong("created_at"))
                ))
            }
            claims
        }
    }
    
    suspend fun saveClaim(claim: Claim) {
        worldPersistence.execute { conn ->
            val stmt = conn.prepareStatement("""
                INSERT INTO land_claims (claim_id, player_uuid, world_id, chunk_x, chunk_z, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
            """)
            stmt.setString(1, claim.claimId.toString())
            stmt.setString(2, claim.playerId.toString())
            stmt.setString(3, claim.worldId.toString())
            stmt.setInt(4, claim.chunkX)
            stmt.setInt(5, claim.chunkZ)
            stmt.setLong(6, claim.createdAt.toEpochMilli())
            stmt.executeUpdate()
        }
    }
}

data class Claim(
    val claimId: UUID,
    val playerId: UUID,
    val worldId: UUID,
    val chunkX: Int,
    val chunkZ: Int,
    val createdAt: Instant
)
```

---

## Configuration System

Configuration is file-based (YAML) and supports hot-reloading via command. Config is never stored in the database.

**YAML Library Recommendation:** Use **Jackson YAML** (`com.fasterxml.jackson.dataformat:jackson-dataformat-yaml`) instead of SnakeYAML for better Kotlin data class support, cleaner output, and more intuitive API.

### ConfigManager

```kotlin
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

/**
 * Manages YAML configuration files with hot-reload support.
 * Config files are stored in LivingLandsReloaded/config/
 * 
 * Uses Jackson YAML for clean serialization and excellent Kotlin support.
 */
class ConfigManager(private val configPath: Path) {
    
    private val yamlMapper = ObjectMapper(
        YAMLFactory()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER) // No '---'
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)          // Clean output
            .enable(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR) // Readable arrays
    ).apply {
        registerKotlinModule() // Essential for Kotlin data classes
        // Optional: configure property naming, null handling, etc.
    }
    
    private val configs = ConcurrentHashMap<String, Any>()
    private val configTypes = ConcurrentHashMap<String, KClass<*>>()
    
    init {
        Files.createDirectories(configPath)
    }
    
    /**
     * Load a configuration file. Creates default if not exists.
     */
    inline fun <reified T : Any> load(moduleId: String, default: T): T {
        configTypes[moduleId] = T::class
        
        val configFile = configPath.resolve("$moduleId.yml")
        
        return if (Files.exists(configFile)) {
            try {
                val loaded = yamlMapper.readValue(configFile.toFile(), T::class.java)
                configs[moduleId] = loaded
                loaded
            } catch (e: Exception) {
                // Failed to load, use default and save it
                save(moduleId, default)
                configs[moduleId] = default
                default
            }
        } else {
            // Create default config
            save(moduleId, default)
            configs[moduleId] = default
            default
        }
    }
    
    /**
     * Save configuration to file.
     */
    fun save(moduleId: String, config: Any) {
        val configFile = configPath.resolve("$moduleId.yml")
        yamlMapper.writeValue(configFile.toFile(), config)
        configs[moduleId] = config
    }
    
    /**
     * Get cached configuration.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(moduleId: String): T? {
        return configs[moduleId] as? T
    }
    
    /**
     * Reload configuration from disk.
     * Called by /ll reload [module] command.
     * 
     * @param moduleId Specific module to reload, or null for all
     * @return List of reloaded module IDs
     */
    fun reload(moduleId: String? = null): List<String> {
        val reloaded = mutableListOf<String>()
        
        val toReload = if (moduleId != null) {
            listOf(moduleId)
        } else {
            configs.keys.toList()
        }
        
        toReload.forEach { id ->
            val configType = configTypes[id] ?: return@forEach
            val configFile = configPath.resolve("$id.yml")
            
            if (Files.exists(configFile)) {
                try {
                    val content = Files.readString(configFile)
                    val loaded = yaml.loadAs(content, configType.java)
                    configs[id] = loaded
                    reloaded.add(id)
                } catch (e: Exception) {
                    // Log error but continue with other configs
                }
            }
        }
        
        // Notify modules of config reload
        reloaded.forEach { id ->
            CoreModule.getModule<Module>(id)?.onConfigReload()
        }
        
        return reloaded
    }
}
```

### Configuration Data Classes

```kotlin
/**
 * Core module configuration.
 */
data class CoreConfig(
    val debug: Boolean = false,
    val enabledModules: List<String> = listOf(
        "metabolism",
        "leveling",
        "claims",
        "hud"
    )
)

/**
 * Metabolism module configuration.
 */
data class MetabolismConfig(
    val enabled: Boolean = true,
    val hunger: HungerConfig = HungerConfig(),
    val thirst: ThirstConfig = ThirstConfig(),
    val energy: EnergyConfig = EnergyConfig(),
    val debuffs: DebuffsConfig = DebuffsConfig(),
    val buffs: BuffsConfig = BuffsConfig()
)

data class HungerConfig(
    val enabled: Boolean = true,
    val baseDepletionRateSeconds: Double = 480.0,
    val activityMultipliers: ActivityMultipliers = ActivityMultipliers()
)

data class ThirstConfig(
    val enabled: Boolean = true,
    val baseDepletionRateSeconds: Double = 360.0,
    val activityMultipliers: ActivityMultipliers = ActivityMultipliers()
)

data class EnergyConfig(
    val enabled: Boolean = true,
    val baseDepletionRateSeconds: Double = 600.0,
    val activityMultipliers: ActivityMultipliers = ActivityMultipliers()
)

data class ActivityMultipliers(
    val idle: Double = 1.0,
    val walking: Double = 1.5,
    val sprinting: Double = 3.0,
    val swimming: Double = 2.5,
    val combat: Double = 4.0
)

data class DebuffsConfig(
    val hungerDamageThreshold: Double = 0.0,
    val hungerRecoveryThreshold: Double = 30.0,
    val thirstSlowThreshold: Double = 20.0,
    val thirstRecoveryThreshold: Double = 40.0,
    val energySlowThreshold: Double = 15.0,
    val energyRecoveryThreshold: Double = 35.0
)

data class BuffsConfig(
    val enabled: Boolean = true,
    val activationThreshold: Double = 90.0,
    val deactivationThreshold: Double = 80.0,
    val speedBonus: Double = 1.15,
    val defenseBonus: Double = 1.10
)
```

### Reload Command

```kotlin
/**
 * Command to hot-reload configuration.
 * Usage: /ll reload [module]
 */
class ReloadCommand : AbstractPlayerCommand(
    "ll reload",
    "Reload Living Lands configuration",
    true // requiresOp
) {
    private val moduleArg = withOptionalArg(
        "module",
        "Module to reload (or all)",
        ArgTypes.STRING
    )
    
    override fun execute(
        ctx: CommandContext,
        store: Store<EntityStore>,
        ref: Ref<EntityStore>,
        playerRef: PlayerRef,
        world: World
    ) {
        val moduleId = ctx.get(moduleArg).orElse(null)
        val reloaded = CoreModule.config.reload(moduleId)
        
        if (reloaded.isEmpty()) {
            ctx.sendMessage(Message.raw("No configurations reloaded"))
        } else {
            ctx.sendMessage(Message.raw("Reloaded: ${reloaded.joinToString(", ")}"))
        }
    }
}
```

### Configuration Migration

Living Lands supports automatic config migration to handle breaking changes during plugin updates while preserving user customizations.

#### Why Config Migration?

During development (especially in beta), config structures often change:
- New fields added with better defaults
- Values rebalanced based on playtesting feedback
- Settings reorganized for clarity
- Deprecated options removed

Without migration, users must manually update or delete configs after updates, losing their customizations.

#### Migration System Architecture

```kotlin
/**
 * Interface for versioned configuration.
 * All module configs should implement this for automatic migration support.
 */
interface VersionedConfig {
    /** Current config schema version */
    val configVersion: Int
}

/**
 * Defines a migration from one config version to another.
 */
data class ConfigMigration(
    val fromVersion: Int,
    val toVersion: Int,
    val description: String,
    val migrate: (old: Map<String, Any>) -> Map<String, Any>?
)

/**
 * Enhanced ConfigManager with migration support using Jackson YAML.
 */
class ConfigManager(private val configPath: Path, private val logger: HytaleLogger) {
    
    private val mapper: ObjectMapper
    private val rawMapper: ObjectMapper
    private val migrations = ConfigMigrationRegistry()
    
    init {
        // Configure Jackson YAML for clean, readable output
        val yamlFactory = YAMLFactory.builder()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
            .build()
        
        mapper = ObjectMapper(yamlFactory).apply {
            registerKotlinModule()
            enable(SerializationFeature.INDENT_OUTPUT)
            disable(SerializationFeature.WRITE_NULL_MAP_VALUES)
        }
        
        rawMapper = ObjectMapper(yamlFactory).apply {
            registerKotlinModule()
        }
    }
    
    /**
     * Register migrations for a module config.
     */
    fun registerMigrations(moduleId: String, moduleMigrations: List<ConfigMigration>) {
        migrations.registerAll(moduleId, moduleMigrations)
    }
    
    /**
     * Load config with automatic migration if needed.
     */
    inline fun <reified T> loadWithMigration(
        moduleId: String, 
        default: T,
        targetVersion: Int
    ): T where T : Any, T : VersionedConfig {
        val configFile = configPath.resolve("$moduleId.yml")
        
        if (!Files.exists(configFile)) {
            // No existing config, save default
            save(moduleId, default)
            return default
        }
        
        try {
            // Load as raw map to check version
            val content = Files.readString(configFile)
            @Suppress("UNCHECKED_CAST")
            val rawConfig = rawMapper.readValue(content, Map::class.java) as Map<String, Any>
            
            val currentVersion = (rawConfig["configVersion"] as? Number)?.toInt() ?: 1
            
            if (currentVersion < targetVersion) {
                // Migration needed
                createBackup(moduleId, "pre-migration-v$currentVersion")
                
                val migrated = migrations.applyMigrations(
                    moduleId, rawConfig, currentVersion, targetVersion
                ) ?: run {
                    logger.warning("Migration failed for $moduleId, using defaults")
                    save(moduleId, default)
                    return default
                }
                
                // Convert migrated map to typed object
                val migratedConfig = mapper.convertValue(migrated, T::class.java)
                save(moduleId, migratedConfig)
                
                logger.info("Migrated $moduleId config from v$currentVersion to v$targetVersion")
                return migratedConfig
            } else {
                // No migration needed, load normally
                val loaded = mapper.readValue(content, T::class.java)
                return loaded
            }
        } catch (e: Exception) {
            logger.warning("Failed to load/migrate $moduleId config: ${e.message}")
            createBackup(configFile, "error")
            save(moduleId, default)
            return default
        }
    }
    
    /**
     * Create timestamped backup of config file.
     */
    private fun createBackup(moduleId: String, reason: String): Path? {
        val configFile = configPath.resolve("$moduleId.yml")
        if (!Files.exists(configFile)) return null
        
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val backupName = "$moduleId.$reason.$timestamp.yml.backup"
        val backupFile = configPath.resolve(backupName)
        
        return try {
            Files.copy(configFile, backupFile, StandardCopyOption.REPLACE_EXISTING)
            logger.info("Created backup: $backupName")
            backupFile
        } catch (e: Exception) {
            logger.warning("Failed to create backup: ${e.message}")
            null
        }
    }
}
```

#### Creating Migrations

**Example: Metabolism Config v1 → v2 (Rebalanced Depletion Rates)**

```kotlin
/**
 * Metabolism config with versioning.
 */
data class MetabolismConfig(
    override val configVersion: Int = 2, // Increment when structure changes
    val enabled: Boolean = true,
    val saveIntervalSeconds: Int = 60,
    val hunger: StatConfig = StatConfig(
        enabled = true,
        baseDepletionRateSeconds = 1440.0, // v2: 24 min (was 480s/8min in v1)
        activityMultipliers = mapOf(
            "idle" to 1.0,
            "walking" to 1.3,    // v2: reduced from 1.5
            "sprinting" to 2.0   // v2: reduced from 3.0
        )
    ),
    val thirst: StatConfig = StatConfig(/* ... */),
    val energy: StatConfig = StatConfig(/* ... */)
) : VersionedConfig

/**
 * Register migrations during module setup.
 */
class MetabolismModule : AbstractModule(/* ... */) {
    
    override suspend fun onSetup() {
        // Register migrations before loading config
        CoreModule.config.registerMigrations(
            "metabolism",
            listOf(
                ConfigMigration(
                    fromVersion = 1,
                    toVersion = 2,
                    description = "Rebalance depletion rates (3x slower, gentler multipliers)",
                    migrate = { old ->
                        old.toMutableMap().apply {
                            // Migrate hunger settings
                            (this["hunger"] as? MutableMap<String, Any>)?.apply {
                                // 3x slower base rate
                                this["baseDepletionRateSeconds"] = 1440.0
                                
                                // Gentler multipliers
                                (this["activityMultipliers"] as? MutableMap<String, Any>)?.apply {
                                    this["walking"] = 1.3  // was 1.5
                                    this["sprinting"] = 2.0 // was 3.0
                                }
                            }
                            
                            // Migrate thirst settings
                            (this["thirst"] as? MutableMap<String, Any>)?.apply {
                                this["baseDepletionRateSeconds"] = 1080.0 // 18 min
                                (this["activityMultipliers"] as? MutableMap<String, Any>)?.apply {
                                    this["walking"] = 1.2
                                    this["sprinting"] = 1.8
                                }
                            }
                            
                            // Migrate energy settings
                            (this["energy"] as? MutableMap<String, Any>)?.apply {
                                this["baseDepletionRateSeconds"] = 2400.0 // 40 min
                                (this["activityMultipliers"] as? MutableMap<String, Any>)?.apply {
                                    this["idle"] = 0.3      // was 0.5
                                    this["sprinting"] = 2.0  // was 2.5
                                }
                            }
                            
                            // Update version
                            this["configVersion"] = 2
                        }
                    }
                )
                // Future migrations go here (v2→v3, v3→v4, etc.)
            )
        )
        
        // Load with migration support (pass target version)
        config = CoreModule.config.loadWithMigration(
            "metabolism", 
            MetabolismConfig(), 
            MetabolismConfig.CURRENT_VERSION
        )
        
        // Rest of setup...
    }
}
```

#### Migration Best Practices

**1. Preserve User Customizations**
```kotlin
// Good: Only migrate specific changed fields
migrate = { old ->
    old.toMutableMap().apply {
        // Only change what needs changing
        (this["hunger"] as? MutableMap<*, *>)?.let { hunger ->
            if ((hunger["baseDepletionRateSeconds"] as? Double) == 480.0) {
                // Only update if user has default value
                (hunger as MutableMap<String, Any>)["baseDepletionRateSeconds"] = 1440.0
            }
        }
    }
}

// Bad: Overwrite everything
migrate = { old -> mapOf("hunger" to newDefaults) }
```

**2. Sequential Migrations**
```kotlin
// v1 → v2: Add new field
ConfigMigration(1, 2, "Add save interval") { old ->
    old + ("saveIntervalSeconds" to 60)
}

// v2 → v3: Rename field
ConfigMigration(2, 3, "Rename depletionRate") { old ->
    old.mapKeys { (k, _) -> 
        if (k == "depletionRate") "baseDepletionRateSeconds" else k
    }
}
```

**3. Validation After Migration**
```kotlin
try {
    val migrated = migrations.applyMigrations(moduleId, rawData, from, to)
    val validated = mapper.convertValue(migrated, T::class.java)
    // If this succeeds, migration worked
} catch (e: Exception) {
    logger.severe("Migration produced invalid config: ${e.message}")
    // Fall back to defaults
}
```

**4. User Communication**
- Log migration clearly: `"Migrated metabolism config from v1 to v2"`
- Create timestamped backup: `metabolism.pre-migration-v1.20260125-143022.yml.backup`
- Document changes in changelog/release notes
- Backup types: `pre-migration`, `parse-error`, `deserialize-error`, `no-migration-path`

---

## Module System

Modules are self-contained features that register with CoreModule.

### Module Interface

```kotlin
/**
 * Base interface for all Living Lands modules.
 */
sealed interface Module {
    /** Unique module identifier (e.g., "metabolism") */
    val id: String
    
    /** Human-readable name */
    val name: String
    
    /** Module version */
    val version: String
    
    /** Dependencies (other module IDs that must be loaded first) */
    val dependencies: Set<String>
    
    /** Current module state */
    val state: ModuleState
    
    /**
     * Setup phase - initialize resources, register listeners/commands.
     * Called after dependencies are set up.
     */
    suspend fun setup(context: ModuleContext)
    
    /**
     * Start phase - begin operations.
     * Called after all modules are set up.
     */
    suspend fun start()
    
    /**
     * Shutdown phase - cleanup resources, save data.
     * Called in reverse dependency order.
     */
    suspend fun shutdown()
    
    /**
     * Called when configuration is reloaded.
     */
    fun onConfigReload() {}
}

enum class ModuleState {
    DISABLED,
    SETUP,
    STARTED,
    STOPPED,
    ERROR
}
```

### AbstractModule

```kotlin
/**
 * Base implementation for modules with common functionality.
 */
abstract class AbstractModule(
    override val id: String,
    override val name: String,
    override val version: String,
    override val dependencies: Set<String> = emptySet()
) : Module {
    
    protected lateinit var context: ModuleContext
    protected val logger: HytaleLogger get() = context.logger
    
    override var state: ModuleState = ModuleState.DISABLED
        protected set
    
    final override suspend fun setup(context: ModuleContext) {
        this.context = context
        try {
            onSetup()
            state = ModuleState.SETUP
        } catch (e: Exception) {
            state = ModuleState.ERROR
            logger.severe("Failed to setup module $id: ${e.message}")
            throw e
        }
    }
    
    final override suspend fun start() {
        try {
            onStart()
            state = ModuleState.STARTED
        } catch (e: Exception) {
            state = ModuleState.ERROR
            logger.severe("Failed to start module $id: ${e.message}")
            throw e
        }
    }
    
    final override suspend fun shutdown() {
        try {
            onShutdown()
            state = ModuleState.STOPPED
        } catch (e: Exception) {
            logger.severe("Failed to shutdown module $id: ${e.message}")
        }
    }
    
    /**
     * Override to perform setup logic.
     */
    protected abstract suspend fun onSetup()
    
    /**
     * Override to perform start logic.
     */
    protected open suspend fun onStart() {}
    
    /**
     * Override to perform shutdown logic.
     */
    protected open suspend fun onShutdown() {}
    
    /**
     * Helper to get a required dependency module.
     */
    protected inline fun <reified T : Module> requireModule(moduleId: String): T {
        return CoreModule.getModule(moduleId)
            ?: throw IllegalStateException("Required module $moduleId not found")
    }
    
    /**
     * Helper to get an optional dependency module.
     */
    protected inline fun <reified T : Module> getModule(moduleId: String): T? {
        return CoreModule.getModule(moduleId)
    }
    
    /**
     * Helper to register an event listener.
     */
    protected inline fun <reified E : IEvent> registerListener(
        noinline handler: (E) -> Unit
    ) {
        context.eventRegistry.registerListener(E::class.java, handler)
    }
    
    /**
     * Helper to register an ECS system.
     */
    protected fun registerSystem(system: ISystem<EntityStore>) {
        context.entityStoreRegistry.registerSystem(system)
    }
    
    /**
     * Helper to register a command.
     */
    protected fun registerCommand(command: AbstractCommand<*>) {
        context.commandRegistry.registerCommand(command)
    }
}
```

### Module Example

```kotlin
/**
 * Metabolism module - hunger, thirst, energy mechanics.
 */
class MetabolismModule : AbstractModule(
    id = "metabolism",
    name = "Metabolism",
    version = "1.0.0",
    dependencies = setOf("core")
) {
    private lateinit var config: MetabolismConfig
    private lateinit var metabolismService: MetabolismService
    
    override suspend fun onSetup() {
        // Load configuration
        config = CoreModule.config.load("metabolism", MetabolismConfig())
        
        // Initialize repositories for all worlds
        CoreModule.worlds.getAllContexts().forEach { worldContext ->
            val repo = worldContext.getRepository { MetabolismRepository(it) }
            repo.initialize()
        }
        
        // Create service
        metabolismService = MetabolismService(config)
        
        // Register service
        CoreModule.services.register(metabolismService)
        
        // Register event listeners
        registerListener<PlayerReadyEvent> { event ->
            onPlayerReady(event.player, event.playerRef, event.world)
        }
        
        registerListener<PlayerDisconnectEvent> { event ->
            runBlocking {
                onPlayerDisconnect(event.playerRef.uuid, event.world)
            }
        }
        
        // Register ECS systems
        registerSystem(MetabolismTickSystem(metabolismService))
        
        // Register commands
        registerCommand(MetabolismCommand(metabolismService))
    }
    
    override suspend fun onStart() {
        logger.info("Metabolism module started")
    }
    
    override suspend fun onShutdown() {
        // Save all player data
        CoreModule.worlds.getAllContexts().forEach { worldContext ->
            metabolismService.saveAllPlayers(worldContext)
        }
        
        logger.info("Metabolism module shutdown")
    }
    
    override fun onConfigReload() {
        config = CoreModule.config.get("metabolism") ?: return
        metabolismService.updateConfig(config)
    }
    
    private fun onPlayerReady(player: Player, playerRef: PlayerRef, world: World) {
        val worldContext = CoreModule.worlds.getContext(world) ?: return
        
        runBlocking {
            metabolismService.initializePlayer(playerRef.uuid, worldContext)
        }
        
        // Register HUD
        CoreModule.hudManager.setHud(
            player,
            playerRef,
            "livinglands:metabolism",
            MetabolismHudElement(playerRef, metabolismService)
        )
    }
    
    private suspend fun onPlayerDisconnect(playerId: UUID, world: World) {
        val worldContext = CoreModule.worlds.getContext(world) ?: return
        metabolismService.savePlayer(playerId, worldContext)
    }
}
```

---

## Thread Safety & ECS Access

### The Problem

Hytale's ECS (Entity Component System) is NOT thread-safe. Components can only be accessed from the WorldThread that owns the entity.

### The Solution

All ECS access is wrapped in `world.execute { }`:

```kotlin
// Extension function for cleaner syntax
inline fun World.executeSafe(crossinline block: () -> Unit) {
    this.execute { block() }
}

// Usage
world.executeSafe {
    val statMap = store.getComponent(ref, EntityStatMap.getComponentType())
    statMap?.putModifier(statId, key, modifier)
}
```

### Thread-Safe Collections

All per-player tracking uses thread-safe collections:

```kotlin
// Thread-safe map for player data
private val playerData = ConcurrentHashMap<UUID, PlayerMetabolismData>()

// Thread-safe set for state tracking
private val starvingPlayers = ConcurrentHashMap.newKeySet<UUID>()

// Nested concurrent map for complex state
private val lastDebuffTickTime = ConcurrentHashMap<UUID, ConcurrentHashMap<DebuffType, Long>>()
```

### Coroutines for Async Operations

Database operations use Kotlin coroutines with `Dispatchers.IO`:

```kotlin
// In repository
suspend fun saveStats(stats: MetabolismStats) {
    withContext(Dispatchers.IO) {
        // SQLite operations here
    }
}

// In module
fun onPlayerDisconnect(playerId: UUID, world: World) {
    // Launch coroutine for async save
    CoroutineScope(Dispatchers.IO).launch {
        val worldContext = CoreModule.worlds.getContext(world) ?: return@launch
        metabolismService.savePlayer(playerId, worldContext)
    }
}
```

### PlayerRegistry

```kotlin
/**
 * Centralized management of player sessions and ECS references.
 */
class PlayerRegistry {
    
    private val sessions = ConcurrentHashMap<UUID, PlayerSession>()
    
    fun getSession(playerId: UUID): PlayerSession? = sessions[playerId]
    
    fun createSession(
        playerId: UUID,
        entityRef: Ref<EntityStore>,
        store: Store<EntityStore>,
        world: World
    ): PlayerSession {
        val session = PlayerSession(playerId, entityRef, store, world)
        sessions[playerId] = session
        return session
    }
    
    fun removeSession(playerId: UUID): PlayerSession? = sessions.remove(playerId)
    
    fun getAllSessions(): Collection<PlayerSession> = sessions.values
}

/**
 * Represents an active player session with ECS references.
 */
data class PlayerSession(
    val playerId: UUID,
    var entityRef: Ref<EntityStore>,
    var store: Store<EntityStore>,
    var world: World
) {
    val isEcsReady: Boolean
        get() = entityRef.isValid
    
    /**
     * Execute a block on the WorldThread.
     */
    inline fun execute(crossinline block: () -> Unit) {
        world.execute { block() }
    }
}
```

---

## Metabolism Core

### MetabolismService

```kotlin
/**
 * Core metabolism service - manages hunger, thirst, energy.
 */
class MetabolismService(private var config: MetabolismConfig) {
    
    // In-memory player data (per-world data is in repositories)
    private val playerData = ConcurrentHashMap<WorldPlayerKey, PlayerMetabolismState>()
    
    fun updateConfig(newConfig: MetabolismConfig) {
        config = newConfig
    }
    
    suspend fun initializePlayer(playerId: UUID, worldContext: WorldContext) {
        val repo = worldContext.getRepository { MetabolismRepository(it) }
        
        // Load from database or create new
        val stats = repo.getStats(playerId) ?: MetabolismStats(
            playerId = playerId,
            hunger = 100.0,
            thirst = 100.0,
            energy = 100.0,
            lastUpdated = Instant.now()
        )
        
        // Cache in memory
        val key = WorldPlayerKey(worldContext.worldId, playerId)
        playerData[key] = PlayerMetabolismState(
            hunger = stats.hunger,
            thirst = stats.thirst,
            energy = stats.energy,
            currentActivity = ActivityState.IDLE,
            lastHungerDepletion = System.currentTimeMillis(),
            lastThirstDepletion = System.currentTimeMillis(),
            lastEnergyDepletion = System.currentTimeMillis()
        )
    }
    
    suspend fun savePlayer(playerId: UUID, worldContext: WorldContext) {
        val key = WorldPlayerKey(worldContext.worldId, playerId)
        val state = playerData.remove(key) ?: return
        
        val repo = worldContext.getRepository { MetabolismRepository(it) }
        repo.saveStats(MetabolismStats(
            playerId = playerId,
            hunger = state.hunger,
            thirst = state.thirst,
            energy = state.energy,
            lastUpdated = Instant.now()
        ))
    }
    
    suspend fun saveAllPlayers(worldContext: WorldContext) {
        val repo = worldContext.getRepository { MetabolismRepository(it) }
        
        playerData.entries
            .filter { it.key.worldId == worldContext.worldId }
            .forEach { (key, state) ->
                repo.saveStats(MetabolismStats(
                    playerId = key.playerId,
                    hunger = state.hunger,
                    thirst = state.thirst,
                    energy = state.energy,
                    lastUpdated = Instant.now()
                ))
            }
    }
    
    /**
     * Process metabolism tick for a player.
     */
    fun processTick(playerId: UUID, worldId: UUID, currentTime: Long) {
        val key = WorldPlayerKey(worldId, playerId)
        val state = playerData[key] ?: return
        
        // Calculate activity multiplier
        val multiplier = when (state.currentActivity) {
            ActivityState.IDLE -> config.hunger.activityMultipliers.idle
            ActivityState.WALKING -> config.hunger.activityMultipliers.walking
            ActivityState.SPRINTING -> config.hunger.activityMultipliers.sprinting
            ActivityState.SWIMMING -> config.hunger.activityMultipliers.swimming
            ActivityState.COMBAT -> config.hunger.activityMultipliers.combat
        }
        
        // Process hunger depletion
        if (config.hunger.enabled) {
            processDepletion(
                state::hunger,
                state::lastHungerDepletion,
                config.hunger.baseDepletionRateSeconds,
                multiplier,
                currentTime
            )
        }
        
        // Process thirst depletion
        if (config.thirst.enabled) {
            processDepletion(
                state::thirst,
                state::lastThirstDepletion,
                config.thirst.baseDepletionRateSeconds,
                multiplier,
                currentTime
            )
        }
        
        // Process energy depletion
        if (config.energy.enabled) {
            processDepletion(
                state::energy,
                state::lastEnergyDepletion,
                config.energy.baseDepletionRateSeconds,
                multiplier,
                currentTime
            )
        }
    }
    
    private fun processDepletion(
        statProperty: KMutableProperty0<Double>,
        lastDepletionProperty: KMutableProperty0<Long>,
        baseRateSeconds: Double,
        multiplier: Double,
        currentTime: Long
    ) {
        val adjustedRateMs = (baseRateSeconds / multiplier * 1000).toLong()
        
        if (currentTime - lastDepletionProperty.get() >= adjustedRateMs) {
            statProperty.set(maxOf(statProperty.get() - 1.0, 0.0))
            lastDepletionProperty.set(currentTime)
        }
    }
    
    fun getState(playerId: UUID, worldId: UUID): PlayerMetabolismState? {
        return playerData[WorldPlayerKey(worldId, playerId)]
    }
    
    fun updateActivity(playerId: UUID, worldId: UUID, activity: ActivityState) {
        playerData[WorldPlayerKey(worldId, playerId)]?.currentActivity = activity
    }
    
    fun restoreHunger(playerId: UUID, worldId: UUID, amount: Double) {
        playerData[WorldPlayerKey(worldId, playerId)]?.let {
            it.hunger = minOf(it.hunger + amount, 100.0)
        }
    }
    
    fun restoreThirst(playerId: UUID, worldId: UUID, amount: Double) {
        playerData[WorldPlayerKey(worldId, playerId)]?.let {
            it.thirst = minOf(it.thirst + amount, 100.0)
        }
    }
    
    fun restoreEnergy(playerId: UUID, worldId: UUID, amount: Double) {
        playerData[WorldPlayerKey(worldId, playerId)]?.let {
            it.energy = minOf(it.energy + amount, 100.0)
        }
    }
}

data class WorldPlayerKey(val worldId: UUID, val playerId: UUID)

data class PlayerMetabolismState(
    var hunger: Double,
    var thirst: Double,
    var energy: Double,
    var currentActivity: ActivityState,
    var lastHungerDepletion: Long,
    var lastThirstDepletion: Long,
    var lastEnergyDepletion: Long
)

enum class ActivityState {
    IDLE, WALKING, SPRINTING, SWIMMING, COMBAT
}
```

### MetabolismTickSystem

```kotlin
/**
 * ECS ticking system for metabolism processing.
 */
class MetabolismTickSystem(
    private val metabolismService: MetabolismService
) : EntityTickingSystem<EntityStore>() {
    
    private var lastTickTime = System.currentTimeMillis()
    
    override fun getQuery(): Query<EntityStore> = PlayerRef.getComponentType()
    
    override fun tick(
        deltaTime: Float,
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        commandBuffer: CommandBuffer<EntityStore>
    ) {
        val currentTime = System.currentTimeMillis()
        
        // Only process once per second
        if (currentTime - lastTickTime < 1000) return
        lastTickTime = currentTime
        
        val ref = chunk.getReferenceTo(index)
        val playerRef = store.getComponent(ref, PlayerRef.getComponentType()) ?: return
        val player = store.getComponent(ref, Player.getComponentType()) ?: return
        
        // Get world from player
        val world = player.world ?: return
        val worldContext = CoreModule.worlds.getContext(world) ?: return
        
        // Detect activity state
        val activity = detectActivity(store, ref)
        metabolismService.updateActivity(playerRef.uuid, worldContext.worldId, activity)
        
        // Process metabolism tick
        metabolismService.processTick(playerRef.uuid, worldContext.worldId, currentTime)
    }
    
    private fun detectActivity(
        store: Store<EntityStore>,
        ref: Ref<EntityStore>
    ): ActivityState {
        val movementStates = store.getComponent(
            ref, 
            MovementStatesComponent.getComponentType()
        ) ?: return ActivityState.IDLE
        
        return when {
            movementStates.isSprinting -> ActivityState.SPRINTING
            movementStates.isSwimming -> ActivityState.SWIMMING
            movementStates.isWalking -> ActivityState.WALKING
            else -> ActivityState.IDLE
        }
    }
}
```

---

## Buff & Debuff Systems

### Hysteresis Pattern

Prevents rapid state flickering when stats hover near thresholds:

```kotlin
/**
 * Hysteresis controller for debuff state management.
 */
class HysteresisController<T>(
    private val enterThreshold: Double,
    private val exitThreshold: Double,
    private val onEnter: (UUID) -> Unit,
    private val onExit: (UUID) -> Unit
) {
    private val activeStates = ConcurrentHashMap.newKeySet<UUID>()
    
    /**
     * Check and update state based on current value.
     * 
     * For debuffs (activate when LOW):
     *   enterThreshold = 20.0 (activate when stat drops TO this)
     *   exitThreshold = 40.0 (deactivate when stat rises ABOVE this)
     *   
     * For buffs (activate when HIGH):
     *   enterThreshold = 90.0 (activate when stat rises TO this)
     *   exitThreshold = 80.0 (deactivate when stat drops BELOW this)
     */
    fun update(playerId: UUID, currentValue: Double, isDebuff: Boolean) {
        val isActive = activeStates.contains(playerId)
        
        if (isDebuff) {
            // Debuff: activate when low, deactivate when recovered
            if (!isActive && currentValue <= enterThreshold) {
                activeStates.add(playerId)
                onEnter(playerId)
            } else if (isActive && currentValue >= exitThreshold) {
                activeStates.remove(playerId)
                onExit(playerId)
            }
        } else {
            // Buff: activate when high, deactivate when dropped
            if (!isActive && currentValue >= enterThreshold) {
                activeStates.add(playerId)
                onEnter(playerId)
            } else if (isActive && currentValue <= exitThreshold) {
                activeStates.remove(playerId)
                onExit(playerId)
            }
        }
    }
    
    fun isActive(playerId: UUID): Boolean = activeStates.contains(playerId)
    
    fun clear(playerId: UUID) {
        activeStates.remove(playerId)
    }
}
```

### DebuffsSystem

```kotlin
/**
 * Manages metabolism debuffs (low stat penalties).
 */
class DebuffsSystem(private val config: DebuffsConfig) {
    
    // Hysteresis controllers for each debuff type
    private val starvationController = HysteresisController<UUID>(
        enterThreshold = config.hungerDamageThreshold,
        exitThreshold = config.hungerRecoveryThreshold,
        onEnter = ::onStarvationStart,
        onExit = ::onStarvationEnd
    )
    
    private val dehydrationController = HysteresisController<UUID>(
        enterThreshold = config.thirstSlowThreshold,
        exitThreshold = config.thirstRecoveryThreshold,
        onEnter = ::onDehydrationStart,
        onExit = ::onDehydrationEnd
    )
    
    private val exhaustionController = HysteresisController<UUID>(
        enterThreshold = config.energySlowThreshold,
        exitThreshold = config.energyRecoveryThreshold,
        onEnter = ::onExhaustionStart,
        onExit = ::onExhaustionEnd
    )
    
    fun processDebuffs(playerId: UUID, state: PlayerMetabolismState) {
        starvationController.update(playerId, state.hunger, isDebuff = true)
        dehydrationController.update(playerId, state.thirst, isDebuff = true)
        exhaustionController.update(playerId, state.energy, isDebuff = true)
    }
    
    fun hasActiveDebuffs(playerId: UUID): Boolean {
        return starvationController.isActive(playerId) ||
               dehydrationController.isActive(playerId) ||
               exhaustionController.isActive(playerId)
    }
    
    fun clearDebuffs(playerId: UUID) {
        starvationController.clear(playerId)
        dehydrationController.clear(playerId)
        exhaustionController.clear(playerId)
    }
    
    private fun onStarvationStart(playerId: UUID) {
        // Apply hunger damage effect
        val session = CoreModule.players.getSession(playerId) ?: return
        session.execute {
            // Apply damage over time
        }
    }
    
    private fun onStarvationEnd(playerId: UUID) {
        // Remove hunger damage effect
    }
    
    private fun onDehydrationStart(playerId: UUID) {
        // Apply movement slow
        CoreModule.services.get<SpeedManager>()?.applyDebuff(
            playerId, 
            SpeedDebuff.DEHYDRATION, 
            0.7f
        )
    }
    
    private fun onDehydrationEnd(playerId: UUID) {
        CoreModule.services.get<SpeedManager>()?.removeDebuff(
            playerId, 
            SpeedDebuff.DEHYDRATION
        )
    }
    
    private fun onExhaustionStart(playerId: UUID) {
        // Apply stamina regeneration debuff
    }
    
    private fun onExhaustionEnd(playerId: UUID) {
        // Remove stamina regeneration debuff
    }
}
```

### BuffsSystem

```kotlin
/**
 * Manages metabolism buffs (high stat bonuses).
 * Buffs are suppressed when any debuff is active.
 */
class BuffsSystem(
    private val config: BuffsConfig,
    private val debuffsSystem: DebuffsSystem
) {
    private val speedBuffController = HysteresisController<UUID>(
        enterThreshold = config.activationThreshold,
        exitThreshold = config.deactivationThreshold,
        onEnter = ::onSpeedBuffStart,
        onExit = ::onSpeedBuffEnd
    )
    
    private val defenseBuffController = HysteresisController<UUID>(
        enterThreshold = config.activationThreshold,
        exitThreshold = config.deactivationThreshold,
        onEnter = ::onDefenseBuffStart,
        onExit = ::onDefenseBuffEnd
    )
    
    fun processBuffs(playerId: UUID, state: PlayerMetabolismState) {
        // Debuffs suppress all buffs
        if (debuffsSystem.hasActiveDebuffs(playerId)) {
            removeAllBuffs(playerId)
            return
        }
        
        // Use average of all stats for buff activation
        val averageStat = (state.hunger + state.thirst + state.energy) / 3.0
        
        speedBuffController.update(playerId, averageStat, isDebuff = false)
        defenseBuffController.update(playerId, averageStat, isDebuff = false)
    }
    
    fun removeAllBuffs(playerId: UUID) {
        if (speedBuffController.isActive(playerId)) {
            speedBuffController.clear(playerId)
            onSpeedBuffEnd(playerId)
        }
        if (defenseBuffController.isActive(playerId)) {
            defenseBuffController.clear(playerId)
            onDefenseBuffEnd(playerId)
        }
    }
    
    private fun onSpeedBuffStart(playerId: UUID) {
        CoreModule.services.get<SpeedManager>()?.applyBuff(
            playerId,
            SpeedBuff.WELL_FED,
            config.speedBonus.toFloat()
        )
    }
    
    private fun onSpeedBuffEnd(playerId: UUID) {
        CoreModule.services.get<SpeedManager>()?.removeBuff(playerId, SpeedBuff.WELL_FED)
    }
    
    private fun onDefenseBuffStart(playerId: UUID) {
        // Apply defense stat modifier
    }
    
    private fun onDefenseBuffEnd(playerId: UUID) {
        // Remove defense stat modifier
    }
}
```

---

## Speed Modification

### The Challenge

Hytale has no "movement speed" stat in `DefaultEntityStatTypes`. Speed is controlled through `MovementManager.settings.baseSpeed`.

### SpeedManager

```kotlin
/**
 * Centralized speed modification manager.
 * Prevents conflicts between multiple systems affecting speed.
 */
class SpeedManager {
    
    // Original speeds before any modifications
    private val originalSpeeds = ConcurrentHashMap<UUID, Float>()
    
    // Active debuffs and their multipliers
    private val activeDebuffs = ConcurrentHashMap<UUID, MutableMap<SpeedDebuff, Float>>()
    
    // Active buffs and their multipliers
    private val activeBuffs = ConcurrentHashMap<UUID, MutableMap<SpeedBuff, Float>>()
    
    fun applyDebuff(playerId: UUID, debuff: SpeedDebuff, multiplier: Float) {
        val debuffs = activeDebuffs.getOrPut(playerId) { ConcurrentHashMap() }
        debuffs[debuff] = multiplier
        recalculateSpeed(playerId)
    }
    
    fun removeDebuff(playerId: UUID, debuff: SpeedDebuff) {
        activeDebuffs[playerId]?.remove(debuff)
        recalculateSpeed(playerId)
    }
    
    fun applyBuff(playerId: UUID, buff: SpeedBuff, multiplier: Float) {
        val buffs = activeBuffs.getOrPut(playerId) { ConcurrentHashMap() }
        buffs[buff] = multiplier
        recalculateSpeed(playerId)
    }
    
    fun removeBuff(playerId: UUID, buff: SpeedBuff) {
        activeBuffs[playerId]?.remove(buff)
        recalculateSpeed(playerId)
    }
    
    private fun recalculateSpeed(playerId: UUID) {
        val session = CoreModule.players.getSession(playerId) ?: return
        
        session.execute {
            val player = session.store.getComponent(
                session.entityRef, 
                Player.getComponentType()
            ) ?: return@execute
            
            val movementManager = session.store.getComponent(
                session.entityRef,
                MovementManager.getComponentType()
            ) ?: return@execute
            
            val settings = movementManager.settings ?: return@execute
            
            // Store original speed if not already stored
            if (!originalSpeeds.containsKey(playerId)) {
                originalSpeeds[playerId] = settings.baseSpeed
            }
            
            val originalSpeed = originalSpeeds[playerId] ?: settings.baseSpeed
            
            // Calculate combined multiplier
            var multiplier = 1.0f
            
            // Debuffs (take the worst one)
            activeDebuffs[playerId]?.values?.minOrNull()?.let {
                multiplier = minOf(multiplier, it)
            }
            
            // Buffs only apply if no debuffs
            if (activeDebuffs[playerId]?.isEmpty() != false) {
                activeBuffs[playerId]?.values?.maxOrNull()?.let {
                    multiplier = maxOf(multiplier, it)
                }
            }
            
            settings.baseSpeed = originalSpeed * multiplier
        }
    }
    
    fun restoreOriginalSpeed(playerId: UUID) {
        activeDebuffs.remove(playerId)
        activeBuffs.remove(playerId)
        
        val originalSpeed = originalSpeeds.remove(playerId) ?: return
        val session = CoreModule.players.getSession(playerId) ?: return
        
        session.execute {
            val movementManager = session.store.getComponent(
                session.entityRef,
                MovementManager.getComponentType()
            ) ?: return@execute
            
            movementManager.settings?.baseSpeed = originalSpeed
        }
    }
    
    fun onPlayerDisconnect(playerId: UUID) {
        originalSpeeds.remove(playerId)
        activeDebuffs.remove(playerId)
        activeBuffs.remove(playerId)
    }
}

enum class SpeedDebuff {
    DEHYDRATION,
    EXHAUSTION,
    NATIVE_SLOW,
    NATIVE_ROOT
}

enum class SpeedBuff {
    WELL_FED,
    POTION_SPEED
}
```

---

## Food & Potion Detection

### The Challenge

Hytale doesn't fire "item consumed" events. We must detect consumption by monitoring `EffectControllerComponent` for new effects.

### FoodEffectDetector

```kotlin
/**
 * Detects food and potion consumption by monitoring active effects.
 */
class FoodEffectDetector {
    
    // Track effects seen on previous tick
    private val previousEffects = ConcurrentHashMap<UUID, Set<Int>>()
    
    // Track processed effect indexes to prevent double-detection
    private val processedIndexes = ConcurrentHashMap<UUID, MutableSet<Int>>()
    
    // Cleanup interval for processed indexes
    private val cleanupIntervalMs = 200L
    private val lastCleanup = ConcurrentHashMap<UUID, Long>()
    
    /**
     * Detect newly applied effects for a player.
     * Should be called at high frequency (50ms) to catch instant effects.
     */
    fun detectNewEffects(session: PlayerSession): List<DetectedEffect> {
        val playerId = session.playerId
        val currentTime = System.currentTimeMillis()
        
        // Periodic cleanup of processed indexes
        if (currentTime - (lastCleanup[playerId] ?: 0) > cleanupIntervalMs) {
            processedIndexes[playerId]?.clear()
            lastCleanup[playerId] = currentTime
        }
        
        val newEffects = mutableListOf<DetectedEffect>()
        
        session.execute {
            val effectController = session.store.getComponent(
                session.entityRef,
                EffectControllerComponent.getComponentType()
            ) ?: return@execute
            
            val activeEffects = effectController.allActiveEntityEffects
            val currentIndexes = mutableSetOf<Int>()
            
            activeEffects.forEachIndexed { index, effect ->
                currentIndexes.add(index)
                
                // Skip if we've already processed this index recently
                if (processedIndexes[playerId]?.contains(index) == true) {
                    return@forEachIndexed
                }
                
                // Check if this is a new effect
                if (previousEffects[playerId]?.contains(index) != true) {
                    val effectId = getEffectId(effect)
                    val detectedType = matchEffectType(effectId)
                    
                    if (detectedType != null) {
                        newEffects.add(DetectedEffect(effectId, detectedType, index))
                        processedIndexes.getOrPut(playerId) { mutableSetOf() }.add(index)
                    }
                }
            }
            
            previousEffects[playerId] = currentIndexes
        }
        
        return newEffects
    }
    
    private fun getEffectId(effect: Any): String {
        // Try getType().getId() pattern
        try {
            val typeMethod = effect::class.java.getMethod("getType")
            val type = typeMethod.invoke(effect)
            val idMethod = type::class.java.getMethod("getId")
            return idMethod.invoke(type) as String
        } catch (e: Exception) {
            // Fallback to direct getId()
            try {
                val idMethod = effect::class.java.getMethod("getId")
                return idMethod.invoke(effect) as String
            } catch (e2: Exception) {
                return "unknown"
            }
        }
    }
    
    private fun matchEffectType(effectId: String): ConsumableType? {
        return when {
            // Food effects
            effectId.startsWith("Food_Instant_Heal") -> ConsumableType.FOOD_INSTANT_HEAL
            effectId.startsWith("Food_Health_Restore") -> ConsumableType.FOOD_HEALTH_RESTORE
            effectId.startsWith("Food_Stamina_Restore") -> ConsumableType.FOOD_STAMINA_RESTORE
            effectId.startsWith("Food_Health_Boost") -> ConsumableType.FOOD_HEALTH_BOOST
            effectId.startsWith("Food_Stamina_Boost") -> ConsumableType.FOOD_STAMINA_BOOST
            effectId.startsWith("Food_Health_Regen") -> ConsumableType.FOOD_HEALTH_REGEN
            effectId.startsWith("Food_Stamina_Regen") -> ConsumableType.FOOD_STAMINA_REGEN
            effectId.startsWith("Meat_Buff") -> ConsumableType.MEAT
            effectId.startsWith("FruitVeggie_Buff") -> ConsumableType.FRUIT_VEGGIE
            effectId == "Antidote" -> ConsumableType.ANTIDOTE
            
            // Potion effects
            effectId.startsWith("Potion_Health") -> ConsumableType.POTION_HEALTH
            effectId.startsWith("Potion_Stamina") -> ConsumableType.POTION_STAMINA
            effectId.startsWith("Potion_Mana") -> ConsumableType.POTION_MANA
            effectId.startsWith("Potion_Signature") -> ConsumableType.POTION_MANA
            effectId.startsWith("Potion_Regen") -> ConsumableType.POTION_REGEN
            
            else -> null
        }
    }
    
    fun onPlayerDisconnect(playerId: UUID) {
        previousEffects.remove(playerId)
        processedIndexes.remove(playerId)
        lastCleanup.remove(playerId)
    }
}

data class DetectedEffect(
    val effectId: String,
    val type: ConsumableType,
    val effectIndex: Int
)

enum class ConsumableType {
    FOOD_INSTANT_HEAL,
    FOOD_HEALTH_RESTORE,
    FOOD_STAMINA_RESTORE,
    FOOD_HEALTH_BOOST,
    FOOD_STAMINA_BOOST,
    FOOD_HEALTH_REGEN,
    FOOD_STAMINA_REGEN,
    MEAT,
    FRUIT_VEGGIE,
    ANTIDOTE,
    POTION_HEALTH,
    POTION_STAMINA,
    POTION_MANA,
    POTION_REGEN
}
```

---

## Poison System

### Poison Types

```kotlin
/**
 * Types of poison effects from consumables.
 */
enum class PoisonEffectType {
    MILD_TOXIN,    // Short burst, fast drain
    SLOW_POISON,   // Long duration, slow drain
    PURGE,         // Severe drain then recovery phase
    RANDOM         // Randomly selects one of above
}
```

### PoisonEffectsSystem

```kotlin
/**
 * Manages consumable poison effects.
 */
class PoisonEffectsSystem(private val config: PoisonConfig) {
    
    private val activePoisonStates = ConcurrentHashMap<UUID, ActivePoisonState>()
    
    fun applyPoison(playerId: UUID, effectType: PoisonEffectType) {
        val actualType = if (effectType == PoisonEffectType.RANDOM) {
            listOf(
                PoisonEffectType.MILD_TOXIN,
                PoisonEffectType.SLOW_POISON,
                PoisonEffectType.PURGE
            ).random()
        } else effectType
        
        val poisonConfig = when (actualType) {
            PoisonEffectType.MILD_TOXIN -> config.mildToxin
            PoisonEffectType.SLOW_POISON -> config.slowPoison
            PoisonEffectType.PURGE -> config.purge
            else -> config.mildToxin
        }
        
        activePoisonStates[playerId] = ActivePoisonState(
            effectType = actualType,
            startTime = System.currentTimeMillis(),
            durationSeconds = poisonConfig.durationSeconds,
            drainPerTick = poisonConfig.drainPerTick,
            tickIntervalMs = poisonConfig.tickIntervalMs
        )
    }
    
    fun processPoisonEffects(metabolismService: MetabolismService, worldId: UUID) {
        val currentTime = System.currentTimeMillis()
        val expired = mutableListOf<UUID>()
        
        activePoisonStates.forEach { (playerId, state) ->
            if (state.isExpired(currentTime)) {
                expired.add(playerId)
                return@forEach
            }
            
            if (currentTime - state.lastTickTime >= state.tickIntervalMs) {
                // Apply drain
                if (!state.inRecoveryPhase) {
                    metabolismService.restoreHunger(playerId, worldId, -state.drainPerTick)
                    metabolismService.restoreThirst(playerId, worldId, -state.drainPerTick)
                    metabolismService.restoreEnergy(playerId, worldId, -state.drainPerTick)
                }
                
                state.lastTickTime = currentTime
                state.ticksApplied++
                
                // PURGE effect: switch to recovery phase after half duration
                if (state.effectType == PoisonEffectType.PURGE) {
                    val halfDuration = state.durationSeconds * 500
                    if (currentTime - state.startTime >= halfDuration && !state.inRecoveryPhase) {
                        state.inRecoveryPhase = true
                    }
                }
            }
        }
        
        expired.forEach { activePoisonStates.remove(it) }
    }
    
    fun hasActivePoison(playerId: UUID): Boolean = activePoisonStates.containsKey(playerId)
    
    fun clearPoison(playerId: UUID) {
        activePoisonStates.remove(playerId)
    }
    
    fun onPlayerDisconnect(playerId: UUID) {
        activePoisonStates.remove(playerId)
    }
}

private data class ActivePoisonState(
    val effectType: PoisonEffectType,
    val startTime: Long,
    val durationSeconds: Float,
    val drainPerTick: Double,
    val tickIntervalMs: Long,
    var ticksApplied: Int = 0,
    var lastTickTime: Long = startTime,
    var inRecoveryPhase: Boolean = false
) {
    fun isExpired(currentTime: Long): Boolean {
        return currentTime - startTime > (durationSeconds * 1000)
    }
}

data class PoisonConfig(
    val mildToxin: PoisonTypeConfig = PoisonTypeConfig(
        durationSeconds = 10f,
        drainPerTick = 5.0,
        tickIntervalMs = 500
    ),
    val slowPoison: PoisonTypeConfig = PoisonTypeConfig(
        durationSeconds = 60f,
        drainPerTick = 1.0,
        tickIntervalMs = 1000
    ),
    val purge: PoisonTypeConfig = PoisonTypeConfig(
        durationSeconds = 30f,
        drainPerTick = 8.0,
        tickIntervalMs = 500
    )
)

data class PoisonTypeConfig(
    val durationSeconds: Float,
    val drainPerTick: Double,
    val tickIntervalMs: Long
)
```

---

## Native Effect Integration

### NativeDebuffDetector

```kotlin
/**
 * Detects Hytale's native debuff effects.
 */
class NativeDebuffDetector {
    
    private val debuffPatterns = mapOf(
        "Poison" to DebuffType.POISON,
        "Poison_T1" to DebuffType.POISON,
        "Poison_T2" to DebuffType.POISON,
        "Poison_T3" to DebuffType.POISON,
        "Burn" to DebuffType.BURN,
        "Lava_Burn" to DebuffType.BURN,
        "Flame_Staff_Burn" to DebuffType.BURN,
        "Stun" to DebuffType.STUN,
        "Bomb_Explode_Stun" to DebuffType.STUN,
        "Freeze" to DebuffType.FREEZE,
        "Root" to DebuffType.ROOT,
        "Slow" to DebuffType.SLOW
    )
    
    fun getActiveDebuffs(session: PlayerSession): Map<DebuffType, ActiveDebuffDetails> {
        val debuffs = mutableMapOf<DebuffType, ActiveDebuffDetails>()
        
        session.execute {
            val effectController = session.store.getComponent(
                session.entityRef,
                EffectControllerComponent.getComponentType()
            ) ?: return@execute
            
            effectController.allActiveEntityEffects.forEach { effect ->
                val effectId = getEffectId(effect)
                val debuffType = matchDebuffType(effectId) ?: return@forEach
                
                debuffs[debuffType] = ActiveDebuffDetails(
                    effectId = effectId,
                    type = debuffType,
                    tier = extractTier(effectId)
                )
            }
        }
        
        return debuffs
    }
    
    fun hasNativePoisonDebuff(session: PlayerSession): Boolean {
        return getActiveDebuffs(session).containsKey(DebuffType.POISON)
    }
    
    private fun matchDebuffType(effectId: String): DebuffType? {
        return debuffPatterns.entries.find { effectId.startsWith(it.key) }?.value
    }
    
    private fun extractTier(effectId: String): Int {
        return when {
            effectId.contains("_T3") -> 3
            effectId.contains("_T2") -> 2
            effectId.contains("_T1") -> 1
            else -> 2 // Default tier
        }
    }
    
    private fun getEffectId(effect: Any): String {
        return try {
            val typeMethod = effect::class.java.getMethod("getType")
            val type = typeMethod.invoke(effect)
            val idMethod = type::class.java.getMethod("getId")
            idMethod.invoke(type) as String
        } catch (e: Exception) {
            "unknown"
        }
    }
}

enum class DebuffType {
    POISON, BURN, STUN, FREEZE, ROOT, SLOW
}

data class ActiveDebuffDetails(
    val effectId: String,
    val type: DebuffType,
    val tier: Int
)
```

### DebuffEffectsSystem

```kotlin
/**
 * Integrates native Hytale debuffs with metabolism system.
 */
class DebuffEffectsSystem(
    private val config: NativeDebuffsConfig,
    private val nativeDebuffDetector: NativeDebuffDetector
) {
    // Track last tick time per player per debuff type
    private val lastTickTime = ConcurrentHashMap<UUID, MutableMap<DebuffType, Long>>()
    
    fun processDebuffEffects(
        metabolismService: MetabolismService,
        worldId: UUID
    ) {
        val currentTime = System.currentTimeMillis()
        
        CoreModule.players.getAllSessions().forEach { session ->
            val activeDebuffs = nativeDebuffDetector.getActiveDebuffs(session)
            
            activeDebuffs.forEach { (debuffType, details) ->
                val playerTickTimes = lastTickTime.getOrPut(session.playerId) { 
                    ConcurrentHashMap() 
                }
                
                val lastTick = playerTickTimes[debuffType] ?: 0L
                val tickInterval = config.tickIntervalMs
                
                if (currentTime - lastTick >= tickInterval) {
                    val tierMultiplier = getTierMultiplier(details.tier)
                    
                    when (debuffType) {
                        DebuffType.POISON -> {
                            metabolismService.restoreHunger(
                                session.playerId, 
                                worldId, 
                                -config.poisonDrainPerTick * tierMultiplier
                            )
                            metabolismService.restoreThirst(
                                session.playerId, 
                                worldId, 
                                -config.poisonDrainPerTick * tierMultiplier
                            )
                        }
                        DebuffType.BURN -> {
                            metabolismService.restoreThirst(
                                session.playerId, 
                                worldId, 
                                -config.burnThirstDrainPerTick * tierMultiplier
                            )
                        }
                        else -> { /* Other debuffs don't drain metabolism */ }
                    }
                    
                    playerTickTimes[debuffType] = currentTime
                }
            }
        }
    }
    
    private fun getTierMultiplier(tier: Int): Double {
        return when (tier) {
            1 -> 0.75
            2 -> 1.0
            3 -> 1.5
            else -> 1.0
        }
    }
    
    fun onPlayerDisconnect(playerId: UUID) {
        lastTickTime.remove(playerId)
    }
}

data class NativeDebuffsConfig(
    val tickIntervalMs: Long = 1000,
    val poisonDrainPerTick: Double = 2.0,
    val burnThirstDrainPerTick: Double = 3.0
)
```

---

## Announcer Module

**Status:** ✅ Complete (v1.3.0)  
**Purpose:** Server messaging system with MOTD, welcome messages, and recurring announcements

The Announcer module provides a comprehensive server communication system for engaging players with customizable messages.

### Architecture

```kotlin
/**
 * Announcer module lifecycle and service registration.
 */
class AnnouncerModule : AbstractModule(
    id = "announcer",
    name = "Announcer",
    version = "1.3.0",
    dependencies = emptyList()
) {
    private lateinit var service: AnnouncerService
    private lateinit var scheduler: AnnouncerScheduler
    
    override suspend fun setup(context: ModuleContext) {
        // Load config
        val config = CoreModule.config.loadWithMigration(
            "announcer",
            AnnouncerConfig.DEFAULT,
            AnnouncerConfig.CURRENT_VERSION
        )
        
        // Initialize services
        service = AnnouncerService(config, context.logger)
        scheduler = AnnouncerScheduler(service, config, context.logger)
        
        // Register services
        CoreModule.services.register(service)
        CoreModule.services.register(scheduler)
    }
    
    override suspend fun start() {
        // Register event handlers
        eventRegistry.register(PlayerReadyEvent::class.java) { event ->
            handlePlayerReady(event)
        }
        
        // Start recurring announcements
        scheduler.start()
    }
    
    override suspend fun shutdown() {
        scheduler.stop()
    }
}
```

### Core Components

#### AnnouncerService

```kotlin
/**
 * Core service for sending announcements to players.
 * Handles MOTD, welcome messages, and custom broadcasts.
 */
class AnnouncerService(
    private val config: AnnouncerConfig,
    private val logger: HytaleLogger
) {
    // In-memory join tracking (no database persistence needed)
    private val joinTracker = ConcurrentHashMap<UUID, Instant>()
    
    /**
     * Send MOTD (Message of the Day) to player on join.
     */
    fun sendMotd(playerRef: PlayerRef, world: World) {
        if (!config.motd.enabled) return
        
        val session = CoreModule.players.getSession(playerRef.uuid) ?: return
        
        world.execute {
            val player = session.store.getComponent(session.entityRef, Player.getComponentType())
            val message = resolvePlaceholders(config.motd.message, playerRef, world)
            playerRef.sendMessage(Message.raw(message))
        }
    }
    
    /**
     * Send welcome message (first-time vs returning player).
     */
    fun sendWelcome(playerRef: PlayerRef, world: World) {
        if (!config.welcome.enabled) return
        
        val playerId = playerRef.uuid
        val lastSeen = joinTracker[playerId]
        val isFirstJoin = lastSeen == null
        
        val message = if (isFirstJoin) {
            resolvePlaceholders(config.welcome.firstJoin, playerRef, world)
        } else {
            val joinCount = joinTracker.keys.count { it == playerId }
            resolvePlaceholders(config.welcome.returning, playerRef, world)
                .replace("{join_count}", joinCount.toString())
        }
        
        // Update last seen
        joinTracker[playerId] = Instant.now()
        
        world.execute {
            playerRef.sendMessage(Message.raw(message))
        }
    }
    
    /**
     * Broadcast message to all online players.
     */
    fun broadcast(message: String) {
        val resolved = MessageFormatter.format(message)
        CoreModule.players.getAllSessions().forEach { session ->
            session.playerRef.sendMessage(Message.raw(resolved))
        }
    }
    
    /**
     * Resolve placeholders in message string.
     */
    private fun resolvePlaceholders(template: String, playerRef: PlayerRef, world: World): String {
        return MessageFormatter.format(template)
            .replace("{player_name}", playerRef.name ?: "Player")
            .replace("{world_name}", world.name)
            .replace("{online_count}", CoreModule.players.getAllSessions().size.toString())
    }
}
```

#### AnnouncerScheduler

```kotlin
/**
 * Coroutine-based scheduler for recurring announcements.
 * Supports multiple announcements running simultaneously.
 */
class AnnouncerScheduler(
    private val service: AnnouncerService,
    private val config: AnnouncerConfig,
    private val logger: HytaleLogger
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val jobs = ConcurrentHashMap<String, Job>()
    
    fun start() {
        if (!config.recurring.enabled) return
        
        config.recurring.announcements.forEach { announcement ->
            if (announcement.enabled) {
                startAnnouncement(announcement)
            }
        }
    }
    
    private fun startAnnouncement(announcement: RecurringAnnouncement) {
        val job = scope.launch {
            val intervalMs = parseDuration(announcement.interval)
            var messageIndex = 0
            
            while (isActive) {
                delay(intervalMs)
                
                // Send next message in sequence
                val message = announcement.messages[messageIndex]
                service.broadcast(message)
                
                // Cycle to next message
                messageIndex = (messageIndex + 1) % announcement.messages.size
            }
        }
        
        jobs[announcement.id] = job
    }
    
    fun stop() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        scope.cancel()
    }
    
    private fun parseDuration(duration: String): Long {
        val number = duration.filter { it.isDigit() }.toLongOrNull() ?: 60
        val unit = duration.filter { it.isLetter() }.lowercase()
        
        return when (unit) {
            "s" -> number * 1000
            "m" -> number * 60 * 1000
            "h" -> number * 60 * 60 * 1000
            else -> number * 60 * 1000 // Default to minutes
        }
    }
}
```

### Configuration

```kotlin
/**
 * Announcer module configuration.
 */
data class AnnouncerConfig(
    override val configVersion: Int = CURRENT_VERSION,
    val enabled: Boolean = true,
    val motd: MotdConfig = MotdConfig(),
    val welcome: WelcomeConfig = WelcomeConfig(),
    val recurring: RecurringConfig = RecurringConfig()
) : VersionedConfig {
    companion object {
        const val CURRENT_VERSION = 1
        const val MODULE_ID = "announcer"
        
        val DEFAULT = AnnouncerConfig()
    }
}

data class MotdConfig(
    val enabled: Boolean = true,
    val message: String = "&6Welcome to {server_name}!"
)

data class WelcomeConfig(
    val enabled: Boolean = true,
    val firstJoin: String = "&aWelcome for the first time, {player_name}!",
    val returning: String = "&7Welcome back, {player_name}! (Visit #{join_count})",
    val minAbsenceDuration: String = "1h"
)

data class RecurringConfig(
    val enabled: Boolean = true,
    val announcements: List<RecurringAnnouncement> = listOf(
        RecurringAnnouncement(
            id = "tips",
            enabled = true,
            interval = "5m",
            messages = listOf(
                "&6[Tip] Stay hydrated! Thirst depletes faster than hunger.",
                "&6[Tip] Sprinting uses 3x more energy."
            ),
            target = "all"
        )
    )
)

data class RecurringAnnouncement(
    val id: String,
    val enabled: Boolean,
    val interval: String,
    val messages: List<String>,
    val target: String
)
```

### Key Features

1. **MOTD** - Immediate welcome messages on join
2. **Welcome Messages** - Different messages for first-time vs returning players
3. **Recurring Announcements** - Automated server tips/info with configurable intervals
4. **Placeholder Support** - `{player_name}`, `{server_name}`, `{join_count}`, etc.
5. **Color Code Support** - Minecraft-style formatting (`&a`, `&6`, etc.)
6. **Hot-Reload** - `/ll reload announcer` updates config without restart
7. **Coroutine-Based** - Non-blocking scheduling with graceful shutdown

### Performance

- **MOTD send:** <1ms per player
- **Welcome message:** <5ms per player (includes join tracking lookup)
- **Recurring announcement:** <50ms total (broadcast to all players)
- **Config reload:** <100ms (restart scheduler)
- **Memory impact:** <1MB for join tracking

---

## Professions Module

**Status:** ✅ Complete (v1.4.0)  
**Purpose:** XP-based profession system with 5 professions, 15 passive abilities, and global progression

The Professions module provides deep character progression across five distinct professions, each with unique abilities unlocked at milestone levels.

### Architecture

```kotlin
/**
 * Professions module with XP system and passive abilities.
 */
class ProfessionsModule : AbstractModule(
    id = "professions",
    name = "Professions",
    version = "1.4.0",
    dependencies = listOf("metabolism") // For Tier 2/3 abilities
) {
    private lateinit var service: ProfessionsService
    private lateinit var repository: ProfessionsRepository
    private lateinit var abilityRegistry: AbilityRegistry
    
    override suspend fun setup(context: ModuleContext) {
        // Load config
        val config = CoreModule.config.loadWithMigration(
            "professions",
            ProfessionsConfig.DEFAULT,
            ProfessionsConfig.CURRENT_VERSION
        )
        
        // Initialize global persistence
        val globalPersistence = CoreModule.services.require<GlobalPersistenceService>()
        repository = ProfessionsRepository(globalPersistence)
        repository.initialize()
        
        // Initialize services
        service = ProfessionsService(repository, config, context.logger)
        abilityRegistry = AbilityRegistry(service, config)
        
        // Register services
        CoreModule.services.register(service)
        CoreModule.services.register(repository)
        CoreModule.services.register(abilityRegistry)
        
        // Register XP listeners
        registerXpListeners(context)
    }
    
    private fun registerXpListeners(context: ModuleContext) {
        // Combat XP
        context.eventRegistry.register(KillFeedEvent.KillerMessage::class.java) { event ->
            handleCombatXp(event)
        }
        
        // Mining XP
        context.eventRegistry.register(BreakBlockEvent::class.java) { event ->
            handleMiningXp(event)
        }
        
        // Logging XP
        context.eventRegistry.register(BreakBlockEvent::class.java) { event ->
            handleLoggingXp(event)
        }
        
        // Building XP
        context.eventRegistry.register(PlaceBlockEvent::class.java) { event ->
            handleBuildingXp(event)
        }
        
        // Gathering XP
        context.eventRegistry.register(InteractivelyPickupItemEvent::class.java) { event ->
            handleGatheringXp(event)
        }
    }
}
```

### Core Components

#### ProfessionsService

```kotlin
/**
 * Core service managing profession XP, levels, and ability unlocks.
 * Uses AtomicLong for thread-safe XP updates.
 */
class ProfessionsService(
    private val repository: ProfessionsRepository,
    private val config: ProfessionsConfig,
    private val logger: HytaleLogger
) {
    // In-memory state per player (global, not per-world)
    private val playerStates = ConcurrentHashMap<UUID, PlayerProfessionState>()
    
    // Precomputed XP table for O(1) level lookups
    private val xpTable: XpTable = XpCalculator.precompute(
        baseXp = config.xpCurve.baseXp,
        multiplier = config.xpCurve.multiplier,
        maxLevel = config.xpCurve.maxLevel
    )
    
    /**
     * Initialize player with defaults (instant), then load from DB async.
     */
    suspend fun initializePlayer(playerId: UUID) {
        // Instant initialization with defaults
        val state = PlayerProfessionState(playerId)
        playerStates[playerId] = state
        
        // Async DB load
        GlobalScope.launch(Dispatchers.IO) {
            val stats = repository.getStats(playerId)
            if (stats != null) {
                // Update state with DB values
                Profession.values().forEach { profession ->
                    val professionStats = stats[profession]
                    if (professionStats != null) {
                        state.setXp(profession, professionStats.xp)
                    }
                }
            }
        }
    }
    
    /**
     * Award XP to a profession (thread-safe with AtomicLong).
     */
    fun awardXp(playerId: UUID, profession: Profession, amount: Long) {
        val state = playerStates[playerId] ?: return
        
        // Apply Tier 1 XP boost if unlocked
        val multiplier = if (hasAbility(playerId, profession, AbilityTier.TIER_1)) {
            1.15 // +15% XP gain
        } else {
            1.0
        }
        
        val adjustedAmount = (amount * multiplier).toLong()
        
        // Thread-safe XP update
        val xpCounter = state.getXpCounter(profession)
        val oldXp = xpCounter.get()
        val oldLevel = xpTable.calculateLevel(oldXp)
        
        xpCounter.addAndGet(adjustedAmount)
        
        val newXp = xpCounter.get()
        val newLevel = xpTable.calculateLevel(newXp)
        
        // Detect level-up (with race condition protection)
        if (newLevel > oldLevel) {
            val lastProcessedLevel = state.getLastProcessedLevel(profession)
            if (lastProcessedLevel.compareAndSet(oldLevel, newLevel)) {
                // We won the race - trigger level-up
                onLevelUp(playerId, profession, newLevel)
            }
        }
    }
    
    /**
     * Handle level-up and ability unlocks.
     */
    private fun onLevelUp(playerId: UUID, profession: Profession, newLevel: Int) {
        logger.info("$playerId leveled up $profession to $newLevel")
        
        // Check for ability unlocks
        val unlockedAbilities = abilityRegistry.getAbilitiesForLevel(profession, newLevel)
        unlockedAbilities.forEach { ability ->
            ability.onUnlock(playerId)
            logger.info("$playerId unlocked ${ability.name}")
        }
        
        // Send level-up notification
        // TODO: Title API
    }
    
    /**
     * Save player stats to global DB.
     */
    suspend fun savePlayer(playerId: UUID) {
        val state = playerStates[playerId] ?: return
        
        val stats = Profession.values().associate { profession ->
            val xp = state.getXpCounter(profession).get()
            val level = xpTable.calculateLevel(xp)
            profession to ProfessionStats(playerId, profession, xp, level)
        }
        
        repository.saveAll(playerId, stats)
    }
}
```

#### XP Calculator

```kotlin
/**
 * Precomputed XP table for O(1) level calculations.
 */
class XpCalculator {
    companion object {
        fun precompute(baseXp: Long, multiplier: Double, maxLevel: Int): XpTable {
            val table = LongArray(maxLevel + 1)
            table[0] = 0L
            
            for (level in 1..maxLevel) {
                // Formula: baseXp * (multiplier ^ (level - 1))
                val xpForLevel = (baseXp * multiplier.pow(level - 1)).toLong()
                table[level] = table[level - 1] + xpForLevel
            }
            
            return XpTable(table)
        }
    }
}

/**
 * XP table with O(1) level lookups.
 */
class XpTable(private val table: LongArray) {
    fun calculateLevel(xp: Long): Int {
        return table.indexOfLast { it <= xp }.coerceAtLeast(0)
    }
    
    fun xpForNextLevel(currentLevel: Int): Long {
        if (currentLevel >= table.size - 1) return Long.MAX_VALUE
        return table[currentLevel + 1]
    }
}
```

#### Ability System

```kotlin
/**
 * Base interface for profession abilities.
 */
sealed interface Ability {
    val profession: Profession
    val tier: AbilityTier
    val name: String
    val description: String
    val unlockLevel: Int
    
    fun onUnlock(playerId: UUID)
}

/**
 * Ability tiers.
 */
enum class AbilityTier(val unlockLevel: Int) {
    TIER_1(15),   // +15% XP gain
    TIER_2(45),   // Permanent max stat increases
    TIER_3(100)   // Powerful passive abilities
}

/**
 * Example Tier 1 ability: XP boost.
 */
class WarriorAbility : Ability {
    override val profession = Profession.COMBAT
    override val tier = AbilityTier.TIER_1
    override val name = "Warrior"
    override val description = "+15% Combat XP gain"
    override val unlockLevel = 15
    
    override fun onUnlock(playerId: UUID) {
        // Passive - handled in ProfessionsService.awardXp()
    }
}

/**
 * Example Tier 2 ability: Max stat increase.
 */
class IronStomachAbility(
    private val metabolismService: MetabolismService
) : Ability {
    override val profession = Profession.COMBAT
    override val tier = AbilityTier.TIER_2
    override val name = "Iron Stomach"
    override val description = "Permanently +15 max hunger capacity"
    override val unlockLevel = 45
    
    override fun onUnlock(playerId: UUID) {
        metabolismService.increaseMaxHunger(playerId, 15.0)
    }
}

/**
 * Example Tier 3 ability: Depletion rate reduction.
 */
class SurvivalistAbility(
    private val metabolismService: MetabolismService
) : Ability {
    override val profession = Profession.COMBAT
    override val tier = AbilityTier.TIER_3
    override val name = "Survivalist"
    override val description = "-15% metabolism depletion rate"
    override val unlockLevel = 100
    
    override fun onUnlock(playerId: UUID) {
        metabolismService.addDepletionModifier(playerId, "survivalist", 0.85)
    }
}
```

### Configuration

```kotlin
/**
 * Professions module configuration.
 */
data class ProfessionsConfig(
    override val configVersion: Int = CURRENT_VERSION,
    val enabled: Boolean = true,
    val xpCurve: XpCurveConfig = XpCurveConfig(),
    val xpRewards: XpRewardsConfig = XpRewardsConfig(),
    val deathPenalty: DeathPenaltyConfig = DeathPenaltyConfig(),
    val abilities: AbilitiesConfig = AbilitiesConfig()
) : VersionedConfig {
    companion object {
        const val CURRENT_VERSION = 1
        const val MODULE_ID = "professions"
        
        val DEFAULT = ProfessionsConfig()
    }
}

data class XpCurveConfig(
    val baseXp: Long = 100,
    val multiplier: Double = 1.15,
    val maxLevel: Int = 100
)

data class XpRewardsConfig(
    val combat: CombatXpConfig = CombatXpConfig(),
    val mining: MiningXpConfig = MiningXpConfig(),
    val logging: LoggingXpConfig = LoggingXpConfig(),
    val building: BuildingXpConfig = BuildingXpConfig(),
    val gathering: GatheringXpConfig = GatheringXpConfig()
)

data class DeathPenaltyConfig(
    val enabled: Boolean = true,
    val basePercent: Double = 0.10,        // 10% base
    val progressivePercent: Double = 0.03, // +3% per death
    val maxPercent: Double = 0.35,         // 35% cap
    val adaptiveMercyThreshold: Int = 5,   // Mercy after 5 deaths
    val adaptiveMercyReduction: Double = 0.50 // 50% reduction
)
```

### Key Features

1. **5 Professions** - Combat, Mining, Logging, Building, Gathering
2. **100 Levels per Profession** - Exponential XP curve
3. **Global Progression** - Stats follow player across worlds
4. **15 Passive Abilities** - 3 tiers × 5 professions
5. **Thread-Safe XP** - AtomicLong counters with compareAndSet
6. **Precomputed XP Table** - O(1) level calculations
7. **Death Penalty System** - Progressive penalty with adaptive mercy
8. **Performance** - <1ms XP event processing, <100ms player join

### Performance

- **XP Event Processing:** <1ms per event
- **Level Calculation:** <1μs (precomputed table lookup)
- **Ability Check:** <0.5ms (registry lookup)
- **Player Join:** <100ms (async stat loading)
- **Player Disconnect:** <200ms (async stat saving)

---

## Modded Consumables Support

**Status:** 📋 Planned (v1.5.0)  
**Purpose:** Allow server admins to configure custom modded food/drink/potion items with tier-based restoration

The Modded Consumables system extends the metabolism food consumption to support items from other mods, enabling seamless integration with custom mod packs.

### Architecture

```kotlin
/**
 * Modded consumables support extending FoodConsumptionProcessor.
 */
class ModdedConsumablesRegistry(
    private val config: ModdedConsumablesConfig,
    private val logger: HytaleLogger
) {
    private val entries = ConcurrentHashMap<String, ModdedConsumableEntry>()
    private val validator = ModdedItemValidator(logger)
    private val tierDetector = ItemTierDetector()
    
    fun initialize() {
        if (!config.enabled) return
        
        // Load and validate all entries
        config.foods.forEach { entry ->
            if (validator.validateItem(entry.effectId, config.warnIfMissing)) {
                entries[entry.effectId] = entry
            }
        }
        
        config.drinks.forEach { entry ->
            if (validator.validateItem(entry.effectId, config.warnIfMissing)) {
                entries[entry.effectId] = entry
            }
        }
        
        config.potions.forEach { entry ->
            if (validator.validateItem(entry.effectId, config.warnIfMissing)) {
                entries[entry.effectId] = entry
            }
        }
        
        logger.info("Loaded ${entries.size} modded consumables")
    }
    
    /**
     * Lookup modded consumable by effect ID.
     */
    fun getEntry(effectId: String): ModdedConsumableEntry? {
        return entries[effectId]
    }
}
```

### Configuration

```kotlin
/**
 * Modded consumables configuration (v5).
 */
data class ModdedConsumablesConfig(
    val enabled: Boolean = true,
    val warnIfMissing: Boolean = true,
    val foods: List<ModdedConsumableEntry> = emptyList(),
    val drinks: List<ModdedConsumableEntry> = emptyList(),
    val potions: List<ModdedConsumableEntry> = emptyList()
)

/**
 * Individual modded consumable entry.
 */
data class ModdedConsumableEntry(
    val effectId: String,           // e.g., "FarmingMod:CookedChicken"
    val category: String,           // MEAT, FRUIT_VEGGIE, WATER, etc.
    val tier: Int?,                 // null = auto-detect, or 1/2/3
    val customMultipliers: CustomMultipliers? = null
)

data class CustomMultipliers(
    val hunger: Double? = null,
    val thirst: Double? = null,
    val energy: Double? = null
)
```

### Integration with Food Detection

```kotlin
/**
 * Extended FoodEffectDetector with modded consumables support.
 */
class FoodEffectDetector(
    private val config: MetabolismConfig,
    private val moddedRegistry: ModdedConsumablesRegistry?,
    private val logger: HytaleLogger
) {
    fun detectFood(effectId: String): FoodInfo? {
        // Check modded registry first
        moddedRegistry?.getEntry(effectId)?.let { entry ->
            val tier = entry.tier ?: tierDetector.detectTier(effectId)
            val foodType = mapCategoryToFoodType(entry.category)
            
            return FoodInfo(
                effectId = effectId,
                tier = tier,
                type = foodType,
                customMultipliers = entry.customMultipliers
            )
        }
        
        // Fall back to vanilla detection
        return detectVanillaFood(effectId)
    }
    
    private fun mapCategoryToFoodType(category: String): FoodType {
        return when (category.uppercase()) {
            "MEAT" -> FoodType.MEAT
            "FRUIT_VEGGIE" -> FoodType.FRUIT_VEGGIE
            "BREAD" -> FoodType.BREAD
            "WATER" -> FoodType.WATER
            "STAMINA_POTION" -> FoodType.STAMINA_POTION
            "HEALTH_POTION" -> FoodType.HEALTH_POTION
            "MANA_POTION" -> FoodType.MANA_POTION
            else -> FoodType.GENERIC
        }
    }
}
```

### Tier Detection

```kotlin
/**
 * Automatic tier detection from item metadata.
 */
class ItemTierDetector {
    private val tierPatterns = listOf(
        Regex("T1|Tier1|tier_1", RegexOption.IGNORE_CASE) to 1,
        Regex("T2|Tier2|tier_2", RegexOption.IGNORE_CASE) to 2,
        Regex("T3|Tier3|tier_3", RegexOption.IGNORE_CASE) to 3
    )
    
    fun detectTier(effectId: String): Int {
        // Check effect ID for tier indicators
        tierPatterns.forEach { (pattern, tier) ->
            if (pattern.containsMatchIn(effectId)) {
                return tier
            }
        }
        
        // TODO: Check item metadata/tags
        
        // Default to Tier 1
        return 1
    }
}
```

### Item Validation

```kotlin
/**
 * Validate that modded items exist in Hytale registry.
 */
class ModdedItemValidator(private val logger: HytaleLogger) {
    private val validationCache = ConcurrentHashMap<String, Boolean>()
    
    fun validateItem(effectId: String, warn: Boolean): Boolean {
        return validationCache.getOrPut(effectId) {
            val exists = checkItemExists(effectId)
            
            if (!exists && warn) {
                logger.warning("Modded consumable not found: $effectId (mod may not be loaded)")
            }
            
            exists
        }
    }
    
    private fun checkItemExists(effectId: String): Boolean {
        // TODO: Query Hytale item registry
        // For now, assume all items exist
        return true
    }
}
```

### Example Configuration

```yaml
# metabolism.yml (v5)
configVersion: 5

# ... existing config ...

# Modded Consumables Support
moddedConsumables:
  enabled: true
  warnIfMissing: true
  
  # Food items (high hunger restoration)
  foods:
    - effectId: "FarmingMod:CookedChicken"
      category: "MEAT"
      tier: null  # Auto-detect
      customMultipliers:
        hunger: 1.5
        thirst: 0.8
        energy: 1.2
    
    - effectId: "FarmingMod:Apple"
      category: "FRUIT_VEGGIE"
      tier: 1  # Force Tier 1
  
  # Drinks (high thirst restoration)
  drinks:
    - effectId: "MagicMod:EnchantedWater"
      category: "WATER"
      tier: 2
    
    - effectId: "MagicMod:EnergyDrink"
      category: "STAMINA_POTION"
      tier: null
  
  # Potions (primarily thirst + effects)
  potions:
    - effectId: "AlchemyMod:GreaterHealingPotion"
      category: "HEALTH_POTION"
      tier: 3
    
    - effectId: "AlchemyMod:ManaPotion"
      category: "MANA_POTION"
      tier: 2
      customMultipliers:
        thirst: 2.5
        energy: 0.5
```

### Key Features

1. **Config-Based Registry** - No code changes needed for new mods
2. **Automatic Tier Detection** - Parse item IDs for tier indicators
3. **Item Validation** - Warn if modded items don't exist
4. **Custom Multipliers** - Override default restoration values per item
5. **Category Classification** - Map items to FoodType system
6. **Graceful Degradation** - Continue if mod not loaded
7. **Performance** - Cached validation, <1ms registry lookups

### Performance Targets

- **Config Load:** <50ms for 100 modded entries
- **Item Validation:** <1ms per item (cached)
- **Tier Detection:** <0.5ms (pattern matching)
- **Registry Lookup:** <0.1ms (ConcurrentHashMap)
- **Food Consumption:** No performance regression

### Migration Path

Existing configs auto-upgrade to v5 with empty `moddedConsumables` section. No action required from users unless they want to add modded items.

---

## Appendix: Key Classes Reference

| Class | Package | Purpose |
|-------|---------|---------|
| `LivingLandsPlugin` | `com.livinglands` | Plugin entry point |
| `CoreModule` | `com.livinglands.core` | Central hub, service registry |
| `ServiceRegistry` | `com.livinglands.core` | Type-safe service locator |
| `WorldRegistry` | `com.livinglands.core.world` | World lifecycle management |
| `WorldContext` | `com.livinglands.core.world` | Per-world data context |
| `MultiHudManager` | `com.livinglands.core.hud` | Multi-HUD support (MHUD) |
| `CompositeHud` | `com.livinglands.core.hud` | Internal HUD compositor |
| `PersistenceService` | `com.livinglands.core.persistence` | SQLite database access |
| `PlayerDataRepository` | `com.livinglands.core.persistence` | Core player data |
| `ConfigManager` | `com.livinglands.core.config` | YAML config management |
| `PlayerRegistry` | `com.livinglands.core.player` | Player session tracking |
| `PlayerSession` | `com.livinglands.core.player` | ECS references for player |
| `SpeedManager` | `com.livinglands.core.util` | Centralized speed control |
| `Module` | `com.livinglands.api` | Module interface |
| `AbstractModule` | `com.livinglands.api` | Module base class |
| `ModuleContext` | `com.livinglands.api` | Setup context for modules |
| `MetabolismModule` | `com.livinglands.modules.metabolism` | Metabolism module |
| `MetabolismService` | `com.livinglands.modules.metabolism` | Core metabolism logic |
| `MetabolismRepository` | `com.livinglands.modules.metabolism.data` | Metabolism persistence |
| `DebuffsSystem` | `com.livinglands.modules.metabolism.debuff` | Low-stat penalties |
| `BuffsSystem` | `com.livinglands.modules.metabolism.buff` | High-stat bonuses |
| `HysteresisController` | `com.livinglands.modules.metabolism.util` | State flickering prevention |
| `FoodEffectDetector` | `com.livinglands.modules.metabolism.consumable` | Food detection |
| `PoisonEffectsSystem` | `com.livinglands.modules.metabolism.poison` | Consumable poison |
| `NativeDebuffDetector` | `com.livinglands.modules.metabolism.native` | Hytale debuff detection |
| `DebuffEffectsSystem` | `com.livinglands.modules.metabolism.native` | Native debuff integration |
| `AnnouncerModule` | `com.livinglands.modules.announcer` | Announcer module |
| `AnnouncerService` | `com.livinglands.modules.announcer` | Message sending logic |
| `AnnouncerScheduler` | `com.livinglands.modules.announcer` | Recurring announcements |
| `ProfessionsModule` | `com.livinglands.modules.professions` | Professions module |
| `ProfessionsService` | `com.livinglands.modules.professions` | XP and level management |
| `ProfessionsRepository` | `com.livinglands.modules.professions.data` | Professions persistence |
| `XpCalculator` | `com.livinglands.modules.professions` | Precomputed XP tables |
| `AbilityRegistry` | `com.livinglands.modules.professions.abilities` | Ability management |
| `ModdedConsumablesRegistry` | `com.livinglands.modules.metabolism.modded` | Modded items registry |
| `ModdedItemValidator` | `com.livinglands.modules.metabolism.modded` | Item validation |
| `ItemTierDetector` | `com.livinglands.modules.metabolism.modded` | Tier detection |
| `ClaimsModule` | `com.livinglands.modules.claims` | Claims module (stub) |

---

## Version History

| Version | Changes |
|---------|---------|
| **1.4.0** | Professions Module complete with all 15 abilities functional (Tier 1/2/3), admin commands, death penalty system, global persistence integration |
| **1.3.1** | HUD performance hotfix: 90% faster XP updates, only profession panels refresh on XP gain |
| **1.3.0** | Announcer Module (MOTD, welcome messages, recurring announcements), HUD crash fix, panel toggle fix, MessageFormatter color codes |
| **1.2.3** | Bug fixes: food consumption, thread safety improvements, memory leak prevention, config ambiguity warnings |
| **1.2.0** | Professions Module foundation: XP system, 5 professions, Tier 1 abilities, global persistence |
| **1.1.0** | Global persistence architecture: dual-database system (global + per-world), 98.75% faster player joins, 99.9% faster world switching |
| **1.0.0-beta** | Initial release with core architecture: Kotlin-based, CoreModule as service locator, per-world SQLite persistence, MultiHUD support (MHUD pattern), YAML config hot-reload, WorldRegistry for world awareness, Metabolism module (hunger, thirst, energy) |
