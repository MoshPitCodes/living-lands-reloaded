package com.livinglands.modules.metabolism

import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.livinglands.api.AbstractModule
import com.livinglands.core.CoreModule
import com.livinglands.modules.metabolism.commands.StatsCommand
import com.livinglands.modules.metabolism.config.MetabolismConfig
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
import kotlinx.coroutines.launch
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
    
    /**
     * Plugin-level coroutine scope for persistence operations.
     * 
     * Uses SupervisorJob so that:
     * 1. Child coroutine failures don't cancel the whole scope
     * 2. The scope survives individual player disconnects
     * 3. Only cancels when the plugin shuts down
     * 
     * This fixes the issue where scope.launch() coroutines were being cancelled
     * when players disconnected, causing saves to never complete.
     */
    private val persistenceScope = CoroutineScope(
        SupervisorJob() + 
        Dispatchers.IO + 
        CoroutineName("MetabolismPersistence") +
        CoroutineExceptionHandler { _, throwable ->
            // Log uncaught exceptions from persistence coroutines
            // (caught exceptions are handled in the coroutine body)
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
        
        // Register event listeners
        registerEventListeners()
        
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
     * Register event listeners for player lifecycle.
     */
    private fun registerEventListeners() {
        // Player ready - initialize their metabolism stats
        // PlayerReadyEvent requires registerGlobal() - use registerListenerGlobal
        registerListenerGlobal<PlayerReadyEvent> { event ->
            handlePlayerReady(event)
        }
        
        // Player disconnect - save their metabolism stats
        // PlayerDisconnectEvent extends PlayerRefEvent<Void>, so registerListener works
        registerListener<PlayerDisconnectEvent> { event ->
            handlePlayerDisconnect(event)
        }
        
        logger.atInfo().log("Registered metabolism event listeners (global for PlayerReadyEvent)")
    }
    
    /**
     * Handle player ready event - initialize their metabolism and HUD.
     */
    private fun handlePlayerReady(event: PlayerReadyEvent) {
        val player = event.player
        
        // Get PlayerRef from the Player entity (not from event.playerRef which is Ref<EntityStore>)
        @Suppress("DEPRECATION")
        val playerRef = player.playerRef
        if (playerRef == null) {
            logger.atFine().log("Player has no PlayerRef in PlayerReadyEvent")
            return
        }
        
        @Suppress("DEPRECATION")
        val playerId = player.getUuid()
        if (playerId == null) {
            logger.atFine().log("Player has no UUID in PlayerReadyEvent")
            return
        }
        
        val world = player.world
        if (world == null) {
            logger.atFine().log("Player $playerId has no world in PlayerReadyEvent")
            return
        }
        
        // Get or create world context (lazy creation)
        val worldContext = CoreModule.worlds.getOrCreateContext(world)
        
        // Store player refs for HUD cleanup on disconnect
        // (PlayerDisconnectEvent doesn't provide the Player entity)
        playerRefs[playerId] = Pair(player, playerRef)
        
        // Initialize synchronously (TODO: make async once coroutines issue is fixed)
        try {
            runBlocking {
                // Ensure repository exists
                if (!worldContext.hasData<MetabolismRepository>()) {
                    val repository = MetabolismRepository(worldContext.persistence, logger)
                    repository.initialize()
                    worldContext.getData { repository }
                    logger.atInfo().log("Initialized MetabolismRepository for world ${world.name}")
                }
                
                metabolismService.initializePlayer(playerId, worldContext)
                logger.atInfo().log("Initialized metabolism for player $playerId")
            }
            
            // Register HUD after stats are loaded
            registerHudForPlayer(player, playerRef, playerId)
            
        } catch (e: Exception) {
            logger.atSevere().withCause(e)
                .log("Failed to initialize metabolism for player $playerId")
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
     * Handle player disconnect event - save their metabolism and cleanup HUD.
     * 
     * Uses persistenceScope.launch() for async save because:
     * 1. We must NOT block the world thread (runBlocking causes server lag)
     * 2. persistenceScope uses SupervisorJob which survives player disconnects
     * 3. onShutdown() waits for pending saves with timeout before completing
     * 
     * Uses CoreModule.players.getSession() to get worldId - this works because
     * the main plugin no longer unregisters sessions immediately on disconnect.
     * This module is responsible for unregistering the session after saving.
     */
    private fun handlePlayerDisconnect(event: PlayerDisconnectEvent) {
        val playerRef = event.playerRef
        
        // Get player UUID from PlayerRef (uuid is a non-null field)
        @Suppress("DEPRECATION")
        val playerId = playerRef.uuid
        
        logger.atInfo().log("Handling disconnect for player $playerId")
        
        // Get session from CoreModule.players to find worldId
        val session = CoreModule.players.getSession(playerId)
        if (session == null) {
            logger.atWarning().log("No session found for disconnecting player $playerId - was player properly initialized?")
            metabolismService.removeFromCache(playerId)
            cleanupPlayerRefs(playerId, playerRef)
            return
        }
        
        val worldContext = CoreModule.worlds.getContext(session.worldId)
        if (worldContext == null) {
            logger.atWarning().log("No world context found for disconnecting player $playerId (worldId: ${session.worldId})")
            metabolismService.removeFromCache(playerId)
            cleanupPlayerRefs(playerId, playerRef)
            CoreModule.players.unregister(playerId)
            return
        }
        
        logger.atInfo().log("Found session for $playerId (worldId: ${session.worldId})")
        
        // Cleanup HUD first (synchronous, fast operation)
        cleanupHudForPlayer(playerId, playerRef)
        
        // Launch save on persistence scope (non-blocking)
        // SupervisorJob ensures this coroutine survives even if player leaves
        // onShutdown() will wait for pending saves before completing
        persistenceScope.launch {
            try {
                metabolismService.savePlayer(playerId, worldContext)
                logger.atInfo().log("Saved metabolism for disconnecting player $playerId")
            } catch (e: CancellationException) {
                // Scope was cancelled (shutdown) - this is expected, don't log as error
                logger.atInfo().log("Save cancelled for player $playerId (module shutting down)")
            } catch (e: Exception) {
                logger.atWarning().withCause(e)
                    .log("Failed to save metabolism for disconnecting player $playerId")
            } finally {
                // Always cleanup, even if save failed
                metabolismService.removeFromCache(playerId)
                
                // Unregister the session from CoreModule.players AFTER saving
                // This is the final cleanup step for the player
                CoreModule.players.unregister(playerId)
                logger.atInfo().log("Unregistered session and removed metabolism from cache for player $playerId")
            }
        }
        
        // Event handler returns immediately - save happens asynchronously
        logger.atFine().log("Disconnect handler completed for $playerId (save in progress)")
    }
    
    /**
     * Cleanup player refs map entry.
     */
    private fun cleanupPlayerRefs(playerId: UUID, playerRef: PlayerRef) {
        val refs = playerRefs.remove(playerId)
        if (refs != null) {
            val (player, _) = refs
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
     * 
     * @param playerId The player's UUID
     * @param playerRef The player's PlayerRef
     */
    private fun cleanupHudForPlayer(playerId: UUID, playerRef: PlayerRef) {
        try {
            // Remove from service tracking (use cached string, will be cleaned up in removeFromCache)
            metabolismService.unregisterHudElement(playerId.toCachedString())
            
            // Get player from our tracked refs for HUD cleanup
            val refs = playerRefs.remove(playerId)
            if (refs != null) {
                val (player, _) = refs
                // Remove from MultiHudManager
                CoreModule.hudManager.removeHud(player, playerRef, MetabolismHudElement.NAMESPACE)
            }
            
            // Notify MultiHudManager about disconnect
            CoreModule.hudManager.onPlayerDisconnect(playerId)
            
            debug("Cleaned up metabolism HUD for player $playerId")
        } catch (e: Exception) {
            logger.atWarning().withCause(e)
                .log("Error cleaning up metabolism HUD for player $playerId")
        }
    }
}
