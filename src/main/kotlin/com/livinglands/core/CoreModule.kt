package com.livinglands.core

import com.hypixel.hytale.logger.HytaleLogger
import com.livinglands.LivingLandsReloadedPlugin
import com.livinglands.api.Module
import com.livinglands.api.ModuleContext
import com.livinglands.api.ModuleState
import com.livinglands.core.config.ConfigManager
import com.livinglands.core.config.CoreConfig
import com.livinglands.core.hud.MultiHudManager
import com.livinglands.core.logging.LogLevel
import com.livinglands.core.logging.LoggingManager
import com.livinglands.core.persistence.GlobalPersistenceService
import com.livinglands.core.persistence.GlobalPlayerDataRepository
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Central module that manages all Living Lands functionality.
 * Provides service registry, world management, and module lifecycle.
 */
object CoreModule {
    
    // Service registry for cross-module communication
    @Volatile
    lateinit var services: ServiceRegistry
        private set
    
    // Configuration manager
    @Volatile
    lateinit var config: ConfigManager
        private set
    
    // Core configuration (cached)
    @Volatile
    lateinit var coreConfig: CoreConfig
        private set
    
    // World management - tracks worlds by UUID
    @Volatile
    lateinit var worlds: WorldRegistry
        private set
    
    // Player tracking across all worlds
    @Volatile
    lateinit var players: PlayerRegistry
        private set
    
    // Multi-HUD support (MHUD pattern)
    @Volatile
    lateinit var hudManager: MultiHudManager
        private set
    
    // Global persistence service for server-wide data (e.g., metabolism stats)
    @Volatile
    lateinit var globalPersistence: GlobalPersistenceService
        private set
    
    // Main command for subcommand registration
    @Volatile
    lateinit var mainCommand: com.livinglands.core.commands.LLCommand
    
    // Logger reference
    @Volatile
    lateinit var logger: HytaleLogger
        private set
    
    // Plugin reference
    @Volatile
    lateinit var plugin: LivingLandsReloadedPlugin
        private set
    
    // Data directory for persistence
    @Volatile
    lateinit var dataDir: File
        private set
    
    // Config directory for YAML files
    @Volatile
    lateinit var configDir: File
        private set
    
    // Initialization state
    @Volatile
    private var initialized = false
    
    // ============ Module Lifecycle Management ============
    
    // Registered modules by ID
    private val modules = ConcurrentHashMap<String, Module>()
    
    // Module load order (after dependency resolution)
    private val moduleOrder = mutableListOf<String>()
    
    /**
     * Initialize the core module with plugin reference.
     * Called during plugin setup phase.
     */
    fun initialize(plugin: LivingLandsReloadedPlugin) {
        if (initialized) {
            plugin.logger.atWarning().log("CoreModule already initialized, skipping")
            return
        }
        
        this.plugin = plugin
        this.logger = plugin.logger
        
        // Setup data directory structure
        this.dataDir = plugin.dataDirectory.resolve("data").toFile()
        if (!dataDir.exists()) {
            dataDir.mkdirs()
            logger.atFine().log("Created data directory: ${dataDir.absolutePath}")
        }
        
        // Setup config directory structure
        this.configDir = plugin.dataDirectory.resolve("config").toFile()
        if (!configDir.exists()) {
            configDir.mkdirs()
            logger.atFine().log("Created config directory: ${configDir.absolutePath}")
        }
        
        // Initialize configuration manager first
        config = ConfigManager(configDir.toPath(), logger)
        
        // Register CoreConfig migrations
        registerCoreConfigMigrations()
        
        // Load core configuration with migration support
        coreConfig = config.loadWithMigration(
            CoreConfig.MODULE_ID,
            CoreConfig(),
            CoreConfig.CURRENT_VERSION
        )
        
        // Register callback to update cached coreConfig on reload
        config.onReload(CoreConfig.MODULE_ID) {
            // Reload with migration support in case file was manually edited to older version
            coreConfig = config.loadWithMigration(
                CoreConfig.MODULE_ID,
                CoreConfig(),
                CoreConfig.CURRENT_VERSION
            )
            
            // Apply logging configuration
            applyLoggingConfig(coreConfig)
            
            logger.atFine().log("Core config reloaded: logLevel=${coreConfig.logging.globalLevel}, version=${coreConfig.configVersion}")
            
            // Notify all modules of config reload
            notifyModulesConfigReload()
        }
        
        // Initialize core components
        services = ServiceRegistry()
        globalPersistence = GlobalPersistenceService(dataDir, logger)
        worlds = WorldRegistry(dataDir, logger)
        players = PlayerRegistry()
        hudManager = MultiHudManager(logger)
        
        // Register core services
        services.register<ConfigManager>(config)
        services.register<GlobalPersistenceService>(globalPersistence)
        services.register<WorldRegistry>(worlds)
        services.register<PlayerRegistry>(players)
        services.register<MultiHudManager>(hudManager)
        services.register<HytaleLogger>(logger)
        
        // Register global player data repository
        val globalPlayerRepository = GlobalPlayerDataRepository(globalPersistence, logger)
        services.register<GlobalPlayerDataRepository>(globalPlayerRepository)
        
        // Apply initial logging configuration
        applyLoggingConfig(coreConfig)
        
        initialized = true
        logger.atInfo().log("CoreModule initialized (logLevel=${coreConfig.logging.globalLevel})")
        logger.atInfo().log(com.livinglands.core.logging.LoggingManager.getConfigurationSummary())
    }
    
    /**
     * Shutdown the core module.
     * Called during plugin shutdown phase.
     */
    fun shutdown() {
        if (!initialized) {
            return
        }
        
        // Clear module state (shutdown is handled separately)
        modules.clear()
        moduleOrder.clear()
        
        // Cleanup world contexts - closes all per-world DB connections
        // IMPORTANT: Take a snapshot to avoid ConcurrentModificationException
        // if context.cleanup() triggers any collection modifications
        val contextSnapshot = worlds.getAllContexts().toList()
        contextSnapshot.forEach { context ->
            try {
                context.cleanup()
            } catch (e: Exception) {
                logger.atInfo().log("Error cleaning up world ${context.worldId}: ${e.message}")
            }
        }
        
        // Close global database connection
        try {
            globalPersistence.close()
        } catch (e: Exception) {
            logger.atWarning().withCause(e).log("Error closing global database")
        }
        
        // Clear config manager
        config.clear()
        
        // Clear HUD manager
        hudManager.clear()
        
        // Clear all registries
        players.clear()
        worlds.clear()
        services.clear()
        
        initialized = false
        logger.atInfo().log("CoreModule shutdown complete")
    }
    
    /**
     * Check if debug mode is enabled.
     * Convenience method for conditional debug logging.
     * 
     * @deprecated Use LoggingManager with appropriate log level instead
     */
    @Deprecated("Use LoggingManager with DEBUG level instead")
    @Suppress("DEPRECATION")
    fun isDebug(): Boolean = if (::coreConfig.isInitialized) coreConfig.debug else false
    
    /**
     * Check if the core module is initialized.
     */
    fun isInitialized(): Boolean = initialized
    
    /**
     * Register migrations for CoreConfig.
     */
    private fun registerCoreConfigMigrations() {
        val migrations = listOf(
            com.livinglands.core.config.ConfigMigration(
                fromVersion = 1,
                toVersion = 2,
                description = "Add logging configuration with log levels",
                migrate = { old ->
                    old.toMutableMap().apply {
                        // Add logging config section
                        this["logging"] = mapOf(
                            "globalLevel" to if (old["debug"] == true) "DEBUG" else "INFO",
                            "moduleOverrides" to emptyMap<String, String>()
                        )
                        
                        // Update enabledModules to include professions
                        val currentModules = (old["enabledModules"] as? List<*>)?.filterIsInstance<String>() ?: listOf("metabolism")
                        if (!currentModules.contains("professions")) {
                            this["enabledModules"] = currentModules + "professions"
                        }
                        
                        // Update version
                        this["configVersion"] = 2
                    }
                }
            )
        )
        
        config.migrations.registerAll(CoreConfig.MODULE_ID, migrations)
    }
    
    /**
     * Apply logging configuration to LoggingManager.
     * Handles both new logging config and legacy debug flag.
     */
    @Suppress("DEPRECATION")
    private fun applyLoggingConfig(config: CoreConfig) {
        // Parse global log level - legacy debug flag takes precedence if set
        val globalLevel = if (config.debug) {
            // Legacy debug flag - set to DEBUG level for backward compatibility
            LogLevel.DEBUG
        } else {
            // Use new logging config
            LogLevel.fromStringOrDefault(config.logging.globalLevel, LogLevel.INFO)
        }
        
        LoggingManager.setGlobalLevel(globalLevel)
        
        // Clear existing module overrides
        LoggingManager.clearModuleOverrides()
        
        // Apply per-module overrides
        config.logging.moduleOverrides.forEach { (moduleId, levelString) ->
            val level = LogLevel.fromString(levelString)
            if (level != null) {
                LoggingManager.setModuleLevel(moduleId, level)
            } else {
                logger.atWarning().log("Invalid log level '$levelString' for module '$moduleId', ignoring")
            }
        }
        
        logger.atInfo().log("Logging configuration applied: global=$globalLevel, overrides=${config.logging.moduleOverrides.size}")
    }
    
    // ============ Module Registration & Lifecycle ============
    
    /**
     * Register a module with CoreModule.
     * Call this BEFORE setupModules().
     * 
     * @param module The module to register
     */
    fun registerModule(module: Module) {
        if (modules.containsKey(module.id)) {
            logger.atWarning().log("Module '${module.id}' already registered, skipping")
            return
        }
        modules[module.id] = module
        logger.atFine().log("Registered module: ${module.id} v${module.version}")
    }
    
    /**
     * Get a registered module by ID.
     * 
     * @param id The module ID
     * @return The module, or null if not found
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Module> getModule(id: String): T? {
        return modules[id] as? T
    }
    
    /**
     * Get all registered modules.
     */
    fun getAllModules(): Collection<Module> = modules.values.toList()
    
    /**
     * Get the number of registered modules.
     */
    fun getModuleCount(): Int = modules.size
    
    /**
     * Setup all registered modules in dependency order.
     * Uses Kahn's algorithm for topological sort.
     * 
     * @param context The module context with plugin references
     */
    suspend fun setupModules(context: ModuleContext) {
        if (modules.isEmpty()) {
            logger.atFine().log("No modules to setup")
            return
        }
        
        // Resolve dependency order
        val sorted = resolveDependencyOrder()
        if (sorted.isEmpty() && modules.isNotEmpty()) {
            logger.atSevere().log("Failed to resolve module dependencies - possible circular dependency")
            return
        }
        
        // Store the resolved order
        moduleOrder.clear()
        moduleOrder.addAll(sorted)
        
        logger.atInfo().log("Setting up ${moduleOrder.size} modules in order: ${moduleOrder.joinToString(" -> ")}")
        
        // Setup each module in order
        for (moduleId in moduleOrder) {
            val module = modules[moduleId] ?: continue
            
            try {
                module.setup(context)
            } catch (e: Exception) {
                logger.atSevere().withCause(e).log("Module '$moduleId' failed during setup")
                // Module is already marked as ERROR by AbstractModule
                // Continue with other modules - don't crash the whole system
            }
        }
        
        val setupCount = modules.values.count { it.state == ModuleState.SETUP }
        val errorCount = modules.values.count { it.state == ModuleState.ERROR }
        logger.atInfo().log("Module setup complete: $setupCount succeeded, $errorCount failed")
    }
    
    /**
     * Start all modules in dependency order.
     * Should be called after setupModules().
     */
    suspend fun startModules() {
        if (moduleOrder.isEmpty()) {
            logger.atFine().log("No modules to start")
            return
        }
        
        logger.atInfo().log("Starting ${moduleOrder.size} modules...")
        
        for (moduleId in moduleOrder) {
            val module = modules[moduleId] ?: continue
            
            // Skip modules that failed setup
            if (module.state == ModuleState.ERROR) {
                logger.atWarning().log("Skipping module '$moduleId' - in ERROR state")
                continue
            }
            
            try {
                module.start()
            } catch (e: Exception) {
                logger.atSevere().withCause(e).log("Module '$moduleId' failed during start")
                // Continue with other modules
            }
        }
        
        val startedCount = modules.values.count { it.state == ModuleState.STARTED }
        val errorCount = modules.values.count { it.state == ModuleState.ERROR }
        logger.atInfo().log("Module start complete: $startedCount running, $errorCount failed")
    }
    
    /**
     * Shutdown all modules in reverse dependency order.
     * Should be called during plugin shutdown.
     */
    suspend fun shutdownModules() {
        if (moduleOrder.isEmpty()) {
            logger.atFine().log("No modules to shutdown")
            return
        }
        
        // Shutdown in reverse order so dependents shutdown before dependencies
        val reverseOrder = moduleOrder.reversed()
        logger.atInfo().log("Shutting down ${reverseOrder.size} modules...")
        
        for (moduleId in reverseOrder) {
            val module = modules[moduleId] ?: continue
            
            try {
                module.shutdown()
            } catch (e: Exception) {
                // Error already logged by AbstractModule, but log again at module level
                logger.atWarning().withCause(e).log("Module '$moduleId' shutdown error")
            }
        }
        
        logger.atInfo().log("All modules shutdown complete")
    }
    
    /**
     * Notify all modules of a config reload.
     * Called when any config file is reloaded.
     */
    private fun notifyModulesConfigReload() {
        for (module in modules.values) {
            // Notify modules that are SETUP or STARTED (but not ERROR/DISABLED)
            if (module.state == ModuleState.SETUP || module.state == ModuleState.STARTED) {
                try {
                    module.onConfigReload()
                } catch (e: Exception) {
                    logger.atWarning().withCause(e).log("Module '${module.id}' config reload failed")
                }
            }
        }
    }
    
    // ============ Player Lifecycle Notifications ============
    
    /**
     * Notify all modules that a player joined.
     * Called by the plugin after session registration.
     * 
     * @param playerId The player's UUID
     * @param session The registered player session
     */
    suspend fun notifyPlayerJoin(playerId: UUID, session: PlayerSession) {
        for (module in modules.values) {
            // Only notify modules that are STARTED
            if (module.state == ModuleState.STARTED) {
                try {
                    module.onPlayerJoin(playerId, session)
                } catch (e: Exception) {
                    logger.atSevere().withCause(e)
                        .log("Module '${module.id}' failed onPlayerJoin for $playerId")
                }
            }
        }
    }
    
    /**
     * Notify all modules that a player disconnected.
     * Called by the plugin BEFORE session unregistration.
     * All modules should save their data for this player.
     * 
     * @param playerId The player's UUID
     * @param session The player session (still valid during this call)
     */
    suspend fun notifyPlayerDisconnect(playerId: UUID, session: PlayerSession) {
        for (module in modules.values) {
            // Only notify modules that are STARTED
            if (module.state == ModuleState.STARTED) {
                try {
                    module.onPlayerDisconnect(playerId, session)
                } catch (e: Exception) {
                    logger.atSevere().withCause(e)
                        .log("Module '${module.id}' failed onPlayerDisconnect for $playerId")
                }
            }
        }
    }
    
    /**
     * Resolve module dependency order using Kahn's algorithm (topological sort).
     * 
     * @return List of module IDs in dependency order, or empty list if circular dependency detected
     */
    private fun resolveDependencyOrder(): List<String> {
        // Build in-degree map and adjacency list
        val inDegree = mutableMapOf<String, Int>()
        val graph = mutableMapOf<String, MutableList<String>>()
        
        // Initialize all modules with 0 in-degree
        modules.keys.forEach { id ->
            inDegree[id] = 0
            graph[id] = mutableListOf()
        }
        
        // Build dependency graph
        // If module A depends on B, then B -> A edge exists
        // A should be loaded AFTER B
        modules.forEach { (id, module) ->
            module.dependencies.forEach { depId ->
                if (modules.containsKey(depId)) {
                    // depId -> id edge: id depends on depId
                    graph[depId]?.add(id)
                    inDegree[id] = inDegree.getOrDefault(id, 0) + 1
                } else {
                    // Dependency not found - warn but continue
                    logger.atWarning().log("Module '$id' has missing dependency: '$depId'")
                }
            }
        }
        
        // Kahn's algorithm
        val queue = ArrayDeque<String>()
        
        // Start with modules that have no dependencies (in-degree 0)
        inDegree.filter { it.value == 0 }.keys.forEach { queue.add(it) }
        
        val result = mutableListOf<String>()
        
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            result.add(current)
            
            // For each module that depends on current, reduce in-degree
            graph[current]?.forEach { neighbor ->
                val newDegree = inDegree.getOrDefault(neighbor, 1) - 1
                inDegree[neighbor] = newDegree
                
                if (newDegree == 0) {
                    queue.add(neighbor)
                }
            }
        }
        
        // Check for circular dependencies
        if (result.size != modules.size) {
            val missing = modules.keys - result.toSet()
            logger.atSevere().log("Circular dependency detected! Modules involved: $missing")
            return emptyList()
        }
        
        return result
    }
}
