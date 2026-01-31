package com.livinglands

import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import java.awt.Color
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import com.hypixel.hytale.server.core.universe.world.events.AddWorldEvent
import com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent
import com.livinglands.api.ModuleContext

import com.livinglands.core.CoreModule
import com.livinglands.core.PlayerSession
import com.livinglands.core.commands.LLCommand
import com.livinglands.modules.metabolism.MetabolismModule
import com.livinglands.modules.professions.ProfessionsModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Living Lands Reloaded - Survival mechanics mod for Hytale.
 * 
 * Implements hunger, thirst, and energy systems with per-world player progression.
 */
class LivingLandsReloadedPlugin(init: JavaPluginInit) : JavaPlugin(init) {
    
    companion object {
        const val PLUGIN_ID = "livinglandsreloaded"
        const val PLUGIN_NAME = "Living Lands Reloaded"
        const val VERSION = "1.0.1"
        
        @Volatile
        private var instance: LivingLandsReloadedPlugin? = null
        
        fun getInstance(): LivingLandsReloadedPlugin? = instance
    }
    
    // Coroutine scope for player lifecycle events (fire-and-forget pattern)
    private val playerLifecycleScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    override fun setup() {
        instance = this
        
        // Startup banner
        logger.atFine().log("========================================")
        logger.atFine().log("  $PLUGIN_NAME v$VERSION")
        logger.atFine().log("  https://github.com/MoshPitCodes")
        logger.atFine().log("========================================")
        logger.atFine().log("$PLUGIN_NAME setting up...")
        
        // Initialize core module
        CoreModule.initialize(this)
        
        // Create main command (but don't register yet)
        createMainCommand()
        
        // Register event listeners
        registerEventListeners()
        
        // Register and setup all modules (they will add subcommands to main command)
        registerModules()
        setupAllModules()
        
        // Now register the main command with all subcommands
        registerMainCommand()
    }
    
    override fun start() {
        // Start all modules
        startAllModules()
        
        logger.atFine().log("========================================")
        logger.atFine().log("  $PLUGIN_NAME v$VERSION - STARTED")
        logger.atFine().log("  Worlds: ${CoreModule.worlds.getWorldCount()}")
        logger.atFine().log("  Players: ${CoreModule.players.getSessionCount()}")
        logger.atFine().log("  Modules: ${CoreModule.getModuleCount()}")
        logger.atFine().log("========================================")
    }
    
    override fun shutdown() {
        logger.atFine().log("$PLUGIN_NAME shutting down...")
        
        // Shutdown all modules first
        shutdownAllModules()
        
        // Shutdown core module
        CoreModule.shutdown()
        
        instance = null
    }
    
    /**
     * Register all modules with CoreModule.
     * Modules are registered but not yet setup.
     */
    private fun registerModules() {
        // Register all modules here
        CoreModule.registerModule(MetabolismModule())
        CoreModule.registerModule(ProfessionsModule())
        CoreModule.registerModule(com.livinglands.modules.announcer.AnnouncerModule())
        
        // Future modules will be registered here:
        // CoreModule.registerModule(ClaimsModule())
        
        logger.atFine().log("Registered ${CoreModule.getModuleCount()} modules")
    }
    
    /**
     * Setup all registered modules.
     * Creates module context and calls setupModules on CoreModule.
     */
    private fun setupAllModules() {
        val context = createModuleContext()
        
        runBlocking {
            CoreModule.setupModules(context)
        }
    }
    
    /**
     * Start all modules that were successfully setup.
     */
    private fun startAllModules() {
        runBlocking {
            CoreModule.startModules()
        }
    }
    
    /**
     * Shutdown all modules in reverse dependency order.
     */
    private fun shutdownAllModules() {
        runBlocking {
            CoreModule.shutdownModules()
        }
    }
    
    /**
     * Create the module context with all necessary references.
     */
    private fun createModuleContext(): ModuleContext {
        return ModuleContext(
            plugin = this,
            logger = logger,
            dataDir = CoreModule.dataDir.toPath(),
            configDir = CoreModule.configDir.toPath(),
            eventRegistry = eventRegistry,
            commandRegistry = commandRegistry,
            entityStoreRegistry = entityStoreRegistry
        )
    }
    
    /**
     * Register all event listeners for core functionality.
     */
    private fun registerEventListeners() {
        logger.atFine().log("=== REGISTERING EVENT LISTENERS ===")
        val events = eventRegistry
        
        // World lifecycle events
        events.register(AddWorldEvent::class.java) { event ->
            logger.atFine().log("=== ADD WORLD EVENT FIRED ===")
            logger.atFine().log("World: ${event.world.worldConfig.uuid}")
            try {
                CoreModule.worlds.onWorldAdded(event)
            } catch (e: Exception) {
                logger.atSevere().withCause(e).log("Error in AddWorldEvent")
            }
        }
        logger.atFine().log("Registered: AddWorldEvent")
        
        events.register(RemoveWorldEvent::class.java) { event ->
            logger.atFine().log("=== REMOVE WORLD EVENT FIRED ===")
            logger.atFine().log("World: ${event.world.worldConfig.uuid}")
            try {
                CoreModule.worlds.onWorldRemoved(event)
            } catch (e: Exception) {
                logger.atSevere().withCause(e).log("Error in RemoveWorldEvent")
            }
        }
        logger.atFine().log("Registered: RemoveWorldEvent")
        
        // Player lifecycle events
        // NOTE: PlayerReadyEvent has KeyType=String, use registerGlobal (like v2.6.0)
        events.registerGlobal(PlayerReadyEvent::class.java) { event ->
            logger.atFine().log("=== PLAYER READY EVENT FIRED ===")
            logger.atFine().log("Player: ${event?.player}")
            try {
                if (event != null) {
                    onPlayerReady(event)
                }
            } catch (e: Exception) {
                logger.atSevere().withCause(e).log("Error in PlayerReadyEvent")
            }
        }
        logger.atFine().log("Registered: PlayerReadyEvent (global)")
        
        events.register(PlayerDisconnectEvent::class.java) { event ->
            logger.atFine().log("=== PLAYER DISCONNECT EVENT FIRED ===")
            logger.atFine().log("PlayerRef: ${event?.playerRef}")
            try {
                if (event != null) {
                    onPlayerDisconnect(event)
                }
            } catch (e: Exception) {
                logger.atSevere().withCause(e).log("Error in PlayerDisconnectEvent")
            }
        }
        logger.atFine().log("Registered: PlayerDisconnectEvent")
        
        logger.atFine().log("=== EVENT LISTENERS REGISTERED ===")
    }
    
    /**
     * Create the main command (before module setup).
     * Subcommands will be added during module setup.
     */
    private fun createMainCommand() {
        logger.atFine().log("=== CREATING MAIN COMMAND ===")
        try {
            val llCmd = LLCommand()
            logger.atFine().log("Created LLCommand: name='${llCmd.name}', description='${llCmd.description}'")
            
            // Store in CoreModule so modules can register subcommands
            CoreModule.mainCommand = llCmd
            logger.atFine().log("Main command created (subcommands will be added by modules)")
        } catch (e: Exception) {
            logger.atSevere().withCause(e).log("FAILED to create main command")
            e.printStackTrace()
        }
    }
    
    /**
     * Register the main command (after modules have added their subcommands).
     */
    private fun registerMainCommand() {
        logger.atFine().log("=== REGISTERING MAIN COMMAND ===")
        try {
            commandRegistry.registerCommand(CoreModule.mainCommand)
            logger.atFine().log("LLCommand registered successfully with all subcommands")
        } catch (e: Exception) {
            logger.atSevere().withCause(e).log("FAILED to register main command")
            e.printStackTrace()
        }
        logger.atFine().log("=== COMMAND REGISTRATION COMPLETE ===")
    }
    
    /**
     * Handle player ready event - create session and track player.
     * Using registerGlobal() like v2.6.0 did.
     */
    @Suppress("DEPRECATION")
    private fun onPlayerReady(event: PlayerReadyEvent) {
        logger.atFine().log(">>> onPlayerReady() called")
        val player = event.player
        logger.atFine().log("Player object: $player")
        
        // Note: getUuid() and getPlayerRef() are deprecated but necessary
        val playerId = player.getUuid()
        logger.atFine().log("Player UUID: $playerId")
        
        if (playerId == null) {
            logger.atFine().log("Player has no UUID, skipping session creation")
            return
        }
        
        val world = player.world
        logger.atFine().log("Player world: $world")
        
        if (world == null) {
            logger.atFine().log("Player $playerId has no world, skipping session creation")
            return
        }
        
        val worldId = world.worldConfig.uuid
        logger.atFine().log("World UUID: $worldId")
        
        // Get the entity reference from the event (this is Ref<EntityStore>)
        val entityRef = event.playerRef
        
        val session = PlayerSession(
            playerId = playerId,
            entityRef = entityRef,
            store = entityRef.store,
            worldId = worldId,
            world = world
        )
        
        // Check if this is a new session or an update (world switch)
        val isNewSession = !CoreModule.players.hasSession(playerId)
        val oldSession = if (isNewSession) {
            CoreModule.players.register(session)
            logger.atFine().log("Registered new player session: $playerId in world ${world.name}")
            null
        } else {
            val old = CoreModule.players.update(session)
            logger.atFine().log("Updated player session: $playerId switched from world ${old?.world?.name} to ${world.name}")
            old
        }
        
        // Get or create world context (lazy creation since AddWorldEvent doesn't fire)
        val worldContext = CoreModule.worlds.getOrCreateContext(world)
        
        // Persist player join to database (fire-and-forget, non-blocking)
        val playerRef = player.getPlayerRef()
        val playerName = playerRef?.username ?: "Unknown"
        worldContext.onPlayerJoin(playerId.toString(), playerName)
        logger.atFine().log("Player joined: $playerName ($playerId) in world ${world.name}")
        
        // Notify all modules of player join asynchronously (non-blocking)
        // Each module handles their own async initialization internally
        playerLifecycleScope.launch {
            try {
                CoreModule.notifyPlayerJoin(playerId, session)
                logger.atFine().log("All modules notified of player join: $playerId")
            } catch (e: Exception) {
                logger.atSevere().withCause(e).log("Error notifying modules of player join: $playerId")
            }
        }
        
        // Send world config info to player (if metabolism module is loaded)
        try {
            val metabolismService = CoreModule.services.get<com.livinglands.modules.metabolism.MetabolismService>()
            if (metabolismService != null) {
                val worldConfig = metabolismService.getConfigForWorld(worldContext)
                
                // Check if world has custom config
                val hasOverride = worldContext.metabolismConfig != null && 
                                  worldContext.metabolismConfig != metabolismService.getConfig()
            
                if (hasOverride) {
                    // World has custom config - inform player
                    playerRef?.sendMessage(
                        Message.raw("[").color(Color(85, 85, 85))
                            .insert(Message.raw("Living Lands").color(Color(255, 170, 0)))
                            .insert(Message.raw("]").color(Color(85, 85, 85)))
                            .insert(Message.raw(" World '${world.name}' has custom metabolism config").color(Color(85, 255, 255)))
                            .insert(Message.raw(" (hunger: ${formatRate(worldConfig.hunger.baseDepletionRateSeconds)}, thirst: ${formatRate(worldConfig.thirst.baseDepletionRateSeconds)}, energy: ${formatRate(worldConfig.energy.baseDepletionRateSeconds)})").color(Color(170, 170, 170)))
                    )
                } else {
                    // Using global defaults
                    playerRef?.sendMessage(
                        Message.raw("[").color(Color(85, 85, 85))
                            .insert(Message.raw("Living Lands").color(Color(255, 170, 0)))
                            .insert(Message.raw("]").color(Color(85, 85, 85)))
                            .insert(Message.raw(" World '${world.name}' using global metabolism config").color(Color(85, 255, 85)))
                    )
                }
            }
        } catch (e: Exception) {
            // Metabolism module might not be loaded, silently continue
            logger.atFine().log("Could not send world config info: ${e.message}")
        }
    }
    
    /**
     * Handle player disconnect event - notify modules and cleanup.
     * 
     * Session lifecycle:
     * 1. Get session (must exist)
     * 2. Persist player leave to database (fire-and-forget)
     * 3. Notify all modules FIRST via CoreModule.notifyPlayerDisconnect() - modules save data
     * 4. Unregister session AFTER modules have completed saving
     * 
     * Note: PlayerDisconnectEvent may fire multiple times (e.g., during server shutdown).
     * This handler is idempotent - if session doesn't exist, it silently returns.
     * 
     * CRITICAL: Session must remain registered until modules complete saving, as some modules
     * may need to access session data (world context, entity ref) for proper cleanup.
     */
    private fun onPlayerDisconnect(event: PlayerDisconnectEvent) {
        val playerRef = event.getPlayerRef()
        @Suppress("DEPRECATION")
        val playerId = playerRef.getUuid()
        
        // Get session - if it doesn't exist, player already disconnected
        // This is normal for duplicate events (e.g., server shutdown)
        val session = CoreModule.players.getSession(playerId) ?: return
        
        logger.atFine().log("Player disconnecting: $playerId from world ${session.worldId}")
        
        // Persist player leave to database (fire-and-forget, non-blocking)
        val worldContext = CoreModule.worlds.getContext(session.worldId)
        if (worldContext != null) {
            worldContext.onPlayerLeave(playerId.toString())
        }
        
        // Notify all modules FIRST, then unregister session AFTER modules complete
        // This ensures modules can access session data during save if needed
        playerLifecycleScope.launch {
            try {
                // Notify modules - they save their data here
                CoreModule.notifyPlayerDisconnect(playerId, session)
                logger.atFine().log("All modules notified of player disconnect: $playerId")
            } catch (e: Exception) {
                logger.atSevere().withCause(e).log("Error notifying modules of player disconnect: $playerId")
            } finally {
                // ALWAYS unregister session after modules complete (or fail) to prevent memory leak
                CoreModule.players.unregister(playerId)
                logger.atFine().log("Unregistered player session: $playerId")
            }
        }
    }
    
    /**
     * Format depletion rate in seconds to human-readable string.
     * Examples: 2880s → "48min", 960s → "16min", 300s → "5min"
     */
    private fun formatRate(seconds: Double): String {
        val minutes = seconds / 60.0
        return if (minutes >= 60) {
            "%.1fh".format(minutes / 60.0)
        } else {
            "%.0fmin".format(minutes)
        }
    }
}
