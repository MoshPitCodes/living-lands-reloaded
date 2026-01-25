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
        const val VERSION = "1.0.0-beta"
        
        @Volatile
        private var instance: LivingLandsReloadedPlugin? = null
        
        fun getInstance(): LivingLandsReloadedPlugin? = instance
    }
    
    override fun setup() {
        instance = this
        
        // Startup banner
        logger.atInfo().log("========================================")
        logger.atInfo().log("  $PLUGIN_NAME v$VERSION")
        logger.atInfo().log("  https://github.com/MoshPitCodes")
        logger.atInfo().log("========================================")
        logger.atInfo().log("$PLUGIN_NAME setting up...")
        
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
        
        logger.atInfo().log("========================================")
        logger.atInfo().log("  $PLUGIN_NAME v$VERSION - STARTED")
        logger.atInfo().log("  Worlds: ${CoreModule.worlds.getWorldCount()}")
        logger.atInfo().log("  Players: ${CoreModule.players.getSessionCount()}")
        logger.atInfo().log("  Modules: ${CoreModule.getModuleCount()}")
        logger.atInfo().log("========================================")
    }
    
    override fun shutdown() {
        logger.atInfo().log("$PLUGIN_NAME shutting down...")
        
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
        
        // Future modules will be registered here:
        // CoreModule.registerModule(LevelingModule())
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
        logger.atInfo().log("=== REGISTERING EVENT LISTENERS ===")
        val events = eventRegistry
        
        // World lifecycle events
        events.register(AddWorldEvent::class.java) { event ->
            logger.atInfo().log("=== ADD WORLD EVENT FIRED ===")
            logger.atInfo().log("World: ${event.world.worldConfig.uuid}")
            try {
                CoreModule.worlds.onWorldAdded(event)
            } catch (e: Exception) {
                logger.atSevere().withCause(e).log("Error in AddWorldEvent")
            }
        }
        logger.atInfo().log("Registered: AddWorldEvent")
        
        events.register(RemoveWorldEvent::class.java) { event ->
            logger.atInfo().log("=== REMOVE WORLD EVENT FIRED ===")
            logger.atInfo().log("World: ${event.world.worldConfig.uuid}")
            try {
                CoreModule.worlds.onWorldRemoved(event)
            } catch (e: Exception) {
                logger.atSevere().withCause(e).log("Error in RemoveWorldEvent")
            }
        }
        logger.atInfo().log("Registered: RemoveWorldEvent")
        
        // Player lifecycle events
        // NOTE: PlayerReadyEvent has KeyType=String, use registerGlobal (like v2.6.0)
        events.registerGlobal(PlayerReadyEvent::class.java) { event ->
            logger.atInfo().log("=== PLAYER READY EVENT FIRED ===")
            logger.atInfo().log("Player: ${event?.player}")
            try {
                if (event != null) {
                    onPlayerReady(event)
                }
            } catch (e: Exception) {
                logger.atSevere().withCause(e).log("Error in PlayerReadyEvent")
            }
        }
        logger.atInfo().log("Registered: PlayerReadyEvent (global)")
        
        events.register(PlayerDisconnectEvent::class.java) { event ->
            logger.atInfo().log("=== PLAYER DISCONNECT EVENT FIRED ===")
            logger.atInfo().log("PlayerRef: ${event?.playerRef}")
            try {
                if (event != null) {
                    onPlayerDisconnect(event)
                }
            } catch (e: Exception) {
                logger.atSevere().withCause(e).log("Error in PlayerDisconnectEvent")
            }
        }
        logger.atInfo().log("Registered: PlayerDisconnectEvent")
        
        logger.atInfo().log("=== EVENT LISTENERS REGISTERED ===")
    }
    
    /**
     * Create the main command (before module setup).
     * Subcommands will be added during module setup.
     */
    private fun createMainCommand() {
        logger.atInfo().log("=== CREATING MAIN COMMAND ===")
        try {
            val llCmd = LLCommand()
            logger.atInfo().log("Created LLCommand: name='${llCmd.name}', description='${llCmd.description}'")
            
            // Store in CoreModule so modules can register subcommands
            CoreModule.mainCommand = llCmd
            logger.atInfo().log("Main command created (subcommands will be added by modules)")
        } catch (e: Exception) {
            logger.atSevere().withCause(e).log("FAILED to create main command")
            e.printStackTrace()
        }
    }
    
    /**
     * Register the main command (after modules have added their subcommands).
     */
    private fun registerMainCommand() {
        logger.atInfo().log("=== REGISTERING MAIN COMMAND ===")
        try {
            commandRegistry.registerCommand(CoreModule.mainCommand)
            logger.atInfo().log("LLCommand registered successfully with all subcommands")
        } catch (e: Exception) {
            logger.atSevere().withCause(e).log("FAILED to register main command")
            e.printStackTrace()
        }
        logger.atInfo().log("=== COMMAND REGISTRATION COMPLETE ===")
    }
    
    /**
     * Handle player ready event - create session and track player.
     * Using registerGlobal() like v2.6.0 did.
     */
    @Suppress("DEPRECATION")
    private fun onPlayerReady(event: PlayerReadyEvent) {
        logger.atInfo().log(">>> onPlayerReady() called")
        val player = event.player
        logger.atInfo().log("Player object: $player")
        
        // Note: getUuid() and getPlayerRef() are deprecated but necessary
        val playerId = player.getUuid()
        logger.atInfo().log("Player UUID: $playerId")
        
        if (playerId == null) {
            logger.atInfo().log("Player has no UUID, skipping session creation")
            return
        }
        
        val world = player.world
        logger.atInfo().log("Player world: $world")
        
        if (world == null) {
            logger.atInfo().log("Player $playerId has no world, skipping session creation")
            return
        }
        
        val worldId = world.worldConfig.uuid
        logger.atInfo().log("World UUID: $worldId")
        
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
            logger.atInfo().log("Registered new player session: $playerId in world ${world.name}")
            null
        } else {
            val old = CoreModule.players.update(session)
            logger.atInfo().log("Updated player session: $playerId switched from world ${old?.world?.name} to ${world.name}")
            old
        }
        
        // Get or create world context (lazy creation since AddWorldEvent doesn't fire)
        val worldContext = CoreModule.worlds.getOrCreateContext(world)
        
        // Persist player join to database
        val playerRef = player.getPlayerRef()
        val playerName = playerRef?.username ?: "Unknown"
        worldContext.onPlayerJoin(playerId.toString(), playerName)
        logger.atInfo().log("Player joined: $playerName ($playerId) in world ${world.name}")
        
        // Notify all modules of player join (after session is registered)
        runBlocking {
            CoreModule.notifyPlayerJoin(playerId, session)
        }
        logger.atInfo().log("All modules notified of player join: $playerId")
        
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
     * 2. Notify all modules via CoreModule.notifyPlayerDisconnect() - modules save data
     * 3. Persist player leave to database
     * 4. Unregister session AFTER all modules have completed
     * 
     * Note: PlayerDisconnectEvent may fire multiple times (e.g., during server shutdown).
     * This handler is idempotent - if session doesn't exist, it silently returns.
     */
    private fun onPlayerDisconnect(event: PlayerDisconnectEvent) {
        val playerRef = event.getPlayerRef()
        @Suppress("DEPRECATION")
        val playerId = playerRef.getUuid()
        
        // Get session - if it doesn't exist, player already disconnected
        // This is normal for duplicate events (e.g., server shutdown)
        val session = CoreModule.players.getSession(playerId) ?: return
        
        logger.atInfo().log("Player disconnecting: $playerId from world ${session.worldId}")
        
        // Notify all modules FIRST (they save their data)
        // This is blocking - all modules must complete before we continue
        runBlocking {
            CoreModule.notifyPlayerDisconnect(playerId, session)
        }
        logger.atInfo().log("All modules notified of player disconnect: $playerId")
        
        // Persist player leave to database
        val worldContext = CoreModule.worlds.getContext(session.worldId)
        if (worldContext != null) {
            worldContext.onPlayerLeave(playerId.toString())
            logger.atInfo().log("Persisted player leave to database: $playerId")
        }
        
        // Unregister session AFTER all modules have saved
        CoreModule.players.unregister(playerId)
        logger.atInfo().log("Unregistered player session: $playerId")
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
