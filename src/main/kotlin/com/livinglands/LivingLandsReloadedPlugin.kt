package com.livinglands

import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent
import com.hypixel.hytale.server.core.plugin.JavaPlugin
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
        
        // Register commands
        registerCommands()
        
        // Register event listeners
        registerEventListeners()
        
        // Register and setup all modules
        registerModules()
        setupAllModules()
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
            logger.atInfo().log("World: ${event?.world?.worldConfig?.uuid}")
            try {
                if (event != null && event.world != null) {
                    CoreModule.worlds.onWorldAdded(event)
                }
            } catch (e: Exception) {
                logger.atSevere().withCause(e).log("Error in AddWorldEvent")
            }
        }
        logger.atInfo().log("Registered: AddWorldEvent")
        
        events.register(RemoveWorldEvent::class.java) { event ->
            logger.atInfo().log("=== REMOVE WORLD EVENT FIRED ===")
            logger.atInfo().log("World: ${event?.world?.worldConfig?.uuid}")
            try {
                if (event != null && event.world != null) {
                    CoreModule.worlds.onWorldRemoved(event)
                }
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
     * Register all plugin commands.
     */
    private fun registerCommands() {
        logger.atInfo().log("=== REGISTERING COMMANDS ===")
        try {
            val llCmd = LLCommand()
            logger.atInfo().log("Created LLCommand: name='${llCmd.name}', description='${llCmd.description}'")
            
            commandRegistry.registerCommand(llCmd)
            logger.atInfo().log("LLCommand registered successfully (with subcommands)")
            
            // List all registered commands
            logger.atInfo().log("CommandRegistry type: ${commandRegistry.javaClass.name}")
        } catch (e: Exception) {
            logger.atSevere().withCause(e).log("FAILED to register commands")
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
            worldId = worldId
        )
        
        try {
            CoreModule.players.register(session)
            logger.atInfo().log("Registered player session: $playerId")
        } catch (e: IllegalStateException) {
            logger.atInfo().log(e.message ?: "Session already exists")
            return
        }
        
        // Get or create world context (lazy creation since AddWorldEvent doesn't fire)
        val worldContext = CoreModule.worlds.getOrCreateContext(world)
        
        // Persist player join to database
        val playerRef = player.getPlayerRef()
        val playerName = playerRef?.username ?: "Unknown"
        worldContext.onPlayerJoin(playerId.toString(), playerName)
        logger.atInfo().log("Player joined: $playerName ($playerId) in world ${world.name}")
    }
    
    /**
     * Handle player disconnect event - persist leave and cleanup.
     * 
     * IMPORTANT: We do NOT unregister the session immediately here!
     * Modules (like MetabolismModule) need to access the session to get worldId
     * for saving data. Since event handler ordering is not guaranteed, we must
     * keep the session available during the disconnect event processing.
     * 
     * Session cleanup happens:
     * 1. During module shutdown (saveAllPlayers uses sessions)
     * 2. During plugin shutdown (CoreModule.shutdown clears sessions)
     * 
     * The session will be naturally cleaned up, and keeping it around briefly
     * doesn't cause issues since it's keyed by UUID and will be replaced on rejoin.
     */
    private fun onPlayerDisconnect(event: PlayerDisconnectEvent) {
        val playerRef = event.getPlayerRef()
        @Suppress("DEPRECATION")
        val playerId = playerRef.getUuid()
        
        // Get session WITHOUT removing it - modules still need access
        val session = CoreModule.players.getSession(playerId)
        if (session != null) {
            // Persist player leave to database
            val worldContext = CoreModule.worlds.getContext(session.worldId)
            if (worldContext != null) {
                worldContext.onPlayerLeave(playerId.toString())
            }
            
            logger.atInfo().log("Player disconnecting: $playerId from world ${session.worldId} (session kept for module cleanup)")
        } else {
            logger.atWarning().log("No session found for disconnecting player $playerId")
        }
        
        // NOTE: Session unregister is now handled by MetabolismModule after saving
        // or during module shutdown via saveAllPlayers()
    }
}
