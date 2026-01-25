package com.livinglands.modules.metabolism

import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.livinglands.api.AbstractModule
import com.livinglands.core.CoreModule
import com.livinglands.core.PlayerSession
import com.livinglands.modules.metabolism.commands.StatsCommand
import com.livinglands.modules.metabolism.config.MetabolismConfig
import com.livinglands.modules.metabolism.food.FoodConsumptionProcessor
import com.livinglands.modules.metabolism.food.FoodDetectionTickSystem
import com.livinglands.modules.metabolism.food.FoodEffectDetector
import com.livinglands.modules.metabolism.hud.MetabolismHudElement
import com.livinglands.util.toCachedString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Metabolism module - hunger, thirst, and energy mechanics.
 * 
 * Features:
 * - Hunger, thirst, energy stat tracking (0-100 scale)
 * - Stat depletion over time based on activity
 * - Activity detection (idle, walking, sprinting, swimming, combat)
 * - Per-world persistence via SQLite
 * - Hot-reloadable configuration
 * - /ll stats command to view current stats
 * - HUD display showing stat bars on screen
 * 
 * Stats deplete at configurable rates based on player activity.
 * Higher activity (sprinting, combat) causes faster depletion.
 */
class MetabolismModule : AbstractModule(
    id = "metabolism",
    name = "Metabolism",
    version = "1.0.0",
    dependencies = emptySet()
) {
    
    /** Configuration loaded from metabolism.yml */
    private lateinit var metabolismConfig: MetabolismConfig
    
    /** Service for managing metabolism stats */
    private lateinit var metabolismService: MetabolismService
    
    /** Food effect detector */
    private lateinit var foodEffectDetector: FoodEffectDetector
    
    /** Food consumption processor */
    private lateinit var foodConsumptionProcessor: FoodConsumptionProcessor
    
    /**
     * Module-level coroutine scope for async persistence operations.
     * 
     * Currently used for:
     * - Background tasks that shouldn't block event handlers
     * - Shutdown coordination (waiting for pending operations)
     * 
     * Note: Player saves on disconnect are now synchronous via lifecycle hooks.
     */
    private val persistenceScope = CoroutineScope(
        SupervisorJob() + 
        Dispatchers.IO + 
        CoroutineName("MetabolismPersistence") +
        CoroutineExceptionHandler { _, throwable ->
            // Log uncaught exceptions from persistence coroutines
            if (throwable !is CancellationException) {
                logger.atSevere().withCause(throwable)
                    .log("Uncaught exception in persistence coroutine")
            }
        }
    )
    
    /** 
     * Track Player and PlayerRef for HUD operations.
     * Key: Player UUID
     * 
     * This is needed because PlayerDisconnectEvent doesn't provide the Player entity,
     * which we need for HUD cleanup.
     * 
     * Note: We use CoreModule.players.getSession() for worldContext because the main
     * plugin no longer unregisters sessions immediately on disconnect.
     */
    private val playerRefs = ConcurrentHashMap<UUID, Pair<Player, PlayerRef>>()
    
    override suspend fun onSetup() {
        logger.atInfo().log("Metabolism module setting up...")
        
        // Register migrations first
        CoreModule.config.registerMigrations(
            MetabolismConfig.MODULE_ID,
            MetabolismConfig.getMigrations()
        )
        logger.atFine().log("Registered ${MetabolismConfig.getMigrations().size} metabolism config migrations")
        
        // Load configuration with migration support
        metabolismConfig = CoreModule.config.loadWithMigration(
            MetabolismConfig.MODULE_ID,
            MetabolismConfig(),
            MetabolismConfig.CURRENT_VERSION
        )
        logger.atFine().log("Loaded metabolism config: enabled=${metabolismConfig.enabled}, version=${metabolismConfig.configVersion}")
        
        // Create service
        metabolismService = MetabolismService(metabolismConfig, logger)
        
        // Register service with CoreModule for access by other modules
        CoreModule.services.register<MetabolismService>(metabolismService)
        
        // Initialize repositories for existing worlds
        initializeRepositories()
        
        // Register ECS tick system
        if (metabolismConfig.enabled) {
            val tickSystem = MetabolismTickSystem(metabolismService, logger)
            registerSystem(tickSystem)
            logger.atFine().log("Registered MetabolismTickSystem")
        }
        
        // Initialize food consumption system
        if (metabolismConfig.enabled && metabolismConfig.foodConsumption.enabled) {
            // Create food detection components (need java.util.logging.Logger)
            val javaLogger = java.util.logging.Logger.getLogger(javaClass.name)
            foodEffectDetector = FoodEffectDetector(javaLogger)
            foodConsumptionProcessor = FoodConsumptionProcessor(metabolismService, metabolismConfig.foodConsumption, javaLogger)
            
            // Register food detection tick system
            val foodTickSystem = FoodDetectionTickSystem(
                foodEffectDetector,
                foodConsumptionProcessor,
                metabolismConfig.foodConsumption,
                logger
            )
            registerSystem(foodTickSystem)
            logger.atFine().log("Registered FoodDetectionTickSystem (interval=${metabolismConfig.foodConsumption.detectionTickInterval} ticks, batch=${metabolismConfig.foodConsumption.batchSize})")
        }
        
        // NOTE: Player lifecycle events are handled via onPlayerJoin/onPlayerDisconnect hooks
        // called by the plugin through CoreModule.notifyPlayerJoin/notifyPlayerDisconnect
        
        // Register commands
        registerCommand(StatsCommand(metabolismService))
        logger.atFine().log("Registered /ll stats command")
        
        // Register config reload callback
        CoreModule.config.onReload("metabolism") {
            onConfigReloaded()
        }
        
        logger.atInfo().log("Metabolism module setup complete")
    }
    
    override suspend fun onStart() {
        logger.atInfo().log("Metabolism module started")
        
        // Initialize metabolism for any players already online
        initializeOnlinePlayers()
    }
    
    // ============ Player Lifecycle Hooks ============
    
    /**
     * Called when a player joins - initialize their metabolism and HUD.
     * Called by plugin through CoreModule after session is registered.
     */
    override suspend fun onPlayerJoin(playerId: UUID, session: PlayerSession) {
        logger.atInfo().log("Metabolism: onPlayerJoin called for $playerId")
        
        // Get world context from session
        val worldContext = CoreModule.worlds.getContext(session.worldId)
        if (worldContext == null) {
            logger.atWarning().log("No world context for player $playerId (worldId: ${session.worldId})")
            return
        }
        
        // Get the Player entity from the store
        val store = session.store
        val entityRef = session.entityRef
        val world = session.world
        
        // Execute on world thread for ECS safety
        world.execute {
            try {
                // Get Player component from entity ref
                val player = store.getComponent(entityRef, Player.getComponentType())
                if (player == null) {
                    logger.atWarning().log("Player component not found for $playerId")
                    return@execute
                }
                
                // Get PlayerRef for HUD operations
                @Suppress("DEPRECATION")
                val playerRef = player.playerRef
                if (playerRef == null) {
                    logger.atWarning().log("PlayerRef is null for player $playerId")
                    return@execute
                }
                
                // Store player refs for HUD cleanup on disconnect
                playerRefs[playerId] = Pair(player, playerRef)
                
                // Ensure repository exists
                if (!worldContext.hasData<MetabolismRepository>()) {
                    val repository = MetabolismRepository(worldContext.persistence, logger)
                    kotlinx.coroutines.runBlocking {
                        repository.initialize()
                    }
                    worldContext.getData { repository }
                    logger.atInfo().log("Initialized MetabolismRepository for world ${session.worldId}")
                }
                
                // Initialize metabolism stats (blocking since we need it done before HUD)
                kotlinx.coroutines.runBlocking {
                    metabolismService.initializePlayer(playerId, worldContext)
                }
                logger.atInfo().log("Initialized metabolism for player $playerId")
                
                // Register HUD after stats are loaded
                registerHudForPlayer(player, playerRef, playerId)
                
                // Initialize food detection tracker to prevent false detections of existing effects
                if (metabolismConfig.enabled && metabolismConfig.foodConsumption.enabled) {
                    foodEffectDetector.initializePlayer(playerId, session)
                }
                
            } catch (e: Exception) {
                logger.atSevere().withCause(e)
                    .log("Failed to initialize metabolism for player $playerId")
            }
        }
    }
    
    /**
     * Called when a player disconnects - save their metabolism and cleanup HUD.
     * Called by plugin through CoreModule BEFORE session is unregistered.
     */
    override suspend fun onPlayerDisconnect(playerId: UUID, session: PlayerSession) {
        logger.atInfo().log("Metabolism: onPlayerDisconnect called for $playerId")
        
        // Get world context from session
        val worldContext = CoreModule.worlds.getContext(session.worldId)
        if (worldContext == null) {
            logger.atWarning().log("No world context for disconnecting player $playerId")
            metabolismService.removeFromCache(playerId)
            cleanupPlayerRefsOnly(playerId)
            return
        }
        
        // Cleanup HUD first
        val refs = playerRefs.remove(playerId)
        if (refs != null) {
            val (player, playerRef) = refs
            cleanupHudForPlayer(playerId, playerRef)
        }
        
        // Save metabolism stats (this is blocking - we need to complete before session is removed)
        try {
            metabolismService.savePlayer(playerId, worldContext)
            logger.atInfo().log("Saved metabolism for disconnecting player $playerId")
        } catch (e: Exception) {
            logger.atWarning().withCause(e)
                .log("Failed to save metabolism for disconnecting player $playerId")
        } finally {
            // Always cleanup cache
            metabolismService.removeFromCache(playerId)
            
            // Cleanup food effect detector tracking
            if (metabolismConfig.enabled && metabolismConfig.foodConsumption.enabled) {
                foodEffectDetector.removePlayer(playerId)
            }
        }
    }
    
    override suspend fun onShutdown() {
        logger.atInfo().log("Metabolism module shutting down...")
        
        // First, cancel the persistence scope to prevent new saves from starting
        // and wait for any in-flight saves to complete (with timeout)
        try {
            logger.atInfo().log("Waiting for pending persistence operations...")
            
            // Get the supervisor job to wait on it
            val supervisorJob = persistenceScope.coroutineContext[Job]
            
            // Cancel the scope (signals no new work should start)
            persistenceScope.cancel("Module shutting down")
            
            // Wait for any in-flight coroutines to complete (with 5 second timeout)
            if (supervisorJob != null) {
                withTimeout(5000) {
                    supervisorJob.join()
                }
                logger.atInfo().log("All pending persistence operations completed")
            }
        } catch (e: CancellationException) {
            // Expected when timeout occurs or scope is cancelled
            logger.atInfo().log("Persistence scope cancelled (some saves may have been interrupted)")
        } catch (e: Exception) {
            logger.atWarning().withCause(e).log("Error waiting for persistence operations")
        }
        
        // Save all remaining players' metabolism data (fallback for any not saved on disconnect)
        try {
            metabolismService.saveAllPlayers()
            logger.atInfo().log("Saved all remaining metabolism data")
        } catch (e: Exception) {
            logger.atWarning().withCause(e).log("Error saving metabolism data during shutdown")
        }
        
        // Clear the service cache
        metabolismService.clearCache()
        
        // Unregister config reload callback
        CoreModule.config.removeReloadCallback("metabolism")
        
        logger.atInfo().log("Metabolism module shutdown complete")
    }
    
    override fun onConfigReload() {
        onConfigReloaded()
    }
    
    /**
     * Handle config reload - update service with new config.
     */
    private fun onConfigReloaded() {
        // Reload with migration support in case the file was manually edited
        val newConfig = CoreModule.config.loadWithMigration(
            MetabolismConfig.MODULE_ID,
            MetabolismConfig(),
            MetabolismConfig.CURRENT_VERSION
        )
        metabolismConfig = newConfig
        metabolismService.updateConfig(newConfig)
        logger.atFine().log("Metabolism config reloaded: enabled=${newConfig.enabled}, version=${newConfig.configVersion}")
    }
    
    /**
     * Initialize repositories for all existing worlds.
     */
    private suspend fun initializeRepositories() {
        for (worldContext in CoreModule.worlds.getAllContexts()) {
            try {
                val repository = MetabolismRepository(worldContext.persistence, logger)
                repository.initialize()
                worldContext.getData { repository }
                logger.atFine().log("Initialized metabolism repository for world ${worldContext.worldId}")
            } catch (e: Exception) {
                logger.atWarning().withCause(e)
                    .log("Failed to initialize metabolism repository for world ${worldContext.worldId}")
            }
        }
    }
    
    /**
     * Initialize metabolism for players already online.
     * This handles the case where the module starts after players have joined.
     */
    private suspend fun initializeOnlinePlayers() {
        for (session in CoreModule.players.getAllSessions()) {
            val worldContext = CoreModule.worlds.getContext(session.worldId)
            if (worldContext != null) {
                try {
                    metabolismService.initializePlayer(session.playerId, worldContext)
                } catch (e: Exception) {
                    logger.atWarning().withCause(e)
                        .log("Failed to initialize metabolism for player ${session.playerId}")
                }
            }
        }
        
        val playerCount = metabolismService.getCacheSize()
        if (playerCount > 0) {
            logger.atFine().log("Initialized metabolism for $playerCount online players")
        }
    }
    
    /**
     * Register the metabolism HUD for a player.
     * Called after metabolism stats are initialized.
     */
    private fun registerHudForPlayer(player: Player, playerRef: PlayerRef, playerId: UUID) {
        if (!metabolismConfig.enabled) return
        
        // Execute on world thread (like v2.6.0 does)
        val world = player.world
        if (world == null) {
            logger.atWarning().log("Player world is null, cannot register HUD for $playerId")
            return
        }
        
        world.execute {
            try {
                // Create the HUD element
                val hudElement = MetabolismHudElement(playerRef)
                
                // Register with the service for updates (use cached string)
                metabolismService.registerHudElement(playerId.toCachedString(), hudElement)
                
                // Set HUD directly on player's HudManager
                logger.atInfo().log("Setting HUD on world thread...")
                player.hudManager.setCustomHud(playerRef, hudElement)
                logger.atInfo().log("Set custom HUD")
                hudElement.show()
                logger.atInfo().log("Called show() on HUD element")
                
                logger.atInfo().log("Registered metabolism HUD for player $playerId")
            } catch (e: Exception) {
                logger.atWarning().withCause(e)
                    .log("Failed to register metabolism HUD for player $playerId")
            }
        }
    }
    
    /**
     * Cleanup player refs map entry only (without HUD cleanup).
     * Used when we don't have a PlayerRef available.
     */
    private fun cleanupPlayerRefsOnly(playerId: UUID) {
        val refs = playerRefs.remove(playerId)
        if (refs != null) {
            val (player, playerRef) = refs
            try {
                CoreModule.hudManager.removeHud(player, playerRef, MetabolismHudElement.NAMESPACE)
                CoreModule.hudManager.onPlayerDisconnect(playerId)
            } catch (e: Exception) {
                logger.atWarning().withCause(e).log("Error cleaning up HUD for player $playerId")
            }
        }
    }
    
    /**
     * Cleanup the metabolism HUD for a disconnecting player.
     * Note: This is called AFTER playerRefs entry has been removed in onPlayerDisconnect.
     * 
     * @param playerId The player's UUID
     * @param playerRef The player's PlayerRef (from the removed playerRefs entry)
     */
    private fun cleanupHudForPlayer(playerId: UUID, playerRef: PlayerRef) {
        try {
            // Remove from service tracking (use cached string, will be cleaned up in removeFromCache)
            metabolismService.unregisterHudElement(playerId.toCachedString())
            
            // Get session to access world for thread-safe ECS operations
            val session = CoreModule.players.getSession(playerId)
            if (session != null) {
                // Execute HUD cleanup on the world thread (required for ECS access)
                session.world.execute {
                    try {
                        val store = session.store
                        val entityRef = session.entityRef
                        val player = store.getComponent(entityRef, Player.getComponentType())
                        if (player != null) {
                            // Remove from MultiHudManager
                            CoreModule.hudManager.removeHud(player, playerRef, MetabolismHudElement.NAMESPACE)
                        }
                    } catch (e: Exception) {
                        logger.atWarning().withCause(e)
                            .log("Error removing HUD from MultiHudManager for player $playerId")
                    }
                }
            }
            
            // Notify MultiHudManager about disconnect (doesn't need world thread)
            CoreModule.hudManager.onPlayerDisconnect(playerId)
            
            debug("Cleaned up metabolism HUD for player $playerId")
        } catch (e: Exception) {
            logger.atWarning().withCause(e)
                .log("Error cleaning up metabolism HUD for player $playerId")
        }
    }
}
