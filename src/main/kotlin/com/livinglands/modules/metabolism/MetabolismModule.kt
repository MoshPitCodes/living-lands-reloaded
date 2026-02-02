package com.livinglands.modules.metabolism

import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.events.StartWorldEvent
import com.livinglands.api.AbstractModule
import com.livinglands.core.CoreModule
import com.livinglands.core.PlayerSession
import com.livinglands.modules.metabolism.commands.TestMetabolismCommand
import com.livinglands.modules.metabolism.config.MetabolismConfig
import com.livinglands.modules.metabolism.config.MetabolismConfigValidator
import com.livinglands.modules.metabolism.config.MetabolismConsumablesConfig
import com.livinglands.modules.metabolism.config.ModdedConsumableEntry
import com.livinglands.modules.metabolism.buffs.BuffsSystem
import com.livinglands.modules.metabolism.buffs.DebuffsSystem
import com.livinglands.modules.metabolism.food.ConsumablesScanner
import com.livinglands.modules.metabolism.food.DiscoveredConsumable
import com.livinglands.modules.metabolism.food.FoodConsumptionProcessor
import com.livinglands.modules.metabolism.food.FoodDetectionTickSystem
import com.livinglands.modules.metabolism.food.FoodEffectDetector
import com.livinglands.core.hud.LivingLandsHudElement
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
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
    
    /** Configuration for modded consumables from metabolism_consumables.yml */
    private lateinit var consumablesConfig: MetabolismConsumablesConfig
    
    /** Track if auto-scan has completed (only run once per server start) */
    private var autoScanComplete = false
    
    /** Global metabolism repository (server-wide) */
    private lateinit var metabolismRepository: MetabolismRepository
    
    /** Service for managing metabolism stats */
    private lateinit var metabolismService: MetabolismService
    
    /** Food effect detector */
    private lateinit var foodEffectDetector: FoodEffectDetector
    
    /** Food consumption processor */
    private lateinit var foodConsumptionProcessor: FoodConsumptionProcessor
    
    /** Modded consumables registry (null if disabled) */
    private var moddedConsumablesRegistry: com.livinglands.modules.metabolism.food.modded.ModdedConsumablesRegistry? = null
    
    /** Modded item validator */
    private var moddedItemValidator: com.livinglands.modules.metabolism.food.modded.ModdedItemValidator? = null
    
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
        logger.atFine().log("Metabolism module setting up...")
        
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
        
        // Load consumables config from separate file
        consumablesConfig = CoreModule.config.load(MetabolismConsumablesConfig.CONFIG_NAME, MetabolismConsumablesConfig())
        logger.atFine().log("Loaded consumables config: enabled=${consumablesConfig.enabled}, entries=${consumablesConfig.getEntryCount()}")
        
        // Validate world overrides
        val knownWorldNames = CoreModule.worlds.getAllWorldNames()
        MetabolismConfigValidator.validateOverrides(metabolismConfig, knownWorldNames, logger)
        
        // Create global repository (server-wide metabolism stats)
        metabolismRepository = MetabolismRepository(CoreModule.globalPersistence, logger)
        metabolismRepository.initialize()
        logger.atFine().log("Initialized global metabolism repository")
        
        // Create service
        metabolismService = MetabolismService(metabolismConfig, logger)
        
        // Register services with CoreModule for access by other modules
        CoreModule.services.register<MetabolismRepository>(metabolismRepository)
        CoreModule.services.register<MetabolismService>(metabolismService)
        
        // Resolve world-specific configs from global config + overrides
        initializeWorldConfigs()
        
        // Register StartWorldEvent listener for auto-scan
        // This triggers auto-scan on first world start (ensures Item registry is fully populated)
        registerListenerAny<StartWorldEvent> { event ->
            handleWorldStarted(event)
        }
        logger.atFine().log("Registered StartWorldEvent listener for auto-scan")
        
        // Note: Tick system registration must happen AFTER buffs/debuffs initialization
        // so they can be passed to the tick system constructor
        
        // Initialize food consumption system
        if (metabolismConfig.enabled && metabolismConfig.foodConsumption.enabled) {
            // Initialize modded consumables system (if enabled)
            initializeModdedConsumables()
            
            // Create food detection components (with optional modded registry)
            foodEffectDetector = FoodEffectDetector(logger, moddedConsumablesRegistry)
            foodConsumptionProcessor = FoodConsumptionProcessor(
                metabolismService, 
                metabolismConfig.foodConsumption, 
                logger,
                moddedConsumablesRegistry
            )
            
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
        
        // Initialize buffs and debuffs system
        if (metabolismConfig.enabled) {
            // Create speed manager (centralized speed modification)
            speedManager = SpeedManager(logger)
            
            // Register SpeedManager with ServiceRegistry so other modules (e.g., professions) can use it
            CoreModule.services.register<SpeedManager>(speedManager)
            logger.atFine().log("Registered SpeedManager with ServiceRegistry")
            
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
            
            // Register buffs/debuffs systems with metabolismService for UI access
            metabolismService.setBuffsSystem(buffsSystem)
            metabolismService.setDebuffsSystem(debuffsSystem)
            
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
        
        // Register test command for Metabolism API validation (Phase 0 - Professions Prerequisites)
        CoreModule.mainCommand.registerSubCommand(TestMetabolismCommand())
        logger.atFine().log("Registered /ll testmeta command")
        
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
        
        // Register scan command for discovering consumables
        CoreModule.mainCommand.registerSubCommand(com.livinglands.core.commands.ScanCommand())
        logger.atFine().log("Registered /ll scan command")
        
        // Register config reload callback
        CoreModule.config.onReload("metabolism") {
            onConfigReloaded()
        }
        
        logger.atFine().log("Metabolism module setup complete")
    }
    
    override suspend fun onStart() {
        logger.atFine().log("Metabolism module started")
        
        // Start periodic auto-save to prevent data loss on server crash
        startPeriodicAutoSave()
        
        // Initialize metabolism for any players already online
        initializeOnlinePlayers()
        
        // Trigger auto-scan if config is empty (events might not fire reliably)
        if (shouldAutoScan() && !autoScanComplete) {
            logger.atFine().log("Empty consumables config detected - triggering auto-scan...")
            autoScanComplete = true
            
            // Run scan on default world thread
            val defaultWorld = com.hypixel.hytale.server.core.universe.Universe.get().defaultWorld
            if (defaultWorld != null) {
                defaultWorld.execute {
                    performAutoScan()
                }
            } else {
                logger.atWarning().log("No default world available for auto-scan")
            }
        }
    }
    
    // ============ Periodic Auto-Save ============
    
    /**
     * Start periodic auto-save to prevent data loss on server crash.
     * 
     * Saves all cached player metabolism data every 5 minutes.
     * This prevents stat loss if the server crashes between player login and logout.
     */
    private fun startPeriodicAutoSave() {
        persistenceScope.launch {
            while (true) {
                delay(5 * 60 * 1000)  // 5 minutes
                
                try {
                    val cacheSize = metabolismService.getCacheSize()
                    if (cacheSize > 0) {
                        logger.atFine().log("Periodic auto-save starting for $cacheSize cached players...")
                        metabolismService.saveAllPlayers(metabolismRepository)
                        logger.atFine().log("Periodic auto-save completed")
                    }
                } catch (e: Exception) {
                    logger.atWarning().withCause(e).log("Periodic auto-save failed")
                }
            }
        }
        
        logger.atFine().log("Periodic auto-save started (every 5 minutes)")
    }
    
    // ============ Player Lifecycle Hooks ============
    
    /**
     * Called when a player joins - initialize their metabolism and HUD.
     * Called by plugin through CoreModule after session is registered.
     */
    override suspend fun onPlayerJoin(playerId: UUID, session: PlayerSession) {
        logger.atFine().log("Metabolism: onPlayerJoin called for $playerId")
        
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
        
        // Check if player already has stats cached (world switch scenario)
        // Do this BEFORE world.execute so we can use it in async code after
        val alreadyCached = metabolismService.isPlayerCached(playerId.toCachedString())
        
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
                
                if (!alreadyCached) {
                    // First join - initialize with defaults immediately (non-blocking)
                    metabolismService.initializePlayerWithDefaults(playerId)
                    logger.atFine().log("First join - initialized with defaults for $playerId")
                } else {
                    // World switch - player already has stats, keep them
                    logger.atFine().log("World switch - keeping existing stats for $playerId")
                }
                
                // Register HUD (will show cached stats immediately if available, defaults otherwise)
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
        
        // Load actual stats from database asynchronously (only if not already cached)
        if (!alreadyCached) {
            // First join - load from database
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
        } else {
            // World switch - just re-evaluate buffs/debuffs with existing stats
            // No need to reload from database since stats are global
            persistenceScope.launch {
                val stats = metabolismService.getStats(playerId.toCachedString())
                if (stats != null) {
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
                    
                    // Force HUD refresh with current stats
                    metabolismService.forceUpdateHud(playerId.toCachedString(), playerId)
                    logger.atFine().log("World switch - re-evaluated buffs/debuffs for $playerId")
                }
            }
        }
    }
    
    /**
     * Called when a player disconnects - save their metabolism and cleanup HUD.
     * Called by plugin through CoreModule BEFORE session is unregistered.
     */
    override suspend fun onPlayerDisconnect(playerId: UUID, session: PlayerSession) {
        logger.atFine().log("Metabolism: onPlayerDisconnect called for $playerId")
        
        // Get world context from session
        val worldContext = CoreModule.worlds.getContext(session.worldId)
        if (worldContext == null) {
            logger.atWarning().log("No world context for disconnecting player $playerId")
            metabolismService.removeFromCache(playerId)
            cleanupPlayerRefsOnly(playerId)
            return
        }
        
        // Save HUD preferences before cleanup (fire and forget - not critical if it fails)
        val hudElement = CoreModule.hudManager.getHud(playerId)
        if (hudElement != null) {
            persistenceScope.launch {
                try {
                    metabolismRepository.saveHudPreferences(playerId.toCachedString(), hudElement.metabolismPreferences)
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
            logger.atFine().log("Saved metabolism for disconnecting player $playerId (to global database)")
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
            
            // Cleanup food detection tracking
            if (metabolismConfig.enabled && metabolismConfig.foodConsumption.enabled) {
                foodEffectDetector.cleanup(playerId)
            }
            
            // Cleanup respawn reset system tracking
            if (::respawnResetSystem.isInitialized) {
                respawnResetSystem.cleanup(playerId)
            }
            
            // Always cleanup cache (includes UUID string cache cleanup)
            metabolismService.removeFromCache(playerId)
        }
    }
    
    override suspend fun onShutdown() {
        logger.atFine().log("Metabolism module shutting down...")
        
        // First, cancel the persistence scope to prevent new saves from starting
        // and wait for any in-flight saves to complete (with timeout)
        try {
            logger.atFine().log("Waiting for pending persistence operations...")
            
            // Get the supervisor job to wait on it
            val supervisorJob = persistenceScope.coroutineContext[Job]
            
            // Cancel the scope (signals no new work should start)
            persistenceScope.cancel("Module shutting down")
            
            // Wait for any in-flight coroutines to complete (with 5 second timeout)
            if (supervisorJob != null) {
                withTimeout(5000) {
                    supervisorJob.join()
                }
                logger.atFine().log("All pending persistence operations completed")
            }
        } catch (e: CancellationException) {
            // Expected when timeout occurs or scope is cancelled
            logger.atFine().log("Persistence scope cancelled (some saves may have been interrupted)")
        } catch (e: Exception) {
            logger.atWarning().withCause(e).log("Error waiting for persistence operations")
        }
        
        // Save all remaining players' metabolism data to global database (fallback for any not saved on disconnect)
        try {
            metabolismService.saveAllPlayers(metabolismRepository)
            logger.atFine().log("Saved all remaining metabolism data to global database")
        } catch (e: Exception) {
            logger.atWarning().withCause(e).log("Error saving metabolism data during shutdown")
        }
        
        // Clear the service cache
        metabolismService.clearCache()
        
        // Unregister config reload callback
        CoreModule.config.removeReloadCallback("metabolism")
        
        logger.atFine().log("Metabolism module shutdown complete")
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
        
        // Re-resolve world-specific configs for all existing worlds
        var worldsResolved = 0
        for (worldContext in CoreModule.worlds.getAllContexts()) {
            try {
                worldContext.resolveMetabolismConfig(newConfig)
                worldsResolved++
                logger.atFine().log("Re-resolved metabolism config for world ${worldContext.worldName}")
            } catch (e: Exception) {
                logger.atWarning().withCause(e)
                    .log("Failed to re-resolve metabolism config for world ${worldContext.worldName}")
            }
        }
        logger.atFine().log("Metabolism configs re-resolved for $worldsResolved worlds")
        
        // Reload consumables config from separate file
        reloadConsumablesConfig()
    }
    
    /**
     * Reload consumables config from separate file.
     * Called during config hot-reload.
     */
    private fun reloadConsumablesConfig() {
        // Load fresh consumables config
        consumablesConfig = CoreModule.config.load(MetabolismConsumablesConfig.CONFIG_NAME, MetabolismConsumablesConfig())
        
        if (!consumablesConfig.enabled) {
            // Disable modded consumables
            moddedConsumablesRegistry?.clear()
            moddedItemValidator?.clearCache()
            logger.atFine().log("Modded consumables support disabled on reload")
            return
        }
        
        // Get all entries from new config
        val allEntries = consumablesConfig.getAllEntries()
        
        // Reload existing registry or create new one
        if (moddedConsumablesRegistry != null) {
            moddedConsumablesRegistry?.reload(allEntries)
        } else {
            moddedConsumablesRegistry = com.livinglands.modules.metabolism.food.modded.ModdedConsumablesRegistry(
                allEntries,
                logger
            )
        }
        
        // Clear validator cache and re-validate
        if (moddedItemValidator != null) {
            moddedItemValidator?.clearCache()
        } else {
            moddedItemValidator = com.livinglands.modules.metabolism.food.modded.ModdedItemValidator(logger)
        }
        
        // Validate entries
        if (allEntries.isNotEmpty()) {
            validateModdedEntries(consumablesConfig)
        }
        
        val registryCount = moddedConsumablesRegistry?.getEntryCount() ?: 0
        logger.atFine().log("Modded consumables reloaded: $registryCount entries")
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
     * Initialize modded consumables system if enabled in config.
     * 
     * Creates the registry and validator, then validates all configured entries.
     * Invalid entries are logged as warnings but don't prevent the system from working.
     */
    private fun initializeModdedConsumables() {
        if (!consumablesConfig.enabled) {
            logger.atFine().log("Modded consumables support is disabled")
            moddedConsumablesRegistry = null
            moddedItemValidator = null
            return
        }
        
        // Create registry from flat entry list and validator
        val allEntries = consumablesConfig.getAllEntries()
        moddedConsumablesRegistry = com.livinglands.modules.metabolism.food.modded.ModdedConsumablesRegistry(
            allEntries,
            logger
        )
        moddedItemValidator = com.livinglands.modules.metabolism.food.modded.ModdedItemValidator(logger)
        
        // Validate all configured entries
        if (allEntries.isNotEmpty()) {
            validateModdedEntries(consumablesConfig)
        }
        
        val registryCount = moddedConsumablesRegistry?.getEntryCount() ?: 0
        if (registryCount > 0) {
            logger.atFine().log("Modded consumables support enabled with $registryCount entries")
        } else {
            logger.atFine().log("Modded consumables support enabled (no entries configured)")
        }
    }
    
    /**
     * Validate all modded consumable entries.
     * 
     * Logs warnings for invalid or missing items but doesn't fail.
     * This allows graceful degradation when mods are removed.
     * 
     * @param config The consumables configuration
     */
    private fun validateModdedEntries(config: MetabolismConsumablesConfig) {
        val validator = moddedItemValidator ?: return
        
        var validCount = 0
        var invalidCount = 0
        
        config.getAllEntries().forEach { entry ->
            if (validator.validateEntry(entry, config.warnIfMissing)) {
                validCount++
            } else {
                invalidCount++
            }
        }
        
        if (invalidCount > 0) {
            logger.atFine().log("Modded consumables validation: $validCount valid, $invalidCount invalid")
        } else {
            logger.atFine().log("Modded consumables validation: all $validCount entries valid")
        }
    }
    
    // ============ Auto-Scan System ============
    
    /**
     * Handle StartWorldEvent to trigger auto-scan on first world start.
     * 
     * This event fires when a world starts, ensuring:
     * - Item registry is fully populated with modded items
     * - All asset packs are loaded and registered
     * - AssetMap.getAssetPack() works correctly
     * 
     * Only runs once per server start, and only if consumablesConfig is empty.
     */
    private fun handleWorldStarted(event: StartWorldEvent) {
        // Only run auto-scan once (on first world)
        if (autoScanComplete) {
            return
        }
        
        // Only run if config is empty (no consumables configured)
        if (!shouldAutoScan()) {
            logger.atFine().log("Consumables config already populated (${consumablesConfig.getEntryCount()} entries) - skipping auto-scan")
            autoScanComplete = true
            return
        }
        
        logger.atInfo().log("Empty consumables config detected - running auto-scan...")
        autoScanComplete = true
        
        // Run scan on the world thread (required for Item AssetStore access)
        event.world.execute {
            performAutoScan()
        }
    }
    
    /**
     * Check if auto-scan should run.
     * Returns true if consumablesConfig is empty (no entries configured).
     */
    private fun shouldAutoScan(): Boolean {
        return consumablesConfig.isEmpty()
    }
    
    /**
     * Perform auto-scan of Item registry for consumables.
     * 
     * Discovers all consumable items, groups by namespace, converts to config entries,
     * and saves to metabolism_consumables.yml.
     * 
     * **Thread Safety:** Must be called from within world.execute {} block.
     */
    private fun performAutoScan() {
        try {
            // Scan with empty exclude set (discover all)
            val discovered = ConsumablesScanner.scanItemRegistry(emptySet(), logger)
            
            if (discovered.isEmpty()) {
                logger.atInfo().log("Auto-scan complete: No consumables discovered")
                return
            }
            
            // Group by namespace and convert to config entries
            val groupedByNamespace = discovered.groupBy { it.namespace }
            val newSections = mutableMapOf<String, List<ModdedConsumableEntry>>()
            
            groupedByNamespace.forEach { (namespace, items) ->
                val sectionName = "AutoScan_${LocalDate.now()}_$namespace"
                val entries = items.map { item ->
                    ModdedConsumableEntry(
                        effectId = item.effectId,
                        category = item.category,
                        tier = item.tier,
                        itemId = item.itemId
                    )
                }
                newSections[sectionName] = entries
                logger.atFine().log("Grouped $namespace: ${entries.size} items -> section '$sectionName'")
            }
            
            // Create new config with all namespace sections
            val updatedConfig = consumablesConfig.copy(
                enabled = true,  // Auto-enable when items are discovered
                consumables = consumablesConfig.consumables + newSections
            )
            
            // Save to config file
            CoreModule.config.save(MetabolismConsumablesConfig.CONFIG_NAME, updatedConfig)
            
            // Update local reference
            consumablesConfig = updatedConfig
            
            // Rebuild the consumables registry with new entries
            rebuildConsumablesRegistry()
            
            logger.atInfo().log("✅ Auto-scan complete: Added ${discovered.size} consumables in ${newSections.size} namespace sections to ${MetabolismConsumablesConfig.CONFIG_NAME}.yml")
            
        } catch (e: Exception) {
            logger.atSevere().withCause(e).log("Auto-scan failed")
        }
    }
    
    /**
     * Rebuild the modded consumables registry after config changes.
     * 
     * This is called after auto-scan or manual scan saves new entries.
     * Re-creates the registry with the updated entry list.
     */
    fun rebuildConsumablesRegistry() {
        if (!consumablesConfig.enabled) {
            moddedConsumablesRegistry?.clear()
            return
        }
        
        val allEntries = consumablesConfig.getAllEntries()
        
        // Rebuild registry with new entries
        if (moddedConsumablesRegistry != null) {
            moddedConsumablesRegistry?.reload(allEntries)
        } else {
            moddedConsumablesRegistry = com.livinglands.modules.metabolism.food.modded.ModdedConsumablesRegistry(
                allEntries,
                logger
            )
        }
        
        logger.atFine().log("Rebuilt consumables registry with ${allEntries.size} entries")
    }
    
    /**
     * Get the current consumables configuration.
     * Used by ScanConsumablesCommand to access configured entries.
     */
    fun getConsumablesConfig(): MetabolismConsumablesConfig = consumablesConfig
    
    /**
     * Update the consumables configuration.
     * Used by ScanConsumablesCommand after saving new entries.
     */
    fun updateConsumablesConfig(newConfig: MetabolismConsumablesConfig) {
        consumablesConfig = newConfig
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
            logger.atFine().log("Initialized metabolism for $playerCount online players")
        }
    }
    
    /**
     * Register the unified Living Lands HUD for a player.
     * Called after metabolism stats are initialized.
     * 
     * **IMPORTANT:** This must be called from within world.execute {} to avoid nested queuing.
     * 
     * The unified HUD contains ALL Living Lands UI elements (metabolism, professions, progress panels).
     * This solves Hytale's limitation of only allowing one append() call per CustomUIHud.
     */
    private fun registerHudForPlayer(player: Player, playerRef: PlayerRef, playerId: UUID) {
        if (!metabolismConfig.enabled) return
        
        try {
            // Get ProfessionsService and AbilityRegistry if they're registered
            val professionsService = try {
                CoreModule.services.get<com.livinglands.modules.professions.ProfessionsService>()
            } catch (e: Exception) {
                null
            }
            
            val abilityRegistry = try {
                CoreModule.services.get<com.livinglands.modules.professions.abilities.AbilityRegistry>()
            } catch (e: Exception) {
                null
            }
            
            // Register the unified HUD with MultiHudManager
            logger.atFine().log("Registering unified HUD via MultiHudManager on world thread...")
            CoreModule.hudManager.registerHud(
                player = player,
                playerRef = playerRef,
                playerId = playerId,
                buffsSystem = if (::buffsSystem.isInitialized) buffsSystem else null,
                debuffsSystem = if (::debuffsSystem.isInitialized) debuffsSystem else null,
                professionsService = professionsService,
                abilityRegistry = abilityRegistry
            )
            logger.atFine().log("Registered unified HUD for player $playerId")
        } catch (e: Exception) {
            logger.atWarning().withCause(e)
                .log("Failed to register unified HUD for player $playerId")
        }
        
        // Load preferences asynchronously (outside world.execute) and update
        persistenceScope.launch {
            try {
                val hudElement = CoreModule.hudManager.getHud(playerId)
                if (hudElement != null) {
                    val preferences = metabolismRepository.loadHudPreferences(playerId.toCachedString())
                    hudElement.metabolismPreferences = preferences
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
                CoreModule.hudManager.removeHud(player, playerRef, playerId)
                CoreModule.hudManager.onPlayerDisconnect(playerId)
            } catch (e: Exception) {
                logger.atWarning().withCause(e).log("Error cleaning up HUD for player $playerId")
            }
        }
    }
    
    /**
     * Cleanup the unified Living Lands HUD for a disconnecting player.
     * Note: This is called AFTER playerRefs entry has been removed in onPlayerDisconnect.
     * 
     * @param playerId The player's UUID
     * @param playerRef The player's PlayerRef (from the removed playerRefs entry)
     */
    private fun cleanupHudForPlayer(playerId: UUID, playerRef: PlayerRef) {
        try {
            // Get session to access world for thread-safe ECS operations
            val session = CoreModule.players.getSession(playerId)
            if (session != null) {
                // Execute HUD cleanup on the world thread (required for ECS access)
                session.world.execute {
                    try {
                        val store = session.store
                        val entityRef = session.entityRef
                        
                        // Check if entity ref is still valid (may be invalidated during disconnect)
                        if (entityRef.isValid) {
                            val player = store.getComponent(entityRef, Player.getComponentType())
                            if (player != null) {
                                // Remove unified HUD from MultiHudManager
                                CoreModule.hudManager.removeHud(player, playerRef, playerId)
                            }
                        } else {
                            // Entity already removed, just notify manager
                            logger.atFine().log("Player entity already removed for $playerId, skipping HUD cleanup")
                        }
                    } catch (e: Exception) {
                        logger.atWarning().withCause(e)
                            .log("Error removing unified HUD for player $playerId")
                    }
                }
            }
            
            // Notify MultiHudManager about disconnect (doesn't need world thread)
            CoreModule.hudManager.onPlayerDisconnect(playerId)
            
            debug("Cleaned up unified HUD for player $playerId")
        } catch (e: Exception) {
            logger.atWarning().withCause(e)
                .log("Error cleaning up unified HUD for player $playerId")
        }
    }
}
