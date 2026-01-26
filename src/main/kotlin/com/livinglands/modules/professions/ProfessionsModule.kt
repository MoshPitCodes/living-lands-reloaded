package com.livinglands.modules.professions

import com.livinglands.api.AbstractModule
import com.livinglands.core.CoreModule
import com.livinglands.core.MessageFormatter
import com.livinglands.modules.professions.abilities.AbilityRegistry
import com.livinglands.modules.professions.config.ProfessionsConfig
import com.livinglands.modules.professions.data.Profession
import com.livinglands.modules.professions.systems.BuildingXpSystem
import com.livinglands.modules.professions.systems.CombatXpSystem
import com.livinglands.modules.professions.systems.GatheringXpSystem
import com.livinglands.modules.professions.systems.LoggingXpSystem
import com.livinglands.modules.professions.systems.MiningXpSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.UUID

/**
 * Professions module - XP-based profession system with passive abilities.
 * 
 * Features:
 * - 5 professions: Combat, Mining, Logging, Building, Gathering
 * - Exponential XP curve with precomputed table
 * - 15 passive abilities (3 per profession, 3 tiers)
 * - Global persistence (stats follow player across worlds)
 * - Death penalty (-85% XP in 2 random professions)
 * - Thread-safe with atomic XP operations
 * 
 * Based on v2.6.0 leveling system with modern Kotlin architecture.
 */
class ProfessionsModule : AbstractModule(
    id = "professions",
    name = "Professions",
    version = "1.0.0",
    dependencies = emptySet()
) {
    
    /** Configuration loaded from professions.yml */
    private lateinit var professionsConfig: ProfessionsConfig
    
    /** Global professions repository (server-wide) */
    private lateinit var professionsRepository: ProfessionsRepository
    
    /** Service for managing profession stats and XP */
    private lateinit var professionsService: ProfessionsService
    
    /** XP calculator with precomputed table */
    private lateinit var xpCalculator: XpCalculator
    
    /** Registry of all 15 abilities */
    private lateinit var abilityRegistry: AbilityRegistry
    
    /** XP event systems (ECS) */
    private lateinit var combatXpSystem: CombatXpSystem
    private lateinit var miningXpSystem: MiningXpSystem
    private lateinit var loggingXpSystem: LoggingXpSystem
    private lateinit var buildingXpSystem: BuildingXpSystem
    private lateinit var gatheringXpSystem: GatheringXpSystem
    
    /**
     * NOTE: HUD elements are now managed by the unified LivingLandsHudElement.
     * The unified HUD is registered by MetabolismModule and contains all Living Lands UI.
     * This module just needs to call refresh methods on the unified HUD when data changes.
     */
    
    /**
     * Module-level coroutine scope for async operations.
     */
    private val moduleScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    override suspend fun onSetup() {
        logger.atInfo().log("Professions module setting up...")
        
        // TODO: Register migrations when we have v2 config
        // CoreModule.config.registerMigrations(
        //     ProfessionsConfig.MODULE_ID,
        //     ProfessionsConfig.getMigrations()
        // )
        
        // Load configuration
        professionsConfig = CoreModule.config.load(
            ProfessionsConfig.MODULE_ID,
            ProfessionsConfig()
        )
        logger.atFine().log("Loaded professions config: enabled=${professionsConfig.enabled}")
        
        // Create XP calculator from config
        xpCalculator = XpCalculator(
            baseXp = professionsConfig.xpCurve.baseXp,
            multiplier = professionsConfig.xpCurve.multiplier,
            maxLevel = professionsConfig.xpCurve.maxLevel
        )
        
        // Log XP curve stats for debugging
        val curveStats = xpCalculator.getStats()
        logger.atInfo().log("XP Curve: baseXp=${curveStats.baseXp}, multiplier=${curveStats.multiplier}, maxLevel=${curveStats.maxLevel}")
        logger.atInfo().log("XP Milestones: L10=${curveStats.level10Xp}, L25=${curveStats.level25Xp}, L50=${curveStats.level50Xp}")
        
        // Create global repository (server-wide profession stats)
        professionsRepository = ProfessionsRepository(CoreModule.globalPersistence, logger)
        professionsRepository.initialize()
        logger.atInfo().log("Initialized global professions repository")
        
        // Create ability registry
        abilityRegistry = AbilityRegistry()
        logger.atInfo().log("Initialized ability registry with ${abilityRegistry.getAllAbilities().size} abilities")
        
        // Create service
        professionsService = ProfessionsService(professionsConfig, xpCalculator, logger)
        
        // Register services with CoreModule
        CoreModule.services.register<ProfessionsRepository>(professionsRepository)
        CoreModule.services.register<ProfessionsService>(professionsService)
        CoreModule.services.register<XpCalculator>(xpCalculator)
        CoreModule.services.register<AbilityRegistry>(abilityRegistry)
        
        // Register config reload callback
        CoreModule.config.onReload(ProfessionsConfig.MODULE_ID) {
            handleConfigReload()
        }
        
        // Register commands (must be in onSetup before main command registration)
        CoreModule.mainCommand.registerSubCommand(com.livinglands.modules.professions.commands.ProfessionCommand())
        logger.atInfo().log("Registered /ll profession command")
        
        CoreModule.mainCommand.registerSubCommand(com.livinglands.modules.professions.commands.ProgressCommand())
        logger.atInfo().log("Registered /ll progress command")
        
        logger.atInfo().log("Professions module setup complete")
    }
    
    override suspend fun onStart() {
        logger.atInfo().log("Professions module starting...")
        
        if (!professionsConfig.enabled) {
            logger.atInfo().log("Professions module is disabled in config")
            return
        }
        
        // Register XP event systems (ECS)
        combatXpSystem = CombatXpSystem(professionsService, abilityRegistry, professionsConfig, logger)
        registerSystem(combatXpSystem)
        
        miningXpSystem = MiningXpSystem(professionsService, abilityRegistry, professionsConfig, logger)
        registerSystem(miningXpSystem)
        
        loggingXpSystem = LoggingXpSystem(professionsService, abilityRegistry, professionsConfig, logger)
        registerSystem(loggingXpSystem)
        
        buildingXpSystem = BuildingXpSystem(professionsService, abilityRegistry, professionsConfig, logger)
        registerSystem(buildingXpSystem)
        
        gatheringXpSystem = GatheringXpSystem(professionsService, abilityRegistry, professionsConfig, logger)
        registerSystem(gatheringXpSystem)
        
        logger.atInfo().log("Registered 5 XP event systems (Combat, Mining, Logging, Building, Gathering)")
        
        // Update HUD manager with profession services so panels can display data
        try {
            CoreModule.hudManager.setProfessionServicesForAll(professionsService, abilityRegistry)
            logger.atInfo().log("Updated HUD manager with profession services")
        } catch (e: Exception) {
            logger.atWarning().withCause(e).log("Failed to update HUD manager with profession services")
        }
        
        // TODO: Register admin commands (Phase 11.6)
        // - /ll prof set <player> <profession> <level> (admin)
        // - /ll prof add <player> <profession> <xp> (admin)
        // - /ll prof reset <player> [profession] (admin)
        
        logger.atInfo().log("Professions module started")
    }
    
    override suspend fun onShutdown() {
        logger.atInfo().log("Professions module shutting down...")
        
        // Save all cached players
        val cachedPlayerCount = professionsService.getCacheSize()
        if (cachedPlayerCount > 0) {
            logger.atInfo().log("Saving $cachedPlayerCount players' profession stats...")
            
            // TODO: Implement bulk save for all cached players
            // For now, just clear the cache
            professionsService.clearCache()
            abilityRegistry.clearCache()
        }
        
        // Cancel async operations
        moduleScope.cancel()
        
        logger.atInfo().log("Professions module shutdown complete")
    }
    
    // ============ Config Reload ============
    
    /**
     * Handle config reload.
     * Called by ConfigManager when professions.yml is reloaded.
     */
    private fun handleConfigReload() {
        logger.atInfo().log("Reloading professions config...")
        
        // Reload configuration
        val newConfig = CoreModule.config.load(
            ProfessionsConfig.MODULE_ID,
            ProfessionsConfig()
        )
        
        // Update service config
        professionsService.updateConfig(newConfig)
        professionsConfig = newConfig
        
        logger.atInfo().log("Professions config reloaded: enabled=${newConfig.enabled}")
    }
    
    // ============ Lifecycle Hooks (called by CoreModule) ============
    
    /**
     * Called when a player joins the server.
     * 
     * @param playerId Player's UUID
     * @param session Player session
     */
    override suspend fun onPlayerJoin(playerId: java.util.UUID, session: com.livinglands.core.PlayerSession) {
        handlePlayerJoin(playerId, session)
    }
    
    /**
     * Called when a player disconnects from the server.
     * 
     * @param playerId Player's UUID
     * @param session Player session
     */
    override suspend fun onPlayerDisconnect(playerId: java.util.UUID, session: com.livinglands.core.PlayerSession) {
        handlePlayerDisconnect(playerId, session)
    }
    
    /**
     * Handle player join.
     * Initialize professions with defaults immediately, load from DB async.
     * 
     * NOTE: HUD registration is handled by MetabolismModule via the unified LivingLandsHudElement.
     * This module just initializes professions data; the unified HUD will display it.
     * 
     * @param playerId Player's UUID
     * @param session Player session
     */
    private fun handlePlayerJoin(playerId: UUID, session: com.livinglands.core.PlayerSession) {
        if (!professionsConfig.enabled) return
        
        // Initialize with defaults immediately (non-blocking)
        professionsService.initializePlayerWithDefaults(playerId)
        
        // Load from database asynchronously
        professionsService.updatePlayerStateAsync(playerId, professionsRepository)
        
        logger.atFine().log("Initialized professions for player $playerId")
    }
    
    /**
     * Handle player disconnect.
     * Save profession stats to database, cleanup cache.
     * 
     * NOTE: HUD cleanup is handled by MetabolismModule via the unified LivingLandsHudElement.
     * 
     * @param playerId Player's UUID
     * @param session Player session
     */
    private fun handlePlayerDisconnect(playerId: UUID, session: com.livinglands.core.PlayerSession) {
        if (!professionsConfig.enabled) return
        
        // Save stats asynchronously
        moduleScope.launch {
            try {
                professionsService.savePlayer(playerId, professionsRepository)
                professionsService.removeFromCache(playerId)
                abilityRegistry.removePlayer(playerId.toString())
                
                logger.atFine().log("Saved and cleaned up professions for player $playerId")
            } catch (e: Exception) {
                logger.atSevere().withCause(e).log("Failed to save professions for player $playerId")
            }
        }
    }
    
    /**
     * Handle player respawn (after death).
     * Apply death penalty if enabled.
     * 
     * @param playerId Player's UUID
     */
    fun handlePlayerRespawn(playerId: UUID) {
        if (!professionsConfig.enabled) return
        if (!professionsConfig.deathPenalty.enabled) return
        
        val result = professionsService.applyDeathPenalty(playerId)
        
        if (result.lostXpMap.isNotEmpty()) {
            logger.atInfo().log("Applied death penalty to player $playerId: ${result.lostXpMap.size} professions affected, death count: ${result.deathCount}, penalty: ${(result.penaltyPercent * 100).toInt()}%")
            
            // Send chat messages to player about XP loss
            sendDeathPenaltyFeedback(playerId, result.lostXpMap, result.penaltyPercent)
        }
    }
    
    // ============ Dynamic HUD Updates ============
    
    /**
     * Notify HUD when XP is gained.
     * Called by XP systems after awarding XP.
     * 
     * Updates the professions panels in the unified HUD (if visible).
     * 
     * @param playerId Player who gained XP
     * @param profession Profession that gained XP
     * @param xpAmount Amount of XP gained (base amount, before multipliers)
     * @param didLevelUp Whether player leveled up
     */
    fun notifyXpGain(playerId: UUID, profession: Profession, xpAmount: Long, didLevelUp: Boolean) {
        // Get the unified HUD element
        val hudElement = CoreModule.hudManager.getHud(playerId)
        if (hudElement == null) {
            logger.atFine().log("No unified HUD found for player $playerId (may not be ready yet)")
            return
        }
        
        // Mark panels for refresh (if visible, will update on next build)
        hudElement.refreshAllProfessionsPanels()
        
        // Send player feedback if leveled up
        if (didLevelUp) {
            sendLevelUpFeedback(playerId, profession)
        }
        
        // Force HUD update to trigger build() immediately
        val session = CoreModule.players.getAllSessions().find { it.playerId == playerId }
        if (session != null) {
            val world = session.world
            world.execute {
                try {
                    val store = session.store
                    val player = store.getComponent(session.entityRef, 
                        com.hypixel.hytale.server.core.entity.entities.Player.getComponentType())
                    if (player != null) {
                        @Suppress("DEPRECATION")
                        val playerRef = player.playerRef
                        if (playerRef != null) {
                            CoreModule.hudManager.refreshHud(player, playerRef)
                            logger.atFine().log("Refreshed unified HUD for player $playerId after XP gain (${profession.name}: +$xpAmount XP)")
                        }
                    }
                } catch (e: Exception) {
                    logger.atWarning().withCause(e).log("Failed to refresh unified HUD for XP gain")
                }
            }
        }
    }
    
    /**
     * Send level-up feedback to player with ability unlock check.
     * 
     * @param playerId Player who leveled up
     * @param profession Profession that leveled up
     */
    private fun sendLevelUpFeedback(playerId: UUID, profession: Profession) {
        // Get player session to send messages
        val session = CoreModule.players.getAllSessions().find { it.playerId == playerId } ?: return
        
        // Get new level
        val newLevel = professionsService.getLevel(playerId, profession)
        val oldLevel = newLevel - 1
        
        // Get profession display name
        val professionName = profession.displayName
        
        // Check if an ability was unlocked at this level
        val abilities = abilityRegistry.getAbilitiesForProfession(profession)
        val unlockedAbility = abilities.find { it.requiredLevel == newLevel }
        
        // Send messages on world thread to access PlayerRef
        val world = session.world
        world.execute {
            try {
                val store = session.store
                val player = store.getComponent(session.entityRef, 
                    com.hypixel.hytale.server.core.entity.entities.Player.getComponentType())
                if (player != null) {
                    @Suppress("DEPRECATION")
                    val playerRef = player.playerRef
                    if (playerRef != null) {
                        if (unlockedAbility != null) {
                            // Level up with ability unlock
                            MessageFormatter.professionLevelUpWithAbility(
                                playerRef,
                                professionName,
                                newLevel,
                                unlockedAbility.name,
                                unlockedAbility.description
                            )
                        } else {
                            // Regular level up
                            MessageFormatter.professionLevelUp(
                                playerRef,
                                professionName,
                                oldLevel,
                                newLevel
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                logger.atWarning().withCause(e).log("Failed to send level-up feedback to player $playerId")
            }
        }
    }
    
    /**
     * Send death penalty feedback to player.
     * 
     * @param playerId Player who died
     * @param lostXpMap Map of professions to XP lost
     * @param penaltyPercent Penalty percentage applied
     */
    private fun sendDeathPenaltyFeedback(playerId: UUID, lostXpMap: Map<Profession, Long>, penaltyPercent: Double) {
        // Get player session to send messages
        val session = CoreModule.players.getAllSessions().find { it.playerId == playerId } ?: return
        
        // Send messages on world thread to access PlayerRef
        val world = session.world
        world.execute {
            try {
                val store = session.store
                val player = store.getComponent(session.entityRef, 
                    com.hypixel.hytale.server.core.entity.entities.Player.getComponentType())
                if (player != null) {
                    @Suppress("DEPRECATION")
                    val playerRef = player.playerRef
                    if (playerRef != null) {
                        // Send a message for each affected profession
                        lostXpMap.forEach { (profession, xpLost) ->
                            MessageFormatter.professionDeathPenalty(
                                playerRef,
                                profession.displayName,
                                xpLost,
                                penaltyPercent
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                logger.atWarning().withCause(e).log("Failed to send death penalty feedback to player $playerId")
            }
        }
    }
}
