package com.livinglands.api

import com.hypixel.hytale.component.system.ISystem
import com.hypixel.hytale.event.IBaseEvent
import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.command.system.AbstractCommand
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.livinglands.core.CoreModule
import com.livinglands.core.PlayerSession
import com.livinglands.core.logging.LoggingManager
import java.util.UUID

/**
 * Base implementation for modules with common functionality.
 * 
 * Provides:
 * - Automatic state tracking with error handling
 * - Helper methods for registering events, commands, ECS systems
 * - Access to module context and logger
 * - Dependency resolution helpers
 * 
 * Subclasses should override:
 * - onSetup() - required, for initialization logic
 * - onStart() - optional, for start logic
 * - onShutdown() - optional, for cleanup logic
 * - onConfigReload() - optional, for config refresh
 */
abstract class AbstractModule(
    override val id: String,
    override val name: String,
    override val version: String,
    override val dependencies: Set<String> = emptySet()
) : Module {
    
    /** Module context - initialized during setup */
    protected lateinit var context: ModuleContext
    
    /** Logger shorthand - available after setup() is called */
    protected val logger: HytaleLogger 
        get() = if (::context.isInitialized) context.logger else CoreModule.logger
    
    /** Current module state */
    override var state: ModuleState = ModuleState.DISABLED
        protected set
    
    /**
     * Setup phase - wraps onSetup() with state management and error handling.
     * This is final - subclasses should override onSetup() instead.
     */
    final override suspend fun setup(context: ModuleContext) {
        this.context = context
        try {
            onSetup()
            state = ModuleState.SETUP
            logger.atFine().log("Module '$id' setup complete")
        } catch (e: Exception) {
            state = ModuleState.ERROR
            logger.atSevere().withCause(e).log("Failed to setup module $id")
            throw e
        }
    }
    
    /**
     * Start phase - wraps onStart() with state management and error handling.
     * This is final - subclasses should override onStart() instead.
     */
    final override suspend fun start() {
        if (state != ModuleState.SETUP) {
            logger.atWarning().log("Cannot start module '$id' - not in SETUP state (current: $state)")
            return
        }
        
        try {
            onStart()
            state = ModuleState.STARTED
            logger.atFine().log("Module '$id' v$version started")
        } catch (e: Exception) {
            state = ModuleState.ERROR
            logger.atSevere().withCause(e).log("Failed to start module $id")
            throw e
        }
    }
    
    /**
     * Shutdown phase - wraps onShutdown() with state management.
     * This is final - subclasses should override onShutdown() instead.
     * Note: Does not rethrow exceptions to allow other modules to shutdown.
     */
    final override suspend fun shutdown() {
        // Allow shutdown from STARTED or ERROR states
        if (state != ModuleState.STARTED && state != ModuleState.ERROR) {
            logger.atFine().log("Module '$id' not started or in error, skipping shutdown")
            return
        }
        
        try {
            onShutdown()
            state = ModuleState.STOPPED
            logger.atFine().log("Module '$id' shutdown complete")
        } catch (e: Exception) {
            // Don't rethrow - allow other modules to continue shutdown
            state = ModuleState.ERROR
            logger.atSevere().withCause(e).log("Failed to shutdown module $id")
        }
    }
    
    /**
     * Override to perform setup logic.
     * Called during the setup phase after dependencies are set up.
     */
    protected abstract suspend fun onSetup()
    
    /**
     * Override to perform start logic.
     * Called during the start phase after all modules are set up.
     */
    protected open suspend fun onStart() {}
    
    /**
     * Override to perform shutdown logic.
     * Called during shutdown in reverse dependency order.
     */
    protected open suspend fun onShutdown() {}
    
    // ============ Helper Methods ============
    
    /**
     * Helper to get a required dependency module.
     * Throws if the module is not found.
     */
    protected inline fun <reified T : Module> requireModule(moduleId: String): T {
        return CoreModule.getModule<T>(moduleId)
            ?: throw IllegalStateException("Required module '$moduleId' not found for module '$id'")
    }
    
    /**
     * Helper to get an optional dependency module.
     * Returns null if the module is not found.
     */
    protected inline fun <reified T : Module> optionalModule(moduleId: String): T? {
        return CoreModule.getModule(moduleId)
    }
    
    /**
     * Helper to register an event listener for events with any key type.
     * Events are automatically registered with the plugin's event registry.
     * 
     * @param handler The event handler function
     */
    protected inline fun <reified E : IBaseEvent<*>> registerListenerAny(noinline handler: (E) -> Unit) {
        context.eventRegistry.register(E::class.java) { event ->
            try {
                handler(event)
            } catch (e: Exception) {
                logger.atWarning().withCause(e).log("Error in ${E::class.simpleName} handler for module '$id'")
            }
        }
    }
    
    /**
     * Helper to register a GLOBAL event listener for events with any key type.
     * Use this for events like PlayerReadyEvent that need registerGlobal().
     * 
     * @param handler The event handler function
     */
    @Suppress("UNCHECKED_CAST")
    protected inline fun <reified E : Any> registerListenerGlobal(noinline handler: (E) -> Unit) {
        context.eventRegistry.registerGlobal(E::class.java as Class<IBaseEvent<Any>>) { event ->
            try {
                handler(event as E)
            } catch (e: Exception) {
                logger.atWarning().withCause(e).log("Error in ${E::class.simpleName} handler for module '$id'")
            }
        }
    }
    
    /**
     * Helper to register an event listener for events with Void key type.
     * Events are automatically registered with the plugin's event registry.
     * 
     * @param handler The event handler function
     */
    protected inline fun <reified E : IBaseEvent<Void>> registerListener(noinline handler: (E) -> Unit) {
        registerListenerAny(handler)
    }
    
    /**
     * Helper to register an ECS system.
     * Systems are registered with the entity store registry.
     * 
     * @param system The ECS system to register
     */
    protected fun registerSystem(system: ISystem<EntityStore>) {
        context.entityStoreRegistry.registerSystem(system)
    }
    
    /**
     * Helper to register a command.
     * Commands are registered with the plugin's command registry.
     * 
     * @param command The command to register
     */
    protected fun registerCommand(command: AbstractCommand) {
        context.commandRegistry.registerCommand(command)
    }
    
    /**
     * Log a debug message using LoggingManager.
     */
    protected fun debug(message: String) {
        LoggingManager.debug(logger, id) { message }
    }
    
    // ============ Player Lifecycle Hooks ============
    
    /**
     * Called when a player joins the server.
     * Override to handle player join. Default implementation does nothing.
     * 
     * @param playerId The player's UUID
     * @param session The registered player session with entityRef and worldId
     */
    override suspend fun onPlayerJoin(playerId: UUID, session: PlayerSession) {
        // Default: no-op - subclasses can override
    }
    
    /**
     * Called when a player disconnects from the server.
     * Override to save player data. Default implementation does nothing.
     * The session will be unregistered AFTER all modules return.
     * 
     * @param playerId The player's UUID
     * @param session The player session (still valid during this call)
     */
    override suspend fun onPlayerDisconnect(playerId: UUID, session: PlayerSession) {
        // Default: no-op - subclasses can override
    }
}
