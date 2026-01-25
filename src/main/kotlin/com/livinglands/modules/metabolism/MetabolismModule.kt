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
import com.livinglands.util.UuidStringCache
import com.livinglands.util.toCachedString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
    
    /** Coroutine scope for async operations */
    private val scope = CoroutineScope(Dispatchers.IO)
    
    /** 
     * Track Player and PlayerRef for HUD operations.
     * Key: Player UUID
     * Needed because PlayerDisconnectEvent doesn't provide the Player entity.
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
        
        // Save all players' metabolism data
        try {
            metabolismService.saveAllPlayers()
            logger.atFine().log("Saved all metabolism data")
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
        
        // Store player refs for later HUD cleanup on disconnect
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
     */
    private fun handlePlayerDisconnect(event: PlayerDisconnectEvent) {
        val playerRef = event.playerRef
        
        // Get player UUID from PlayerRef (uuid is a non-null field)
        @Suppress("DEPRECATION")
        val playerId = playerRef.uuid
        
        // Cleanup HUD first
        cleanupHudForPlayer(playerId, playerRef)
        
        // Get session to find world
        val session = CoreModule.players.getSession(playerId)
        if (session == null) {
            // Player might already be unregistered, try to save anyway
            metabolismService.removeFromCache(playerId)
            return
        }
        
        val worldContext = CoreModule.worlds.getContext(session.worldId)
        if (worldContext == null) {
            metabolismService.removeFromCache(playerId)
            return
        }
        
        // Save asynchronously
        scope.launch {
            try {
                metabolismService.savePlayer(playerId, worldContext)
                metabolismService.removeFromCache(playerId)
                debug("Saved and removed metabolism for disconnecting player $playerId")
            } catch (e: Exception) {
                logger.atWarning().withCause(e)
                    .log("Failed to save metabolism for disconnecting player $playerId")
                // Still remove from cache
                metabolismService.removeFromCache(playerId)
            }
        }
    }
    
    /**
     * Cleanup the metabolism HUD for a disconnecting player.
     */
    private fun cleanupHudForPlayer(playerId: UUID, playerRef: PlayerRef) {
        try {
            // Remove from service tracking (use cached string, will be cleaned up in removeFromCache)
            metabolismService.unregisterHudElement(playerId.toCachedString())
            
            // Remove from MultiHudManager - need the Player entity
            val refs = playerRefs.remove(playerId)
            if (refs != null) {
                val (player, _) = refs
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
