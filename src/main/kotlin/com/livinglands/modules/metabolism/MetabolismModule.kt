package com.livinglands.modules.metabolism

import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.livinglands.api.AbstractModule
import com.livinglands.core.CoreModule
import com.livinglands.core.PlayerSession
import com.livinglands.modules.metabolism.commands.StatsCommand
import com.livinglands.modules.metabolism.config.MetabolismConfig
import com.livinglands.modules.metabolism.config.MetabolismConfigValidator
import com.livinglands.modules.metabolism.buffs.BuffsSystem
import com.livinglands.modules.metabolism.buffs.DebuffsSystem
import com.livinglands.modules.metabolism.food.FoodConsumptionProcessor
import com.livinglands.modules.metabolism.food.FoodDetectionTickSystem
import com.livinglands.modules.metabolism.food.FoodEffectDetector
import com.livinglands.modules.metabolism.hud.MetabolismHudElement
import com.livinglands.core.SpeedManager
import com.livinglands.core.toCachedString
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
    
    /** Global metabolism repository (server-wide) */
    private lateinit var metabolismRepository: MetabolismRepository
    
    /** Service for managing metabolism stats */
    private lateinit var metabolismService: MetabolismService
    
    /** Food effect detector */
    private lateinit var foodEffectDetector: FoodEffectDetector
    
    /** Food consumption processor */
    private lateinit var foodConsumptionProcessor: FoodConsumptionProcessor
    
    /** Speed manager for centralized speed modifications */
    private lateinit var speedManager: SpeedManager
    
    /** Debuffs system (penalties for low stats) */
    private lateinit var debuffsSystem: DebuffsSystem
    
    /** Buffs system (bonuses for high stats) */
    private lateinit var buffsSystem: BuffsSystem
    
    /** Respawn reset system (resets metabolism on death/respawn) */
    private lateinit var respawnResetSystem: RespawnResetSystem
    
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
        
        // Validate world overrides
        val knownWorldNames = CoreModule.worlds.getAllWorldNames()
        MetabolismConfigValidator.validateOverrides(metabolismConfig, knownWorldNames, logger)
        
        // Create global repository (server-wide metabolism stats)
        metabolismRepository = MetabolismRepository(CoreModule.globalPersistence, logger)
        metabolismRepository.initialize()
        logger.atInfo().log("Initialized global metabolism repository")
        
        // Create service
        metabolismService = MetabolismService(metabolismConfig, logger)
        
        // Register services with CoreModule for access by other modules
        CoreModule.services.register<MetabolismRepository>(metabolismRepository)
        CoreModule.services.register<MetabolismService>(metabolismService)
        
        // Resolve world-specific configs from global config + overrides
        initializeWorldConfigs()
        
        // Note: Tick system registration must happen AFTER buffs/debuffs initialization
        // so they can be passed to the tick system constructor
        
        // Initialize food consumption system
        if (metabolismConfig.enabled && metabolismConfig.foodConsumption.enabled) {
            // Create food detection components
            foodEffectDetector = FoodEffectDetector(logger)
            foodConsumptionProcessor = FoodConsumptionProcessor(metabolismService, metabolismConfig.foodConsumption, logger)
            
            // Register food detection tick system
            val foodTickSystem = FoodDetectionTickSystem(
                foodEffectDetector,
                foodConsumptionProcessor,
                metabolismConfig.foodConsumption,
                logger
            )
            registerSystem(foodTickSystem)
            logger.atInfo().log("Registered FoodDetectionTickSystem (interval=${metabolismConfig.foodConsumption.detectionTickInterval} ticks, batch=${metabolismConfig.foodConsumption.batchSize})")
        }
        
        // Initialize buffs and debuffs system
        if (metabolismConfig.enabled) {
            // Create speed manager (centralized speed modification)
            speedManager = SpeedManager(logger)
            
            // Create debuffs system (must be created before buffs for suppression check)
            debuffsSystem = DebuffsSystem(
                config = metabolismConfig.debuffs,
                speedManager = speedManager,
                logger = logger
            )
            
            // Create buffs system (checks debuffs for suppression)
            buffsSystem = BuffsSystem(
                config = metabolismConfig.buffs,
                debuffsSystem = debuffsSystem,
                speedManager = speedManager,
                logger = logger
            )
            
            logger.atFine().log("Initialized buffs and debuffs system")
        }
        
        // Register ECS tick system (must be after buffs/debuffs initialization)
        if (metabolismConfig.enabled) {
            val tickSystem = MetabolismTickSystem(
                metabolismService = metabolismService,
                debuffsSystem = if (::debuffsSystem.isInitialized) debuffsSystem else null,
                buffsSystem = if (::buffsSystem.isInitialized) buffsSystem else null,
                logger = logger
            )
            registerSystem(tickSystem)
            logger.atFine().log("Registered MetabolismTickSystem with buffs/debuffs")
            
            // Register respawn reset system to handle metabolism reset on player death/respawn
            respawnResetSystem = RespawnResetSystem(
                metabolismService = metabolismService,
                metabolismRepository = metabolismRepository,
                debuffsSystem = if (::debuffsSystem.isInitialized) debuffsSystem else null,
                buffsSystem = if (::buffsSystem.isInitialized) buffsSystem else null,
                logger = logger
            )
            registerSystem(respawnResetSystem)
            logger.atFine().log("Registered RespawnResetSystem for death/respawn handling")
        }
        
        // NOTE: Player lifecycle events are handled via onPlayerJoin/onPlayerDisconnect hooks
        // called by the plugin through CoreModule.notifyPlayerJoin/notifyPlayerDisconnect
        
        // Register commands as subcommands of /ll
        CoreModule.mainCommand.registerSubCommand(StatsCommand(metabolismService))
        logger.atFine().log("Registered /ll show command")
        
        // Register HUD toggle commands (matching v2.6.0: /ll stats, /ll buffs, /ll debuffs)
        CoreModule.mainCommand.registerSubCommand(com.livinglands.modules.metabolism.commands.HudToggleCommand(
            com.livinglands.modules.metabolism.commands.HudToggleCommand.ToggleType.STATS
        ))
        CoreModule.mainCommand.registerSubCommand(com.livinglands.modules.metabolism.commands.HudToggleCommand(
            com.livinglands.modules.metabolism.commands.HudToggleCommand.ToggleType.BUFFS
        ))
        CoreModule.mainCommand.registerSubCommand(com.livinglands.modules.metabolism.commands.HudToggleCommand(
            com.livinglands.modules.metabolism.commands.HudToggleCommand.ToggleType.DEBUFFS
        ))
        logger.atFine().log("Registered HUD toggle commands: /ll stats, /ll buffs, /ll debuffs")
        
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
                
                // Clean up any stale modifiers from previous session (crash recovery)
                // This prevents modifiers from persisting if the player disconnected abnormally
                try {
                    val statMap = store.getComponent(entityRef, com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap.getComponentType())
                    if (statMap != null) {
                        val staminaId = com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes.getStamina()
                        val healthId = com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes.getHealth()
                        statMap.removeModifier(com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap.Predictable.SELF, staminaId, "livinglands_debuff_stamina")
                        statMap.removeModifier(com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap.Predictable.SELF, staminaId, "livinglands_buff_stamina")
                        statMap.removeModifier(com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap.Predictable.SELF, healthId, "livinglands_buff_health")
                        logger.atFine().log("Cleaned up stale modifiers for player $playerId")
                    }
                } catch (e: Exception) {
                    logger.atFine().log("Could not cleanup stale modifiers for $playerId: ${e.message}")
                }
                
                // Ensure world config is resolved (for lazily created worlds)
                if (worldContext.metabolismConfig == null) {
                    worldContext.resolveMetabolismConfig(metabolismConfig)
                    logger.atFine().log("Resolved metabolism config for lazily created world ${worldContext.worldName}")
                }
                
                // Initialize metabolism with default values immediately (non-blocking)
                metabolismService.initializePlayerWithDefaults(playerId)
                
                // Register HUD immediately with default values (no blocking)
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
        
        // Load actual stats from database asynchronously (outside world.execute)
        persistenceScope.launch {
            try {
                val stats = metabolismRepository.ensureStats(playerId.toCachedString())
                // Update in-memory state with loaded values
                metabolismService.updatePlayerState(playerId, stats)
                
                // Immediately evaluate buffs/debuffs based on loaded stats
                // This ensures buffs appear instantly instead of waiting for next tick
                val worldContext = CoreModule.worlds.getContext(session.worldId)
                val worldConfig = worldContext?.metabolismConfig ?: metabolismConfig
                
                session.world.execute {
                    if (::debuffsSystem.isInitialized) {
                        debuffsSystem.tick(playerId, stats, session.entityRef, session.store, worldConfig.debuffs)
                    }
                    if (::buffsSystem.isInitialized) {
                        buffsSystem.tick(playerId, stats, session.entityRef, session.store, worldConfig.buffs)
                    }
                }
                
                // Force HUD refresh to show loaded values AND buffs/debuffs
                metabolismService.forceUpdateHud(playerId.toCachedString(), playerId)
                logger.atFine().log("Loaded metabolism stats from database for $playerId")
            } catch (e: Exception) {
                logger.atWarning().withCause(e).log("Failed to load metabolism stats for $playerId, using defaults")
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
        
        // Save HUD preferences before cleanup (fire and forget - not critical if it fails)
        val hudElement = CoreModule.hudManager.getHud<MetabolismHudElement>(playerId, MetabolismHudElement.NAMESPACE)
        if (hudElement != null) {
            persistenceScope.launch {
                try {
                    metabolismRepository.saveHudPreferences(playerId.toCachedString(), hudElement.preferences)
                    logger.atFine().log("Saved HUD preferences for disconnecting player $playerId")
                } catch (e: Exception) {
                    logger.atWarning().withCause(e).log("Failed to save HUD preferences for $playerId")
                }
            }
        }
        
        // Cleanup HUD
        val refs = playerRefs.remove(playerId)
        if (refs != null) {
            val (player, playerRef) = refs
            cleanupHudForPlayer(playerId, playerRef)
        }
        
        // Save metabolism stats to global database (this is blocking - we need to complete before session is removed)
        try {
            metabolismService.savePlayer(playerId, metabolismRepository)
            logger.atInfo().log("Saved metabolism for disconnecting player $playerId (to global database)")
        } catch (e: Exception) {
            logger.atWarning().withCause(e)
                .log("Failed to save metabolism for disconnecting player $playerId")
        } finally {
            // Cleanup buffs/debuffs on world thread to access ECS
            session.world.execute {
                if (::debuffsSystem.isInitialized) {
                    debuffsSystem.cleanup(playerId, session.entityRef, session.store)
                }
                if (::buffsSystem.isInitialized) {
                    buffsSystem.cleanup(playerId, session.entityRef, session.store)
                }
            }
            if (::speedManager.isInitialized) {
                speedManager.cleanup(playerId)
            }
            
            // Cleanup food detection
            if (metabolismConfig.enabled && metabolismConfig.foodConsumption.enabled) {
                foodEffectDetector.cleanup(playerId)
            }
            
            // Cleanup respawn reset system tracking
            if (::respawnResetSystem.isInitialized) {
                respawnResetSystem.cleanup(playerId)
            }
            
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
        
        // Save all remaining players' metabolism data to global database (fallback for any not saved on disconnect)
        try {
            metabolismService.saveAllPlayers(metabolismRepository)
            logger.atInfo().log("Saved all remaining metabolism data to global database")
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
        logger.atInfo().log("Metabolism config reloaded: enabled=${newConfig.enabled}, version=${newConfig.configVersion}")
        
        // Re-resolve world-specific configs for all existing worlds
        var worldsResolved = 0
        for (worldContext in CoreModule.worlds.getAllContexts()) {
            try {
                worldContext.resolveMetabolismConfig(newConfig)
                worldsResolved++
                logger.atInfo().log("Re-resolved metabolism config for world ${worldContext.worldName}")
            } catch (e: Exception) {
                logger.atWarning().withCause(e)
                    .log("Failed to re-resolve metabolism config for world ${worldContext.worldName}")
            }
        }
        logger.atInfo().log("Metabolism configs re-resolved for $worldsResolved worlds")
    }
    
    /**
     * Resolve world-specific configs from global config + overrides.
     * 
     * **ARCHITECTURE CHANGE:** Repository is now global, but configs can still be per-world.
     * This allows different depletion rates/rules per world while stats follow the player.
     */
    private fun initializeWorldConfigs() {
        for (worldContext in CoreModule.worlds.getAllContexts()) {
            try {
                // Resolve world-specific config (merges overrides with global config)
                worldContext.resolveMetabolismConfig(metabolismConfig)
                logger.atFine().log("Resolved metabolism config for world ${worldContext.worldName} (${worldContext.worldId})")
            } catch (e: Exception) {
                logger.atWarning().withCause(e)
                    .log("Failed to resolve metabolism config for world ${worldContext.worldName} (${worldContext.worldId})")
            }
        }
    }
    
    /**
     * Initialize metabolism for players already online.
     * This handles the case where the module starts after players have joined.
     * 
     * **ARCHITECTURE CHANGE:** Now uses global repository.
     */
    private suspend fun initializeOnlinePlayers() {
        for (session in CoreModule.players.getAllSessions()) {
            try {
                metabolismService.initializePlayer(session.playerId, metabolismRepository)
            } catch (e: Exception) {
                logger.atWarning().withCause(e)
                    .log("Failed to initialize metabolism for player ${session.playerId}")
            }
        }
        
        val playerCount = metabolismService.getCacheSize()
        if (playerCount > 0) {
            logger.atInfo().log("Initialized metabolism for $playerCount online players")
        }
    }
    
    /**
     * Register the metabolism HUD for a player.
     * Called after metabolism stats are initialized.
     * 
     * **IMPORTANT:** This must be called from within world.execute {} to avoid nested queuing.
     */
    private fun registerHudForPlayer(player: Player, playerRef: PlayerRef, playerId: UUID) {
        if (!metabolismConfig.enabled) return
        
        try {
            // Get world UUID from player's session
            val session = CoreModule.players.getSession(playerId)
            if (session == null) {
                logger.atWarning().log("No session found for player $playerId")
                return
            }
            
            // Get world context
            val worldContext = CoreModule.worlds.getContext(session.worldId)
            
            // Create the HUD element with default preferences (non-blocking)
            val hudElement = MetabolismHudElement(
                playerRef,
                playerId,
                if (::buffsSystem.isInitialized) buffsSystem else null,
                if (::debuffsSystem.isInitialized) debuffsSystem else null
            )
            
            // Register with MultiHudManager (standardized pattern)
            logger.atInfo().log("Registering HUD via MultiHudManager on world thread...")
            CoreModule.hudManager.setHud(player, playerRef, MetabolismHudElement.NAMESPACE, hudElement)
            logger.atInfo().log("Registered metabolism HUD for player $playerId with default preferences")
        } catch (e: Exception) {
            logger.atWarning().withCause(e)
                .log("Failed to register metabolism HUD for player $playerId")
        }
        
        // Load preferences asynchronously (outside world.execute) and update
        persistenceScope.launch {
            try {
                val hudElement = CoreModule.hudManager.getHud<MetabolismHudElement>(playerId, MetabolismHudElement.NAMESPACE)
                if (hudElement != null) {
                    val preferences = metabolismRepository.loadHudPreferences(playerId.toCachedString())
                    hudElement.preferences = preferences
                    logger.atFine().log("Loaded HUD preferences for $playerId")
                }
            } catch (e: Exception) {
                logger.atFine().log("Failed to load HUD preferences for $playerId, using defaults")
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
            // HUD is managed by MultiHudManager (no service tracking needed)
            
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
