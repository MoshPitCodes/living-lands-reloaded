package com.livinglands.modules.professions

import com.livinglands.api.AbstractModule
import com.livinglands.core.CoreModule
import com.livinglands.core.MessageFormatter
import com.livinglands.modules.professions.abilities.AbilityEffectService
import com.livinglands.modules.professions.abilities.AbilityRegistry
import com.livinglands.modules.professions.config.ProfessionsConfig
import com.livinglands.modules.professions.data.Profession
import com.livinglands.modules.professions.migration.V260DataMigration
import com.livinglands.modules.professions.systems.BuildingXpSystem
import com.livinglands.modules.professions.systems.CombatXpSystem
import com.livinglands.modules.professions.systems.GatheringXpSystem
import com.livinglands.modules.professions.systems.LoggingXpSystem
import com.livinglands.modules.professions.systems.MiningXpSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

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
    
    /** Service for applying ability effects */
    private lateinit var abilityEffectService: AbilityEffectService
    
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
    
    /**
     * Shutdown-safe coroutine scope for critical save operations.
     * This scope survives moduleScope.cancel() to ensure player data is saved.
     */
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    /**
     * Counter for pending save operations.
     * Used to wait for saves to complete during shutdown.
     */
    private val pendingSaves = AtomicInteger(0)
    
    /**
     * Track which players were migrated from v2.6.0.
     * Used to send welcome message on first login after migration.
     */
    private val migratedPlayers = mutableSetOf<UUID>()
    
    override suspend fun onSetup() {
        logger.atFine().log("Professions module setting up...")
        
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
        logger.atFine().log("XP Curve: baseXp=${curveStats.baseXp}, multiplier=${curveStats.multiplier}, maxLevel=${curveStats.maxLevel}")
        logger.atFine().log("XP Milestones: L10=${curveStats.level10Xp}, L25=${curveStats.level25Xp}, L50=${curveStats.level50Xp}")
        
        // Create global repository (server-wide profession stats)
        professionsRepository = ProfessionsRepository(CoreModule.globalPersistence, logger)
        professionsRepository.initialize()
        logger.atFine().log("Initialized global professions repository")
        
        // Create ability registry
        abilityRegistry = AbilityRegistry()
        logger.atFine().log("Initialized ability registry with ${abilityRegistry.getAllAbilities().size} abilities")
        
        // Create ability effect service (handles applying ability bonuses)
        abilityEffectService = AbilityEffectService(logger)
        logger.atFine().log("Initialized ability effect service")
        
        // Create service (pass repository for immediate DB saves)
        professionsService = ProfessionsService(professionsConfig, xpCalculator, professionsRepository, logger)
        
        // Register services with CoreModule
        CoreModule.services.register<ProfessionsRepository>(professionsRepository)
        CoreModule.services.register<ProfessionsService>(professionsService)
        CoreModule.services.register<XpCalculator>(xpCalculator)
        CoreModule.services.register<AbilityRegistry>(abilityRegistry)
        CoreModule.services.register<AbilityEffectService>(abilityEffectService)
        
        // Register config reload callback
        CoreModule.config.onReload(ProfessionsConfig.MODULE_ID) {
            handleConfigReload()
        }
        
        // Register commands (must be in onSetup before main command registration)
        CoreModule.mainCommand.registerSubCommand(com.livinglands.modules.professions.commands.ProfessionCommand())
        logger.atFine().log("Registered /ll profession command")
        
        CoreModule.mainCommand.registerSubCommand(com.livinglands.modules.professions.commands.ProgressCommand())
        logger.atFine().log("Registered /ll progress command")
        
        CoreModule.mainCommand.registerSubCommand(com.livinglands.modules.professions.commands.MigrateCommand())
        logger.atFine().log("Registered /ll migrate command")

        // Register admin commands
        CoreModule.mainCommand.registerSubCommand(
            com.livinglands.modules.professions.commands.ProfAdminCommand(professionsService, abilityRegistry, abilityEffectService)
        )
        logger.atFine().log("Registered /ll prof admin command")

        logger.atFine().log("Professions module setup complete")
    }
    
    override suspend fun onStart() {
        logger.atFine().log("Professions module starting...")
        
        if (!professionsConfig.enabled) {
            logger.atFine().log("Professions module is disabled in config")
            return
        }
        
        // Register XP event systems (ECS)
        combatXpSystem = CombatXpSystem(professionsService, abilityRegistry, abilityEffectService, professionsConfig, logger)
        registerSystem(combatXpSystem)
        
        miningXpSystem = MiningXpSystem(professionsService, abilityRegistry, abilityEffectService, professionsConfig, logger)
        registerSystem(miningXpSystem)
        
        loggingXpSystem = LoggingXpSystem(professionsService, abilityRegistry, abilityEffectService, professionsConfig, logger)
        registerSystem(loggingXpSystem)
        
        buildingXpSystem = BuildingXpSystem(professionsService, abilityRegistry, abilityEffectService, professionsConfig, logger)
        registerSystem(buildingXpSystem)
        
        gatheringXpSystem = GatheringXpSystem(professionsService, abilityRegistry, abilityEffectService, professionsConfig, logger)
        registerSystem(gatheringXpSystem)
        
        logger.atFine().log("Registered 5 XP event systems (Combat, Mining, Logging, Building, Gathering)")
        
        // Update HUD manager with profession services so panels can display data
        try {
            CoreModule.hudManager.setProfessionServicesForAll(professionsService, abilityRegistry)
            logger.atFine().log("Updated HUD manager with profession services")
        } catch (e: Exception) {
            logger.atWarning().withCause(e).log("Failed to update HUD manager with profession services")
        }
        
        // Start periodic auto-save to prevent data loss on server crash
        startPeriodicAutoSave()
        
        // Migrate v2.6.0 data if exists
        migrateV260Data()
        
        // TODO: Register admin commands (Phase 11.6)
        // - /ll prof set <player> <profession> <level> (admin)
        // - /ll prof add <player> <profession> <xp> (admin)
        // - /ll prof reset <player> [profession] (admin)
        
        logger.atFine().log("Professions module started")
    }
    
    override suspend fun onShutdown() {
        logger.atFine().log("Professions module shutting down...")
        
        // Cancel moduleScope first to stop new operations
        moduleScope.cancel()
        
        // Wait for any pending save operations to complete (with timeout)
        val maxWaitMs = 5000L
        val startTime = System.currentTimeMillis()
        val pendingCount = pendingSaves.get()
        
        if (pendingCount > 0) {
            logger.atFine().log("Waiting for $pendingCount pending save operation(s) to complete...")
            
            while (pendingSaves.get() > 0 && System.currentTimeMillis() - startTime < maxWaitMs) {
                delay(100)
            }
            
            val remaining = pendingSaves.get()
            if (remaining > 0) {
                logger.atWarning().log("Shutdown timeout with $remaining pending saves - some player data may be lost")
            } else {
                logger.atFine().log("All pending saves completed successfully")
            }
        }
        
        // Save all remaining cached players (bulk save)
        val cachedPlayerCount = professionsService.getCacheSize()
        if (cachedPlayerCount > 0) {
            logger.atFine().log("Saving $cachedPlayerCount remaining players' profession stats...")
            
            try {
                professionsService.saveAllPlayers(professionsRepository)
            } catch (e: Exception) {
                logger.atSevere().withCause(e).log("Failed to save all players during shutdown")
            }
            
            professionsService.clearCache()
            abilityRegistry.clearCache()
            abilityEffectService.clearCache()
        }
        
        // Now cancel saveScope (all critical saves should be done)
        saveScope.cancel()
        
        logger.atFine().log("Professions module shutdown complete")
    }
    
    // ============ Config Reload ============
    
    /**
     * Handle config reload.
     * Called by ConfigManager when professions.yml is reloaded.
     */
    private fun handleConfigReload() {
        logger.atFine().log("Reloading professions config...")
        
        // Reload configuration
        val newConfig = CoreModule.config.load(
            ProfessionsConfig.MODULE_ID,
            ProfessionsConfig()
        )
        
        // Update service config
        professionsService.updateConfig(newConfig)
        professionsConfig = newConfig
        
        logger.atFine().log("Professions config reloaded: enabled=${newConfig.enabled}")
    }
    
    // ============ Periodic Auto-Save ============
    
    /**
     * Start periodic auto-save to prevent data loss on server crash.
     * 
     * Saves all cached player profession data every 5 minutes.
     * This prevents XP loss if the server crashes between player login and logout.
     */
    private fun startPeriodicAutoSave() {
        moduleScope.launch {
            while (true) {
                delay(5 * 60 * 1000)  // 5 minutes
                
                try {
                    val cacheSize = professionsService.getCacheSize()
                    if (cacheSize > 0) {
                        logger.atFine().log("Periodic auto-save starting for $cacheSize cached players...")
                        professionsService.saveAllPlayers(professionsRepository)
                        logger.atFine().log("Periodic auto-save completed")
                    }
                } catch (e: Exception) {
                    logger.atWarning().withCause(e).log("Periodic auto-save failed")
                }
            }
        }
        
        logger.atFine().log("Periodic auto-save started (every 5 minutes)")
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
        
        // Load from database asynchronously, then apply abilities
        moduleScope.launch {
            try {
                // CRITICAL: Single DB load to prevent race condition
                // Wait for DB load to complete
                val statsMap = professionsRepository.ensureStats(playerId.toString())
                
                // Update the in-memory cache with loaded data
                val playerIdStr = playerId.toString()
                statsMap.forEach { (profession, stats) ->
                    val state = professionsService.getState(playerId, profession)
                    if (state != null) {
                        state.setXp(stats.xp, stats.level)
                    }
                }
                
                // Apply unlocked abilities if enabled
                if (statsMap.isNotEmpty() && 
                    (professionsConfig.abilities.tier2ResourceRestore || professionsConfig.abilities.tier3Passives)) {
                    
                    // Use the freshly loaded statsMap directly
                    val professionLevels = statsMap.mapValues { (_, stats) -> stats.level }
                    
                    // Apply all unlocked abilities (Tier 2 at 45, Tier 3 at 100)
                    abilityEffectService.reapplyAllAbilities(playerId, professionLevels)
                }
                
                logger.atFine().log("Loaded and applied abilities for player $playerId")
            } catch (e: Exception) {
                logger.atWarning().withCause(e).log("Failed to load abilities for player $playerId")
            }
        }
        
        // Send migration welcome message if this player was migrated from v2.6.0
        if (migratedPlayers.contains(playerId)) {
            sendMigrationWelcomeMessage(playerId, session)
            migratedPlayers.remove(playerId)  // Only show once
        }
        
        // NOTE: HUD registration is now handled by MetabolismModule via unified LivingLandsHudElement
        // No need to register separate HUD elements here
        
        logger.atFine().log("Initialized professions for player $playerId")
    }
    
    /**
     * Handle player disconnect.
     * Save profession stats to database, cleanup cache.
     * 
     * NOTE: HUD cleanup is handled by MetabolismModule via the unified LivingLandsHudElement.
     * 
     * THREAD SAFETY: Uses saveScope instead of moduleScope to ensure saves complete
     * even during module shutdown. Tracks pending saves with AtomicInteger.
     * 
     * @param playerId Player's UUID
     * @param session Player session
     */
    private fun handlePlayerDisconnect(playerId: UUID, session: com.livinglands.core.PlayerSession) {
        if (!professionsConfig.enabled) return
        
        // Increment pending saves counter
        pendingSaves.incrementAndGet()
        
        // Use saveScope (survives moduleScope.cancel) to ensure saves complete
        saveScope.launch {
            try {
                professionsService.savePlayer(playerId, professionsRepository)
                logger.atFine().log("Saved professions for player $playerId")
            } catch (e: Exception) {
                logger.atSevere().withCause(e).log("Failed to save professions for player $playerId")
            } finally {
                // CRITICAL: Always cleanup cache, even if save failed
                // This prevents memory leaks from players who disconnect during save failures
                professionsService.removeFromCache(playerId)
                abilityRegistry.removePlayer(playerId.toString())
                abilityEffectService.removePlayer(playerId)
                pendingSaves.decrementAndGet()
                
                logger.atFine().log("Cleaned up professions cache for player $playerId")
            }
        }
    }
    
    /**
     * Send migration welcome message to player.
     * Called on first login after v2.6.0 data migration.
     */
    private fun sendMigrationWelcomeMessage(playerId: UUID, session: com.livinglands.core.PlayerSession) {
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
                        // Send welcome message
                        com.livinglands.core.MessageFormatter.success(
                            playerRef,
                            "Welcome back to Living Lands!",
                            "Your v2.6.0 profession data has been migrated to v1.1.0"
                        )
                        
                        // Get migrated professions with levels > 1 to show in message
                        val stats = professionsService.getAllStats(playerId)
                        val migratedProfessions = stats.values.filter { it.level > 1 }
                        
                        if (migratedProfessions.isNotEmpty()) {
                            val profList = migratedProfessions.joinToString(", ") { 
                                "${it.profession.name}: Level ${it.level}"
                            }
                            com.livinglands.core.MessageFormatter.info(
                                playerRef,
                                "Migrated professions:",
                                profList
                            )
                        }
                        
                        logger.atFine().log("Sent migration welcome message to player $playerId")
                    }
                }
            } catch (e: Exception) {
                logger.atWarning().withCause(e).log("Failed to send migration message to $playerId")
            }
        }
    }
    
    /**
     * Migrate v2.6.0 JSON data if it exists.
     * 
     * Runs asynchronously on module startup. Checks for legacy data directory
     * and migrates all player JSON files to new SQLite database.
     */
    /**
     * Migrate v2.6.0 player data if it exists.
     * 
     * CRITICAL: This runs SYNCHRONOUSLY as a suspend function before any player joins.
     * If it runs async, players will join and create default data, causing migration to skip.
     * 
     * Race condition fix: Changed from moduleScope.launch{} to direct suspend call.
     * onStart() is already a suspend function, so we can call other suspend functions directly.
     */
    private suspend fun migrateV260Data() {
        logger.atFine().log("Checking for v2.6.0 data migration...")
        logger.atFine().log("Plugin data directory: ${context.dataDir}")
        
        try {
            val migration = V260DataMigration(professionsRepository, xpCalculator, logger)
            
            // Get plugin data directory (Saves/{world}/mods/MPC_LivingLandsReloaded/)
            // Legacy data is at UserData/Mods/LivingLands/
            val pluginDataDir = context.dataDir
            
            if (migration.hasLegacyData(pluginDataDir)) {
                logger.atFine().log("Detected v2.6.0 legacy data, starting migration...")
                
                // This suspend call will complete before onStart() returns
                // Ensures migration finishes before player joins are processed
                val result = migration.migrate(pluginDataDir)
                
                if (result.migratedPlayers > 0) {
                    logger.atFine().log(
                        "Successfully migrated ${result.migratedPlayers} players from v2.6.0"
                    )
                    // Track migrated players so we can send welcome message on login
                    migratedPlayers.addAll(result.migratedPlayerIds)
                }
                
                if (result.failedPlayers > 0) {
                    logger.atWarning().log(
                        "Failed to migrate ${result.failedPlayers} players (check logs)"
                    )
                }
            } else {
                logger.atFine().log("No v2.6.0 legacy data found, skipping migration")
            }
        } catch (e: Exception) {
            logger.atSevere().withCause(e).log("v2.6.0 migration failed")
        }
        
        logger.atFine().log("v2.6.0 migration check complete")
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
            logger.atFine().log("Applied death penalty to player $playerId: ${result.lostXpMap.size} professions affected, death count: ${result.deathCount}, penalty: ${(result.penaltyPercent * 100).toInt()}%")
            
            // Send chat messages to player about XP loss
            sendDeathPenaltyFeedback(playerId, result.lostXpMap, result.penaltyPercent)
        }
    }
    
    // ============ Dynamic HUD Updates ============
    
    /**
     * Notify HUD when XP is gained.
     * Called by XP systems after awarding XP.
     * 
     * Updates ONLY the professions panels in the unified HUD (if visible).
     * Does NOT trigger a full HUD rebuild - uses efficient update() instead.
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
        
        // Update ONLY the profession panels (efficient - no full rebuild)
        // refreshAllProfessionsPanels() already calls update() internally
        hudElement.refreshAllProfessionsPanels()
        
        // Send player feedback if leveled up
        if (didLevelUp) {
            sendLevelUpFeedback(playerId, profession)
        }
        
        logger.atFine().log("Updated profession panels for player $playerId after XP gain (${profession.name}: +$xpAmount XP)")
    }
    
    /**
     * Send level-up feedback to player with ability unlock check.
     * Also applies ability effects when unlocking Tier 2 (level 45) or Tier 3 (level 100).
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
        
        // Apply ability effects if this level unlocks one
        val tier2Enabled = professionsConfig.abilities.tier2ResourceRestore
        val tier3Enabled = professionsConfig.abilities.tier3Passives
        if (unlockedAbility != null && (tier2Enabled || tier3Enabled)) {
            when (unlockedAbility.tier) {
                2 -> {
                    // Apply Tier 2 ability (level 45)
                    if (tier2Enabled) {
                        abilityEffectService.applyTier2Ability(playerId, profession)
                    }
                }
                3 -> {
                    // Apply Tier 3 ability (level 100)
                    if (tier3Enabled) {
                        abilityEffectService.applyTier3Ability(playerId, profession)
                    }
                }
            }
        }
        
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
