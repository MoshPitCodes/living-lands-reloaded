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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withTimeout

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
    
    /** Resource tracker for cleanup reporting */
    private val resourceTracker = ModuleResourceTracker(id)
    
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
     * 
     * If onStart() returns without calling any operations (early return due to config disabled),
     * the module will be in STARTED state. Subclasses should call [markDisabledByConfig] 
     * to properly indicate the module is disabled by configuration.
     */
    final override suspend fun start() {
        if (state != ModuleState.SETUP) {
            logger.atWarning().log("Cannot start module '$id' - not in SETUP state (current: $state)")
            return
        }
        
        try {
            onStart()
            // Only set to STARTED if not already marked as DISABLED_BY_CONFIG
            if (state == ModuleState.SETUP) {
                state = ModuleState.STARTED
                logger.atFine().log("Module '$id' v$version started")
            }
        } catch (e: Exception) {
            state = ModuleState.ERROR
            logger.atSevere().withCause(e).log("Failed to start module $id")
            throw e
        }
    }
    
    /**
     * Mark this module as disabled by configuration.
     * Call this in onStart() when the module's enabled config is false.
     * 
     * This sets the state to DISABLED_BY_CONFIG, which:
     * - Prevents player lifecycle hooks from being called
     * - Causes ModuleCommand subclasses to show "disabled" message
     * - Allows the module to be re-enabled via config hot-reload
     * 
     * Example usage in onStart():
     * ```kotlin
     * override suspend fun onStart() {
     *     if (!config.enabled) {
     *         markDisabledByConfig()
     *         return
     *     }
     *     // ... normal start logic
     * }
     * ```
     */
    protected fun markDisabledByConfig() {
        state = ModuleState.DISABLED_BY_CONFIG
        logger.atFine().log("Module '$id' is disabled by configuration")
    }
    
    /**
     * Mark this module as started after being disabled by config.
     * Call this during config hot-reload when the module is re-enabled.
     * 
     * This is for use during onConfigReload() when the module was DISABLED_BY_CONFIG
     * and the config has changed to enabled=true.
     */
    protected fun markStarted() {
        if (state == ModuleState.DISABLED_BY_CONFIG) {
            state = ModuleState.STARTED
            logger.atFine().log("Module '$id' enabled via config reload")
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
             
             // Report cleanup information
             val report = resourceTracker.getCleanupReport()
             if (report.warnings.isNotEmpty()) {
                 logger.atWarning().log(report.formatSummary())
            }
         } catch (e: Exception) {
             // Don't rethrow - allow other modules to continue shutdown
             state = ModuleState.ERROR
             logger.atSevere().withCause(e).log("Failed to shutdown module $id")
             
             // Still report cleanup information for error case
             val report = resourceTracker.getCleanupReport()
             if (report.warnings.isNotEmpty()) {
                 logger.atWarning().log("Cleanup warnings during error shutdown for module '$id':")
                 report.warnings.forEach { warning ->
                     logger.atWarning().log("  - $warning")
                 }
             }
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
      * Safe dependency resolution for optional modules.
      * 
      * Returns the module only if it exists AND is in operational state (STARTED).
      * Returns null if:
      * - The module is not registered
      * - The module is not in STARTED state (setup, disabled, error, etc.)
      * 
      * Use this when your module has optional dependencies on other modules.
      * Logs warnings for diagnostic purposes.
      * 
      * Example:
      * ```kotlin
      * val metabolismModule = safeModule<MetabolismModule>("metabolism")
      * if (metabolismModule != null) {
      *     // Use metabolism module features
      *     val service = metabolismModule.metabolismService
      * } else {
      *     logger.atFine().log("Metabolism module not available - running in degraded mode")
      * }
      * ```
      * 
      * @param moduleId The ID of the module to look up
      * @return The module if found and operational, null otherwise
      */
     protected inline fun <reified T : Module> safeModule(moduleId: String): T? {
         val module = CoreModule.getModule<T>(moduleId)
         
         return when {
             module == null -> {
                 LoggingManager.debug(logger, id) {
                     "Optional dependency module '$moduleId' is not registered"
                 }
                 null
             }
             !module.state.isOperational() -> {
                 LoggingManager.debug(logger, id) {
                     "Optional dependency module '$moduleId' is not operational (state: ${module.state})"
                 }
                 null
             }
             else -> module
         }
     }
     
     /**
      * Safe service resolution with module state checking.
      * 
      * Returns the service only if:
      * - The providing module exists
      * - The providing module is in operational state (STARTED)
      * - The service is registered with CoreModule.services
      * 
      * Returns null if any condition fails. Logs warnings for diagnostic purposes.
      * 
      * Use this for services provided by optional modules.
      * 
      * Example:
      * ```kotlin
      * val professionsService = safeService<ProfessionsService>("professions")
      * if (professionsService != null) {
      *     val xp = professionsService.getPlayerXp(playerId, profession)
      * } else {
      *     logger.atFine().log("Professions module not available")
      * }
      * ```
      * 
      * @param moduleId The ID of the module that provides this service
      * @return The service if the module is operational and service is registered, null otherwise
      */
     protected inline fun <reified T : Any> safeService(moduleId: String): T? {
         // First check if the module is operational
         val module = CoreModule.getModule<Module>(moduleId)
         
         if (module == null) {
             LoggingManager.debug(logger, id) {
                 "Service module '$moduleId' is not registered"
             }
             return null
         }
         
         if (!module.state.isOperational()) {
             LoggingManager.debug(logger, id) {
                 "Service module '$moduleId' is not operational (state: ${module.state})"
             }
             return null
         }
         
         // Try to get the service
         return try {
             CoreModule.services.get<T>()
         } catch (e: Exception) {
             LoggingManager.debug(logger, id) {
                 "Service from module '$moduleId' not found: ${e.message}"
             }
             null
         }
     }
    
     /**
      * Helper to register an event listener for events with any key type.
      * Events are automatically registered with the plugin's event registry.
      * 
      * NOTE: Must be called during setup phase (onSetup()). Calling from onStart()
      * or later will throw an error as the module has already moved past SETUP state.
      * 
      * WARNING: Since Hytale's EventRegistry does not provide an unregister API,
      * event listeners cannot be unregistered. This listener will persist for the
      * lifetime of the server, even if the module is disabled or removed.
      * 
      * @param handler The event handler function
      * @throws IllegalStateException if called outside setup phase
      */
     protected inline fun <reified E : IBaseEvent<*>> registerListenerAny(noinline handler: (E) -> Unit) {
         require(state == ModuleState.SETUP) {
             "Cannot register listener for ${E::class.simpleName} - module '$id' is in $state state, not SETUP. " +
             "Listeners must be registered during onSetup()."
         }
         resourceTracker.trackEventListener(E::class.simpleName ?: E::class.java.simpleName)
         context.eventRegistry.register(E::class.java) { event ->
             try {
                 // Only handle event if module is in operational state
                 if (state == ModuleState.STARTED) {
                     handler(event)
                 }
             } catch (e: Exception) {
                 logger.atWarning().withCause(e).log("Error in ${E::class.simpleName} handler for module '$id'")
             }
         }
     }
    
     /**
      * Helper to register a GLOBAL event listener for events with any key type.
      * Use this for events like PlayerReadyEvent that need registerGlobal().
      * 
      * NOTE: Must be called during setup phase (onSetup()). Calling from onStart()
      * or later will throw an error as the module has already moved past SETUP state.
      * 
      * WARNING: Since Hytale's EventRegistry does not provide an unregister API,
      * event listeners cannot be unregistered. This listener will persist for the
      * lifetime of the server, even if the module is disabled or removed.
      * 
      * @param handler The event handler function
      * @throws IllegalStateException if called outside setup phase
      */
     @Suppress("UNCHECKED_CAST")
     protected inline fun <reified E : Any> registerListenerGlobal(noinline handler: (E) -> Unit) {
         require(state == ModuleState.SETUP) {
             "Cannot register global listener for ${E::class.simpleName} - module '$id' is in $state state, not SETUP. " +
             "Listeners must be registered during onSetup()."
         }
         resourceTracker.trackEventListener("(global) ${E::class.simpleName ?: E::class.java.simpleName}")
         context.eventRegistry.registerGlobal(E::class.java as Class<IBaseEvent<Any>>) { event ->
             try {
                 // Only handle event if module is in operational state
                 if (state == ModuleState.STARTED) {
                     handler(event as E)
                 }
             } catch (e: Exception) {
                 logger.atWarning().withCause(e).log("Error in ${E::class.simpleName} handler for module '$id'")
             }
         }
     }
    
     /**
      * Helper to register an event listener for events with Void key type.
      * Events are automatically registered with the plugin's event registry.
      * 
      * NOTE: Must be called during setup phase (onSetup()). This is a convenience
      * wrapper around registerListenerAny() which enforces the same constraint.
      * 
      * @param handler The event handler function
      * @throws IllegalStateException if called outside setup phase
      */
     protected inline fun <reified E : IBaseEvent<Void>> registerListener(noinline handler: (E) -> Unit) {
         registerListenerAny(handler)
     }
    
     /**
      * Helper to register an ECS system.
      * Systems are registered with the entity store registry.
      * 
      * NOTE: Must be called during setup phase (onSetup()). Calling from onStart()
      * or later will throw an error as the module has already moved past SETUP state.
      * 
      * WARNING: Since Hytale's EntityStoreRegistry does not provide an unregister API,
      * ECS systems cannot be unregistered. This system will continue to tick for the
      * lifetime of the server, even if the module is disabled or removed. The system
      * should check the module's state within its tick() method if it needs to be
      * conditionally disabled.
      * 
      * @param system The ECS system to register
      * @throws IllegalStateException if called outside setup phase
      */
     protected fun registerSystem(system: ISystem<EntityStore>) {
         require(state == ModuleState.SETUP) {
             "Cannot register system - module '$id' is in $state state, not SETUP. " +
             "Systems must be registered during onSetup()."
         }
         resourceTracker.trackSystem(system::class.simpleName ?: system::class.java.simpleName)
         context.entityStoreRegistry.registerSystem(system)
     }
    
     /**
      * Helper to register a command.
      * Commands are registered with the plugin's command registry.
      * 
      * NOTE: Must be called during setup phase (onSetup()). Calling from onStart()
      * or later will throw an error as the module has already moved past SETUP state.
      * 
      * @param command The command to register
      * @throws IllegalStateException if called outside setup phase
      */
     protected fun registerCommand(command: AbstractCommand) {
         require(state == ModuleState.SETUP) {
             "Cannot register command '${command.name}' - module '$id' is in $state state, not SETUP. " +
             "Commands must be registered during onSetup()."
         }
         resourceTracker.trackCommand(command.name)
         context.commandRegistry.registerCommand(command)
     }
    
     /**
      * Log a debug message using LoggingManager.
      */
     protected fun debug(message: String) {
         LoggingManager.debug(logger, id) { message }
     }
     
     // ============ Coroutine Lifecycle Helpers ============
     
     /**
      * Safely shutdown a coroutine scope during module shutdown.
      * 
      * This helper ensures:
      * 1. The scope is cancelled (no new work starts)
      * 2. Existing coroutines are given time to finish (respects timeout)
      * 3. Cancellation is logged appropriately
      * 4. Exceptions are logged but don't propagate
      * 
      * Usage in onShutdown():
      * ```kotlin
      * override suspend fun onShutdown() {
      *     // Wait for pending persistence operations with 5 second timeout
      *     shutdownScopeWithTimeout(persistenceScope, "persistence", 5000)
      *     
      *     // Do synchronous cleanup if needed
      *     logger.atFine().log("Cleaning up resources...")
      * }
      * ```
      * 
      * @param scope The coroutine scope to shutdown
      * @param name A human-readable name for logging ("persistence", "background tasks", etc.)
      * @param timeoutMs How long to wait for coroutines to complete (default 5000ms)
      */
     protected suspend fun shutdownScopeWithTimeout(
         scope: CoroutineScope,
         name: String,
         timeoutMs: Long = 5000L
     ) {
         try {
             logger.atFine().log("Waiting for $name scope to complete (timeout: ${timeoutMs}ms)...")
             
             val job = scope.coroutineContext[Job]
             scope.cancel("Module shutting down")
             
             if (job != null) {
                 try {
                     withTimeout(timeoutMs) {
                         job.join()
                     }
                     logger.atFine().log("All $name scope coroutines completed")
                 } catch (e: TimeoutCancellationException) {
                     logger.atWarning().log(
                         "$name scope did not complete within ${timeoutMs}ms - " +
                         "some operations may not have finished. Check logs for incomplete saves."
                     )
                 }
             }
         } catch (e: CancellationException) {
             logger.atFine().log("$name scope cancelled")
         } catch (e: Exception) {
             logger.atWarning().withCause(e).log("Error shutting down $name scope")
         }
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
